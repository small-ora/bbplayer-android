package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.data.cache.HttpCacheStore
import com.bbplayer.android.data.cache.RequestThrottle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

/**
 * [HttpFetcher] 缓存接入的端到端单测（OkHttp 拦截器伪造响应，纯 JVM 无网络）：
 * 命中不重复请求、非 2xx 不缓存、写缓存前校验失败不缓存。
 */
class HttpFetcherCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var now = 1_000_000L

    /** 所有请求都返回固定 body/状态码，并统计真实请求次数。 */
    private class FakeTransport(
        private val body: String,
        private val statusCode: Int = 200,
    ) {
        val calls = AtomicInteger()
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                calls.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("fake")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
    }

    private fun store() = HttpCacheStore(temp.newFolder()) { now }

    private fun fetcher(transport: FakeTransport, cache: HttpCacheStore): HttpFetcher =
        HttpFetcher(
            client = transport.client,
            json = BiliJson,
            cache = cache,
            throttle = RequestThrottle(minIntervalMs = 0L, jitterMs = 0L),
        )

    @Test
    fun `同一 URL 第二次读取命中缓存不发请求`() = runTest {
        val transport = FakeTransport("""{"tag_name":"v9.9.9","assets":[]}""")
        val cache = store()
        val fetcher = fetcher(transport, cache)
        val url = "https://api.github.com/repos/a/b/releases/latest"

        val first = fetcher.getJsonObject(url)
        val second = fetcher.getJsonObject(url)

        assertEquals("v9.9.9", first.strOrNull("tag_name"))
        assertEquals(first.toString(), second.toString())
        assertEquals("命中缓存不应重复请求", 1, transport.calls.get())
        assertEquals(1, cache.entryCount())
    }

    @Test
    fun `非 2xx 响应不写缓存`() = runTest {
        val transport = FakeTransport("""{"message":"rate limited"}""", statusCode = 403)
        val cache = store()
        val fetcher = fetcher(transport, cache)

        val failed = runCatching { fetcher.getJsonObject("https://api.github.com/repos/a/b/releases/latest") }
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull() is AppException)
        assertEquals(0, cache.entryCount())
        assertNull(cache.get(com.bbplayer.android.data.cache.CacheKey.ofUrl("GET", "https://api.github.com/repos/a/b/releases/latest")))
    }

    @Test
    fun `写缓存前校验失败不缓存且可重试`() = runTest {
        val cache = store()
        val url = "https://s.search.bilibili.com/main/suggest?term=x"
        val blocked = FakeTransport("""{"code":-412,"message":"blocked"}""")
        val blockedFetcher = fetcher(blocked, cache)

        val failed = runCatching {
            blockedFetcher.getJsonObject(url) { payload ->
                payload.requireSuccess("搜索建议加载失败，请稍后重试")
            }
        }
        assertTrue(failed.isFailure)
        assertEquals("业务 code 失败不应缓存", 0, cache.entryCount())

        // 换成正常响应后（共享同一 store）应可重新请求并成功写缓存
        val healthy = FakeTransport("""{"code":0,"result":["夜曲"]}""")
        val healthyFetcher = fetcher(healthy, cache)
        val first = healthyFetcher.getJsonObject(url) { payload -> payload.requireSuccess("ok") }
        val second = healthyFetcher.getJsonObject(url) { payload -> payload.requireSuccess("ok") }
        assertEquals(first.toString(), second.toString())
        assertEquals("成功后第二次应命中缓存", 1, healthy.calls.get())
        assertEquals(1, cache.entryCount())
    }
}
