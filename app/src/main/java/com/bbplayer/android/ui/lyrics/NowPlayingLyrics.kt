package com.bbplayer.android.ui.lyrics

import com.bbplayer.android.core.model.Lyrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide "current lyric line" state shared by the in-app player, the desktop-lyrics
 * overlay service and the media notification (status-bar lyric line).
 */
object NowPlayingLyrics {
    data class Snapshot(
        val trackKey: String = "",
        val text: String = "",
        val translation: String? = null,
        val hasLyrics: Boolean = false,
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** True while the in-app lyrics page is visible (desktop overlay hides itself). */
    val inAppLyricsVisible = MutableStateFlow(false)

    private var lyrics: Lyrics? = null
    private var lastIndex: Int = -2

    @Volatile private var loadedTrackKey: String = ""

    fun setLyrics(trackKey: String, value: Lyrics?) {
        if (trackKey == loadedTrackKey && lyrics != null) return
        loadedTrackKey = trackKey
        lyrics = value
        lastIndex = -2
        if (value == null || value.isEmpty) {
            _snapshot.value = Snapshot(trackKey = trackKey, text = "", translation = null, hasLyrics = false)
        }
    }

    fun clear() {
        lyrics = null
        lastIndex = -2
        _snapshot.value = Snapshot()
    }

    /** Called on every playback tick; publishes the active line only when it changes. */
    fun update(positionMs: Long) {
        val current = lyrics
        if (current == null || current.isEmpty) return
        val index = current.activeIndex(positionMs)
        if (index == lastIndex) return
        lastIndex = index
        val line = current.lines.getOrNull(index)
        _snapshot.value = Snapshot(
            trackKey = _snapshot.value.trackKey,
            text = line?.text.orEmpty(),
            translation = line?.translation,
            hasLyrics = true,
        )
    }
}
