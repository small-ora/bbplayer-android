package com.bbplayer.android.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pure sleep-timer arithmetic (unit tested on the JVM, `now` injectable).
 */
object SleepTimerMath {
    /** Sentinel: timer disabled. */
    const val DISABLED_END_MS: Long = 0L

    /** Absolute end timestamp for [minutes] from [nowMs]; null/<=0 disables. */
    fun endsAt(nowMs: Long, minutes: Int?): Long {
        if (minutes == null || minutes <= 0) return DISABLED_END_MS
        return nowMs + minutes.toLong() * 60_000L
    }

    /** Remaining milliseconds, never negative; 0 means disabled/expired. */
    fun remainingMs(endsAtMs: Long, nowMs: Long): Long {
        if (endsAtMs <= DISABLED_END_MS) return 0L
        return (endsAtMs - nowMs).coerceAtLeast(0L)
    }

    fun isEnabled(endsAtMs: Long): Boolean = endsAtMs > DISABLED_END_MS

    fun isExpired(endsAtMs: Long, nowMs: Long): Boolean =
        isEnabled(endsAtMs) && nowMs >= endsAtMs

    /** Human-friendly mm:ss (also used by tests/UI). */
    fun formatRemaining(remainingMs: Long): String {
        val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * Countdown sleep timer. Emits remaining milliseconds once per second
 * (`0` = disabled) and invokes [onExpire] exactly once when it reaches zero.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = 1_000L,
    private val onExpire: () -> Unit,
) {
    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    @Volatile
    var endsAtMs: Long = SleepTimerMath.DISABLED_END_MS
        private set

    private var ticker: Job? = null

    /** `null` or a non-positive value cancels the timer. */
    fun set(minutes: Int?) {
        ticker?.cancel()
        ticker = null
        val end = SleepTimerMath.endsAt(now(), minutes)
        endsAtMs = end
        if (!SleepTimerMath.isEnabled(end)) {
            _remainingMs.value = 0L
            return
        }
        _remainingMs.value = SleepTimerMath.remainingMs(end, now())
        ticker = scope.launch {
            while (isActive) {
                delay(tickMs)
                val remaining = SleepTimerMath.remainingMs(endsAtMs, now())
                _remainingMs.value = remaining
                if (remaining <= 0L) {
                    endsAtMs = SleepTimerMath.DISABLED_END_MS
                    ticker = null
                    onExpire()
                    break
                }
            }
        }
    }

    fun cancel() {
        ticker?.cancel()
        ticker = null
        endsAtMs = SleepTimerMath.DISABLED_END_MS
        _remainingMs.value = 0L
    }
}
