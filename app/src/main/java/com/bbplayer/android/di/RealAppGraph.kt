package com.bbplayer.android.di

import android.content.Context
import com.bbplayer.android.core.contracts.BackupRepository
import com.bbplayer.android.core.contracts.CommentRepository
import com.bbplayer.android.core.contracts.DiscoverRepository
import com.bbplayer.android.core.contracts.DownloadRepository
import com.bbplayer.android.core.contracts.ExternalPlaylistRepository
import com.bbplayer.android.core.contracts.HistoryRepository
import com.bbplayer.android.core.contracts.LibraryRepository
import com.bbplayer.android.core.contracts.LyricsRepository
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.contracts.PlaybackController
import com.bbplayer.android.core.contracts.PreferencesRepository
import com.bbplayer.android.core.contracts.SubtitleRepository
import com.bbplayer.android.core.contracts.UpdateRepository
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.data.BiliCommentRepository
import com.bbplayer.android.data.BiliDiscoverRepository
import com.bbplayer.android.data.BiliExternalPlaylistRepository
import com.bbplayer.android.data.BiliMusicRepository
import com.bbplayer.android.data.BiliSubtitleRepository
import com.bbplayer.android.data.DataStorePreferences
import com.bbplayer.android.data.GitHubUpdateRepository
import com.bbplayer.android.data.JsonHistoryRepository
import com.bbplayer.android.data.JsonLibraryRepository
import com.bbplayer.android.data.ZipBackupRepository
import com.bbplayer.android.lyrics.QqNeteaseLyricsRepository
import com.bbplayer.android.playback.PlaybackControllerImpl
import com.bbplayer.android.playback.download.BiliDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Production wiring: real Bilibili data, Media3 playback, downloads, lyrics and v2 services. */
class RealAppGraph(context: Context, private val appScope: CoroutineScope) : AppGraph {
    private val appContext = context.applicationContext

    override val preferencesRepository: PreferencesRepository = DataStorePreferences(appContext)

    override val musicRepository: MusicRepository = BiliMusicRepository(appContext).also {
        runCatching { it.warmUp(appScope) }
    }

    override val libraryRepository: LibraryRepository = JsonLibraryRepository(appContext)

    override val historyRepository: HistoryRepository = JsonHistoryRepository(appContext)

    override val lyricsRepository: LyricsRepository = QqNeteaseLyricsRepository(appContext)

    // Settings that the playback/download layers need synchronously.
    @Volatile private var currentQuality: AudioQuality = AudioQuality.DEFAULT
    @Volatile private var currentParallel: Int = 1
    init {
        appScope.launch {
            preferencesRepository.audioQuality.collect { currentQuality = it }
        }
        appScope.launch {
            preferencesRepository.downloadParallel.collect { currentParallel = it }
        }
    }

    override val discoverRepository: DiscoverRepository = BiliDiscoverRepository(appContext)

    override val commentRepository: CommentRepository = BiliCommentRepository(appContext)

    override val externalPlaylistRepository: ExternalPlaylistRepository =
        BiliExternalPlaylistRepository(musicRepository)

    override val subtitleRepository: SubtitleRepository = BiliSubtitleRepository(appContext)

    override val updateRepository: UpdateRepository = GitHubUpdateRepository()

    override val downloadRepository: DownloadRepository = BiliDownloadManager(
        context = appContext,
        scope = appScope,
        musicRepository = musicRepository,
        qualityProvider = { currentQuality },
        maxParallelProvider = { currentParallel },
    )

    override val backupRepository: BackupRepository = ZipBackupRepository(appContext)

    override val networkCacheRepository: com.bbplayer.android.core.contracts.NetworkCacheRepository =
        com.bbplayer.android.data.cache.BiliNetworkCache(appContext)

    override val playbackController: PlaybackController = PlaybackControllerImpl(
        appContext = appContext,
        scope = appScope,
        musicRepository = musicRepository,
        localFileProvider = { key -> downloadRepository.localFileFor(key) },
    ).also { controller ->
        // Notification lyric line follows the user's setting.
        appScope.launch {
            preferencesRepository.statusBarLyricLine.collect { enabled ->
                runCatching { controller.setLyricLineEnabled(enabled) }
            }
        }
    }
}
