package com.bbplayer.android.playback.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Text fields embedded as iTunes atoms. `null`/blank values are skipped, so a
 * value that is not supplied (or is empty) never overwrites an existing atom.
 */
data class Mp4TagMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val date: String? = null,
)

/**
 * Small, dependency-free ISOBMFF writer that embeds the iTunes-style metadata
 * atoms Bilibili downloads lack: title, artist, album, lyrics and cover art.
 *
 * Only the `moov` box is rewritten; every other top-level box (and every byte
 * after `moov`) is copied verbatim. This is safe for Bilibili's fragmented MP4
 * (`ftyp, moov, sidx, moof…mdat`) because `tfhd` uses `default-base-is-moof`
 * and carries no absolute `base_data_offset`: moof/mdat stay valid when moov
 * grows. `sidx` stores its own size and a first-offset relative to the end of
 * `sidx`, so it does not need patching either.
 *
 * The code is plain Kotlin/JVM (no Android imports) so the atom handling is
 * covered by host JVM tests.
 */
object Mp4TagWriter {

    /** Cover art larger than this is not embedded (keeps moov small/sane). */
    const val MAX_COVER_BYTES: Int = 5 * 1024 * 1024

    /** `data` atom content type for UTF-8 text. */
    private const val DATA_TYPE_UTF8 = 1

    /** `data` atom content type for JPEG. */
    private const val DATA_TYPE_JPEG = 13

    /** `data` atom content type for PNG. */
    private const val DATA_TYPE_PNG = 14

    private const val COPY_BUFFER_SIZE = 64 * 1024

    /**
     * Writes a tagged copy of [source] to [dest].
     *
     * [dest] is only replaced after the complete tagged file has been written
     * to a sibling temporary file, so a failure (malformed source, I/O error,
     * missing moov…) leaves an existing [dest] untouched.
     *
     * Empty/blank [metadata] fields, empty [lyrics] and empty/invalid/oversized
     * [coverBytes] are skipped; any existing atom for such a field is preserved.
     */
    fun prepareTaggedFile(
        source: File,
        dest: File,
        metadata: Mp4TagMetadata,
        coverBytes: ByteArray? = null,
        lyrics: String? = null,
    ): Result<Unit> = try {
        if (!source.isFile) throw Mp4TagException("源文件不存在：${source.name}")
        if (source.length() <= 0L) throw Mp4TagException("源文件为空：${source.name}")
        if (sameFile(source, dest)) throw Mp4TagException("源文件与目标文件相同")

        val cover = encodeCover(coverBytes)
        dest.absoluteFile.parentFile?.let { parent ->
            if (!parent.isDirectory && !parent.mkdirs()) throw Mp4TagException("无法创建导出目录")
        }
        val parent = dest.absoluteFile.parentFile
            ?: throw Mp4TagException("无法解析导出目录")
        val temp = File.createTempFile("bbplayer-tag-", ".m4a", parent)
        try {
            writeTaggedCopy(source, temp, metadata, cover, lyrics)
            moveIntoPlace(temp, dest)
        } finally {
            temp.delete()
        }
        Result.success(Unit)
    } catch (error: Exception) {
        val wrapped = if (error is Mp4TagException) error else Mp4TagException("写入元数据失败", error)
        Result.failure(wrapped)
    }

    // region Public data types

    class Mp4TagException(message: String, cause: Throwable? = null) : IOException(message, cause)

    // endregion

    // region Top-level rewriting

    private data class BoxRef(
        val type: String,
        val offset: Long,
        val headerSize: Int,
        val size: Long,
    ) {
        val payloadOffset: Long get() = offset + headerSize
        val payloadSize: Long get() = size - headerSize
    }

