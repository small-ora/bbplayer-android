package com.bbplayer.android.playback

/**
 * Which upstream HTTP failures are worth one retry with a freshly resolved URL.
 *
 * Pure logic (no ExoPlayer / Android types) so the "403/410 -> re-resolve once"
 * policy from SPEC C8 is unit tested instead of only reviewed.
 */
object StreamRetryPolicy {
    /** Exactly one retry per data-source open/read (avoids retry storms). */
    const val MAX_RETRIES: Int = 1

    /** CDN expiry / missing mirror / rate limit cases worth a fresh resolve. */
    private val RETRYABLE_STATUS_CODES: Set<Int> = setOf(403, 404, 408, 410, 425, 429)

    fun isRetryableStatus(statusCode: Int): Boolean =
        statusCode in RETRYABLE_STATUS_CODES || statusCode in 500..599
}
