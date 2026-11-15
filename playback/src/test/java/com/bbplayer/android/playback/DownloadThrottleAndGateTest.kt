package com.bbplayer.android.playback

import com.bbplayer.android.playback.download.DownloadProgressThrottle
import com.bbplayer.android.playback.download.ParallelDownloadGate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressThrottleTest {
    @Test
    fun `first sample is always reported`() {
        val throttle = DownloadProgressThrottle()
        assertTrue(throttle.shouldEmit(nowMs = 0L, downloadedBytes = 0L, totalBytes = 1000L))
    }

    @Test
    fun `identical or sub threshold samples are suppressed inside the interval`() {
        val throttle = DownloadProgressThrottle(minIntervalMs = 250L, minFractionDelta = 0.02f)
        assertTrue(throttle.shouldEmit(0L, 0L, 1000L))
        assertFalse(throttle.shouldEmit(100L, 10L, 1000L)) // +1%, 100 ms
        assertFalse(throttle.shouldEmit(120L, 10L, 1000L)) // no new bytes
    }

    @Test
    fun `two percent delta reports immediately`() {
        val throttle = DownloadProgressThrottle()
        assertTrue(throttle.shouldEmit(0L, 0L, 1000L))
        assertTrue(throttle.shouldEmit(50L, 20L, 1000L)) // +2% before 250 ms
        assertFalse(throttle.shouldEmit(60L, 30L, 1000L)) // +1%
    }

    @Test
    fun `time elapsed forces a report and completion is always emitted`() {
        val throttle = DownloadProgressThrottle()
        assertTrue(throttle.shouldEmit(0L, 0L, 1000L))
        assertTrue(throttle.shouldEmit(250L, 5L, 1000L)) // 250 ms elapsed
        assertTrue(throttle.shouldEmit(300L, 1000L, 1000L)) // 100%
        assertFalse(throttle.shouldEmit(400L, 1000L, 1000L)) // duplicate final tick
        assertEquals(1f, DownloadProgressThrottle.fractionOf(1000L, 1000L), 0f)
        assertEquals(0f, DownloadProgressThrottle.fractionOf(5L, 0L), 0f)
    }
}

class ParallelDownloadGateTest {
    @Test
    fun `never runs more downloads than the configured maximum`() = runTest {
        val gate = ParallelDownloadGate({ 2 }, pollIntervalMs = 1L)
        val running = AtomicInteger(0)
        val observedMax = AtomicInteger(0)
        (1..6).map {
            async {
                gate.withPermit {
                    val current = running.incrementAndGet()
                    observedMax.updateAndGet { previous -> maxOf(previous, current) }
                    delay(20L)
                    running.decrementAndGet()
                }
            }
        }.awaitAll()
        assertEquals(2, observedMax.get())
        assertEquals(0, gate.activeCount)
    }

    @Test
    fun `limit is clamped into one through three`() {
        assertEquals(1, ParallelDownloadGate({ 0 }).limit())
        assertEquals(1, ParallelDownloadGate({ -4 }).limit())
        assertEquals(3, ParallelDownloadGate({ 9 }).limit())
    }

    @Test
    fun `a failing task releases its slot`() = runTest {
        val gate = ParallelDownloadGate({ 1 }, pollIntervalMs = 1L)
        runCatching {
            gate.withPermit<Unit> { throw IllegalStateException("boom") }
        }
        assertEquals(0, gate.activeCount)
        // The single slot is available again.
        var ran = false
        gate.withPermit { ran = true }
        assertTrue(ran)
    }
}
