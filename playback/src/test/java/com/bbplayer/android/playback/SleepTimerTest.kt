package com.bbplayer.android.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    @Test
    fun `endsAt arithmetic`() {
        assertEquals(0L, SleepTimerMath.endsAt(1_000L, null))
        assertEquals(0L, SleepTimerMath.endsAt(1_000L, 0))
        assertEquals(0L, SleepTimerMath.endsAt(1_000L, -5))
        assertEquals(1_000L + 15L * 60_000L, SleepTimerMath.endsAt(1_000L, 15))
        assertEquals(1_000L + 60L * 60_000L, SleepTimerMath.endsAt(1_000L, 60))
    }

    @Test
    fun `remaining is clamped and disabled aware`() {
        assertEquals(0L, SleepTimerMath.remainingMs(0L, 5_000L))
        assertEquals(0L, SleepTimerMath.remainingMs(10_000L, 12_000L))
        assertEquals(4_000L, SleepTimerMath.remainingMs(10_000L, 6_000L))
        assertTrue(SleepTimerMath.isEnabled(10_000L))
        assertFalse(SleepTimerMath.isEnabled(0L))
        assertTrue(SleepTimerMath.isExpired(10_000L, 10_000L))
        assertFalse(SleepTimerMath.isExpired(10_000L, 9_999L))
        assertFalse(SleepTimerMath.isExpired(0L, 999_999L))
    }

    @Test
    fun `remaining is formatted as mm ss`() {
        assertEquals("00:00", SleepTimerMath.formatRemaining(0L))
        assertEquals("00:01", SleepTimerMath.formatRemaining(1L))
        assertEquals("01:00", SleepTimerMath.formatRemaining(60_000L))
        assertEquals("15:00", SleepTimerMath.formatRemaining(15L * 60_000L))
        assertEquals("00:30", SleepTimerMath.formatRemaining(29_500L))
    }

    @Test
    fun `timer counts down and fires expire exactly once`() = runTest {
        var expired = 0
        val timer = SleepTimer(
            scope = this,
            now = { testScheduler.currentTime },
            onExpire = { expired += 1 },
        )
        timer.set(1)
        assertEquals(60_000L, timer.endsAtMs)
        runCurrent()
        assertEquals(60_000L, timer.remainingMs.value)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(30_000L, timer.remainingMs.value)
        assertEquals(0, expired)

        advanceTimeBy(30_001L)
        runCurrent()
        assertEquals(1, expired)
        assertEquals(0L, timer.remainingMs.value)
        assertEquals(0L, timer.endsAtMs)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun `cancel resets state and cancelling with null disables`() = runTest {
        var expired = 0
        val timer = SleepTimer(
            scope = this,
            now = { testScheduler.currentTime },
            onExpire = { expired += 1 },
        )
        timer.set(15)
        runCurrent()
        assertTrue(timer.endsAtMs > 0L)
        timer.cancel()
        assertEquals(0L, timer.endsAtMs)
        assertEquals(0L, timer.remainingMs.value)
        advanceTimeBy(16L * 60_000L)
        runCurrent()
        assertEquals(0, expired)

        timer.set(null)
        assertEquals(0L, timer.endsAtMs)
        assertEquals(0L, timer.remainingMs.value)
    }
}
