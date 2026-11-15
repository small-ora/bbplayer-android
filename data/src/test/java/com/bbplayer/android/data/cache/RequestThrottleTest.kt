package com.bbplayer.android.data.cache

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 请求节流：最小间隔、抖动范围、并发串行化（fake clock，不真实等待）。 */
class RequestThrottleTest {

    @Test
    fun `首次请求不等待后续请求补足最小间隔`() = runTest {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val throttle = RequestThrottle(
            minIntervalMs = 350L,
            jitterMs = 0L,
            clock = { now },
            sleep = { ms -> sleeps += ms; now += ms },
            jitterProvider = { 0L },
        )

        throttle.acquire()
        assertTrue("首次不应等待", sleeps.isEmpty())
        now += 100L
        throttle.acquire()
        assertEquals(listOf(250L), sleeps) // 补足到 350ms
        // 第二次请求发生在 t=350；再等满一个间隔后请求，无需补时
        now += 350L
        throttle.acquire()
        assertEquals(listOf(250L), sleeps)
    }

    @Test
    fun `抖动在正负区间内且生效`() = runTest {
        suspend fun build(jitter: Long): Pair<RequestThrottle, MutableList<Long>> {
            var now = 0L
            val sleeps = mutableListOf<Long>()
            val throttle = RequestThrottle(
                minIntervalMs = 350L,
                jitterMs = 100L,
                clock = { now },
                sleep = { ms -> sleeps += ms; now += ms },
                jitterProvider = { jitter },
            )
            throttle.acquire() // 首次不等待
            return throttle to sleeps
        }

        val (minThrottle, minSleeps) = build(-100L)
        minThrottle.acquire()
        assertEquals("350 - 100 = 250ms", listOf(250L), minSleeps)

        val (maxThrottle, maxSleeps) = build(100L)
        maxThrottle.acquire()
        assertEquals("350 + 100 = 450ms", listOf(450L), maxSleeps)
    }

    @Test
    fun `并发请求被串行化`() = runTest {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val throttle = RequestThrottle(
            minIntervalMs = 350L,
            jitterMs = 0L,
            clock = { now },
            sleep = { ms -> sleeps += ms; now += ms },
            jitterProvider = { 0L },
        )

        coroutineScope {
            repeat(5) { launch { throttle.acquire() } }
        }
        // 5 次请求 = 4 次补间隔，每次 350ms
        assertEquals(4, sleeps.size)
        assertTrue(sleeps.all { it == 350L })
        assertEquals(1_400L, now)
    }
}
