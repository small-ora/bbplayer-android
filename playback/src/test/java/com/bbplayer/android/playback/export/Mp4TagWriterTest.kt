package com.bbplayer.android.playback.export

import com.bbplayer.android.playback.export.Mp4TagWriter.Mp4TagException
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM coverage for [Mp4TagWriter] against a synthetic fragmented MP4
 * (`ftyp, moov, sidx, moof…mdat`, like a Bilibili m4s). The read-back side is an
 * independent test parser, so a bug in the writer cannot hide behind its own
 * reader.
 */
class Mp4TagWriterTest {

    private lateinit var dir: File
    private var fileCounter = 0

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("bbplayer-mp4-tag").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    // region Writing happy paths

    @Test
    fun `writes title artist album lyrics and jpeg cover`() {
        val cover = jpegBytes()
        val out = tag(
            source = syntheticFmp4(),
            metadata = Mp4TagMetadata(
                title = "夜曲",
                artist = "周杰伦",
                album = "十一月的萧邦",
            ),
            cover = cover,
            lyrics = "[00:01.00]一群嗜血的蚂蚁",
        )

        assertEquals("夜曲", text(out, "©nam"))
        assertEquals("周杰伦", text(out, "©ART"))
        assertEquals("十一月的萧邦", text(out, "©alb"))
        assertEquals("[00:01.00]一群嗜血的蚂蚁", text(out, "©lyr"))
        val parsedCover = cover(out)
        assertNotNull(parsedCover)
        assertEquals(13, parsedCover!!.dataType)
        assertArrayEquals(cover, parsedCover.bytes)
    }

    @Test
    fun `round trips multi line UTF-8 lyrics and optional day genre`() {
        val lyrics = "[ti:测试]\n[00:01.00]第一行\n[00:02.50]第二行 – with dash"
        val out = tag(
            source = syntheticFmp4(),
            metadata = Mp4TagMetadata(
                title = "标题",
                artist = "艺术家",
                album = "专辑",
                date = "2024-03-01",
                genre = "J-Pop",
            ),
            lyrics = lyrics,
        )

        assertEquals(lyrics, text(out, "©lyr"))
        assertEquals("2024-03-01", text(out, "©day"))
        assertEquals("J-Pop", text(out, "©gen"))
    }

    @Test
    fun `adds udta meta hdlr and ilst when moov has none`() {
        val out = tag(syntheticFmp4(), metadata = Mp4TagMetadata(title = "T"))

        val udta = moovChildren(out).singleOrNull { it.type == "udta" }
        assertNotNull("udta should be added", udta)
        val meta = rawBoxes(udta!!.payload).singleOrNull { it.type == "meta" }
        assertNotNull("meta should be added", meta)
        assertEquals(0, intAt(meta!!.payload, 0)) // full-box version/flags
        val metaChildren = rawBoxes(meta.payload.copyOfRange(4, meta.payload.size))
        assertEquals("mdir", handlerType(metaChildren))
        val ilst = metaChildren.singleOrNull { it.type == "ilst" }
        assertNotNull("ilst should be added", ilst)
        assertTrue(rawBoxes(ilst!!.payload).any { it.type == "©nam" })
    }

    @Test
    fun `canonical hdlr is a 33 byte mdir appl box`() {
        val out = tag(syntheticFmp4(), metadata = Mp4TagMetadata(title = "T"))
        val hdlr = metaChildren(out).single { it.type == "hdlr" }

        assertEquals(33, hdlr.payload.size + 8)
        // full-box 4 + pre_defined 4 + handler_type 4 + reserved 12 + name 1
        assertEquals(25, hdlr.payload.size)
        assertEquals("mdir", fourCc(hdlr.payload, 8))
        assertEquals("appl", fourCc(hdlr.payload, 12))
        assertEquals(0, hdlr.payload.last().toInt())
    }

    @Test
    fun `replaces existing managed atom in place without duplicates`() {
        val existing = syntheticFmp4(
            existingUdta = udta(
                meta(
                    hdlr() + ilst(
                        textAtom("©nam", "Old title"),
                        textAtom("©ART", "Old artist"),
                    ),
                ),
            ),
        )

        val out = tag(existing, metadata = Mp4TagMetadata(title = "New title"))

        val atoms = ilstAtoms(out)
        assertEquals(1, atoms.count { it.type == "©nam" })
        assertEquals("New title", text(out, "©nam"))
        assertEquals("Old artist", text(out, "©ART")) // not supplied -> preserved
        assertEquals(1, atoms.count { it.type == "©ART" })
    }

