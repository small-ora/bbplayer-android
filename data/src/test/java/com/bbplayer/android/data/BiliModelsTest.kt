package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliModelsTest {

    private fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text).jsonObject

    // ------------------------------------------------------------ 搜索映射

    @Test
    fun `搜索页映射并过滤课堂和空 bvid`() {
        val data = json(
            """
            {
              "numPages": 50,
              "result": [
                {
                  "type": "video",
                  "bvid": "BV1q4FTeBEMj",
                  "aid": 113911642789603,
                  "title": "【无损音质】<em class=\"keyword\">周杰伦</em> - 夜曲",
                  "author": "无损音乐合集",
                  "mid": 1346923466,
                  "pic": "//i2.hdslb.com/bfs/archive/a.jpg",
                  "duration": "3:48",
                  "play": 10086,
                  "pubdate": 1700000000
                },
                {"type": "ketang", "bvid": "BV1classroom", "title": "课程"},
                {"type": "video", "bvid": "", "title": "脏数据"}
              ]
            }
            """.trimIndent(),
        )
        val page = BiliMapper.searchPage(data, 1)
        assertEquals(1, page.items.size)
        assertEquals(50, page.totalPages)
        assertTrue(page.hasMore)
        val track = page.items.first()
        assertEquals("BV1q4FTeBEMj", track.bvid)
        assertEquals("【无损音质】周杰伦 - 夜曲", track.title)
        assertEquals("无损音乐合集", track.uploader)
        assertEquals(1346923466L, track.uploaderMid)
        assertEquals("https://i2.hdslb.com/bfs/archive/a.jpg", track.coverUrl)
        assertEquals(228_000L, track.durationMs)
        assertEquals(10086L, track.playCount)
        assertEquals(1700000000L, track.pubDateSec)
        // 搜索结果没有 cid，播放时由 playurl 兜底
        assertEquals(0L, track.cid)
    }

    @Test
    fun `搜索最后一页没有更多`() {
        val data = json("""{"numPages": 3, "result": []}""")
        val page = BiliMapper.searchPage(data, 3)
        assertFalse(page.hasMore)
        assertEquals(3, page.page)
    }

    @Test
    fun `搜索无 numPages 时按条数判断`() {
        val items = (1..20).joinToString(",") {
            """{"type":"video","bvid":"BV1q4FTeBE$it","title":"歌$it","duration":"1:00"}"""
        }
        val page = BiliMapper.searchPage(json("""{"result":[$items]}"""), 1)
        assertTrue(page.hasMore)
        assertEquals(20, page.items.size)
    }

    // ------------------------------------------------------------ 详情 / 分 P

    @Test
    fun `view 映射为 Track`() {
        val view = json(
            """
            {
              "bvid": "BV1q4FTeBEMj",
              "aid": 113911642789603,
              "title": "周杰伦精选",
              "pic": "http://i2.hdslb.com/bfs/archive/b.jpg",
              "duration": 25142,
              "pubdate": 1700000000,
              "owner": {"mid": 1346923466, "name": "无损音乐合集"},
              "stat": {"view": 60779958},
              "pages": [
                {"cid": 28139389900, "page": 1, "part": "001. 夜曲-周杰伦", "duration": 228},
                {"cid": 28139390958, "page": 2, "part": "002. 晴天-周杰伦", "duration": 270}
              ]
            }
            """.trimIndent(),
        )
        val track = BiliMapper.viewTrack(view)
        assertEquals("BV1q4FTeBEMj", track.bvid)
        assertEquals(28139389900L, track.cid)
        assertEquals("周杰伦精选", track.title)
        assertEquals("无损音乐合集", track.uploader)
        assertEquals(1346923466L, track.uploaderMid)
        assertEquals("https://i2.hdslb.com/bfs/archive/b.jpg", track.coverUrl)
        assertEquals(228_000L, track.durationMs)
        assertEquals(60779958L, track.playCount)
        assertEquals(1700000000L, track.pubDateSec)
        assertEquals("", track.pageTitle)
    }

    @Test
    fun `多 P 视频展开为每 P 一个 Track`() {
        val view = json(
            """
            {
              "bvid": "BV1q4FTeBEMj",
              "title": "周杰伦精选",
              "pic": "//i2.hdslb.com/bfs/archive/b.jpg",
              "owner": {"mid": 1, "name": "UP"},
              "stat": {"view": 1},
              "pages": [
                {"cid": 11, "page": 1, "part": "001. 夜曲", "duration": 228},
                {"cid": 22, "page": 2, "part": "002. 晴天", "duration": 270}
              ]
            }
            """.trimIndent(),
        )
        val tracks = BiliMapper.videoPages(view)
        assertEquals(2, tracks.size)
        assertEquals(listOf("P1 001. 夜曲", "P2 002. 晴天"), tracks.map { it.pageTitle })
        assertEquals(listOf(11L, 22L), tracks.map { it.cid })
        assertEquals("周杰伦精选 · P2 002. 晴天", tracks[1].displayTitle)
        assertEquals("https://i2.hdslb.com/bfs/archive/b.jpg", tracks[0].coverUrl)
    }

    @Test
    fun `单 P 视频不追加分 P 标题`() {
        val view = json(
            """
            {
              "bvid": "BV1q4FTeBEMj",
              "title": "夜曲",
              "owner": {"mid": 1, "name": "UP"},
              "pages": [{"cid": 11, "page": 1, "part": "夜曲", "duration": 228}]
            }
            """.trimIndent(),
        )
        val tracks = BiliMapper.videoPages(view)
        assertEquals(1, tracks.size)
        assertEquals("", tracks.first().pageTitle)
        assertEquals("夜曲", tracks.first().displayTitle)
    }

    // ------------------------------------------------------------ 排行 / 相关

    @Test
    fun `排行列表映射`() {
        val list = json(
            """
            {"list": [
              {
                "aid": 1, "bvid": "BV1q4FTeBEMj", "cid": 29167322421,
                "title": "给我们的组合起个名！",
                "pic": "//i1.hdslb.com/bfs/archive/c.jpg",
                "duration": 15, "pubdate": 1743423401,
                "owner": {"mid": 477676711, "name": "田一名sir"},
                "stat": {"view": 17886927}
              },
              {
                "aid": 2, "bvid": "BV1xx411c7mD",
                "title": "第二首", "pic": "http://i0.hdslb.com/d.jpg",
                "duration": 208, "pubdate": 1700000001,
                "owner": {"mid": 2, "name": "UP2"},
                "stat": {"view": 3}
              }
            ]}
            """.trimIndent(),
        )
        val tracks = BiliMapper.videoList(list.arrOrNull("list"))
        assertEquals(2, tracks.size)
        assertEquals("BV1q4FTeBEMj", tracks[0].bvid)
        assertEquals(29167322421L, tracks[0].cid)
        assertEquals("田一名sir", tracks[0].uploader)
        assertEquals(477676711L, tracks[0].uploaderMid)
        assertEquals("https://i1.hdslb.com/bfs/archive/c.jpg", tracks[0].coverUrl)
        assertEquals(15_000L, tracks[0].durationMs)
        assertEquals(17886927L, tracks[0].playCount)
        assertEquals("https://i0.hdslb.com/d.jpg", tracks[1].coverUrl)
        assertEquals(208_000L, tracks[1].durationMs)
    }

    @Test
    fun `相关列表过滤无 bvid 条目`() {
        val array = BiliJson.parseToJsonElement(
            """[{"bvid": "BV1q4FTeBEMj", "title": "A", "duration": 1}, {"bvid": "", "title": "B"}]""",
        ) as kotlinx.serialization.json.JsonArray
        val tracks = BiliMapper.videoList(array)
        assertEquals(1, tracks.size)
        assertEquals("A", tracks[0].title)
    }

    // ------------------------------------------------------------ 音频流解析

    @Test
    fun `playurl 两种字段名兼容且命中请求档位`() {
        val data = json(
            """
            {
              "dash": {
                "audio": [
                  {"id": 30216, "base_url": "https://cdn/64.m4s", "backup_url": "https://bak/64.m4s"},
                  {"id": 30232, "baseUrl": "https://cdn/128.m4s", "backupUrl": "https://bak/128.m4s"},
                  {"id": 30280, "baseUrl": "https://cdn/192.m4s", "backup_url": "https://bak/192.m4s"}
                ],
                "dolby": {"audio": null},
                "flac": null
              },
              "volume": {"measured_i": -8.5}
            }
            """.trimIndent(),
        )
        val stream = BiliMapper.audioStream(data, 30232)
        assertEquals("https://cdn/128.m4s", stream.url)
        assertEquals(listOf("https://bak/128.m4s"), stream.backupUrls)
        assertEquals(30232, stream.qualityId)
        assertEquals("audio/mp4", stream.mimeType)
        assertEquals(-8.5, stream.measuredLoudnessDb!!, 0.0001)
    }

    @Test
    fun `playurl 请求档位不存在时回退最高可用`() {
        val data = json(
            """
            {"dash": {"audio": [
              {"id": 30216, "base_url": "https://cdn/64.m4s", "backup_url": ["https://bak1/64.m4s"]},
              {"id": 30280, "baseUrl": "https://cdn/192.m4s", "backup_url": ["https://bak1/192.m4s", "https://bak2/192.m4s"]}
            ]}}
            """.trimIndent(),
        )
        val stream = BiliMapper.audioStream(data, 30232)
        assertEquals(30280, stream.qualityId)
        assertEquals("https://cdn/192.m4s", stream.url)
        assertEquals(listOf("https://bak1/192.m4s", "https://bak2/192.m4s"), stream.backupUrls)
    }

    @Test
    fun `dash audio 为空时回退 dolby 与 flac`() {
        val dolbyData = json(
            """
            {"dash": {"audio": [], "dolby": {"audio": [{"id": 30250, "base_url": "https://cdn/dolby.m4s"}]}}}
            """.trimIndent(),
        )
        assertEquals(30250, BiliMapper.audioStream(dolbyData, 30280).qualityId)

        val flacData = json(
            """
            {"dash": {"audio": [], "dolby": {"audio": null}, "flac": {"audio": {"id": 30251, "base_url": "https://cdn/flac.m4s"}}}}
            """.trimIndent(),
        )
        val flacStream = BiliMapper.audioStream(flacData, 30280)
        assertEquals(30251, flacStream.qualityId)
        assertEquals("https://cdn/flac.m4s", flacStream.url)
    }

    @Test
    fun `无 dash 时回退 durl`() {
        val data = json(
            """
            {"quality": 32, "durl": [{"url": "https://cdn/old.flv", "backup_url": "https://bak/old.flv"}]}
            """.trimIndent(),
        )
        val stream = BiliMapper.audioStream(data, 30280)
        assertEquals("https://cdn/old.flv", stream.url)
        assertEquals(listOf("https://bak/old.flv"), stream.backupUrls)
        assertEquals(30280, stream.qualityId)
        assertEquals(null, stream.measuredLoudnessDb)
    }

    @Test
    fun `完全没有可用流时抛 AppException`() {
        val exception = assertThrows(AppException::class.java) {
            BiliMapper.audioStream(json("""{"dash": {"audio": []}}"""), 30280)
        }
        assertFalse(exception.retryable)
    }

    @Test
    fun `选流器规则`() {
        val candidates = listOf(
            AudioCandidate(30216, "u64"),
            AudioCandidate(30280, "u192"),
        )
        assertEquals(AudioCandidate(30216, "u64"), AudioStreamSelector.select(candidates, 30216))
        assertEquals(AudioCandidate(30280, "u192"), AudioStreamSelector.select(candidates, 99999))
    }
}
