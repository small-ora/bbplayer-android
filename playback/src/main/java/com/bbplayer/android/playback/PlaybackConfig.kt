package com.bbplayer.android.playback

/**
 * Static configuration for the playback module.
 *
 * Kept free of Android imports so it can be referenced from plain-JVM unit tests.
 */
object PlaybackConfig {
    /** Desktop Chrome UA — Bilibili CDN rejects some mobile/default UAs. */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** Bilibili audio CDN (upos-*.bilivideo.com) requires this referer. */
    const val BILIBILI_REFERER = "https://www.bilibili.com/"

    /** Resolved playurl entries are valid ~2h; refresh well before that. */
    const val STREAM_CACHE_TTL_MS: Long = 90L * 60L * 1000L

    /** UI position update cadence required by the SPEC (500 ms). */
    const val POSITION_TICK_MS: Long = 500L

    /** Persist the resume snapshot while playing at most this often. */
    const val PERSIST_INTERVAL_MS: Long = 10_000L

    /** Debounce window for "state changed" persistence (seek / queue edits). */
    const val PERSIST_DEBOUNCE_MS: Long = 800L

    const val SESSION_ID = "bbplayer-session"

    const val STATE_FILE_NAME = "playback_state.json"

    /** Headers added to every stream request (datasource factory defaults). */
    val STREAM_HEADERS: Map<String, String> = mapOf(
        "Referer" to BILIBILI_REFERER,
        "User-Agent" to DESKTOP_USER_AGENT,
        "Accept" to "*/*",
    )

    /** Headers used when loading notification artwork from hdslb CDNs. */
    val ARTWORK_HEADERS: Map<String, String> = mapOf(
        "Referer" to BILIBILI_REFERER,
        "User-Agent" to DESKTOP_USER_AGENT,
    )
}
