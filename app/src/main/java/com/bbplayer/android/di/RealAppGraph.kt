package com.bbplayer.android.di

import android.content.Context
import com.bbplayer.android.core.contracts.HistoryRepository
import com.bbplayer.android.core.contracts.LibraryRepository
import com.bbplayer.android.core.contracts.LyricsRepository
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.contracts.PlaybackController
import com.bbplayer.android.core.contracts.PreferencesRepository
import com.bbplayer.android.data.BiliMusicRepository
import com.bbplayer.android.data.DataStorePreferences
import com.bbplayer.android.data.JsonHistoryRepository
import com.bbplayer.android.data.JsonLibraryRepository
import com.bbplayer.android.lyrics.QqNeteaseLyricsRepository
import com.bbplayer.android.playback.PlaybackControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Production wiring: real Bilibili data, Media3 playback and online lyrics. */
class RealAppGraph(context: Context, private val appScope: CoroutineScope) : AppGraph {
    private val appContext = context.applicationContext

    override val preferencesRepository: PreferencesRepository = DataStorePreferences(appContext)

    override val musicRepository: MusicRepository = BiliMusicRepository(appContext).also {
        runCatching { it.warmUp(appScope) }
    }

    override val libraryRepository: LibraryRepository = JsonLibraryRepository(appContext)

    override val historyRepository: HistoryRepository = JsonHistoryRepository(appContext)

    override val lyricsRepository: LyricsRepository = QqNeteaseLyricsRepository(appContext)

    override val playbackController: PlaybackController = PlaybackControllerImpl(
        appContext = appContext,
        scope = appScope,
        musicRepository = musicRepository,
    )
}
