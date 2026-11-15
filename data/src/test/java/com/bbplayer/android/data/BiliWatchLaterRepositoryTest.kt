package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [BiliWatchLaterRepository] 纯 JVM 单测（假传输层 + 真实响应形状，不联网）。 */
class BiliWatchLaterRepositoryTest {

    private fun repo(
        api: BiliContentApi,
        auth: AuthCookieProvider = FakeAuth(),
    ): BiliWatchLaterRepository = BiliWatchLaterRepository(api, auth)

    private fun failure(result: Result<*>): AppException =
        result.exceptionOrNull() as? AppException
            ?: throw AssertionError("expected AppException but got $result")

    @Test
    fun `list 映射稍后再看条目`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                assertEquals("/x/v2/history/toview", path)
                assertTrue(params.isEmpty())
                json(
                    """
                    {
                      "count": 2,
                      "list": [
                        {
                          "bvid": "BV1later001",
                          "cid": 111222,
                          "title": "夜曲",
                          "cover": "//i0.hdslb.com/bfs/archive/l.jpg",
                          "duration": 228,
                          "progress": -1,
                          "owner": {"mid": 1346923466, "name": "无损音乐合集"},
                          "pubdate": 1700000000
                        },
                        {"bvid": "", "title": "脏数据"}
                      ]
                    }
                    """.trimIndent(),
                )
            },
        )
        val tracks = repo(api).list().getOrThrow()
        assertEquals(1, tracks.size)
        val track = tracks.single()
        assertEquals("BV1later001", track.bvid)
        assertEquals(111222L, track.cid)
        assertEquals("夜曲", track.title)
        assertEquals("无损音乐合集", track.uploader)
        assertEquals(1346923466L, track.uploaderMid)
        assertEquals("https://i0.hdslb.com/bfs/archive/l.jpg", track.coverUrl)
        assertEquals(228_000L, track.durationMs)
        assertEquals(1700000000L, track.pubDateSec)
    }

    @Test
    fun `list 空列表返回空`() = runBlocking {
        val api = FakeContentApi(onGet = { _, _ -> json("""{"count":0,"list":[]}""") })
        assertTrue(repo(api).list().getOrThrow().isEmpty())
    }

    @Test
    fun `add 表单含 aid cid csrf`() = runBlocking {
        val api = FakeContentApi()
        repo(api).add(aid = 113911642789603L, cid = 111222L).getOrThrow()

        val call = api.posts.single()
        assertEquals("/x/v2/history/toview/add", call.path)
        assertEquals("113911642789603", call.params["aid"])
        assertEquals("111222", call.params["cid"])
        assertEquals("csrf-token-123", call.params["csrf"])
    }

    @Test
    fun `add 无 cid 时省略 cid 字段`() = runBlocking {
        val api = FakeContentApi()
        repo(api).add(aid = 1L).getOrThrow()
        val params = api.posts.single().params
        assertEquals("1", params["aid"])
        assertFalse(params.containsKey("cid"))
        assertEquals("csrf-token-123", params["csrf"])
    }

    @Test
    fun `remove 表单含 aid 与 csrf`() = runBlocking {
        val api = FakeContentApi()
        repo(api).remove(42L).getOrThrow()
        val call = api.posts.single()
        assertEquals("/x/v2/history/toview/del", call.path)
        assertEquals(mapOf("aid" to "42", "csrf" to "csrf-token-123"), call.params)
    }

    @Test
    fun `clear 表单只有 csrf`() = runBlocking {
        val api = FakeContentApi()
        repo(api).clear().getOrThrow()
        val call = api.posts.single()
        assertEquals("/x/v2/history/toview/clear", call.path)
        assertEquals(mapOf("csrf" to "csrf-token-123"), call.params)
    }

    @Test
    fun `add 非法 aid 直接失败`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api).add(0L))
        assertEquals("视频ID不能为空", error.userMessage)
        assertTrue(api.posts.isEmpty())
    }

    @Test
    fun `缺 csrf 时 add 报登录态失效`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api, FakeAuth(csrf = "")).add(1L))
        assertEquals("登录状态已失效，请重新登录", error.userMessage)
        assertTrue(api.posts.isEmpty())
    }

    @Test
    fun `list 未登录返回统一 AppException`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api, FakeAuth(loggedIn = false)).list())
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
        assertFalse(error.retryable)
        assertTrue(api.gets.isEmpty())
    }

    @Test
    fun `clear 未登录返回统一 AppException`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api, FakeAuth(loggedIn = false)).clear())
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
        assertTrue(api.posts.isEmpty())
    }
}