    private fun writeTaggedCopy(
        source: File,
        target: File,
        metadata: Mp4TagMetadata,
        cover: Cover?,
        lyrics: String?,
    ) {
        val boxes = scanTopLevel(source)
        if (boxes.none { it.type == "ftyp" }) {
            throw Mp4TagException("不是有效的 MP4 文件：缺少 ftyp")
        }
        val moov = boxes.singleOrNull { it.type == "moov" }
            ?: throw Mp4TagException("不是有效的 MP4 文件：缺少 moov")
        if (moov.payloadSize <= 0L) throw Mp4TagException("moov 盒为空")
        if (moov.payloadSize > Int.MAX_VALUE) throw Mp4TagException("moov 盒过大")

        val moovBytes = readRange(source, moov.payloadOffset, moov.payloadSize.toInt())
        // A non-fragmented file can carry absolute chunk offsets (stco/co64)
        // into an mdat that follows moov. Inserting bytes into moov would shift
        // those offsets, so refuse instead of producing an unplayable export.
        if (boxes.any { it.type == "mdat" && it.offset > moov.offset } && hasNonZeroChunkOffsets(moovBytes)) {
            throw Mp4TagException("该 MP4 使用绝对 stco/co64 偏移，写入元数据会破坏播放；已跳过")
        }
        val newMoovPayload = rebuildMoov(moovBytes, metadata, cover, lyrics)
        val newMoov = atom("moov", newMoovPayload)

        RandomAccessFile(source, "r").use { input ->
            FileOutputStream(target).use { output ->
                for (box in boxes) {
                    if (box.type == "moov") {
                        output.write(newMoov)
                    } else {
                        input.seek(box.offset)
                        copyExactly(input, output, box.size)
                    }
                }
                output.flush()
            }
        }
    }

    /** Scans the top-level box list without loading the media payloads. */
    private fun scanTopLevel(source: File): List<BoxRef> {
        val length = source.length()
        val boxes = ArrayList<BoxRef>()
        val header = ByteArray(16)
        RandomAccessFile(source, "r").use { input ->
            var offset = 0L
            while (offset < length) {
                val remaining = length - offset
                if (remaining < 8L) throw Mp4TagException("文件尾部存在不完整的盒")
                input.seek(offset)
                input.readFully(header, 0, 8)
                val size32 = u32(header, 0)
                val type = fourCc(header, 4)
                var size = size32
                var headerSize = 8
                when {
                    size32 == 1L -> {
                        if (remaining < 16L) throw Mp4TagException("盒长度字段不完整")
                        input.readFully(header, 8, 8)
                        size = u64(header, 8)
                        headerSize = 16
                    }
                    size32 == 0L -> size = remaining
                }
                if (type == "uuid") headerSize += 16
                if (size < headerSize || size > remaining) {
                    throw Mp4TagException("盒长度非法：$type")
                }
                if (size > Int.MAX_VALUE && type == "moov") {
                    throw Mp4TagException("moov 盒过大")
                }
                boxes += BoxRef(type, offset, headerSize, size)
                offset += size
            }
        }
        return boxes
    }

    private fun readRange(source: File, offset: Long, size: Int): ByteArray {
        val bytes = ByteArray(size)
        RandomAccessFile(source, "r").use { input ->
            input.seek(offset)
            input.readFully(bytes)
        }
        return bytes
    }

