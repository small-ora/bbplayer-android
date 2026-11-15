package com.bbplayer.android.data.cache

import com.bbplayer.android.core.contracts.AppException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** 并发去重：同 key 只执行一次，失败后可重试。 */
class InFlightDeduplicatorTest {

    @Test
    fun `并发相同 key 只执行一次`() = runTest {
        val deduplicator = InFlightDeduplicator()
        val calls = AtomicInteger()
        val results = (1..10).map {
            async {
                deduplicator.run("same-key") {
                    delay(20)
                    calls.incrementAndGet()
                    "value"
                }
            }
        }.awaitAll()

        assertEquals(1, calls.get())
        assertTrue(results.all { it == "value" })
    }

    @Test
    fun `不同 key 各自执行`() = runTest {
        val deduplicator = InFlightDeduplicator()
        val calls = AtomicInteger()
        val results = listOf("a", "b", "c").map { key ->
            async {
                deduplicator.run(key) {
                    delay(10)
                    calls.incrementAndGet()
                    key
                }
            }
        }.awaitAll()

        assertEquals(3, calls.get())
        assertEquals(listOf("a", "b", "c"), results)
    }

    @Test
    fun `失败后移除 in-flight 记录可重试`() = runTest {
        val deduplicator = InFlightDeduplicator()
        val calls = AtomicInteger()
        val first = runCatching {
            deduplicator.run("key") {
                calls.incrementAndGet()
                throw AppException("boom", retryable = true)
            }
        }
        assertTrue(first.isFailure)
        assertEquals(1, calls.get())

        val second = deduplicator.run("key") {
            calls.incrementAndGet()
            "recovered"
        }
        assertEquals("recovered", second)
        assertEquals("失败后应重新执行", 2, calls.get())
    }

    @Test
    fun `等待方收到与执行方相同的异常`() = runTest {
        val deduplicator = InFlightDeduplicator()
        val awaitFailure = async {
            runCatching {
                deduplicator.run("shared") {
                    delay(20)
                    throw AppException("failed", retryable = false)
                }
            }
        }
        val waiter = launch {
            runCatching { deduplicator.run("shared") { "never" } }
        }
        assertTrue(awaitFailure.await().isFailure)
        waiter.join()
    }
}
