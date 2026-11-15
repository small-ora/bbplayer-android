package com.bbplayer.android.di

import android.content.Context
import com.bbplayer.android.core.contracts.BackupRepository
import com.bbplayer.android.core.contracts.CommentRepository
import com.bbplayer.android.core.contracts.DiscoverRepository
import com.bbplayer.android.core.contracts.DownloadRepository
import com.bbplayer.android.core.contracts.ExternalPlaylistRepository
import com.bbplayer.android.core.contracts.HistoryRepository
import com.bbplayer.android.core.contracts.ImportTaskRepository
import com.bbplayer.android.core.contracts.LibraryRepository
import com.bbplayer.android.core.contracts.LyricsRepository
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.contracts.NetworkCacheRepository
import com.bbplayer.android.core.contracts.PlaybackController
import com.bbplayer.android.core.contracts.PreferencesRepository
import com.bbplayer.android.core.contracts.SubtitleRepository
import com.bbplayer.android.core.contracts.UpdateRepository
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.LyricSource
import com.bbplayer.android.data.BiliApi
import com.bbplayer.android.data.BiliCollectionRepository
import com.bbplayer.android.data.BiliCommentRepository
import com.bbplayer.android.data.BiliFavoritesRepository
import com.bbplayer.android.data.BiliUploaderRepository
import com.bbplayer.android.data.BiliWatchLaterRepository
import com.bbplayer.android.data.PlaybackReporterImpl
import com.bbplayer.android.data.auth.BiliAuthRepository
import com.bbplayer.android.data.BiliDiscoverRepository
import com.bbplayer.android.data.BiliExternalPlaylistRepository
import com.bbplayer.android.data.BiliMusicRepository
import com.bbplayer.android.data.BiliSubtitleRepository
import com.bbplayer.android.data.DataStorePreferences
import com.bbplayer.android.data.FileImportTaskRepository
import com.bbplayer.android.data.GitHubUpdateRepository
import com.bbplayer.android.data.JsonHistoryRepository
import com.bbplayer.android.data.JsonLibraryRepository
import com.bbplayer.android.data.ZipBackupRepository
import com.bbplayer.android.data.cache.BiliNetworkCache
import com.bbplayer.android.lyrics.QqNeteaseLyricsRepository
import com.bbplayer.android.playback.PlaybackControllerImpl
import com.bbplayer.android.playback.download.BiliDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Production wiring: real Bilibili data, Media3 playback, downloads, lyrics and v2+/v3/v4 services. */
class RealAppGraph(context: Context, private val appScope: CoroutineScope) : AppGraph {
    private val appContext = context.applicationContext

    override val preferencesRepository: PreferencesRepository = DataStorePreferences(appContext)

    // Settings the playback/download/lyrics layers need synchronously.
    @Volatile private var currentQuality: AudioQuality = AudioQuality.DEFAULT
    @Volatile private var currentParallel: Int = 1
    @Volatile private var currentLyricSource: LyricSource = LyricSource.AUTO

    init {
        appScope.launch { preferencesRepository.audioQuality.collect { currentQuality = it } }
        appScope.launch { preferencesRepository.downloadParallel.collect { currentParallel = it } }
        appScope.launch { preferencesRepository.lyricSource.collect { currentLyricSource = it } }
    }

    /** Shared authenticated API client: cookies come from the process-wide AuthRegistry. */
    private val biliApi: BiliApi = BiliApi(appContext)

    override val musicRepository: MusicRepository = BiliMusicRepository(
        biliApi,
        highQualityAudio = preferencesRepository.highQualityAudio,
    )

    override val libraryRepository: LibraryRepository = JsonLibraryRepository(appContext)

    override val historyRepository: HistoryRepository = JsonHistoryRepository(appContext)

    override val lyricsRepository: LyricsRepository = QqNeteaseLyricsRepository(
        appContext,
        sourcePreference = { currentLyricSource },
    )

    override val importTaskRepository: ImportTaskRepository = FileImportTaskRepository(appContext)

    override val discoverRepository: DiscoverRepository = BiliDiscoverRepository(appContext)

    override val commentRepository: CommentRepository = BiliCommentRepository(appContext)

    override val externalPlaylistRepository: ExternalPlaylistRepository =
        BiliExternalPlaylistRepository(musicRepository, importTaskRepository)

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

    override val networkCacheRepository: NetworkCacheRepository = BiliNetworkCache(appContext)

    // ---- v5 account services ----
    override val authRepository: com.bbplayer.android.core.contracts.AuthRepository =
        BiliAuthRepository(appContext, appScope)

    override val favoritesRepository: com.bbplayer.android.core.contracts.FavoritesRepository =
        BiliFavoritesRepository(biliApi, authRepository)

    override val collectionRepository: com.bbplayer.android.core.contracts.CollectionRepository =
        BiliCollectionRepository(biliApi, authRepository)

    override val watchLaterRepository: com.bbplayer.android.core.contracts.WatchLaterRepository =
        BiliWatchLaterRepository(biliApi, authRepository)

    override val uploaderRepository: com.bbplayer.android.core.contracts.UploaderRepository =
        BiliUploaderRepository(biliApi, authRepository)

    override val playbackReporter: com.bbplayer.android.core.contracts.PlaybackReporter =
        PlaybackReporterImpl(biliApi, preferencesRepository)

    override val playbackController: PlaybackController = PlaybackControllerImpl(
        appContext = appContext,
        scope = appScope,
        musicRepository = musicRepository,
        localFileProvider = { key -> downloadRepository.localFileFor(key) },
    ).also { controller ->
        appScope.launch {
            preferencesRepository.statusBarLyricLine.collect { enabled ->
                runCatching { controller.setLyricLineEnabled(enabled) }
            }
        }
    }
}
