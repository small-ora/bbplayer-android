package com.bbplayer.android.core.contracts

import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.AudioStream
import com.bbplayer.android.core.model.DailyStat
import com.bbplayer.android.core.model.FavoriteEntry
import com.bbplayer.android.core.model.HistoryEntry
import com.bbplayer.android.core.model.ListeningSummary
import com.bbplayer.android.core.model.LyricCandidate
import com.bbplayer.android.core.model.Lyrics
import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Playlist
import com.bbplayer.android.core.model.SearchPage
import com.bbplayer.android.core.model.ThemeMode
import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Remote content discovery + audio stream resolution (Bilibili). */
interface MusicRepository {
    suspend fun search(query: String, page: Int = 1): Result<SearchPage>

    /** Resolve BV / AV / b23.tv / full URL / plain query into playable tracks. */
    suspend fun resolveInput(input: String): Result<List<Track>>

    /** Bilibili music-zone ranking (rid=3) used by the home page. */
    suspend fun getTrending(): Result<List<Track>>

    suspend fun getRelated(bvid: String): Result<List<Track>>

    /** All pages (分 P) of a video, or just the single page. */
    suspend fun getVideoPages(bvid: String): Result<List<Track>>

    /** Resolve a fresh (possibly expiring) audio stream URL for a track. */
    suspend fun getAudioStream(track: Track, quality: AudioQuality? = null): Result<AudioStream>

    suspend fun getVideoDetail(bvid: String): Result<Track>
}

/** Local playlists + favorites, persisted on device. */
interface LibraryRepository {
    val playlists: Flow<List<Playlist>>
    val favorites: Flow<List<FavoriteEntry>>

    suspend fun createPlaylist(name: String): Playlist
    suspend fun renamePlaylist(id: String, name: String)
    suspend fun deletePlaylist(id: String)
    suspend fun getPlaylist(id: String): Playlist?
    /** 记录歌单最近一次播放时间（首页「最近播放的歌单」）。 */
    suspend fun markPlaylistPlayed(id: String)

    /** Returns false when the track already exists in the playlist. */
    suspend fun addTrack(playlistId: String, track: Track): Boolean
    suspend fun removeTrack(playlistId: String, trackKey: String)

    suspend fun isFavorite(trackKey: String): Boolean
    /** Returns the new favorite state. */
    suspend fun toggleFavorite(track: Track): Boolean
}

/** Extra read-only views over the local listening history (v2). */
interface HistoryInsights {
    fun allHistory(): Flow<List<HistoryEntry>>
    fun ranking(limit: Int = 50): Flow<List<com.bbplayer.android.core.model.ListeningRank>>
    fun historyByDay(days: Int = 30): Flow<List<com.bbplayer.android.core.model.DailyHistory>>
}

/** Listening history, resume point and aggregate statistics. */
interface HistoryRepository {
    fun recent(limit: Int = 30): Flow<List<HistoryEntry>>
    suspend fun recordPlay(track: Track, listenedMs: Long, completed: Boolean)
    suspend fun clearHistory()

    fun dailyStats(days: Int = 84): Flow<List<DailyStat>>
    fun summary(): Flow<ListeningSummary>

    suspend fun saveResume(trackKey: String, positionMs: Long)
    suspend fun loadResume(): Pair<Track, Long>?

    // ---- v2: read-only insights (defaults keep older implementations compiling) ----
    fun allHistory(): Flow<List<HistoryEntry>> = flowOf(emptyList())
    fun ranking(limit: Int = 50): Flow<List<com.bbplayer.android.core.model.ListeningRank>> = flowOf(emptyList())
    fun historyByDay(days: Int = 30): Flow<List<com.bbplayer.android.core.model.DailyHistory>> = flowOf(emptyList())
}

/** User settings, persisted with DataStore. */
interface PreferencesRepository {
    val themeMode: Flow<ThemeMode>
    val dynamicColor: Flow<Boolean>
    val audioQuality: Flow<AudioQuality>
    val autoplayOnStart: Flow<Boolean>
    val resumeOnStart: Flow<Boolean>
    val lyricsFontScale: Flow<Float>
    val lyricsOffsetMs: Flow<Int>
    val lastNormalVolume: Flow<Float>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAudioQuality(quality: AudioQuality)
    suspend fun setAutoplayOnStart(enabled: Boolean)
    suspend fun setResumeOnStart(enabled: Boolean)
    suspend fun setLyricsFontScale(scale: Float)
    suspend fun setLyricsOffsetMs(offsetMs: Int)

