package com.bbplayer.android.data.cache

/**
 * Process-wide circuit breaker for Bilibili risk-control responses.
 *
 * When any request gets `-412` / HTTP 412 / 429 we pause *all* outgoing Bilibili requests for a
 * cool-down window instead of hammering the endpoint (which makes the block longer). Callers get a
 * friendly, retryable [com.bbplayer.android.core.contracts.AppException] meanwhile.
 */
internal object SecurityCooldown {

    @Volatile
    private var blockedUntilMs: Long = 0L

    /** Cool-down after a risk-control hit (default 60s). */
    var penaltyMs: Long = 60_000L

    fun markBlocked(nowMs: Long = System.currentTimeMillis(), penalty: Long = penaltyMs) {
        blockedUntilMs = maxOf(blockedUntilMs, nowMs + penalty)
    }

    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long =
        (blockedUntilMs - nowMs).coerceAtLeast(0L)

    fun clear() {
        blockedUntilMs = 0L
    }
}
