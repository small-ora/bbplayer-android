package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [BiliCollectionRepository] 纯 JVM 单测（假传输层 + 真实响应形状，不联网）。 */
class BiliCollectionRepositoryTest {

    private fun repo(
        api: BiliContentApi,
        auth: AuthCookieProvider = FakeAuth(),
    ): BiliCollectionRepository = BiliCollectionRepository(api, auth)

    private fun failure(result: Result<*>): AppException =
        result.exceptionOrNull() as? AppException
            ?: throw AssertionError("expected AppException but got $result")

    private val spaceJson = """
        {
          "items_lists": {
            "seasons_list": [
              {
                "season_id": 9911,
                "meta": {
                  "name": "<em>2024</em> 年度歌单",
                  "cover": "//i1.hdslb.com/bfs/archive/s.jpg",
                  "total": 18,
                  "description": "年度精选"
                }
              }
            ],
            "series_list": [
              {"series_id": 7711, "meta": {"name": "翻唱系列", "cover": "https://i1.hdslb.com/x.jpg", "total": 6}}
            ]
          }
        }
    """.trimIndent()

    // ------------------------------------------------------ myCollections

    @Test
    fun `myCollections 首选空间合集并映射 season 与 series`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                assertEquals("/x/polymer/web-space/seasons_series_list", path)
                assertEquals("10086", params["mid"])
                assertEquals("1", params["page_num"])
                assertEquals("50", params["page_size"])
                json(spaceJson)
            },
        )
        val collections = repo(api).myCollections().getOrThrow()
        assertEquals(2, collections.size)
        assertEquals(9911L, collections[0].id)
        assertEquals("2024 年度歌单", collections[0].title)
        assertEquals("https://i1.hdslb.com/bfs/archive/s.jpg", collections[0].coverUrl)
        assertEquals(18, collections[0].count)
        assertEquals("年度精选", collections[0].description)
        assertEquals(7711L, collections[1].id)
        assertEquals("翻唱系列", collections[1].title)
        assertEquals(6, collections[1].count)
        assertEquals(1, api.gets.size)
    }

    @Test
    fun `myCollections 空间接口为空时回退 collected list`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                when (path) {
                    "/x/polymer/web-space/seasons_series_list" -> json("""{"items_lists":{}}""")
                    "/x/v3/fav/folder/collected/list" -> {
                        assertEquals("10086", params["up_mid"])
                        assertEquals("1", params["pn"])
                        assertEquals("50", params["ps"])
                        assertEquals("web", params["platform"])
                        json(
                            """
                            {"list":[
                              {"id": 4567, "title": "华语经典", "cover": "//i0.hdslb.com/bfs/archive/c.jpg", "media_count": 42}
                            ]}
                            """.trimIndent(),
                        )
                    }

                    else -> error("unexpected path $path")
                }
            },
        )
        val collections = repo(api).myCollections().getOrThrow()
        assertEquals(listOf(4567L), collections.map { it.id })
        assertEquals("华语经典", collections.single().title)
        assertEquals(42, collections.single().count)
        assertEquals(2, api.gets.size)
    }

    @Test
    fun `myCollections 空间接口失败时回退 collected list`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, _ ->
                when (path) {
                    "/x/polymer/web-space/seasons_series_list" ->
                        throw AppException("B站接口错误（code=-404）", retryable = false)
                    else -> json("""{"list":[{"id": 5, "title": "兜底"}]}""")
                }
            },
        )
        val collections = repo(api).myCollections().getOrThrow()
        assertEquals(listOf(5L), collections.map { it.id })
    }

    @Test
    fun `myCollections 两个接口都失败时返回空列表`() = runBlocking {
        val api = FakeContentApi(
            onGet = { _, _ -> throw AppException("B站接口错误（code=-404）", retryable = false) },
        )
        assertTrue(repo(api).myCollections().getOrThrow().isEmpty())
    }

    // ---------------------------------------------------- collectionTracks

    @Test
    fun `collectionTracks 映射 medias 与 has_more`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                assertEquals("/x/space/fav/season/list", path)
                assertEquals("9911", params["season_id"])
                assertEquals("1", params["pn"])
                assertEquals("30", params["ps"])
                json(
                    """
                    {
                      "info": {"id": 9911, "title": "2024 年度歌单", "media_count": 2},
                      "medias": [
                        {
                          "bvid": "BV1season01",
                          "title": "晴天",
                          "cover": "//i2.hdslb.com/bfs/archive/q.jpg",
                          "duration": 269,
                          "upper": {"mid": 2, "name": "周杰伦"},
                          "cnt_info": {"play": 999},
                          "pubtime": 1700000001
                        }
                      ],
                      "has_more": false
                    }
                    """.trimIndent(),
                )
            },
        )
        val page = repo(api).collectionTracks(9911L).getOrThrow()
        assertEquals(1, page.page)
        assertFalse(page.hasMore)
        assertEquals(2, page.total)
        val track = page.items.single()
        assertEquals("BV1season01", track.bvid)
        assertEquals("晴天", track.title)
        assertEquals("周杰伦", track.uploader)
        assertEquals(2L, track.uploaderMid)
        assertEquals("https://i2.hdslb.com/bfs/archive/q.jpg", track.coverUrl)
        assertEquals(269_000L, track.durationMs)
        assertEquals(999L, track.playCount)
        assertEquals(1, api.gets.size)
    }

    @Test
    fun `collectionTracks 主接口失败时回退空间合集视频接口`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                when (path) {
                    "/x/space/fav/season/list" ->
                        throw AppException("B站接口错误（code=-404）", retryable = false)
                    "/x/polymer/web-space/seasons_archives_list" -> {
                        assertEquals("10086", params["mid"])
                        assertEquals("9911", params["season_id"])
                        assertEquals("1", params["page_num"])
                        assertEquals("30", params["page_size"])
                        assertEquals("false", params["sort_reverse"])
                        json(
                            """
                            {
                              "archives": [
                                {
                                  "bvid": "BV1fallback",
                                  "title": "夜曲",
                                  "pic": "//i0.hdslb.com/bfs/archive/n.jpg",
                                  "duration": 228,
                                  "owner": {"mid": 3, "name": "UP主"},
                                  "stat": {"view": 12345},
                                  "pubdate": 1700000002
                                }
                              ],
                              "page": {"total": 1}
                            }
                            """.trimIndent(),
                        )
                    }

                    else -> error("unexpected path $path")
                }
            },
        )
        val page = repo(api).collectionTracks(9911L).getOrThrow()
        assertEquals(1, page.total)
        assertFalse(page.hasMore)
        val track = page.items.single()
        assertEquals("BV1fallback", track.bvid)
        assertEquals(12345L, track.playCount)
        assertEquals(228_000L, track.durationMs)
    }

    @Test
    fun `collectionTracks 主接口首页空时回退并取到内容`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, _ ->
                when (path) {
                    "/x/space/fav/season/list" -> json("""{"medias":[],"has_more":true}""")
                    else -> json("""{"archives":[{"bvid":"BV1fb2","title":"歌","length":"01:00"}],"page":{"total":1}}""")
                }
            },
        )
        val page = repo(api).collectionTracks(9911L).getOrThrow()
        assertEquals("BV1fb2", page.items.single().bvid)
        assertEquals(2, api.gets.size)
    }

    @Test
    fun `collectionTracks 第二页为空时不回退`() = runBlocking {
        val api = FakeContentApi(
            onGet = { _, _ -> json("""{"medias":[],"has_more":false}""") },
        )
        val page = repo(api).collectionTracks(9911L, page = 2).getOrThrow()
        assertTrue(page.items.isEmpty())
        assertEquals(2, page.page)
        assertEquals(1, api.gets.size)
    }

    @Test
    fun `collectionTracks 空 seasonId 直接失败`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api).collectionTracks(0L))
        assertEquals("合集ID不能为空", error.userMessage)
        assertTrue(api.gets.isEmpty())
    }

    // ------------------------------------------------------------ 未登录

    @Test
    fun `myCollections 未登录返回统一 AppException`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api, FakeAuth(loggedIn = false)).myCollections())
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
        assertFalse(error.retryable)
        assertTrue(api.gets.isEmpty())
    }

    @Test
    fun `collectionTracks 未登录返回统一 AppException`() = runBlocking {
        val error = failure(repo(FakeContentApi(), FakeAuth(loggedIn = false)).collectionTracks(1L))
        assertEquals("请先登录 Bilibili 账号", error.userMessage)
    }
}
