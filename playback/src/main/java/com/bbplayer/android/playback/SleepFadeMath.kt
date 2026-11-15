package com.bbplayer.android.playback

/**
 * Sleep-timer fade-out curve (SPEC: final 10 s fade to silence, one step every
 * 200 ms; the previous volume is restored when the timer expires or is
 * cancelled).
 *
 * Pure arithmetic so the curve and the step count are unit tested instead of
 * only reviewed.
 */
object SleepFadeMath {
    /** Fade window: the last 10 seconds before the timer fires. */
    const val FADE_WINDOW_MS: Long = 10_000L

    /** One volume step every 200 ms => 50 steps across the window. */
    const val STEP_MS: Long = 200L

    const val STEPS: Int = (FADE_WINDOW_MS / STEP_MS).toInt()

    /** Full base volume before the window, linear ramp to 0 at expiry. */
    fun volumeAt(
        remainingMs: Long,
        baseVolume: Float,
        windowMs: Long = FADE_WINDOW_MS,
    ): Float {
        val base = if (baseVolume.isNaN() || baseVolume.isInfinite()) {
            1f
        } else {
            baseVolume.coerceIn(0f, 1f)
        }
        if (windowMs <= 0L || remainingMs <= 0L) return 0f
        if (remainingMs >= windowMs) return base
        return (base * (remainingMs.toFloat() / windowMs.toFloat())).coerceIn(0f, 1f)
    }

    /**
     * The 50 volume values actually written to the player, newest-remaining
     * first: `step 1` at t=9.8 s ... `step 50` at t=0 s (exactly 0).
     */
    fun fadeSteps(
        baseVolume: Float,
        windowMs: Long = FADE_WINDOW_MS,
        stepMs: Long = STEP_MS,
    ): List<Float> {
        if (windowMs <= 0L || stepMs <= 0L) return emptyList()
        val steps = (windowMs / stepMs).toInt()
        if (steps <= 0) return emptyList()
        return (1..steps).map { index ->
            val remaining = windowMs - index.toLong() * stepMs
            volumeAt(remaining.coerceAtLeast(0L), baseVolume, windowMs)
        }
    }

    fun isInFadeWindow(remainingMs: Long, windowMs: Long = FADE_WINDOW_MS): Boolean =
        remainingMs in 1..windowMs
}
