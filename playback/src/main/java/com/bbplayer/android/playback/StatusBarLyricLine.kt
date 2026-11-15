package com.bbplayer.android.playback

/**
 * Shared state for the "status-bar lyric line" feature (SPEC G5).
 *
 * The app's lyric UI calls [PlaybackControllerImpl.setLyricLineProvider] with a
 * lambda that returns the current line; every media-notification build reads
 * [current] so the second notification line shows the lyric without touching
 * MediaItem metadata (which would interrupt playback).
 *
 * Deliberately tiny + Android free so it is thread-safe (volatile fields) and
 * unit testable.
 */
object StatusBarLyricLine {
    /** Longer lines are ellipsized so the collapsed notification stays readable. */
    const val MAX_LINE_LENGTH: Int = 80

    private val WHITESPACE = Regex("\\s+")

    @Volatile
    private var provider: (() -> String?)? = null

    @Volatile
    var enabled: Boolean = true
        private set

    fun setProvider(newProvider: (() -> String?)?) {
        provider = newProvider
    }

    fun setEnabled(newEnabled: Boolean) {
        enabled = newEnabled
    }

    /** Current sanitized lyric line, or null when disabled / absent / blank. */
    fun current(): String? = if (!enabled) null else format(provider?.invoke())

    /** Trim, collapse line breaks (a notification row is a single line), cap length. */
    fun format(raw: String?): String? {
        val line = raw?.let { WHITESPACE.replace(it, " ") }?.trim() ?: return null
        if (line.isEmpty()) return null
        if (line.length <= MAX_LINE_LENGTH) return line
        return line.take(MAX_LINE_LENGTH - 1).trimEnd() + "…"
    }

    /** Test hook: forget provider + restore the default enabled state. */
    fun reset() {
        provider = null
        enabled = true
    }
}
