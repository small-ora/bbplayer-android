package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.Lyrics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * On-disk cache entry for a track's lyrics. Kept separate from [Lyrics] so provenance (`source`, `fetchedAt`,
 * `query`) can be inspected without changing the frozen core model.
 */
@Serializable
data class CachedLyrics(
    val trackKey: String,
    val lyrics: Lyrics,
    val source: String = "",
    val fetchedAt: Long = 0L,
    val query: String = "",
)

/** JSON codec for [CachedLyrics]. Tolerant of unknown keys so future fields do not invalidate old caches. */
object LyricsCacheCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(entry: CachedLyrics): String = json.encodeToString(CachedLyrics.serializer(), entry)

    /** Returns null for corrupt / incompatible JSON instead of throwing. */
    fun decode(text: String): CachedLyrics? =
        runCatching { json.decodeFromString(CachedLyrics.serializer(), text) }.getOrNull()
}

/** Storage abstraction so the repository can be tested without Android [android.content.Context]. */
interface LyricsCacheStore {
    fun read(trackKey: String): CachedLyrics?
    fun write(entry: CachedLyrics)
    fun clear()
    fun sizeBytes(): Long
}

/**
 * File cache: one JSON file per track under `dir`, named `<sha1(trackKey)>.json`.
 * Writes go through a temp file + rename so a crash cannot leave a half-written cache entry.
 */
class FileLyricsCacheStore(private val dir: File) : LyricsCacheStore {

    private fun fileFor(trackKey: String): File = File(dir, sha1Hex(trackKey) + ".json")

    override fun read(trackKey: String): CachedLyrics? {
        val file = fileFor(trackKey)
        if (!file.isFile) return null
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return LyricsCacheCodec.decode(text)
    }

    override fun write(entry: CachedLyrics) {
        dir.mkdirs()
        val target = fileFor(entry.trackKey)
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(LyricsCacheCodec.encode(entry), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }

    override fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    override fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}

/** In-memory store used by unit tests and as a lightweight fallback. */
class InMemoryLyricsCacheStore : LyricsCacheStore {
    private val entries = LinkedHashMap<String, CachedLyrics>()

    override fun read(trackKey: String): CachedLyrics? = entries[trackKey]

    override fun write(entry: CachedLyrics) {
        entries[entry.trackKey] = entry
    }

    override fun clear() {
        entries.clear()
    }

    override fun sizeBytes(): Long = entries.values.sumOf { LyricsCacheCodec.encode(it).toByteArray(Charsets.UTF_8).size.toLong() }
}

/** Stable, filesystem-safe cache file name for a track key (`bvid:cid`). */
fun sha1Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    val builder = StringBuilder(digest.size * 2)
    for (byte in digest) {
        builder.append("%02x".format(byte.toInt() and 0xFF))
    }
    return builder.toString()
}
