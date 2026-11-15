package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.model.ExternalSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlaylistParserTest {

    private fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text).jsonObject

    private fun ref(input: String): ExternalInput.Ref =
        ExternalPlaylistParser.parse(input) as ExternalInput.Ref

    // ------------------------------------------------------------ 输入解析

    @Test
    fun `网易云歌单链接解析出id`() {
        val ref = ref("https://music.163.com/playlist?id=3778678&userid=123456")
        assertEquals(ExternalSource.NETEASE, ref.source)
        assertEquals("3778678", ref.id)
    }

    @Test
    fun `网易云hash路由链接解析出id`() {
        val ref = ref("https://music.163.com/#/playlist?id=24381616")
        assertEquals(ExternalSource.NETEASE, ref.source)
        assertEquals("24381616", ref.id)
    }

    @Test
    fun `纯数字ID默认按网易云处理`() {
        val ref = ref("  3778678  ")
        assertEquals(ExternalSource.NETEASE, ref.source)
        assertEquals("3778678", ref.id)
    }

    @Test
    fun `网易云短链识别为待跟随重定向`() {
        val parsed = ExternalPlaylistParser.parse("http://163cn.tv/abcdEF")
        assertTrue(parsed is ExternalInput.ShortLink)
        assertEquals("http://163cn.tv/abcdEF", (parsed as ExternalInput.ShortLink).url)
    }

    @Test
    fun `分享文案里的网易云短链也能识别`() {
        val text = "分享歌单： http://163cn.tv/abcdEF （来自网易云音乐）"
        val parsed = ExternalPlaylistParser.parse(text)
        assertTrue(parsed is ExternalInput.ShortLink)
    }

    @Test
    fun `QQ音乐歌单链接解析出id`() {
        val ref = ref("https://y.qq.com/n/ryqq/playlist/7011264340")
        assertEquals(ExternalSource.QQ_MUSIC, ref.source)
        assertEquals("7011264340", ref.id)
    }

    @Test
    fun `QQ音乐分享页链接解析出id`() {
        val ref = ref(
            "https://i.y.qq.com/n2/m/share/details/taoge.html?platform=11&appshare=android_qq&id=7011264340",
        )
        assertEquals(ExternalSource.QQ_MUSIC, ref.source)
        assertEquals("7011264340", ref.id)
    }

    @Test
    fun `无法识别的输入与空输入返回错误`() {
        assertTrue(ExternalPlaylistParser.parse("") is ExternalInput.Invalid)
        assertTrue(ExternalPlaylistParser.parse("abc") is ExternalInput.Invalid)
        assertTrue(
            ExternalPlaylistParser.parse("https://example.com/playlist?id=1") is ExternalInput.Invalid,
        )
        assertTrue(
            ExternalPlaylistParser.parse("https://music.163.com/playlist") is ExternalInput.Invalid,
        )
    }

    @Test
    fun `匹配查询词为标题加歌手`() {
        val query = ExternalPlaylistParser.matchQuery(
            com.bbplayer.android.core.model.ExternalTrack(title = "夜曲", artist = "周杰伦"),
        )
        assertEquals("夜曲 周杰伦", query)
        assertEquals(
            "夜曲",
            ExternalPlaylistParser.matchQuery(
                com.bbplayer.android.core.model.ExternalTrack(title = "夜曲", artist = " "),
            ),
        )
    }

    // ------------------------------------------------------------ 网易云 JSON

    private val neteaseJson = json(
        """
        {
          "code": 200,
          "playlist": {
            "id": 3778678,
            "name": "热歌榜",
            "coverImgUrl": "https://p1.music.126.net/cover.jpg",
            "description": "云音乐热歌榜：用户一周内收听 TOP 排行榜",
            "trackCount": 2,
            "tracks": [
              {
                "name": "晴天",
                "ar": [{"name": "周杰伦"}],
                "al": {"name": "叶惠美"},
                "dt": 269000
              },
              {
                "name": "Faded",
                "ar": [{"name": "Alan Walker"}, {"name": "Iselin Solheim"}],
                "al": {"name": "Faded"},
                "dt": 212000
              }
            ]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `网易云JSON映射为歌单模型`() {
        val playlist = ExternalPlaylistParser.parseNetease(neteaseJson, "3778678")
        assertEquals(ExternalSource.NETEASE, playlist.source)
        assertEquals("3778678", playlist.sourceId)
        assertEquals("热歌榜", playlist.name)
        assertEquals("https://p1.music.126.net/cover.jpg", playlist.coverUrl)
        assertTrue(playlist.description.contains("TOP"))
        assertEquals(2, playlist.tracks.size)

        val first = playlist.tracks.first()
        assertEquals("晴天", first.title)
        assertEquals("周杰伦", first.artist)
        assertEquals("叶惠美", first.album)
        assertEquals(269_000L, first.durationMs)
        assertEquals("Alan Walker、Iselin Solheim", playlist.tracks[1].artist)
    }

    @Test
    fun `网易云错误码与空歌单抛AppException`() {
        assertThrows(AppException::class.java) {
            ExternalPlaylistParser.parseNetease(json("""{"code":404,"msg":"not found"}"""), "1")
        }
        assertThrows(AppException::class.java) {
            ExternalPlaylistParser.parseNetease(
                json("""{"code":200,"playlist":{"name":"空","tracks":[]}}"""),
                "1",
            )
        }
        assertThrows(AppException::class.java) {
            ExternalPlaylistParser.parseNetease(json("""{"code":200}"""), "1")
        }
    }

    @Test
    fun `网易云旧字段artists与album兼容`() {
        val legacy = json(
            """
            {
              "playlist": {
                "name": "旧接口歌单",
                "artists": [],
                "tracks": [
                  {"name": "七里香", "artists": [{"name": "周杰伦"}], "album": {"name": "七里香"}, "duration": 299000}
                ]
              }
            }
            """.trimIndent(),
        )
        val playlist = ExternalPlaylistParser.parseNetease(legacy, "42")
        assertEquals("周杰伦", playlist.tracks.first().artist)
        assertEquals("七里香", playlist.tracks.first().album)
        assertEquals(299_000L, playlist.tracks.first().durationMs)
    }

    // ------------------------------------------------------------ QQ JSON

    private val qqJson = json(
        """
        {
          "code": 0,
          "data": {
            "cdlist": [
              {
                "disstid": "7011264340",
                "dissname": "电子 · 房间里的单人舞池",
                "logo": "http://qpic.y.qq.com/music_cover/abc/300?n=1",
                "desc": "跳舞不需要舞台，打开这张歌单",
                "songnum": 2,
                "songlist": [
                  {
                    "songname": "Faded",
                    "singer": [{"name": "Alan Walker"}],
                    "interval": 212,
                    "albumname": "Faded"
                  },
                  {
                    "songname": "Alone",
                    "singer": [{"name": "Alan Walker"}, {"name": "Marshmello"}],
                    "interval": 180,
                    "albumname": "Alone"
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `QQ音乐JSON映射为歌单模型`() {
        val playlist = ExternalPlaylistParser.parseQq(qqJson, "7011264340")
        assertEquals(ExternalSource.QQ_MUSIC, playlist.source)
        assertEquals("7011264340", playlist.sourceId)
        assertEquals("电子 · 房间里的单人舞池", playlist.name)
        assertEquals("https://qpic.y.qq.com/music_cover/abc/300?n=1", playlist.coverUrl)
        assertEquals(2, playlist.tracks.size)
        assertEquals("Faded", playlist.tracks.first().title)
        assertEquals(212_000L, playlist.tracks.first().durationMs)
        assertEquals("Alan Walker、Marshmello", playlist.tracks[1].artist)
    }

    @Test
    fun `QQ音乐新版字段name与album对象兼容`() {
        val modern = json(
            """
            {
              "code": 0,
              "data": {
                "cdlist": [{
                  "dissname": "雾雨一夜 歌单",
                  "songlist": [{
                    "name": "雾雨一夜",
                    "title": "雾雨一夜",
                    "singer": [{"name": "MISFITS文咏曜"}, {"name": "某某"}],
                    "interval": 156,
                    "album": {"name": "雾雨一夜"}
                  }]
                }]
              }
            }
            """.trimIndent(),
        )
        val playlist = ExternalPlaylistParser.parseQq(modern, "1")
        val track = playlist.tracks.single()
        assertEquals("雾雨一夜", track.title)
        assertEquals("MISFITS文咏曜、某某", track.artist)
        assertEquals(156_000L, track.durationMs)
        assertEquals("雾雨一夜", track.album)
    }

    @Test
    fun `QQ音乐错误码与空歌单抛AppException`() {
        assertThrows(AppException::class.java) {
            ExternalPlaylistParser.parseQq(json("""{"code":1000,"data":{"cdlist":[]}}"""), "1")
        }
        assertThrows(AppException::class.java) {
            ExternalPlaylistParser.parseQq(
                json("""{"code":0,"data":{"cdlist":[{"dissname":"空","songlist":[]}]}}"""),
                "1",
            )
        }
    }
}
