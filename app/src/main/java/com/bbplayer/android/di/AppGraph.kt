package com.bbplayer.android.di

import androidx.compose.runtime.staticCompositionLocalOf
import com.bbplayer.android.core.contracts.HistoryRepository
import com.bbplayer.android.core.contracts.LibraryRepository
import com.bbplayer.android.core.contracts.LyricsRepository
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.contracts.PlaybackController
import com.bbplayer.android.core.contracts.PreferencesRepository

/**
 * Manually-wired dependency graph (single source of truth for singletons).
 * Created by [com.bbplayer.android.BbPlayerApp] and provided to Compose via [LocalAppGraph].
 */
interface AppGraph {
    val musicRepository: MusicRepository
    val libraryRepository: LibraryRepository
    val historyRepository: HistoryRepository
    val preferencesRepository: PreferencesRepository
    val lyricsRepository: LyricsRepository
    val playbackController: PlaybackController
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("AppGraph not provided — wrap the UI in CompositionLocalProvider(LocalAppGraph provides …)")
}