    @Test
    fun `replaces duplicate managed atoms with a single copy`() {
        val existing = syntheticFmp4(
            existingUdta = udta(
                meta(
                    hdlr() + ilst(
                        textAtom("©nam", "one"),
                        textAtom("©nam", "two"),
                        textAtom("©nam", "three"),
                    ),
                ),
            ),
        )

        val out = tag(existing, metadata = Mp4TagMetadata(title = "only"))

        assertEquals(1, ilstAtoms(out).count { it.type == "©nam" })
        assertEquals("only", text(out, "©nam"))
    }

    @Test
    fun `preserves unknown ilst atoms and their payloads`() {
        val tooPayload = dataAtom(1, "Lavf59.27.100".toByteArray(StandardCharsets.UTF_8))
        val descPayload = dataAtom(1, "Packed by Bilibili".toByteArray(StandardCharsets.UTF_8))
        val customPayload = byteArrayOf(1, 2, 3, 4, 5)
        val existing = syntheticFmp4(
            existingUdta = udta(
                meta(
                    hdlr() + ilst(
                        box("©too", tooPayload),
                        box("desc", descPayload),
                        box("zzzz", customPayload),
                    ),
                ),
            ),
        )

        val out = tag(existing, metadata = Mp4TagMetadata(title = "T"))
        val atoms = ilstAtoms(out)

        assertArrayEquals(tooPayload, atoms.single { it.type == "©too" }.payload)
        assertArrayEquals(descPayload, atoms.single { it.type == "desc" }.payload)
        assertArrayEquals(customPayload, atoms.single { it.type == "zzzz" }.payload)
        assertEquals("T", text(out, "©nam"))
    }

    @Test
    fun `adds ilst when an existing meta only has hdlr`() {
        val hdlr = hdlr()
        val existing = syntheticFmp4(existingUdta = udta(meta(hdlr)))

        val out = tag(existing, metadata = Mp4TagMetadata(title = "T"))

        assertEquals("T", text(out, "©nam"))
        assertEquals("mdir", handlerType(metaChildren(out)))
        assertArrayEquals(boxPayload(hdlr), metaChildren(out).single { it.type == "hdlr" }.payload)
    }

    @Test
    fun `adds meta to an existing udta while preserving its other children`() {
        val namePayload = "keep me".toByteArray(StandardCharsets.UTF_8)
        val existing = syntheticFmp4(existingUdta = udta(box("name", namePayload)))

        val out = tag(existing, metadata = Mp4TagMetadata(title = "T"))

        val udta = moovChildren(out).single { it.type == "udta" }
        val children = rawBoxes(udta.payload)
        assertArrayEquals(namePayload, children.single { it.type == "name" }.payload)
        assertNotNull(children.singleOrNull { it.type == "meta" })
        assertEquals("T", text(out, "©nam"))
    }

    @Test
    fun `preserves moov children other than udta byte for byte`() {
        val source = syntheticFmp4()
        val before = moovChildren(source)
        val out = tag(source, metadata = Mp4TagMetadata(title = "T"))
        val after = moovChildren(out)

        for (type in listOf("mvhd", "trak", "mvex")) {
            assertArrayEquals(
                before.single { it.type == type }.payload,
                after.single { it.type == type }.payload,
            )
        }
    }

    @Test
    fun `png cover uses data type 14`() {
        val cover = pngBytes()
        val out = tag(syntheticFmp4(), metadata = Mp4TagMetadata(title = "T"), cover = cover)
        val parsed = cover(out)

        assertNotNull(parsed)
        assertEquals(14, parsed!!.dataType)
        assertArrayEquals(cover, parsed.bytes)
    }

    @Test
    fun `zero stco offsets are accepted for fragmented mp4`() {
        val source = ftyp() +
            box("moov", mvhd() + trakWithStco(0) + mvex()) +
            tail()

        val out = tag(source, metadata = Mp4TagMetadata(title = "T"))

        assertEquals("T", text(out, "©nam"))
    }

    // endregion

    // region Skipping rules

    @Test
    fun `empty cover blank lyrics and blank text fields are skipped`() {
        val out = tag(
            source = syntheticFmp4(),
            metadata = Mp4TagMetadata(title = "   ", artist = "", album = null),
            cover = ByteArray(0),
            lyrics = " \n\t ",
        )

        assertEquals(0, ilstAtoms(out).size)
    }

