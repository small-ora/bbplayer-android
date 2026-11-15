package com.bbplayer.android.lyrics

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for the provider payload plumbing: NetEase YRC / QQ QRC extraction, verbatim-preferred parsing with
 * LRC fallback, and cache round trips that keep word timings (AC G1/G7).
 */
class ProviderLyricPayloadTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `netease payload carries yrc main lrc and translation`() {
        val body = """
            {"lrc":{"lyric":"[00:01.00]Hello"},
             "yrc":{"lyric":"[1000,2000](1000,500,0)Hello","version":1},
             "tlyric":{"lyric":"[00:01.00]你好"}}
        """.trimIndent()

        val payload = NeteaseApi.parseLyric(body)

        assertNotNull(payload)
        assertEquals("[00:01.00]Hello", payload!!.lrc)
        assertEquals("[1000,2000](1000,500,0)Hello", payload.yrc)
        assertEquals("[00:01.00]你好", payload.translation)
        assertTrue(NeteaseApi.lyricUrl("1").contains("yv=-1"))
    }

    @Test
    fun `netease yrc only payload is still usable`() {
        val payload = NeteaseApi.parseLyric("""{"yrc":{"lyric":"[0,1000](0,500,0)嗨"}}""")

        assertNotNull(payload)
        assertEquals("", payload!!.lrc)
        assertEquals("[0,1000](0,500,0)嗨", payload.yrc)
    }

    @Test
    fun `netease yrc is preferred over lrc and gains words`() {
        val payload = NeteaseApi.parseLyric(
            """{"lrc":{"lyric":"[00:01.00]Hello"},"yrc":{"lyric":"[1000,2000](1000,500,0)Hel(1500,500,0)lo"}}""",
        )!!

        val lyrics = ProviderLyricParsers.parse(payload, NeteaseApi.SOURCE)

        assertEquals(NeteaseApi.SOURCE, lyrics.source)
        assertEquals(1, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals(1_000L, lyrics.lines[0].words[0].startMs)
    }

    @Test
    fun `missing yrc falls back to lrc`() {
        val payload = NeteaseApi.parseLyric("""{"lrc":{"lyric":"[00:01.00]Hello\n[00:02.00]World"}}""")!!

        val lyrics = ProviderLyricParsers.parse(payload, NeteaseApi.SOURCE)

        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertTrue(lyrics.lines.none { it.words.isNotEmpty() })
        assertNull(ProviderLyricParsers.parseVerbatim(payload, null, NeteaseApi.SOURCE))
    }

    @Test
    fun `malformed yrc falls back to lrc`() {
        val payload = NeteaseApi.parseLyric(
            """{"lrc":{"lyric":"[00:01.00]Hello"},"yrc":{"lyric":"garbage not yrc"}}""",
        )!!

        val lyrics = ProviderLyricParsers.parse(payload, NeteaseApi.SOURCE)

        assertEquals(1, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertTrue(lyrics.lines[0].words.isEmpty())
    }

    @Test
    fun `qq payload carries qrc and prefers it with translation`() {
        val body = """
            {"retcode":0,"code":0,"lyric":"[00:01.00]Hello",
             "qrc":"[1000,2000]<1000,500>Hello","trans":"[00:01.00]你好"}
        """.trimIndent()

        val payload = QqMusicApi.parseLyric(body)

        assertNotNull(payload)
        assertEquals("[1000,2000]<1000,500>Hello", payload!!.qrc)
        assertEquals("[00:01.00]你好", payload.translation)

        val lyrics = ProviderLyricParsers.parse(payload, QqMusicApi.SOURCE)

        assertEquals(QqMusicApi.SOURCE, lyrics.source)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals(1, lyrics.lines[0].words.size)
        assertEquals("你好", lyrics.lines[0].translation)
        assertTrue(QqMusicApi.lyricUrl("mid").contains("qrc=1"))
    }

    @Test
    fun `qq qrc only payload and base64 qrc are decoded`() {
        val plain = QqMusicApi.parseLyric("""{"retcode":0,"qrc":"[0,1000]<0,500>Hi"}""")
        assertNotNull(plain)
        assertEquals("[0,1000]<0,500>Hi", plain!!.qrc)

        val encoded = Base64.getEncoder().encodeToString("[0,1000]<0,500>Hi".toByteArray(Charsets.UTF_8))
        val payload = QqMusicApi.parseLyric("""{"retcode":0,"qrc":"$encoded"}""")
        assertNotNull(payload)
        assertEquals("[0,1000]<0,500>Hi", payload!!.qrc)

        val lyrics = ProviderLyricParsers.parse(payload, QqMusicApi.SOURCE)
        assertEquals("Hi", lyrics.lines[0].text)
        assertEquals(1, lyrics.lines[0].words.size)
    }

    @Test
    fun `cache round trip keeps word timings`() {
        val dir = temporaryFolder.newFolder("cache")
        val store = FileLyricsCacheStore(dir)
        val lyrics = YrcParser.parse("[1000,2000](1000,500,0)Hel(1500,500,0)lo", source = "netease")

        store.write(CachedLyrics(trackKey = "BV1xx:1", lyrics = lyrics, source = "netease"))

        val restored = store.read("BV1xx:1")
        assertNotNull(restored)
        assertEquals(1, restored!!.lyrics.lines.size)
        assertEquals(2, restored.lyrics.lines[0].words.size)
        assertEquals("Hel", restored.lyrics.lines[0].words[0].text)
        assertEquals(1_500L, restored.lyrics.lines[0].words[0].endMs)
        assertEquals("lo", restored.lyrics.lines[0].words[1].text)
    }

    @Test
    fun `manual spl paste is parsed through the unified entry point`() {
        val lyrics = LyricsParser.parse("[00:01.00]你<00:02.00>好", LyricsFormat.AUTO, source = "manual")

        assertEquals(1, lyrics.lines.size)
        assertEquals("你好", lyrics.lines[0].text)
        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals("manual", lyrics.source)
    }
}
