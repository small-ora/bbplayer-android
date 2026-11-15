package com.bbplayer.android.data.cache

import com.bbplayer.android.core.contracts.AppException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

/** 读穿缓存执行器：命中不请求、失败不缓存、ttl=null 不缓存、并发只请求一次。 */
class CachedRequestExecutorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var now = 1_000_000L

    private fun store() = HttpCacheStore(temp.newFolder()) { now }

    private fun executor(store: HttpCacheStore?) = CachedRequestExecutor(
        cache = store,
        throttle = null,
        deduplicator = InFlightDeduplicator(),
    )

    /** 用 String 作为 T，避免测试里泛型推断噪音。 */
    private suspend fun CachedRequestExecutor.cachedString(
        key: String,
        ttlMs: Long?,
        fetch: suspend () -> String,
    ): String = run(
        key = key,
        ttlMs = ttlMs,
        decode = { raw: String -> raw },
        encode = { value: String -> value },
        fetch = fetch,
    )

    @Test
    fun `缓存命中不再执行 fetch`() = runTest {
        val store = store()
        val executor = executor(store)
        val calls = AtomicInteger()

        val first = executor.cachedString("k", 60_000L) {
            calls.incrementAndGet()
            "v1"
        }
        val second = executor.cachedString("k", 60_000L) {
            calls.incrementAndGet()
            "v2"
        }

        assertEquals("v1", first)
        assertEquals("v1", second)
        assertEquals("命中缓存不应再请求", 1, calls.get())
        assertEquals(1, store.entryCount())
    }

    @Test
    fun `失败结果不写缓存且可重试`() = runTest {
        val store = store()
        val executor = executor(store)
        val calls = AtomicInteger()

        val failed = runCatching {
            executor.cachedString("k", 60_000L) {
                calls.incrementAndGet()
                throw AppException("请求被B站安全策略拦截", retryable = true)
            }
        }
        assertTrue(failed.isFailure)
        assertEquals(0, store.entryCount())
        assertNull(store.get("k"))

        val recovered = executor.cachedString("k", 60_000L) {
            calls.incrementAndGet()
            "ok"
        }
        assertEquals("ok", recovered)
        assertEquals("失败后应重新请求", 2, calls.get())
        assertEquals(1, store.entryCount())
    }

    @Test
    fun `ttl 为 null 时不缓存每次都请求`() = runTest {
        val store = store()
        val executor = executor(store)
        val calls = AtomicInteger()
        repeat(3) {
            executor.cachedString("k", null) {
                calls.incrementAndGet()
                "v"
            }
        }
        assertEquals(3, calls.get())
        assertEquals(0, store.entryCount())
    }

    @Test
    fun `并发相同 key 只真实请求一次`() = runTest {
        val store = store()
        val executor = executor(store)
        val calls = AtomicInteger()
        val results = (1..8).map {
            async {
                executor.cachedString("k", 60_000L) {
                    delay(20)
                    calls.incrementAndGet()
                    "v"
                }
            }
        }.awaitAll()
        assertEquals(1, calls.get())
        assertTrue(results.all { it == "v" })
        assertEquals(1, store.entryCount())
    }

    @Test
    fun `现有缓存条目过期后重新请求`() = runTest {
        val store = store()
        val executor = executor(store)
        val calls = AtomicInteger()

        suspend fun runOnce(): String = executor.cachedString("k", 1_000L) {
            calls.incrementAndGet()
            "v${calls.get()}"
        }

        assertEquals("v1", runOnce())
        now += 1_500L
        assertEquals("v2", runOnce())
        assertEquals(2, calls.get())
    }
}