    @Test
    fun `cover larger than 5 MiB is skipped but text tags are written`() {
        val oversized = jpegBytes() + ByteArray(Mp4TagWriter.MAX_COVER_BYTES)
        assertTrue(oversized.size > Mp4TagWriter.MAX_COVER_BYTES)

        val out = tag(syntheticFmp4(), metadata = Mp4TagMetadata(title = "T"), cover = oversized)

        assertNull(cover(out))
        assertEquals("T", text(out, "©nam"))
    }

    @Test
    fun `unknown image format skips only the cover`() {
        val gif = "GIF89a not a jpeg or png".toByteArray(StandardCharsets.US_ASCII)
        val out = tag(
            syntheticFmp4(),
            metadata = Mp4TagMetadata(title = "T", artist = "A"),
            cover = gif,
        )

        assertNull(cover(out))
        assertEquals("T", text(out, "©nam"))
        assertEquals("A", text(out, "©ART"))
    }

    @Test
    fun `re-tagging with blank fields keeps existing atoms`() {
        val firstSource = File(dir, "re-tag-source.m4s").apply { writeBytes(syntheticFmp4()) }
        val first = tag(firstSource, metadata = Mp4TagMetadata(title = "T", artist = "A"))
        val second = tag(first, dest = "second.m4a", metadata = Mp4TagMetadata(), lyrics = "").readBytes()

        assertEquals("T", text(second, "©nam"))
        assertEquals("A", text(second, "©ART"))
    }

    // endregion

    // region Failures and file integrity

