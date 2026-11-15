package com.bbplayer.android.lyrics

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.LyricsRepository
import com.bbplayer.android.core.model.LyricCandidate
import com.bbplayer.android.core.model.LyricSource
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
 * Acquisition order of [fetch] (controlled by [sourcePreference]):
 *  1. cache (`context.filesDir/lyrics/<sha1(trackKey)>.json`) -> return immediately;
 *  2. candidates in preference order ([LyricSource.AUTO] = QQ -> NetEase, [LyricSource.QQ] = QQ only,
 *     [LyricSource.NETEASE] = NetEase only): search with the cleaned title, score candidates (title 0.75 / artist 0.25,
 *     threshold 0.5), download the best match's lyric, parse (main LRC + optional translation + verbatim track),
 *     cache, return;
 *  3. otherwise `Result.failure(AppException("未找到歌词"))`.
 *
 * Network failures of one provider never abort the AUTO fallback chain; with an explicit provider preference there is
 * only one provider, so its failure is the final failure. `CancellationException` is always rethrown.
 * All endpoints are anonymous, read-only GETs; requests carry a desktop UA + provider Referer and an 8s timeout.
 *
 * @param context Android context used for the cache / offset directories.
 * @param httpClient shared OkHttp client (8s timeouts by default).
 * @param cacheStore cache storage; defaults to [FileLyricsCacheStore] under `filesDir/lyrics`.
 * @param offsetStore per-track lyric offset persistence (AC G2); defaults to [FileTrackOffsetStore] at
 *   `filesDir/lyric_offsets.json`.
 * @param clock wall clock used for cache `fetchedAt` (injectable for tests).
 * @param estimateWordTiming when true (default) lyrics that only carry line timestamps are extended with a uniform
 *   estimated word timeline ([EstimatedTiming]) and their `source` is marked `qq:estimated` / `netease:estimated`, so the UI
 *   karaoke fill works even without a real YRC/QRC track. Set to false to keep strict provider timing only. It also
 *   enables the legacy-cache word backfill in [getCached].
 * @param sourcePreference provider pick for [fetch] / [searchCandidates], read once per call. Defaults to
 *   [LyricSource.AUTO] (QQ -> NetEase), preserving the historical behaviour for all existing call sites.
 */
