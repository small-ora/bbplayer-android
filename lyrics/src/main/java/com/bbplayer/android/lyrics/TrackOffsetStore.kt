package com.bbplayer.android.lyrics

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Per-track lyric offset is limited to ±5 s (AC G2). */
const val MAX_TRACK_OFFSET_MS: Int = 5_000

/** Clamps a lyric offset into the supported ±[MAX_TRACK_OFFSET_MS] range. */
fun clampTrackOffset(offsetMs: Int): Int = offsetMs.coerceIn(-MAX_TRACK_OFFSET_MS, MAX_TRACK_OFFSET_MS)

/**
 * Persistence of the per-track lyric offset (AC G2: "每首歌词偏移独立保存（切换歌曲后恢复该曲偏移）").
 * Implementations must never throw on corrupt/missing storage and must return `0` for unknown tracks.
 */
interface TrackOffsetStore {

    /** Persisted offset for [trackKey] in ms; `0` when absent. */
    fun get(trackKey: String): Int

    /** Stores (or clears, when clamped to 0) the offset for [trackKey], clamped to ±5 000 ms. */
    fun set(trackKey: String, offsetMs: Int)

    /** Removes every persisted offset. */
    fun clear()

    /** Snapshot of all persisted offsets (clamped). */
    fun snapshot(): Map<String, Int>
}

/** JSON codec for the `trackKey -> offsetMs` map. Tolerant: a corrupt document decodes to an empty map. */
object TrackOffsetCodec {

    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    fun encode(offsets: Map<String, Int>): String =
        JsonObject(offsets.mapValues { JsonPrimitive(clampTrackOffset(it.value)) }).toString()

    fun decode(text: String): Map<String, Int> {
        if (text.isBlank()) return emptyMap()
        val objectValue = runCatching { JSON.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for ((key, element) in objectValue) {
            if (key.isBlank()) continue
            val value = (element as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: continue
            val clamped = clampTrackOffset(value)
            if (clamped != 0) out[key] = clamped
        }
        return out
    }
}

/**
 * File store: a single JSON object `{ "<trackKey>": offsetMs, ... }` at [file]
 * (default `filesDir/lyric_offsets.json`). Writes go through a temp file + rename so a crash cannot truncate the
 * offsets of every track; corrupt files are treated as empty. Thread safe via a monitor.
 */
class FileTrackOffsetStore(private val file: File) : TrackOffsetStore {

    private val lock = Any()

    override fun get(trackKey: String): Int = synchronized(lock) {
        TrackOffsetCodec.decode(readText()).getOrDefault(trackKey, 0)
    }

    override fun set(trackKey: String, offsetMs: Int) {
        if (trackKey.isBlank()) return
        synchronized(lock) {
            val updated = LinkedHashMap(TrackOffsetCodec.decode(readText()))
            val clamped = clampTrackOffset(offsetMs)
            if (clamped == 0) updated.remove(trackKey) else updated[trackKey] = clamped
            writeText(TrackOffsetCodec.encode(updated))
        }
    }

    override fun clear() {
        synchronized(lock) {
            runCatching { if (file.exists()) file.delete() }
        }
    }

    override fun snapshot(): Map<String, Int> = synchronized(lock) { TrackOffsetCodec.decode(readText()) }

    private fun readText(): String {
        if (!file.isFile) return ""
        return runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
    }

    private fun writeText(text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            }
        }
    }
}

/** In-memory store used by unit tests. */
class InMemoryTrackOffsetStore(initial: Map<String, Int> = emptyMap()) : TrackOffsetStore {

    private val offsets = LinkedHashMap<String, Int>()

    init {
        initial.forEach { (key, value) -> set(key, value) }
    }

    override fun get(trackKey: String): Int = synchronized(offsets) { offsets[trackKey] ?: 0 }

    override fun set(trackKey: String, offsetMs: Int) {
        if (trackKey.isBlank()) return
        val clamped = clampTrackOffset(offsetMs)
        synchronized(offsets) {
            if (clamped == 0) offsets.remove(trackKey) else offsets[trackKey] = clamped
        }
    }

    override fun clear() {
        synchronized(offsets) { offsets.clear() }
    }

    override fun snapshot(): Map<String, Int> = synchronized(offsets) { LinkedHashMap(offsets) }
}
