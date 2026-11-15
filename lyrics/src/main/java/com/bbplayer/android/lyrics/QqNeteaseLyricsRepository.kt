package com.bbplayer.android.lyrics

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.LyricsRepository
import com.bbplayer.android.core.model.LyricCandidate
import com.bbplayer.android.core.model.Lyrics
import com.bbplayer.android.core.model.Track
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Default [LyricsRepository]: QQ Music first, NetEase Cloud Music second, manual paste and a JSON file cache.
 *
 * Acquisition order of [fetch]:
 *  1. cache (`context.filesDir/lyrics/<sha1(trackKey)>.json`) -> return immediately;
 *  2. QQ Music: search with the cleaned title, score candidates (title 0.75 / artist 0.25, threshold 0.5),
 *     download the best match's lyric, parse (main LRC + optional `trans` translation), cache, return;
 *  3. NetEase: same flow with `lrc.lyric` + `tlyric.lyric`;
 *  4. otherwise `Result.failure(AppException("未找到歌词"))`.
 *
 * Network failures of one provider never abort the fallback chain; `CancellationException` is always rethrown.
 * All endpoints are anonymous, read-only GETs; requests carry a desktop UA + provider Referer and an 8s timeout.
 *
 * @param context Android context used for the cache directory.
 * @param httpClient shared OkHttp client (8s timeouts by default).
 * @param cacheStore cache storage; defaults to [FileLyricsCacheStore] under `filesDir/lyrics`.
 * @param clock wall clock used for cache `fetchedAt` (injectable for tests).
 */
class QqNeteaseLyricsRepository(
    context: Context,
    private val httpClient: OkHttpClient = LyricsHttp.defaultClient(),
    private val cacheStore: LyricsCacheStore = FileLyricsCacheStore(File(context.filesDir, CACHE_DIR)),
    private val clock: () -> Long = System::currentTimeMillis,
) : LyricsRepository {

    companion object {
        const val CACHE_DIR = "lyrics"
        const val SOURCE_QQ = QqMusicApi.SOURCE
        const val SOURCE_NETEASE = NeteaseApi.SOURCE
        const val SOURCE_MANUAL = "manual"
    }

    override suspend fun getCached(trackKey: String): Lyrics? = withContext(Dispatchers.IO) {
        cacheStore.read(trackKey)?.lyrics
    }

    override suspend fun fetch(track: Track, queryOverride: String?): Result<Lyrics> {
        getCached(track.key)?.let { return Result.success(it) }

        val query = (queryOverride ?: TitleCleaner.clean(track.displayTitle)).trim()
        if (query.isEmpty()) return Result.failure(AppException("未找到歌词"))

        var lastError: Throwable? = null

        // 1) QQ Music (preferred by SPEC D2).
        try {
            val best = LyricMatcher.bestCandidate(track, QqMusicApi.search(httpClient, query))
            if (best != null) {
                val payload = QqMusicApi.fetchLyric(httpClient, best.sourceId)
                val lyrics = LrcParser.parse(payload.lrc, payload.translation, source = SOURCE_QQ)
                if (!lyrics.isEmpty) {
                    store(track.key, lyrics, SOURCE_QQ, query)
                    return Result.success(lyrics)
                }
                lastError = AppException("QQ 音乐歌词为空")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            lastError = error
        }

        // 2) NetEase Cloud Music.
        try {
            val best = LyricMatcher.bestCandidate(track, NeteaseApi.search(httpClient, query))
            if (best != null) {
                val payload = NeteaseApi.fetchLyric(httpClient, best.sourceId)
                val lyrics = LrcParser.parse(payload.lrc, payload.translation, source = SOURCE_NETEASE)
                if (!lyrics.isEmpty) {
                    store(track.key, lyrics, SOURCE_NETEASE, query)
                    return Result.success(lyrics)
                }
                lastError = AppException("网易云歌词为空")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            lastError = error
        }

        return Result.failure(AppException("未找到歌词", lastError))
    }

    override suspend fun searchCandidates(track: Track): Result<List<LyricCandidate>> {
        val query = TitleCleaner.clean(track.displayTitle).trim()
        if (query.isEmpty()) return Result.success(emptyList())

        val candidates = ArrayList<LyricCandidate>()
        var anySuccess = false
        var lastError: Throwable? = null

        try {
            candidates += QqMusicApi.search(httpClient, query)
            anySuccess = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            lastError = error
        }

        try {
            candidates += NeteaseApi.search(httpClient, query)
            anySuccess = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            lastError = error
        }

        if (!anySuccess) return Result.failure(AppException("歌曲搜索失败", lastError))

        val deduplicated = candidates.distinctBy { it.source + '\u0000' + it.sourceId }
        return Result.success(LyricMatcher.ranked(track, deduplicated).map { it.candidate })
    }

    override suspend fun fetchCandidate(track: Track, candidate: LyricCandidate): Result<Lyrics> {
        return try {
            val payload: LyricPayload
            val source: String
            when (candidate.source) {
                SOURCE_QQ -> {
                    source = SOURCE_QQ
                    payload = if (candidate.lrc.isNotBlank()) {
                        LyricPayload(candidate.lrc)
                    } else {
                        QqMusicApi.fetchLyric(httpClient, candidate.sourceId)
                    }
                }

                SOURCE_NETEASE -> {
                    source = SOURCE_NETEASE
                    payload = if (candidate.lrc.isNotBlank()) {
                        LyricPayload(candidate.lrc)
                    } else {
                        NeteaseApi.fetchLyric(httpClient, candidate.sourceId)
                    }
                }

                else -> return Result.failure(AppException("不支持的歌词来源：${candidate.source}"))
            }
            val lyrics = LrcParser.parse(payload.lrc, payload.translation, source = source)
            if (lyrics.lines.isEmpty() && lyrics.plainText.isBlank()) {
                Result.failure(AppException("未找到歌词"))
            } else {
                store(track.key, lyrics, source, candidate.title)
                Result.success(lyrics)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(AppException("未找到歌词", error))
        }
    }

    override suspend fun saveManual(trackKey: String, lrcText: String): Result<Lyrics> {
        if (lrcText.isBlank()) return Result.failure(AppException("歌词内容为空"))
        val lyrics = LrcParser.parse(lrcText, source = SOURCE_MANUAL)
        if (lyrics.lines.isEmpty() && lyrics.plainText.isBlank()) {
            return Result.failure(AppException("未解析到歌词内容"))
        }
        return try {
            store(trackKey, lyrics, SOURCE_MANUAL, query = "")
            Result.success(lyrics)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(AppException("歌词保存失败", error))
        }
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheStore.clear()
    }

    override fun cacheSizeBytes(): Long = cacheStore.sizeBytes()

    private suspend fun store(trackKey: String, lyrics: Lyrics, source: String, query: String) {
        withContext(Dispatchers.IO) {
            cacheStore.write(
                CachedLyrics(
                    trackKey = trackKey,
                    lyrics = lyrics,
                    source = source,
                    fetchedAt = clock(),
                    query = query,
                ),
            )
        }
    }
}