    @Test
    fun `non mp4 source fails without touching an existing destination`() {
        val source = File(dir, "not-mp4.m4s").apply {
            writeText("this is definitely not an ISOBMFF file")
        }
        val dest = File(dir, "existing.m4a").apply { writeText("keep") }

        val result = Mp4TagWriter.prepareTaggedFile(source, dest, Mp4TagMetadata(title = "T"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Mp4TagException)
        assertEquals("keep", dest.readText())
    }

    @Test
    fun `mp4 without moov fails`() {
        val source = File(dir, "no-moov.m4s").apply {
            writeBytes(ftyp() + box("mdat", ByteArray(16)))
        }

        val result = Mp4TagWriter.prepareTaggedFile(
            source,
            File(dir, "out.m4a"),
            Mp4TagMetadata(title = "T"),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Mp4TagException)
    }

    @Test
    fun `truncated box fails`() {
        val whole = syntheticFmp4()
        val source = File(dir, "truncated.m4s").apply { writeBytes(whole.copyOf(whole.size - 20)) }

        val result = Mp4TagWriter.prepareTaggedFile(
            source,
            File(dir, "out.m4a"),
            Mp4TagMetadata(title = "T"),
        )

        assertTrue(result.isFailure)
        assertFalse(File(dir, "out.m4a").exists())
    }

    @Test
    fun `absolute stco offsets reject tagging instead of breaking playback`() {
        val source = File(dir, "absolute-stco.m4s").apply {
            writeBytes(
                ftyp() +
                    box("moov", mvhd() + trakWithStco(0x1000) + mvex()) +
                    box("mdat", ByteArray(32)),
            )
        }
        val dest = File(dir, "absolute-stco-out.m4a")

        val result = Mp4TagWriter.prepareTaggedFile(source, dest, Mp4TagMetadata(title = "T"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Mp4TagException)
        assertFalse(dest.exists())
    }

    @Test
    fun `source and destination being the same file fails`() {
        val source = File(dir, "same.m4s").apply { writeBytes(syntheticFmp4()) }
        val original = source.readBytes()

        val result = Mp4TagWriter.prepareTaggedFile(source, source, Mp4TagMetadata(title = "T"))

        assertTrue(result.isFailure)
        assertArrayEquals(original, source.readBytes())
    }

    @Test
    fun `second write is byte-for-byte idempotent`() {
        val metadata = Mp4TagMetadata(title = "T", artist = "A", album = "B")
        val firstSource = File(dir, "idempotent-source.m4s").apply { writeBytes(syntheticFmp4()) }
        val first = tag(firstSource, metadata = metadata, cover = pngBytes(), lyrics = "[00:01]x")
        val second = tag(first, dest = "second.m4a", metadata = metadata, cover = pngBytes(), lyrics = "[00:01]x")

        assertArrayEquals(first.readBytes(), second.readBytes())
        assertEquals(1, ilstAtoms(second.readBytes()).count { it.type == "©nam" })
    }

    @Test
    fun `tail boxes are copied verbatim and only moov changes size`() {
        val sourceBytes = syntheticFmp4()
        val source = File(dir, "tail-source.m4s").apply { writeBytes(sourceBytes) }
        val dest = File(dir, "tail-out.m4a")
        val result = Mp4TagWriter.prepareTaggedFile(
            source,
            dest,
            Mp4TagMetadata(title = "T", artist = "A"),
            jpegBytes(),
            "[00:01]x",
        )
        assertTrue(result.isSuccess)
        val outBytes = dest.readBytes()

        assertArrayEquals(tail(sourceBytes), tail(outBytes))
        val oldMoov = rawRanges(sourceBytes).single { it.type == "moov" }.size
        val newMoov = rawRanges(outBytes).single { it.type == "moov" }.size
        assertEquals(oldMoov + (outBytes.size - sourceBytes.size), newMoov)
    }

    @Test
    fun `top level ftyp and media payload remain identical`() {
        val sourceBytes = syntheticFmp4()
        val out = tag(sourceBytes, metadata = Mp4TagMetadata(title = "T"))

        val before = rawRanges(sourceBytes)
        val after = rawRanges(out)
        assertEquals(before.first().type, after.first().type)
        assertArrayEquals(
            before.single { it.type == "ftyp" }.payload(sourceBytes),
            after.single { it.type == "ftyp" }.payload(out),
        )
        assertArrayEquals(
            before.single { it.type == "mdat" }.payload(sourceBytes),
            after.single { it.type == "mdat" }.payload(out),
        )
    }

    // endregion

    // region Helpers: synthetic fMP4 construction

    private fun tag(
        source: ByteArray,
        metadata: Mp4TagMetadata,
        cover: ByteArray? = null,
        lyrics: String? = null,
        dest: String = "out.m4a",
    ): ByteArray {
        val sourceFile = File(dir, "source-${fileCounter++}.m4s").apply { writeBytes(source) }
        return tag(sourceFile, metadata, cover, lyrics, dest).readBytes()
    }

    private fun tag(
        source: File,
        metadata: Mp4TagMetadata,
        cover: ByteArray? = null,
        lyrics: String? = null,
        dest: String = "out.m4a",
    ): File {
        val destFile = File(dir, "tagged-${fileCounter++}-$dest")
        val result = Mp4TagWriter.prepareTaggedFile(source, destFile, metadata, cover, lyrics)
        assertTrue("tagging failed: ${result.exceptionOrNull()}", result.isSuccess)
        return destFile
    }

    private fun syntheticFmp4(existingUdta: ByteArray? = null): ByteArray {
        val moovPayload = mvhd() + trak() + mvex() + (existingUdta ?: ByteArray(0))
        return ftyp() + box("moov", moovPayload) + tail()
    }

    private fun ftyp(): ByteArray =
        box(
            "ftyp",
            "isom".toByteArray(StandardCharsets.ISO_8859_1) +
                be32(0x200) +
                "isomiso2m4a".toByteArray(StandardCharsets.ISO_8859_1),
        )

    private fun mvhd(): ByteArray = box("mvhd", ByteArray(100) { (it * 7).toByte() })

    private fun trak(): ByteArray = box("trak", box("tkhd", ByteArray(84) { (it * 3).toByte() }))

    private fun trakWithStco(offset: Int): ByteArray = box(
        "trak",
        box("mdia", box("minf", box("stbl", box("stco", be32(0) + be32(1) + be32(offset))))),
    )

    private fun mvex(): ByteArray = box("mvex", box("trex", ByteArray(24)))

    /** Fragmented tail: sidx + moof with a default-base-is-moof tfhd + mdat. */
    private fun tail(): ByteArray =
        box("sidx", ByteArray(32) { (it * 11).toByte() }) +
            box(
                "moof",
                box("mfhd", be32(1)) +
                    box(
                        "traf",
                        // flags 0x2002a: default-base-is-moof, no base_data_offset.
                        box("tfhd", be32(0x0002002a) + be32(1)),
                    ),
            ) +
            box("mdat", ByteArray(4096) { (it % 251).toByte() })

    private fun udta(vararg children: ByteArray): ByteArray = box("udta", children.concat())

    private fun meta(vararg children: ByteArray, versionFlags: Int = 0): ByteArray =
        box("meta", be32(versionFlags) + children.concat())

    private fun hdlr(handler: String = "mdir"): ByteArray = box(
        "hdlr",
        ByteArray(4) +
            ByteArray(4) +
            handler.toByteArray(StandardCharsets.ISO_8859_1) +
            "appl".toByteArray(StandardCharsets.ISO_8859_1) +
            ByteArray(8) +
            byteArrayOf(0),
    )

    private fun ilst(vararg atoms: ByteArray): ByteArray = box("ilst", atoms.concat())

    private fun textAtom(type: String, value: String): ByteArray =
        box(type, dataAtom(1, value.toByteArray(StandardCharsets.UTF_8)))

    private fun dataAtom(type: Int, value: ByteArray): ByteArray = box("data", be32(type) + be32(0) + value)

    private fun box(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.ISO_8859_1)
        require(typeBytes.size == 4) { "bad box type: $type" }
        return be32(8 + payload.size) + typeBytes + payload
    }

    private fun boxPayload(fullBox: ByteArray): ByteArray =
        fullBox.copyOfRange(8, fullBox.size)

    private fun be32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun jpegBytes(): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
            "fake-jpeg-payload".toByteArray(StandardCharsets.US_ASCII)

    private fun pngBytes(): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            "fake-png-payload".toByteArray(StandardCharsets.US_ASCII)

    private fun Array<out ByteArray>.concat(): ByteArray {
        val out = ByteArrayOutputStream()
        for (part in this) out.write(part)
        return out.toByteArray()
    }

    // endregion

    // region Helpers: independent MP4 reader

    private data class RawBox(val type: String, val payload: ByteArray)

    private data class RawRange(val type: String, val start: Int, val size: Int) {
        fun payload(source: ByteArray): ByteArray = source.copyOfRange(start + 8, start + size)
    }

    private fun rawRanges(source: ByteArray): List<RawRange> {
        val boxes = ArrayList<RawRange>()
        var offset = 0
        while (offset < source.size) {
            require(source.size - offset >= 8) { "truncated box header at $offset" }
            val size = readBe32(source, offset)
            val type = fourCc(source, offset + 4)
            require(size >= 8 && offset + size <= source.size) { "bad box $type size $size at $offset" }
            boxes += RawRange(type, offset, size)
            offset += size
        }
        return boxes
    }

    private fun rawBoxes(payload: ByteArray): List<RawBox> =
        rawRanges(payload).map { RawBox(it.type, payload.copyOfRange(it.start + 8, it.start + it.size)) }

    private fun moovChildren(source: ByteArray): List<RawBox> =
        rawBoxes(rawRanges(source).single { it.type == "moov" }.payload(source))

    private fun metaChildren(source: ByteArray): List<RawBox> {
        val udta = moovChildren(source).single { it.type == "udta" }
        val meta = rawBoxes(udta.payload).single { it.type == "meta" }
        return rawBoxes(meta.payload.copyOfRange(4, meta.payload.size))
    }

    private fun handlerType(children: List<RawBox>): String? {
        val hdlr = children.firstOrNull { it.type == "hdlr" } ?: return null
        return fourCc(hdlr.payload, 8)
    }

    private fun ilstAtoms(source: ByteArray): List<RawBox> {
        val ilst = metaChildren(source).firstOrNull { it.type == "ilst" } ?: return emptyList()
        return rawBoxes(ilst.payload)
    }

    private fun text(source: ByteArray, type: String): String? {
        val atom = ilstAtoms(source).firstOrNull { it.type == type } ?: return null
        val data = rawBoxes(atom.payload).firstOrNull { it.type == "data" } ?: return null
        return String(data.payload, 8, data.payload.size - 8, StandardCharsets.UTF_8)
    }

    private data class ParsedCover(val dataType: Int, val bytes: ByteArray)

    private fun cover(source: ByteArray): ParsedCover? {
        val atom = ilstAtoms(source).firstOrNull { it.type == "covr" } ?: return null
        val data = rawBoxes(atom.payload).firstOrNull { it.type == "data" } ?: return null
        return ParsedCover(intAt(data.payload, 0), data.payload.copyOfRange(8, data.payload.size))
    }

    private fun tail(source: ByteArray): ByteArray {
        val ranges = rawRanges(source)
        val moovIndex = ranges.indexOfFirst { it.type == "moov" }
        val out = ByteArrayOutputStream()
        ranges.drop(moovIndex + 1).forEach { out.write(source, it.start, it.size) }
        return out.toByteArray()
    }

    private fun intAt(bytes: ByteArray, offset: Int): Int =
        readBe32(bytes, offset)

    private fun readBe32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun fourCc(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, StandardCharsets.ISO_8859_1)

    // endregion
}
