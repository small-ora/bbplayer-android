package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.model.VideoCollectionKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 视频合集（多 P / season）映射与仓储解析的纯 JVM 单测。
 *
 * 覆盖 SPEC AC5（单 P → null）、AC6（season 映射 + `ep_count` 兜底分页）、AC7（view 失败传播）。
 * 不联网：仓储通过 [BiliMusicApi] 假实现注入，响应形状与真实接口一致（fixture 见各用例）。
 */
class BiliVideoCollectionMapperTest {

    // ------------------------------------------------------------ 多 P

    private val multiPageView = json(
        """
        {
          "bvid": "BV1o6k4YUEeX",
          "title": "【小酒馆经典民谣歌单】 <em class=\"keyword\">无损音质</em> 分集播放",
          "pic": "//i0.hdslb.com/bfs/archive/cover.jpg",
          "owner": {"mid": 1346923466, "name": "无损音乐合集"},
          "stat": {"view": 88000},
          "pubdate": 1700000000,
          "pages": [
            {"cid": 111, "page": 1, "part": "001. 安和桥 - 宋冬野", "duration": 240, "first_frame": "//i0.hdslb.com/f1.jpg"},
            {"cid": 222, "page": 2, "part": "002. 董小姐 - 宋冬野", "duration": 250},
            {"cid": 333, "page": 3, "part": "", "duration": 260}
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `多 P 视频映射为 MULTI_PAGE 合集并保留每 P 的 cid 与 pageTitle`() {
        val collection = BiliMapper.videoCollection(multiPageView) ?: error("expected collection")
        assertEquals(VideoCollectionKind.MULTI_PAGE, collection.kind)
        assertEquals("BV1o6k4YUEeX", collection.bvid)
        assertEquals("【小酒馆经典民谣歌单】 无损音质 分集播放", collection.title)
        assertEquals("无损音乐合集", collection.uploader)
        assertEquals("https://i0.hdslb.com/bfs/archive/cover.jpg", collection.coverUrl)
        assertEquals(3, collection.size)
        assertTrue(collection.isCollection)

        assertEquals(listOf(111L, 222L, 333L), collection.tracks.map { it.cid })
        assertEquals(List(3) { "BV1o6k4YUEeX" }, collection.tracks.map { it.bvid })
        assertEquals(
            listOf("P1 001. 安和桥 - 宋冬野", "P2 002. 董小姐 - 宋冬野", "P3"),
            collection.tracks.map { it.pageTitle },
        )
        collection.tracks.forEach { track ->
            assertEquals("【小酒馆经典民谣歌单】 无损音质 分集播放", track.title)
        }
        assertEquals(listOf(240_000L, 250_000L, 260_000L), collection.tracks.map { it.durationMs })
        assertEquals(
            "https://i0.hdslb.com/f1.jpg",
            collection.tracks.first().coverUrl,
        )
    }

    @Test
    fun `单 P 视频不构造合集`() {
        val singlePageView = json(
            """
            {
              "bvid": "BV1single",
              "title": "单曲",
              "pic": "//i0.hdslb.com/single.jpg",
              "owner": {"mid": 1, "name": "UP"},
              "pages": [{"cid": 9, "page": 1, "part": "单曲", "duration": 180}]
            }
            """.trimIndent(),
        )
        assertNull(BiliMapper.videoCollection(singlePageView))
        // 连 pages 都没有的纯脏数据也不构造合集。
        assertNull(BiliMapper.videoCollection(json("""{"bvid":"BV1x","title":"x"}""")))
    }

    // ------------------------------------------------------------ season

    private val seasonView = json(
        """
        {
          "bvid": "BV1be41127ft",
          "title": "第一集：样片日记",
          "pic": "//i0.hdslb.com/bfs/archive/ep.jpg",
          "owner": {"mid": 946974, "name": "影视飓风"},
          "ugc_season": {
            "id": 2046621,
            "title": "<em>样片日记</em> 第一季",
            "cover": "//i0.hdslb.com/bfs/archive/season.jpg",
            "ep_count": 4,
            "sections": [
              {
                "episodes": [
                  {
                    "bvid": "BV1ep001", "aid": 1, "cid": 10001,
                    "title": "<em>第一集</em> 开箱",
                    "arc": {
                      "pic": "//i0.hdslb.com/ep1.jpg", "duration": 600, "pubdate": 1700000001,
                      "author": {"mid": 946974, "name": "影视飓风"},
                      "stat": {"view": 11111}
                    }
                  },
                  {
                    "bvid": "BV1ep002", "aid": 2, "cid": 10002, "title": "",
                    "arc": {
                      "title": "<em>第二集</em> 正片",
                      "pic": "https://i0.hdslb.com/ep2.jpg", "duration": 700, "pubdate": 1700000002,
                      "author": {"mid": 946974, "name": "影视飓风"},
                      "stat": {"view": 22222}
                    }
                  },
                  {"bvid": "", "cid": 10099, "title": "脏数据"}
                ]
              },
              {
                "episodes": [
                  {
                    "bvid": "BV1ep003", "aid": 3, "cid": 10003,
                    "title": "第三集 花絮",
                    "arc": {
                      "pic": "//i0.hdslb.com/ep3.jpg", "duration": 800, "pubdate": 1700000003,
                      "author": {"mid": 946974, "name": "影视飓风"},
                      "stat": {"view": 33333}
                    }
                  },
                  {
                    "bvid": "BV1ep002", "aid": 2, "cid": 10002, "title": "重复集",
                    "arc": {"pic": "//i0.hdslb.com/dup.jpg", "duration": 700}
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `season 合集映射为 SEASON 并展平 sections 去空去重`() {
        val collection = BiliMapper.videoCollection(seasonView) ?: error("expected collection")
        assertEquals(VideoCollectionKind.SEASON, collection.kind)
        assertEquals("BV1be41127ft", collection.bvid)
        assertEquals("样片日记 第一季", collection.title)
        assertEquals("影视飓风", collection.uploader)
        assertEquals("https://i0.hdslb.com/bfs/archive/season.jpg", collection.coverUrl)
        assertEquals(3, collection.size)
        // ep_count 仍是接口声明的真实集数（供仓储判断要不要兜底分页）。
        assertEquals(4, BiliMapper.seasonEpisodeCount(seasonView))

        val first = collection.tracks[0]
        assertEquals("BV1ep001", first.bvid)
        assertEquals(10001L, first.cid)
        assertEquals("第一集 开箱", first.title)
        assertEquals("影视飓风", first.uploader)
        assertEquals(946974L, first.uploaderMid)
        assertEquals("https://i0.hdslb.com/ep1.jpg", first.coverUrl)
        assertEquals(600_000L, first.durationMs)
        assertEquals(11111L, first.playCount)
        assertEquals(1700000001L, first.pubDateSec)

        // episode.title 为空时回退 arc.title（清洗后）。
        assertEquals("BV1ep002", collection.tracks[1].bvid)
        assertEquals("第二集 正片", collection.tracks[1].title)
        assertEquals("https://i0.hdslb.com/ep2.jpg", collection.tracks[1].coverUrl)
        assertEquals(700_000L, collection.tracks[1].durationMs)
        assertEquals(22222L, collection.tracks[1].playCount)
        assertEquals("第三集 花絮", collection.tracks[2].title)
        // 重复的 BV1ep002 只保留第一次出现的那条（重复项的 pic 不应覆盖首条）。
        assertEquals(listOf("BV1ep001", "BV1ep002", "BV1ep003"), collection.tracks.map { it.bvid })
    }

    @Test
    fun `season ep_count 缺失时回退 episodes 展平数`() {
        val noCount = json(
            """
            {
              "bvid": "BV1season0",
              "title": "无集数",
              "ugc_season": {
                "title": "合集",
                "sections": [
                  {"episodes": [{"bvid": "BV1a"}, {"bvid": "BV1b"}]},
                  {"episodes": [{"bvid": "BV1c"}]}
                ]
              }
            }
            """.trimIndent(),
        )
        assertEquals(3, BiliMapper.seasonEpisodeCount(noCount))
        // 非 season 视频没有集数概念。
        assertEquals(0, BiliMapper.seasonEpisodeCount(json("""{"bvid":"BV1x"}""")))
    }

    // ------------------------------------------------------ 仓储 season 兜底

    /** view 内只有 2 集，但 `ep_count` 声明 5 集 → 需要 `seasons_archives_list` 补全。 */
    private fun truncatedSeasonView(epCount: Int = 5): JsonObject = json(
        """
        {
          "bvid": "BV1be41127ft",
          "title": "第一集",
          "pic": "//i0.hdslb.com/bfs/archive/ep.jpg",
          "owner": {"mid": 946974, "name": "影视飓风"},
          "ugc_season": {
            "id": 2046621,
            "title": "样片日记",
            "cover": "//i0.hdslb.com/bfs/archive/season.jpg",
            "ep_count": $epCount,
            "sections": [
              {
                "episodes": [
                  {
                    "bvid": "BV1s1", "aid": 1, "cid": 11, "title": "第一集",
                    "arc": {
                      "pic": "//i0.hdslb.com/s1.jpg", "duration": 60, "pubdate": 1,
                      "author": {"mid": 946974, "name": "影视飓风"},
                      "stat": {"view": 100}
                    }
                  },
                  {
                    "bvid": "BV1s2", "aid": 2, "cid": 12, "title": "第二集",
                    "arc": {
                      "pic": "//i0.hdslb.com/s2.jpg", "duration": 60, "pubdate": 2,
                      "author": {"mid": 946974, "name": "影视飓风"},
                      "stat": {"view": 200}
                    }
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `season ep_count 超出 view 内 episodes 时按页兜底补全并去重`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, params ->
                when (path) {
                    "/x/web-interface/view" -> truncatedSeasonView()
                    "/x/polymer/web-space/seasons_archives_list" -> {
                        assertEquals("946974", params["mid"])
                        assertEquals("2046621", params["season_id"])
                        assertEquals("false", params["sort_reverse"])
                        assertEquals("30", params["page_size"])
                        when (params["page_num"]) {
                            "1" -> json(
                                """
                                {
                                  "archives": [
                                    {"bvid": "BV1s2", "title": "第二集(重复)", "pic": "//i0.hdslb.com/s2.jpg", "duration": 60, "pubdate": 2, "stat": {"view": 200}},
                                    {"bvid": "BV1s3", "title": "第三集 <em>正片</em>", "pic": "//i0.hdslb.com/s3.jpg", "duration": 70, "pubdate": 3, "stat": {"view": 300}},
                                    {"bvid": "BV1s4", "title": "第四集", "pic": "//i0.hdslb.com/s4.jpg", "duration": 80, "pubdate": 4, "stat": {"view": 400}}
                                  ]
                                }
                                """.trimIndent(),
                            )
                            // 第二页为空：停止翻页，不再请求第三页。
                            else -> json("""{"archives":[]}""")
                        }
                    }

                    else -> error("unexpected path $path")
                }
            },
        )
        val result = repo(api).getVideoCollection("BV1be41127ft")
        val collection = result.getOrThrow() ?: error("expected collection")

        assertEquals(3, api.gets.size)
        assertEquals(
            listOf(
                "/x/web-interface/view",
                "/x/polymer/web-space/seasons_archives_list",
                "/x/polymer/web-space/seasons_archives_list",
            ),
            api.gets.map { it.path },
        )
        assertEquals(listOf("1", "2"), api.gets.drop(1).map { it.params["page_num"] })

        assertEquals(VideoCollectionKind.SEASON, collection.kind)
        assertEquals(4, collection.size)
        assertEquals(
            listOf("BV1s1", "BV1s2", "BV1s3", "BV1s4"),
            collection.tracks.map { it.bvid },
        )
        val fallbackTrack = collection.tracks.last()
        assertEquals("第三集 正片", collection.tracks[2].title)
        assertEquals(0L, fallbackTrack.cid) // archives 无 cid，播放时 pagelist 兜底
        assertEquals("https://i0.hdslb.com/s4.jpg", fallbackTrack.coverUrl)
        assertEquals(80_000L, fallbackTrack.durationMs)
        assertEquals(400L, fallbackTrack.playCount)
        assertEquals(4L, fallbackTrack.pubDateSec)
    }

    @Test
    fun `season 兜底分页失败时保留 view 内 episodes 且整体仍成功`() = runBlocking {
        val api = FakeContentApi(
            onGet = { path, _ ->
                when (path) {
                    "/x/web-interface/view" -> truncatedSeasonView()
                    else -> throw AppException("B站接口错误（code=-404）", retryable = false)
                }
            },
        )
        val result = repo(api).getVideoCollection("BV1be41127ft")
        assertTrue(result.isSuccess)
        val collection = result.getOrThrow() ?: error("expected collection")
        assertEquals(VideoCollectionKind.SEASON, collection.kind)
        assertEquals(2, collection.size)
        assertEquals(listOf("BV1s1", "BV1s2"), collection.tracks.map { it.bvid })
        assertEquals(2, api.gets.size)
    }

    @Test
    fun `season 集数已完整时不发兜底请求`() = runBlocking {
        val completeView = truncatedSeasonView(epCount = 2)
        val api = FakeContentApi(
            onGet = { path, _ ->
                assertEquals("/x/web-interface/view", path)
                completeView
            },
        )
        val collection = repo(api).getVideoCollection("BV1be41127ft").getOrThrow()
        assertEquals(2, collection?.size)
        assertEquals(1, api.gets.size)
    }

    @Test
    fun `单 P 视频仓储返回 success null`() = runBlocking {
        val singleView = json(
            """
            {
              "bvid": "BV1single",
              "title": "单曲",
              "owner": {"mid": 1, "name": "UP"},
              "pages": [{"cid": 9, "page": 1, "part": "单曲", "duration": 180}]
            }
            """.trimIndent(),
        )
        val api = FakeContentApi(
            onGet = { path, _ ->
                assertEquals("/x/web-interface/view", path)
                singleView
            },
        )
        assertNull(repo(api).getVideoCollection("BV1single").getOrThrow())
        assertEquals(1, api.gets.size)
    }

    // ------------------------------------------------------------ 失败路径

    @Test
    fun `view 请求失败时传播为 Result failure`() = runBlocking {
        val api = FakeContentApi(
            onGet = { _, _ -> throw AppException("B站接口错误（code=-404）", retryable = false) },
        )
        val error = failure(repo(api).getVideoCollection("BV1missing"))
        assertEquals("B站接口错误（code=-404）", error.userMessage)
        assertEquals(1, api.gets.size)
    }

    @Test
    fun `空 bvid 直接失败且不发请求`() = runBlocking {
        val api = FakeContentApi()
        val error = failure(repo(api).getVideoCollection("  "))
        assertEquals("视频ID不能为空", error.userMessage)
        assertTrue(api.gets.isEmpty())
    }

    // ------------------------------------------------------------ 工具

    private fun repo(api: FakeContentApi): BiliMusicRepository = BiliMusicRepository(TestMusicApi(api))

    private fun failure(result: Result<*>): AppException =
        result.exceptionOrNull() as? AppException
            ?: throw AssertionError("expected AppException but got $result")

    /** [BiliMusicApi] 测试实现：只把 GET 委托给 [FakeContentApi]（记录调用/返回 fixture）。 */
    private class TestMusicApi(private val content: FakeContentApi = FakeContentApi()) : BiliMusicApi {
        override suspend fun getObject(path: String, params: Map<String, String>): JsonObject =
            content.getObject(path, params)

        override suspend fun getArray(path: String, params: Map<String, String>): JsonArray =
            JsonArray(emptyList())

        override suspend fun ensureBuvid(): String? = null

        override fun isLoggedIn(): Boolean = false

        override suspend fun resolveShortLink(url: String): ShortLinkResolution? = null
    }
}
