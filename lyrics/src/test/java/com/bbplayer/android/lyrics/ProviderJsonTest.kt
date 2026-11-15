package com.bbplayer.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for provider response parsing. Fixtures mirror the real anonymous endpoints (verified with curl) but
 * the tests never touch the network.
 */
class ProviderJsonTest {

    // ---- QQ -------------------------------------------------------------------------------------------------

    private val qqSearchJson = """
        {"code":0,"data":{"keyword":"夜に駆ける","song":{"curnum":2,"curpage":1,"list":[
          {"songmid":"003WFMXk4O5ywc","songname":"夜に駆ける (向夜晚奔去)",
           "singer":[{"id":4731886,"mid":"001Erp1x1jDOoQ","name":"YOASOBI (ヨアソビ)"}],"interval":261},
          {"songmid":"001Qu4I30eVFYb","songname":"夜に駆ける - Remix",
           "singer":[{"id":1,"name":"DJ Test"},{"id":2,"name":"Other"}],"interval":240}
        ]}}}
    """.trimIndent()

    @Test
    fun `qq search response is parsed into candidates`() {
        val candidates = QqMusicApi.parseSearch(qqSearchJson)

        assertEquals(2, candidates.size)
        assertEquals("夜に駆ける (向夜晚奔去)", candidates[0].title)
        assertEquals("YOASOBI (ヨアソビ)", candidates[0].artist)
        assertEquals("003WFMXk4O5ywc", candidates[0].sourceId)
        assertEquals(QqMusicApi.SOURCE, candidates[0].source)
        assertEquals("DJ Test, Other", candidates[1].artist)
    }

    @Test
    fun `qq search tolerates malformed and empty responses`() {
        assertTrue(QqMusicApi.parseSearch("<html>blocked</html>").isEmpty())
        assertTrue(QqMusicApi.parseSearch("""{"code":0}""").isEmpty())
        assertTrue(
            QqMusicApi.parseSearch("""{"data":{"song":{"list":[{"songname":"no mid"}]}}}""").isEmpty(),
        )
    }

    @Test
    fun `qq lyric response decodes html entities and translation`() {
        val body = """
            {"retcode":0,"code":0,"lyric":"[ti:夜に駆ける]&#10;[00:01.00]Hello&#10;[00:02.00]World",
             "trans":"[00:01.00]你好&#10;[00:02.00]世界"}
        """.trimIndent()

        val payload = QqMusicApi.parseLyric(body)
        assertTrue(payload != null)
        assertEquals("[ti:夜に駆ける]\n[00:01.00]Hello\n[00:02.00]World", payload!!.lrc)
        assertEquals("[00:01.00]你好\n[00:02.00]世界", payload.translation)

        val lyrics = LrcParser.parse(payload.lrc, payload.translation, source = QqMusicApi.SOURCE)
        assertEquals(2, lyrics.lines.size)
        assertEquals("你好", lyrics.lines[0].translation)
    }

    @Test
    fun `qq lyric handles jsonp wrapper`() {
        val body = """MusicJsonCallback({"retcode":0,"lyric":"[00:01.00]Hi","trans":""});"""
        val payload = QqMusicApi.parseLyric(body)
        assertEquals("[00:01.00]Hi", payload?.lrc)
        assertNull(payload?.translation)
    }

    @Test
    fun `qq lyric error responses yield null`() {
        assertNull(QqMusicApi.parseLyric("""{"retcode":-1,"lyric":""}"""))
        assertNull(QqMusicApi.parseLyric("""{"retcode":0,"lyric":""}"""))
        assertNull(QqMusicApi.parseLyric("not json"))
    }

    @Test
    fun `html entity unescaping covers numeric and named entities`() {
        assertEquals("a\nb", QqMusicApi.unescapeHtmlEntities("a&#10;b"))
        assertEquals("a\nb", QqMusicApi.unescapeHtmlEntities("a&#x0A;b"))
        assertEquals("Tom & Jerry <3", QqMusicApi.unescapeHtmlEntities("Tom &amp; Jerry &lt;3"))
        assertEquals("it's", QqMusicApi.unescapeHtmlEntities("it&#39;s"))
        assertEquals("plain text", QqMusicApi.unescapeHtmlEntities("plain text"))
    }

    // ---- NetEase ---------------------------------------------------------------------------------------------

    private val neteaseSearchJson = """
        {"result":{"songCount":2,"songs":[
          {"id":1409311773,"name":"夜に駆ける","artists":[{"id":33927412,"name":"YOASOBI"}]},
          {"id":123456,"name":"晴天","artists":[{"id":6452,"name":"周杰伦"}]}
        ]}}
    """.trimIndent()

    @Test
    fun `netease search response is parsed into candidates`() {
        val candidates = NeteaseApi.parseSearch(neteaseSearchJson)

        assertEquals(2, candidates.size)
        assertEquals("夜に駆ける", candidates[0].title)
        assertEquals("YOASOBI", candidates[0].artist)
        assertEquals("1409311773", candidates[0].sourceId)
        assertEquals(NeteaseApi.SOURCE, candidates[0].source)
        assertEquals("周杰伦", candidates[1].artist)
    }

    @Test
    fun `netease search tolerates malformed responses`() {
        assertTrue(NeteaseApi.parseSearch("{}").isEmpty())
        assertTrue(NeteaseApi.parseSearch("""{"result":{"songs":[{"name":"no id"}]}}""").isEmpty())
    }

    @Test
    fun `netease lyric response carries main and translation tracks`() {
        // NetEase returns real JSON `\n` escapes (not HTML entities), unlike QQ.
        val body = """
            {"lrc":{"lyric":"[00:01.00]Hello\n[00:02.00]World"},
             "tlyric":{"lyric":"[00:01.00]你好\n[00:02.00]世界"}}
        """.trimIndent()

        val payload = NeteaseApi.parseLyric(body)
        assertTrue(payload != null)
        assertEquals("[00:01.00]Hello\n[00:02.00]World", payload!!.lrc)
        assertEquals("[00:01.00]你好\n[00:02.00]世界", payload.translation)

        val lyrics = LrcParser.parse(payload.lrc, payload.translation, source = NeteaseApi.SOURCE)
        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("你好", lyrics.lines[0].translation)
        assertEquals("世界", lyrics.lines[1].translation)
    }

    @Test
    fun `netease lyric without translation is valid`() {
        val payload = NeteaseApi.parseLyric("""{"lrc":{"lyric":"[00:01.00]Hello"},"tlyric":{"lyric":""}}""")
        assertEquals("[00:01.00]Hello", payload?.lrc)
        assertNull(payload?.translation)
    }

    @Test
    fun `netease lyric without main track yields null`() {
        assertNull(NeteaseApi.parseLyric("""{"code":200}"""))
        assertNull(NeteaseApi.parseLyric("""{"lrc":{"lyric":""}}"""))
        assertNull(NeteaseApi.parseLyric("oops"))
    }

    @Test
    fun `provider request urls encode the query`() {
        assertTrue(QqMusicApi.searchUrl("夜に駆ける").contains("w=%E5%A4%9C"))
        assertTrue(QqMusicApi.lyricUrl("003WFMXk4O5ywc").contains("songmid=003WFMXk4O5ywc"))
        assertTrue(NeteaseApi.searchUrl("夜に駆ける").contains("s=%E5%A4%9C"))
        assertTrue(NeteaseApi.lyricUrl("1409311773").contains("id=1409311773"))
        assertTrue(NeteaseApi.lyricUrl("1").contains("lv=1&kv=1&tv=-1"))
    }
}