    private fun copyExactly(input: RandomAccessFile, output: OutputStream, count: Long) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var remaining = count
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw Mp4TagException("源文件在复制过程中被截断")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun moveIntoPlace(temp: File, dest: File) {
        try {
            Files.move(
                temp.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (unsupported: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sameFile(source: File, dest: File): Boolean = try {
        source.canonicalFile == dest.canonicalFile
    } catch (ignored: IOException) {
        source.absoluteFile == dest.absoluteFile
    }

    // endregion

    // region moov / udta / meta / ilst

    /** Rewrites only the `udta` child of moov; all other children are copied. */
    private fun rebuildMoov(
        moov: ByteArray,
        metadata: Mp4TagMetadata,
        cover: Cover?,
        lyrics: String?,
    ): ByteArray {
        val children = parseBoxes(moov, "moov")
        val existingUdta = children.firstOrNull { it.type == "udta" }
        val newUdta = buildUdta(existingUdta?.let { moov.copyOfRange(it.payloadStart, it.payloadEnd) }, metadata, cover, lyrics)
        val out = ByteArrayOutputStream(moov.size + newUdta.size)
        var udtaWritten = false
        for (child in children) {
            when {
                child.type == "udta" && !udtaWritten -> {
                    out.write(newUdta)
                    udtaWritten = true
                }
                child.type == "udta" -> Unit // collapse accidental duplicate udta boxes
                else -> out.write(moov, child.start, child.size)
            }
        }
        if (!udtaWritten) out.write(newUdta)
        return out.toByteArray()
    }

    private fun buildUdta(
        existingPayload: ByteArray?,
        metadata: Mp4TagMetadata,
        cover: Cover?,
        lyrics: String?,
    ): ByteArray {
        val existingChildren = existingPayload?.let { parseBoxes(it, "udta") }.orEmpty()
        val existingMeta = existingChildren.firstOrNull { it.type == "meta" }
        val newMeta = buildMeta(
            existingMeta?.let { existingPayload!!.copyOfRange(it.payloadStart, it.payloadEnd) },
            metadata,
            cover,
            lyrics,
        )
        val payload = ByteArrayOutputStream()
        var metaWritten = false
        for (child in existingChildren) {
            if (child.type == "meta") {
                if (!metaWritten) {
                    payload.write(newMeta)
                    metaWritten = true
                }
            } else {
                payload.write(existingPayload!!, child.start, child.size)
            }
        }
        if (!metaWritten) payload.write(newMeta)
        return atom("udta", payload.toByteArray())
    }

    private fun buildMeta(
        existingPayload: ByteArray?,
        metadata: Mp4TagMetadata,
        cover: Cover?,
        lyrics: String?,
    ): ByteArray {
        if (existingPayload != null && existingPayload.size < 4) {
            throw Mp4TagException("meta 盒缺少 full-box 头")
        }
        val versionFlags = existingPayload?.copyOfRange(0, 4) ?: ByteArray(4)
        val existingChildren = existingPayload?.let {
            parseBoxes(it, "meta", from = 4, to = it.size)
        }.orEmpty()
        val existingIlst = existingChildren.firstOrNull { it.type == "ilst" }
        val newIlst = buildIlst(
            existingIlst?.let { existingPayload!!.copyOfRange(it.payloadStart, it.payloadEnd) },
            metadata,
            cover,
            lyrics,
        )

        val payload = ByteArrayOutputStream()
        payload.write(versionFlags)
        // Always emit one canonical hdlr first; iTunes expects mdir/appl and
        // players ignore a container whose handler says something else.
        payload.write(canonicalHdlr())
        for (child in existingChildren) {
            // hdlr/ilst are rebuilt above; every other meta child is preserved.
            if (child.type != "hdlr" && child.type != "ilst") {
                payload.write(existingPayload!!, child.start, child.size)
            }
        }
        payload.write(newIlst)
        return atom("meta", payload.toByteArray())
    }

    private fun buildIlst(
        existingPayload: ByteArray?,
        metadata: Mp4TagMetadata,
        cover: Cover?,
        lyrics: String?,
    ): ByteArray {
        val existingChildren = existingPayload?.let { parseBoxes(it, "ilst") }.orEmpty()
        val replacements = LinkedHashMap<String, ByteArray>()
        metadata.title?.takeIf { it.isNotBlank() }?.let { replacements["©nam"] = textAtom(it, "©nam") }
        metadata.artist?.takeIf { it.isNotBlank() }?.let { replacements["©ART"] = textAtom(it, "©ART") }
        metadata.album?.takeIf { it.isNotBlank() }?.let { replacements["©alb"] = textAtom(it, "©alb") }
        lyrics?.takeIf { it.isNotBlank() }?.let { replacements["©lyr"] = textAtom(it, "©lyr") }
        metadata.date?.takeIf { it.isNotBlank() }?.let { replacements["©day"] = textAtom(it, "©day") }
        metadata.genre?.takeIf { it.isNotBlank() }?.let { replacements["©gen"] = textAtom(it, "©gen") }
        cover?.let { replacements["covr"] = coverAtom(it) }

        val payload = ByteArrayOutputStream()
        val written = HashSet<String>()
        for (child in existingChildren) {
            val replacement = replacements[child.type]
            if (replacement != null) {
                // Replace at the original position, and drop duplicate copies of
                // the same managed atom (idempotent re-tagging).
                if (written.add(child.type)) payload.write(replacement)
            } else {
                payload.write(existingPayload!!, child.start, child.size)
            }
        }
        for ((type, bytes) in replacements) {
            if (written.add(type)) payload.write(bytes)
        }
        return atom("ilst", payload.toByteArray())
    }

    private fun canonicalHdlr(): ByteArray = atom(
        "hdlr",
        // version/flags, pre_defined, handler_type 'mdir', reserved 'appl' + 8 zeros, name
        u32(0) + u32(0) + "mdir".toByteArray(StandardCharsets.ISO_8859_1) +
            "appl".toByteArray(StandardCharsets.ISO_8859_1) + ByteArray(8) + byteArrayOf(0),
    )

    private fun textAtom(value: String, type: String): ByteArray =
        atom(type, dataAtom(DATA_TYPE_UTF8, value.toByteArray(StandardCharsets.UTF_8)))

    private fun coverAtom(cover: Cover): ByteArray =
        atom("covr", dataAtom(cover.dataType, cover.bytes))

    private fun dataAtom(dataType: Int, value: ByteArray): ByteArray =
        atom("data", u32(dataType) + u32(0) + value)

    // endregion

    // region Chunk-offset safety

    private val CHUNK_CONTAINERS = setOf("trak", "mdia", "minf", "stbl")

    /** True when any stco/co64 entry is non-zero (i.e. an absolute file offset). */
    private fun hasNonZeroChunkOffsets(moov: ByteArray): Boolean =
        parseBoxes(moov, "moov").any { containsNonZeroChunkOffset(moov, it) }

    private fun containsNonZeroChunkOffset(bytes: ByteArray, box: ParsedBox): Boolean = when (box.type) {
        "stco", "co64" -> chunkOffsetsNonZero(bytes, box)
        in CHUNK_CONTAINERS -> parseBoxes(bytes, box.type, box.payloadStart, box.payloadEnd)
            .any { containsNonZeroChunkOffset(bytes, it) }
        else -> false
    }

    private fun chunkOffsetsNonZero(bytes: ByteArray, box: ParsedBox): Boolean {
        val payload = box.payloadStart
        if (box.payloadEnd - payload < 8) throw Mp4TagException("${box.type} 盒不完整")
        val count = u32(bytes, payload + 4)
        val entrySize = if (box.type == "stco") 4 else 8
        if (count > (box.payloadEnd - payload - 8) / entrySize) {
            throw Mp4TagException("${box.type} 条目数超出盒长度")
        }
        var offset = payload + 8
        repeat(count.toInt()) {
            val value = if (box.type == "stco") u32(bytes, offset) else u64(bytes, offset)
            if (value != 0L) return true
            offset += entrySize
        }
        return false
    }

    // endregion

    // region Box helpers

    private data class ParsedBox(
        val type: String,
        val start: Int,
        val headerSize: Int,
        val size: Int,
    ) {
        val payloadStart: Int get() = start + headerSize
        val payloadEnd: Int get() = start + size
    }

    private fun parseBoxes(
        bytes: ByteArray,
        parent: String,
        from: Int = 0,
        to: Int = bytes.size,
    ): List<ParsedBox> {
        val boxes = ArrayList<ParsedBox>()
        var offset = from
        while (offset < to) {
            if (to - offset < 8) throw Mp4TagException("$parent 内盒不完整")
            val size32 = u32(bytes, offset)
            val type = fourCc(bytes, offset + 4)
            var size = size32
            var headerSize = 8
            when {
                size32 == 1L -> {
                    if (to - offset < 16) throw Mp4TagException("$parent 内盒长度字段不完整")
                    size = u64(bytes, offset + 8)
                    headerSize = 16
                }
                size32 == 0L -> size = (to - offset).toLong()
            }
            if (type == "uuid") headerSize += 16
            if (size < headerSize || size > (to - offset).toLong()) {
                throw Mp4TagException("$parent 内盒长度非法：$type")
            }
            boxes += ParsedBox(type, offset, headerSize, size.toInt())
            offset += size.toInt()
        }
        return boxes
    }

    private fun atom(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.ISO_8859_1)
        if (typeBytes.size != 4) throw Mp4TagException("非法 atom 名：$type")
        val total = 8L + payload.size
        if (total > Int.MAX_VALUE) throw Mp4TagException("atom 过大：$type")
        val out = ByteArrayOutputStream(total.toInt())
        out.write(u32(total.toInt()))
        out.write(typeBytes)
        out.write(payload)
        return out.toByteArray()
    }

    private fun u32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun u64(bytes: ByteArray, offset: Int): Long {
        val high = u32(bytes, offset)
        if (high > Int.MAX_VALUE.toLong()) throw Mp4TagException("盒长度超出 JVM 限制")
        return (high shl 32) or u32(bytes, offset + 4)
    }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun fourCc(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, StandardCharsets.ISO_8859_1)

    // endregion

    private data class Cover(val bytes: ByteArray, val dataType: Int)

    private fun encodeCover(bytes: ByteArray?): Cover? {
        if (bytes == null || bytes.isEmpty()) return null
        if (bytes.size > MAX_COVER_BYTES) return null
        return when {
            isJpeg(bytes) -> Cover(bytes, DATA_TYPE_JPEG)
            isPng(bytes) -> Cover(bytes, DATA_TYPE_PNG)
            else -> null
        }
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()

    private fun isPng(bytes: ByteArray): Boolean {
        val magic = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        if (bytes.size < magic.size) return false
        for (index in magic.indices) if (bytes[index] != magic[index]) return false
        return true
    }
}
