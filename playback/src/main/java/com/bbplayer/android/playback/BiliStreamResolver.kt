package com.bbplayer.android.playback

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.AudioStream
import com.bbplayer.android.core.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves fresh Bilibili audio stream URLs through [MusicRepository] and keeps
 * a small in-memory cache (key = track key + requested quality, TTL ≈ 90 min).
 *
 * Failure policy:
 *  - a requested quality that fails is retried once with `quality = null`
 *    (repository default = highest available) per SPEC C7;
 *  - after that, an [AppException] is thrown with a Chinese user message.
 */
class BiliStreamResolver(
    private val musicRepository: MusicRepository,
    private val ttlMs: Long = PlaybackConfig.STREAM_CACHE_TTL_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val stream: AudioStream, val resolvedAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val resolveMutex = Mutex()

    /** Distinct cache slot per quality; `quality == null` is the repository default. */
    fun cacheKey(trackKey: String, quality: AudioQuality?): String =
        "$trackKey@${quality?.id ?: 0}"

    fun cached(track: Track, quality: AudioQuality?): AudioStream? =
        cachedByKey(cacheKey(track.key, quality))

    private fun cachedByKey(key: String): AudioStream? {
        val entry = cache[key] ?: return null
        if (isExpired(entry)) {
            cache.remove(key, entry)
            return null
        }
        return entry.stream
    }

    private fun isExpired(entry: Entry): Boolean = now() - entry.resolvedAtMs >= ttlMs

    /**
     * Returns a playable stream, serving from cache when fresh.
     * @throws AppException when the repository cannot resolve a URL.
     */
    suspend fun resolve(
        track: Track,
        quality: AudioQuality? = null,
        forceRefresh: Boolean = false,
    ): AudioStream {
        val key = cacheKey(track.key, quality)
        if (!forceRefresh) {
            cachedByKey(key)?.let { return it }
        }
        return resolveMutex.withLock {
            // Another coroutine may have resolved while we waited for the lock.
            if (!forceRefresh) {
                cachedByKey(key)?.let { return@withLock it }
            }
            val stream = fetch(track, quality)
            cache[key] = Entry(stream = stream, resolvedAtMs = now())
            stream
        }
    }

    /**
     * Drops cached entries for [trackKey] (all qualities). Called when a CDN URL
     * starts returning 403/410 so the next open forces a fresh playurl request.
     */
    fun invalidate(trackKey: String) {
        val prefix = "$trackKey@"
        cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    fun clear() = cache.clear()

    val size: Int get() = cache.size

    private suspend fun fetch(track: Track, quality: AudioQuality?): AudioStream {
        val primary = musicRepository.getAudioStream(track, quality)
        primary.getOrNull()?.takeIf { it.url.isNotBlank() }?.let { return it }

        // SPEC C7: requested tier unavailable/failed -> fall back to the default
        // (highest anonymous-safe) quality instead of failing playback.
        if (quality != null) {
            val fallback = musicRepository.getAudioStream(track, null)
            fallback.getOrNull()?.takeIf { it.url.isNotBlank() }?.let { return it }
            throw toAppException(fallback.exceptionOrNull() ?: primary.exceptionOrNull())
        }
        throw toAppException(primary.exceptionOrNull())
    }

    private fun toAppException(cause: Throwable?): AppException {
        if (cause is AppException) return cause
        val userMessage = cause?.toUserMessage()?.takeIf { it.isNotBlank() }
            ?: PlaybackErrorMapper.MSG_RESOLVE
        // A network error message alone is fine; prefix it for a clearer player
        // error surface while keeping the retry classification.
        return AppException(userMessage = "音频解析失败：$userMessage", cause2 = cause, retryable = true)
    }
}