    // ---- v2 appearance / lyrics / download settings ----
    val playerBackgroundStyle: Flow<com.bbplayer.android.core.model.PlayerBackgroundStyle>
    val miniPlayerStyle: Flow<com.bbplayer.android.core.model.MiniPlayerStyle>
    val lyricVisualStyle: Flow<com.bbplayer.android.core.model.LyricVisualStyle>
    val verbatimLyrics: Flow<Boolean>
    val accentPreset: Flow<com.bbplayer.android.core.model.AccentPreset>
    val showTranslation: Flow<Boolean>
    val statusBarLyricLine: Flow<Boolean>
    val desktopLyricsEnabled: Flow<Boolean>
    val desktopLyricsTextSize: Flow<Float>
    val desktopLyricsColor: Flow<Long>
    val downloadParallel: Flow<Int>
    val autoCheckUpdate: Flow<Boolean>

    suspend fun setPlayerBackgroundStyle(style: com.bbplayer.android.core.model.PlayerBackgroundStyle)
    suspend fun setMiniPlayerStyle(style: com.bbplayer.android.core.model.MiniPlayerStyle)
    suspend fun setLyricVisualStyle(style: com.bbplayer.android.core.model.LyricVisualStyle)
    suspend fun setVerbatimLyrics(enabled: Boolean)
    suspend fun setAccentPreset(preset: com.bbplayer.android.core.model.AccentPreset)
    suspend fun setShowTranslation(enabled: Boolean)
    suspend fun setStatusBarLyricLine(enabled: Boolean)
    suspend fun setDesktopLyricsEnabled(enabled: Boolean)
    suspend fun setDesktopLyricsTextSize(size: Float)
    suspend fun setDesktopLyricsColor(color: Long)
    suspend fun setDownloadParallel(count: Int)
    suspend fun setAutoCheckUpdate(enabled: Boolean)

    // ---- v4: lyric provider preference + import batching ----
    val lyricSource: Flow<com.bbplayer.android.core.model.LyricSource>
    val importBatchSize: Flow<Int>

    suspend fun setLyricSource(source: com.bbplayer.android.core.model.LyricSource)
    suspend fun setImportBatchSize(size: Int)

    // ---- v5: account-related settings ----
    val highQualityAudio: Flow<Boolean>
    val reportPlaybackProgress: Flow<Boolean>

    suspend fun setHighQualityAudio(enabled: Boolean)
    suspend fun setReportPlaybackProgress(enabled: Boolean)
}

/** Lyrics acquisition, caching and manual editing. */
interface LyricsRepository {
    /** Cached lyrics for a track, if any. */
    suspend fun getCached(trackKey: String): Lyrics?
    /** Best-effort online fetch; caches the result. */
    suspend fun fetch(track: Track, queryOverride: String? = null): Result<Lyrics>
    suspend fun searchCandidates(track: Track): Result<List<LyricCandidate>>
    suspend fun fetchCandidate(track: Track, candidate: LyricCandidate): Result<Lyrics>
    suspend fun saveManual(trackKey: String, lrcText: String): Result<Lyrics>
    suspend fun clearCache()
    fun cacheSizeBytes(): Long

    // ---- v2: per-track lyric offset (defaults keep older implementations compiling) ----
    suspend fun getTrackOffset(trackKey: String): Int = 0
    suspend fun setTrackOffset(trackKey: String, offsetMs: Int) {}
}

/** Full playback control surface. Implemented by [com.bbplayer.android.playback.PlaybackControllerImpl]. */
interface PlaybackController {
    val state: Flow<PlaybackState>

    /** Replace the queue with [tracks] and start at [startIndex]. */
    suspend fun playQueue(tracks: List<Track>, startIndex: Int = 0, playWhenReady: Boolean = true)

    fun playPause()
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setPlayMode(mode: PlayMode)
    fun setSpeed(speed: Float)
    fun addToQueueNext(track: Track)
    fun addToQueueEnd(track: Track)
    fun removeFromQueue(index: Int)
    fun clearQueue()
    fun jumpTo(index: Int)

    /** null cancels the running timer. */
    fun setSleepTimer(minutes: Int?)
    val sleepTimerRemainingMs: Flow<Long>

    /**
     * v2: provide the current lyric line so the media notification (status-bar lyric line)
     * can show it. Default no-op keeps older implementations compiling.
     */
    fun setLyricLineProvider(provider: (() -> String?)?) {}
}
