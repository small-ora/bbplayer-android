package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [BiliUploaderRepository] 纯 JVM 单测（假传输层 + 固定 WBI dead key，不联网）。 */
class BiliUploaderRepositoryTest {

    private fun repo(
        api: BiliContentApi,
        auth: AuthCookieProvider = FakeAuth(),
    ): BiliUploaderRepository = BiliUploaderRepository(api, auth, wbiSigner())

    private fun failure(result: Result<*>): AppException =
        result.exceptionOrNull() as? AppException
            ?: throw AssertionError("expected AppException but got $result")

    private val accInfo = """
        {
          "mid": 1346923466,
          "name": "无损音乐合集",
          "sex": "保密",
          "face": "//i0.hdslb.com/bfs/face/a.jpg",
          "sign": "每天分享无损音乐",
          "level": 6
        }
    """.trimIndent()

    // ------------------------------------------------------------------ info

    @Test
    fun `info 映射资料 关注状态与粉丝数兜底`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                when (path) {
                    "/x/space/wbi/acc/info" -> {
                        assertEquals("1346923466", params["mid"])
                        assertTrue(params.containsKey("wts"))
                        assertTrue(params.containsKey("w_rid"))
                        json(accInfo)
                    }

                    "/x/relation" -> {
                        assertEquals("1346923466", params["fid"])
                        json("""{"mid": 10086, "attribute": 2}""")
                    }

                    "/x/relation/stat" -> {
                        assertEquals("1346923466", params["vmid"])
                        json("""{"mid": 1346923466, "follower": 987654, "following": 12}""")
                    }

                    else -> error("unexpected path $path")
                }
            },
        )
        val info = repo(api).info(1346923466L).getOrThrow()
        assertEquals(1346923466L, info.mid)
        assertEquals("无损音乐合集", info.name)
        assertEquals("https://i0.hdslb.com/bfs/face/a.jpg", info.avatarUrl)
        assertEquals("每天分享无损音乐", info.sign)
        assertEquals(987654L, info.follower)
        assertTrue(info.followed)
        assertEquals(3, api.gets.size)
    }

    @Test
    fun `info attribute 6 视为已关注 attribute 0 视为未关注`() = runBlocking {
        fun repoWithAttribute(attribute: Int): BiliUploaderRepository {
            val api = FakeContentApi(
                onGet = { path, _ ->
                    when (path) {
                        "/x/space/wbi/acc/info" -> json(accInfo)
                        "/x/relation" -> json("""{"attribute": $attribute}""")
                        "/x/relation/stat" -> json("""{"follower": 1}""")
                        else -> error("unexpected path $path")
                    }
                },
            )
            return repo(api)
        }

        assertTrue(repoWithAttribute(6).info(1L).getOrThrow().followed)
        assertFalse(repoWithAttribute(0).info(1L).getOrThrow().followed)
        assertFalse(repoWithAttribute(1).info(1L).getOrThrow().followed)
    }

    @Test
    fun `info 自带 follower 时不再请求 relation stat`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, _ ->
                when (path) {
                    "/x/space/wbi/acc/info" ->
                        json("""{"mid": 7, "name": "UP", "follower": 888}""")
                    "/x/relation" -> json("""{"attribute": 2}""")
                    else -> error("unexpected path $path")
                }
            },
        )
        val info = repo(api).info(7L).getOrThrow()
        assertEquals(888L, info.follower)
        assertEquals(2, api.gets.size)
    }

    @Test
    fun `info 缺 mid 时报 UP 主不存在`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, _ ->
                when (path) {
                    "/x/space/wbi/acc/info" -> json("""{"code": -404}""")
                    else -> json("{}")
                }
            },
        )
        val error = failure(repo(api).info(1L))
        assertEquals("UP主不存在或已被封禁", error.userMessage)
        assertFalse(error.retryable)
    }

    // ---------------------------------------------------------------- videos

    @Test
    fun `videos 映射 vlist 并带 WBI 签名参数`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                assertEquals("/x/space/wbi/arc/search", path)
                assertEquals("1346923466", params["mid"])
                assertEquals("30", params["ps"])
                assertEquals("1", params["pn"])
                assertEquals("pubdate", params["order"])
                assertEquals("1700000000", params["wts"])
                assertTrue(params["w_rid"]!!.matches(Regex("[0-9a-f]{32}")))
                assertFalse(params.containsKey("keyword"))
                json(
                    """
                    {
                      "list": {
                        "vlist": [
                          {
                            "aid": 113911642789603,
                            "bvid": "BV1q4FTeBEMj",
                            "title": "夜曲",
                            "pic": "//i2.hdslb.com/bfs/archive/n.jpg",
                            "length": "03:48",
                            "duration": 228,
                            "play": 10086,
                            "created": 1700000000
                          },
                          {"bvid": "", "title": "脏数据"}
                        ]
                      },
                      "page": {"pn": 1, "ps": 30, "count": 1, "total": 61}
                    }
                    """.trimIndent(),
                )
            },
        )
        val page = repo(api).videos(1346923466L).getOrThrow()
        assertEquals(1, page.page)
        assertTrue(page.hasMore)
        assertEquals(61, page.total)
        val track = page.items.single()
        assertEquals("BV1q4FTeBEMj", track.bvid)
        assertEquals("夜曲", track.title)
        assertEquals("https://i2.hdslb.com/bfs/archive/n.jpg", track.coverUrl)
        assertEquals(228_000L, track.durationMs)
        assertEquals(10086L, track.playCount)
        assertEquals(1700000000L, track.pubDateSec)
        assertEquals(1346923466L, track.uploaderMid)
    }

    @Test
    fun `videos 最后一页没有更多`() = runBlocking {
        val api = FakeContentApi(
            onGet = { _, _ ->
                json(
                    """
                    {
                      "list": {"vlist": [{"bvid": "BV1last", "title": "歌", "length": "01:00"}]},
                      "page": {"pn": 3, "ps": 30, "count": 1, "total": 61}
                    }
                    """.trimIndent(),
                )
            },
        )
        val page = repo(api).videos(1L, page = 3).getOrThrow()
        assertFalse(page.hasMore)
    }

    @Test
    fun `videos 关键词非空时进入签名参数`() = runBlocking {
        var captured: Map<String, String>? = null
        val api = FakeContentApi(
            onGet = { _, params ->
                captured = params
                json("""{"list":{"vlist":[]},"page":{"total":0}}""")
            },
        )
        repo(api).videos(1L, keyword = "  夜曲  ").getOrThrow()
        assertEquals("夜曲", captured!!["keyword"])
    }

    @Test
    fun `videos 无 total 且不满一页时没有更多`() = runBlocking {
        val api = FakeContentApi(
            onGet = { _, _ -> json("""{"list":{"vlist":[{"bvid":"BV1x","title":"歌"}]}}""") },
        )
        val page = repo(api).videos(1L).getOrThrow()
        assertFalse(page.hasMore)
        assertEquals(0, page.total)
    }

    // ------------------------------------------------------------- setFollow

    @Test
    fun `setFollow 关注表单 act=1 re_src=11 csrf`() = runBlocking {
        val api = FakeContentApi()
        repo(api).setFollow(1346923466L, follow = true).getOrThrow()
        val call = api.posts.single()
        assertEquals("/x/relation/modify", call.path)
        assertEquals("1346923466", call.params["fid"])
        assertEquals("1", call.params["act"])
        assertEquals("11", call.params["re_src"])
        assertEquals("csrf-token-123", call.params["csrf"])
    }

    @Test
    fun `setFollow 取关表单 act=2`() = runBlocking {
        val api = FakeContentApi()
        repo(api).setFollow(1346923466L, follow = false).getOrThrow()
        assertEquals("2", api.posts.single().params["act"])
    }

    @Test
    fun `setFollow 空 mid 直接失败`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api).setFollow(0L, follow = true))
        assertEquals("UP主ID不能为空", error.userMessage)
        assertTrue(api.posts.isEmpty())
    }

    // ------------------------------------------------------------ 未登录

    @Test
    fun `info 未登录返回统一 AppException`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api, FakeAuth(loggedIn = false)).info(1L))
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
        assertFalse(error.retryable)
        assertTrue(api.gets.isEmpty())
    }

    @Test
    fun `setFollow 未登录返回统一 AppException`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api, FakeAuth(loggedIn = false)).setFollow(1L, follow = true))
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
        assertTrue(api.posts.isEmpty())
    }
}
