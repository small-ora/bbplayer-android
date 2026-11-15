package com.bbplayer.android.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [BiliContentMapper] 纯函数单测：字段命名差异 / 分页语义 / 元数据形状。 */
class BiliContentMapperTest {

    private fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text) as JsonObject

    @Test
    fun `mediaTrack 兼容 length 时间串与 cnt_info 播放数`() {
        val track = BiliContentMapper.mediaTrack(
            json(
                """
                {
                  "bvid": "BV1length",
                  "title": "歌名",
                  "pic": "//i0.hdslb.com/x.jpg",
                  "length": "12:34",
                  "cnt_info": {"play": 77}
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals(754_000L, track.durationMs)
        assertEquals(77L, track.playCount)
        assertEquals("https://i0.hdslb.com/x.jpg", track.coverUrl)
        assertEquals("未知UP主", track.uploader)
    }

    @Test
    fun `mediaTrack 无 bvid 返回 null`() {
        assertNull(BiliContentMapper.mediaTrack(json("""{"title":"x"}""")))
        assertNull(BiliContentMapper.mediaTrack(json("""{"bvid":"  ","title":"x"}""")))
    }

    @Test
    fun `favoriteTracks 支持 has_more 数字 0 与 1`() {
        val more = BiliContentMapper.favoriteTracks(
            json("""{"medias":[{"bvid":"BV1a","title":"a"}],"has_more":1}"""),
            1,
            40,
        )
        assertTrue(more.hasMore)

        val end = BiliContentMapper.favoriteTracks(
            json("""{"medias":[{"bvid":"BV1a","title":"a"}],"has_more":0}"""),
            1,
            40,
        )
        assertFalse(end.hasMore)
    }

    @Test
    fun `collection 缺顶层字段时回退 meta`() {
        val collection = BiliContentMapper.collection(
            json(
                """
                {
                  "season_id": 9911,
                  "meta": {
                    "name": "<em>年度</em>歌单",
                    "cover": "//i1.hdslb.com/s.jpg",
                    "total": 18,
                    "description": "精选"
                  }
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals(9911L, collection.id)
        assertEquals("年度歌单", collection.title)
        assertEquals("https://i1.hdslb.com/s.jpg", collection.coverUrl)
        assertEquals(18, collection.count)
        assertEquals("精选", collection.description)
    }

    @Test
    fun `spaceCollections 忽略无 id 项并去重`() {
        val collections = BiliContentMapper.spaceCollections(
            json(
                """
                {
                  "items_lists": {
                    "seasons_list": [
                      {"season_id": 1, "meta": {"name": "a"}},
                      {"season_id": 0, "meta": {"name": "脏数据"}},
                      {"season_id": 1, "meta": {"name": "重复"}}
                    ],
                    "series_list": [{"series_id": 2, "meta": {"name": "b"}}]
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(1L, 2L), collections.map { it.id })
    }

    @Test
    fun `uploaderVideos 满页无 total 时视为还有更多`() {
        val vlist = (1..30).joinToString(",") {
            """{"bvid":"BV1v$it","title":"歌$it","length":"01:00"}"""
        }
        val page = BiliContentMapper.uploaderVideos(
            json("""{"list":{"vlist":[$vlist]}}"""),
            page = 1,
            pageSize = 30,
        )
        assertEquals(30, page.items.size)
        assertTrue(page.hasMore)
        assertEquals(0, page.total)
    }

    @Test
    fun `uploaderVideos 空 vlist 没有更多`() {
        val page = BiliContentMapper.uploaderVideos(json("""{"list":{"vlist":[]}}"""), 1, 30)
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `relatedFollowed 兼容 attribute 与布尔字段`() {
        assertTrue(BiliContentMapper.relationFollowed(json("""{"attribute":2}""")))
        assertTrue(BiliContentMapper.relationFollowed(json("""{"attribute":6}""")))
        assertFalse(BiliContentMapper.relationFollowed(json("""{"attribute":0}""")))
        assertTrue(BiliContentMapper.relationFollowed(json("""{"following":true}""")))
        assertFalse(BiliContentMapper.relationFollowed(json("""{}""")))
    }

    @Test
    fun `uploaderInfo 缺失 mid 返回 null`() {
        assertNull(BiliContentMapper.uploaderInfo(json("""{"name":"UP"}"""), followed = false))
    }
}
