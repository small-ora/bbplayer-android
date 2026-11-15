package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BiliFavoritesRepository] 纯 JVM 单测：假传输层 + 真实响应形状 JSON，不联网。
 */
class BiliFavoritesRepositoryTest {

    private fun repo(
        api: BiliContentApi,
        auth: AuthCookieProvider = FakeAuth(),
    ): BiliFavoritesRepository = BiliFavoritesRepository(api, auth)

    private fun failure(result: Result<*>): AppException =
        result.exceptionOrNull() as? AppException
            ?: throw AssertionError("expected AppException but got $result")

    // ------------------------------------------------------------ folders

    @Test
    fun `folders 映射默认收藏夹与封面规范化`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, _ ->
                assertEquals("/x/v3/fav/folder/created/list-all", path)
                json(
                    """
                    {
                      "count": 2,
                      "list": [
                        {
                          "id": 123456,
                          "fid": 123456,
                          "mid": 10086,
                          "attr": 1,
                          "title": "默认收藏夹",
                          "media_count": 2,
                          "cover": "http://i0.hdslb.com/bfs/archive/a.jpg"
                        },
                        {
                          "id": 789012,
                          "attr": 0,
                          "title": "<em>我</em>的音乐",
                          "media_count": 25,
                          "cover": "//i2.hdslb.com/bfs/archive/b.jpg"
                        },
                        {"id": 0, "title": "脏数据"}
                      ]
                    }
                    """.trimIndent(),
                )
            },
        )
        val folders = repo(api).folders().getOrThrow()
        assertEquals("10086", api.gets.single().params["up_mid"])
        assertEquals(2, folders.size)

        val first = folders[0]
        assertEquals(123456L, first.id)
        assertEquals("默认收藏夹", first.title)
        assertEquals("https://i0.hdslb.com/bfs/archive/a.jpg", first.coverUrl)
        assertEquals(2, first.mediaCount)
        assertTrue(first.isDefault)

        val second = folders[1]
        assertEquals("我的音乐", second.title)
        assertEquals("https://i2.hdslb.com/bfs/archive/b.jpg", second.coverUrl)
        assertEquals(25, second.mediaCount)
        assertFalse(second.isDefault)
    }

    @Test
    fun `folders 空列表返回空`() = runBlocking {
        val api = FakeContentApi(onGet = { _, _ -> json("""{"count":0,"list":[]}""") })
        assertTrue(repo(api).folders().getOrThrow().isEmpty())
    }

    // -------------------------------------------------------- folderTracks

    @Test
    fun `folderTracks 映射媒体条目并过滤失效视频`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                assertEquals("/x/v3/fav/resource/list", path)
                assertEquals("123456", params["media_id"])
                assertEquals("1", params["pn"])
                assertEquals("40", params["ps"])
                assertEquals("web", params["platform"])
                json(
                    """
                    {
                      "info": {"id": 123456, "title": "默认收藏夹", "media_count": 3},
                      "medias": [
                        {
                          "id": 113911642789603,
                          "bvid": "BV1q4FTeBEMj",
                          "title": "【无损】<em>夜曲</em>",
                          "cover": "//i2.hdslb.com/bfs/archive/a.jpg",
                          "duration": 228,
                          "upper": {"mid": 1346923466, "name": "无损音乐合集"},
                          "cnt_info": {"play": 10086},
                          "pubtime": 1700000000,
                          "attr": 0
                        },
                        {"id": 2, "bvid": "", "title": "脏数据"},
                        {"id": 3, "bvid": "BV1deleted", "title": "已失效视频"}
                      ],
                      "has_more": true
                    }
                    """.trimIndent(),
                )
            },
        )
        val page = repo(api).folderTracks(123456L).getOrThrow()
        assertEquals(1, page.page)
        assertTrue(page.hasMore)
        assertEquals(3, page.total)
        assertEquals(1, page.items.size)

        val track = page.items.single()
        assertEquals("BV1q4FTeBEMj", track.bvid)
        assertEquals("【无损】夜曲", track.title)
        assertEquals("无损音乐合集", track.uploader)
        assertEquals(1346923466L, track.uploaderMid)
        assertEquals("https://i2.hdslb.com/bfs/archive/a.jpg", track.coverUrl)
        assertEquals(228_000L, track.durationMs)
        assertEquals(10086L, track.playCount)
        assertEquals(1700000000L, track.pubDateSec)
    }

    @Test
    fun `folderTracks 页码与页大小越界时归一`() = runBlocking {
        val api = FakeContentApi(onGet = { _, _ -> json("""{"medias":[]}""") })
        repo(api).folderTracks(123L, page = 0, pageSize = 999).getOrThrow()
        val params = api.gets.single().params
        assertEquals("1", params["pn"])
        assertEquals("40", params["ps"])
    }

    @Test
    fun `folderTracks 无 has_more 且不足一页时没有更多`() = runBlocking {
        val media = """{"bvid":"BV1aa","title":"歌","duration":100}"""
        val api = FakeContentApi(
            onGet = { _, _ -> json("""{"medias":[$media]}""") },
        )
        val page = repo(api).folderTracks(1L, page = 2).getOrThrow()
        assertEquals(2, page.page)
        assertFalse(page.hasMore)
        assertEquals(0, page.total)
    }

    @Test
    fun `folderTracks 无 has_more 但满页时视为还有更多`() = runBlocking {
        val media = (1..40).joinToString(",") {
            """{"bvid":"BV1m$it","title":"歌$it","duration":60}"""
        }
        val api = FakeContentApi(onGet = { _, _ -> json("""{"medias":[$media]}""") })
        val page = repo(api).folderTracks(1L).getOrThrow()
        assertEquals(40, page.items.size)
        assertTrue(page.hasMore)
    }

    @Test
    fun `folderTracks 空 mediaId 直接失败`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api).folderTracks(0L))
        assertEquals("收藏夹ID不能为空", error.userMessage)
        assertFalse(error.retryable)
        assertTrue(api.gets.isEmpty())
    }

    // ------------------------------------------------------ updateFavorite

    @Test
    fun `updateFavorite 表单含 rid type csrf 与增删 id`() = runBlocking {
        val api = FakeContentApi()
        repo(api).updateFavorite(
            aid = 113911642789603L,
            addTo = listOf(123L, 456L, 123L, -1L),
            removeFrom = listOf(789L),
        ).getOrThrow()

        val call = api.posts.single()
        assertEquals("/x/v3/fav/resource/deal", call.path)
        assertEquals("113911642789603", call.params["rid"])
        assertEquals("2", call.params["type"])
        assertEquals("123,456", call.params["add_media_ids"])
        assertEquals("789", call.params["del_media_ids"])
        assertEquals("csrf-token-123", call.params["csrf"])
    }

    @Test
    fun `updateFavorite 只加不加 del_media_ids`() = runBlocking {
        val api = FakeContentApi()
        repo(api).updateFavorite(1L, addTo = listOf(9L), removeFrom = emptyList()).getOrThrow()
        val params = api.posts.single().params
        assertEquals("9", params["add_media_ids"])
        assertFalse(params.containsKey("del_media_ids"))
    }

    @Test
    fun `updateFavorite 只删不带 add_media_ids`() = runBlocking {
        val api = FakeContentApi()
        repo(api).updateFavorite(1L, addTo = emptyList(), removeFrom = listOf(8L, 9L)).getOrThrow()
        val params = api.posts.single().params
        assertEquals("8,9", params["del_media_ids"])
        assertFalse(params.containsKey("add_media_ids"))
    }

    @Test
    fun `updateFavorite 两侧为空不请求`() = runBlocking {
        val api = FakeContentApi()
        repo(api).updateFavorite(1L, emptyList(), emptyList()).getOrThrow()
        assertTrue(api.posts.isEmpty())
    }

    @Test
    fun `updateFavorite 缺 csrf 时报登录态失效`() = runBlocking {
        val api = FakeContentApi()
        val auth = FakeAuth(csrf = null)
        val error = failure(repo(api, auth).updateFavorite(1L, listOf(2L), emptyList()))
        assertEquals("登录状态已失效，请重新登录", error.userMessage)
        assertFalse(error.retryable)
        assertTrue(api.posts.isEmpty())
    }

    // --------------------------------------------------------- isFavorited

    @Test
    fun `isFavorited 只返回带收藏状态的收藏夹`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                assertEquals("/x/v3/fav/folder/created/list-all", path)
                assertEquals("10086", params["up_mid"])
                assertEquals("113911642789603", params["rid"])
                assertEquals("2", params["type"])
                json(
                    """
                    {"list":[
                      {"id": 1, "fav_state": 1, "title": "a"},
                      {"id": 2, "fav_state": 0, "title": "b"},
                      {"id": 3, "fav_state": 1, "title": "c"}
                    ]}
                    """.trimIndent(),
                )
            },
        )
        assertEquals(listOf(1L, 3L), repo(api).isFavorited(113911642789603L).getOrThrow())
    }

    @Test
    fun `isFavorited 响应无状态字段时全部视为包含`() = runBlocking {
        val api = FakeContentApi(
            onGet = { _, _ ->
                json("""{"list":[{"id": 11, "title": "a"},{"id": 22, "title": "b"}]}""")
            },
        )
        assertEquals(listOf(11L, 22L), repo(api).isFavorited(1L).getOrThrow())
    }

    @Test
    fun `isFavorited 接口失败返回空列表而不抛错`() = runBlocking {
        val api = FakeContentApi(
            onGet = { _, _ -> throw AppException("视频不存在或已被删除", retryable = false) },
        )
        assertTrue(repo(api).isFavorited(1L).getOrThrow().isEmpty())
    }

    // ------------------------------------------------------------ 未登录

    @Test
    fun `folders 未登录返回统一 AppException 且不发请求`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api, FakeAuth(loggedIn = false)).folders())
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
        assertFalse(error.retryable)
        assertTrue(api.gets.isEmpty())
    }

    @Test
    fun `folderTracks 未登录返回统一 AppException`() = runBlocking {
        val error = failure(repo(FakeContentApi(), FakeAuth(loggedIn = false)).folderTracks(1L))
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
        assertFalse(error.retryable)
    }

    @Test
    fun `updateFavorite 未登录返回统一 AppException 且不发请求`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(
            repo(api, FakeAuth(loggedIn = false)).updateFavorite(1L, listOf(2L), emptyList()),
        )
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
        assertTrue(api.posts.isEmpty())
    }

    @Test
    fun `isFavorited 未登录返回统一 AppException`() = runBlocking {
        val error = failure(repo(FakeContentApi(), FakeAuth(loggedIn = false)).isFavorited(1L))
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
    }
}