class QqNeteaseLyricsRepository(
    context: Context,
    private val httpClient: OkHttpClient = LyricsHttp.defaultClient(),
    private val cacheStore: LyricsCacheStore = FileLyricsCacheStore(File(context.filesDir, CACHE_DIR)),
    private val offsetStore: TrackOffsetStore = FileTrackOffsetStore(File(context.filesDir, OFFSET_FILE_NAME)),
    private val clock: () -> Long = System::currentTimeMillis,
    private val estimateWordTiming: Boolean = true,
    private val sourcePreference: () -> LyricSource = { LyricSource.AUTO },
) : LyricsRepository {

    companion object {
        const val CACHE_DIR = "lyrics"

        /** File name of the per-track lyric offset store inside `filesDir` (AC G2). */
        const val OFFSET_FILE_NAME = "lyric_offsets.json"
        const val SOURCE_QQ = QqMusicApi.SOURCE
        const val SOURCE_NETEASE = NeteaseApi.SOURCE
        const val SOURCE_MANUAL = "manual"
    }

    /**
     * Cached lyrics, with legacy entries upgraded in memory:
     *
     * Pre-v4 caches only carry line timestamps. When [estimateWordTiming] is on and the cached lyrics have lines but
     * at least one line still lacks `words`, [EstimatedTiming.apply] fills the missing word timeline and marks
     * `source` with [EstimatedTiming.SOURCE_SUFFIX] (e.g. `qq:estimated`). The upgraded entry is written back to the
     * cache once, best effort, so repeated reads do not recompute; a failed writeback is ignored and the in-memory
     * result is still returned. Entries that already have words, are already marked estimated, or when estimation is
     * disabled are returned untouched.
     */
    override suspend fun getCached(trackKey: String): Lyrics? = withContext(Dispatchers.IO) {
        val entry = cacheStore.read(trackKey) ?: return@withContext null
        val cached = entry.lyrics
        if (!needsEstimatedWords(cached)) return@withContext cached

        val upgraded = EstimatedTiming.apply(cached)
        if (upgraded !== cached) {
            runCatching { cacheStore.write(entry.copy(lyrics = upgraded)) }
        }
        upgraded
    }

    private fun needsEstimatedWords(lyrics: Lyrics): Boolean =
        estimateWordTiming &&
            lyrics.lines.isNotEmpty() &&
            !EstimatedTiming.isEstimated(lyrics) &&
            lyrics.lines.any { it.words.isEmpty() }

    override suspend fun fetch(track: Track, queryOverride: String?): Result<Lyrics> {
        getCached(track.key)?.let { return Result.success(it) }

        val query = (queryOverride ?: TitleCleaner.clean(track.displayTitle)).trim()
        if (query.isEmpty()) return Result.failure(AppException("未找到歌词"))

        var lastError: Throwable? = null

        for (source in providerOrder()) {
            try {
                val best = LyricMatcher.bestCandidate(track, searchProvider(source, query))
                if (best != null) {
                    val payload = fetchProviderLyric(source, best.sourceId)
                    val lyrics = ProviderLyricParsers.parse(payload, source, estimateWordTiming)
                    if (!lyrics.isEmpty) {
                        store(track.key, lyrics, source, query)
                        return Result.success(lyrics)
                    }
                    lastError = AppException(emptyLyricsMessage(source))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                lastError = error
            }
        }

        return Result.failure(AppException("未找到歌词", lastError))
    }

    override suspend fun searchCandidates(track: Track): Result<List<LyricCandidate>> {
        val query = TitleCleaner.clean(track.displayTitle).trim()
        if (query.isEmpty()) return Result.success(emptyList())

        val candidates = ArrayList<LyricCandidate>()
        var anySuccess = false
        var lastError: Throwable? = null

        for (source in providerOrder()) {
            try {
                candidates += searchProvider(source, query)
                anySuccess = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                lastError = error
            }
        }

        if (!anySuccess) return Result.failure(AppException("歌曲搜索失败", lastError))

        val deduplicated = candidates.distinctBy { it.source + '\u0000' + it.sourceId }
        return Result.success(LyricMatcher.ranked(track, deduplicated).map { it.candidate })
    }

    /** Provider chain for the current [sourcePreference]; [LyricSource.AUTO] keeps the historical QQ -> NetEase order. */
    private fun providerOrder(): List<String> = when (sourcePreference()) {
        LyricSource.AUTO -> listOf(SOURCE_QQ, SOURCE_NETEASE)
        LyricSource.QQ -> listOf(SOURCE_QQ)
        LyricSource.NETEASE -> listOf(SOURCE_NETEASE)
    }

    private suspend fun searchProvider(source: String, query: String): List<LyricCandidate> =
        when (source) {
            SOURCE_QQ -> QqMusicApi.search(httpClient, query)
            SOURCE_NETEASE -> NeteaseApi.search(httpClient, query)
            else -> throw IllegalArgumentException("不支持的歌词来源：$source")
        }

    private suspend fun fetchProviderLyric(source: String, sourceId: String): LyricPayload =
        when (source) {
            SOURCE_QQ -> QqMusicApi.fetchLyric(httpClient, sourceId)
            SOURCE_NETEASE -> NeteaseApi.fetchLyric(httpClient, sourceId)
            else -> throw IllegalArgumentException("不支持的歌词来源：$source")
        }

    private fun emptyLyricsMessage(source: String): String =
        if (source == SOURCE_QQ) "QQ 音乐歌词为空" else "网易云歌词为空"

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
            val lyrics = ProviderLyricParsers.parse(payload, source, estimateWordTiming)
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
        val lyrics = LyricsParser.parse(lrcText, LyricsFormat.AUTO, source = SOURCE_MANUAL)
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

    override suspend fun getTrackOffset(trackKey: String): Int = withContext(Dispatchers.IO) {
        offsetStore.get(trackKey)
    }

    override suspend fun setTrackOffset(trackKey: String, offsetMs: Int) {
        withContext(Dispatchers.IO) { offsetStore.set(trackKey, offsetMs) }
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
