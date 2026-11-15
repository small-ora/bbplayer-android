package com.bbplayer.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end provider tests for word-level timing:
 *  - real captured NetEase YRC and decrypted QQ QRC payloads must stay verbatim and unmarked;
 *  - LRC-only payloads get the [EstimatedTiming] fallback and a `:estimated` source marker when requested.
 */
class ProviderLyricEstimationTest {

    private fun fixture(name: String): String {
        val stream = javaClass.getResourceAsStream("/$name")
        assertNotNull("missing test fixture $name", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
    }

    @Test
    fun `lrc only payload gains estimated words and an estimated source marker`() {
        val payload = LyricPayload(lrc = "[00:01.00]晴天\n[00:03.00]故事的小黄花")

        val lyrics = ProviderLyricParsers.parse(payload, QqMusicApi.SOURCE, estimateMissingWords = true)

        assertEquals(2, lyrics.lines.size)
        assertTrue(lyrics.lines.all { it.words.isNotEmpty() })
        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals("qq:estimated", lyrics.source)
        assertTrue(EstimatedTiming.isEstimated(lyrics))
    }

    @Test
    fun `strict mode keeps line level timing only`() {
        val payload = LyricPayload(lrc = "[00:01.00]晴天\n[00:03.00]故事的小黄花")

        val lyrics = ProviderLyricParsers.parse(payload, QqMusicApi.SOURCE, estimateMissingWords = false)

        assertTrue(lyrics.lines.all { it.words.isEmpty() })
        assertEquals("qq", lyrics.source)
        assertFalse(EstimatedTiming.isEstimated(lyrics))
    }

    @Test
    fun `real verbatim qrc is preferred and never marked estimated`() {
        val payload = LyricPayload(lrc = "[00:01.00]Hello", qrc = "[1000,2000]<1000,500>Hello")

        val lyrics = ProviderLyricParsers.parse(payload, QqMusicApi.SOURCE, estimateMissingWords = true)

        assertEquals("qq", lyrics.source)
        assertFalse(EstimatedTiming.isEstimated(lyrics))
        assertEquals(1, lyrics.lines[0].words.size)
        assertEquals(1_000L, lyrics.lines[0].words[0].startMs)
        assertEquals(1_500L, lyrics.lines[0].words[0].endMs)
    }

    @Test
    fun `live netease yrc fixture gives real word timing`() {
        val payload = NeteaseApi.parseLyric(fixture("netease_yrc_real.json"))
        assertNotNull(payload)
        assertNotNull(payload!!.yrc)

        val lyrics = ProviderLyricParsers.parse(payload, NeteaseApi.SOURCE, estimateMissingWords = true)

        assertEquals("netease", lyrics.source)
        assertFalse(EstimatedTiming.isEstimated(lyrics))
        assertEquals(67, lyrics.lines.size)
        val words = lyrics.lines.sumOf { it.words.size }
        assertTrue("expected real per-word timings, got $words", words > 500)
        val lyricLine = lyrics.lines.first { it.startMs == 14_400L }
        assertEquals("联合出品：拳头游戏/腾讯游戏/腾讯视频", lyricLine.text)
        assertEquals(14_400L, lyricLine.words.first().startMs)
        assertEquals(14_670L, lyricLine.words.first().endMs)
    }

    @Test
    fun `live qq qrc fixture gives real word timing through the provider pipeline`() {
        val qrc = QqQrcDecryptor.decrypt(fixture("qq_qrc_real.hex"))!!
        val payload = LyricPayload(lrc = "", qrc = qrc)

        val lyrics = ProviderLyricParsers.parse(payload, QqMusicApi.SOURCE, estimateMissingWords = true)

        assertEquals("qq", lyrics.source)
        assertFalse(EstimatedTiming.isEstimated(lyrics))
        assertEquals(63, lyrics.lines.size)
        assertTrue(lyrics.lines.sumOf { it.words.size } > 500)
        val lyricLine = lyrics.lines.first { it.startMs == 29_264L }
        assertEquals("故事的小黄花", lyricLine.text)
        assertEquals(29_264L, lyricLine.words.first().startMs)
        assertEquals(29_654L, lyricLine.words.first().endMs)
    }

    @Test
    fun `parseLyricInfo decrypts the musicu payload and keeps the translation`() {
        val hex = fixture("qq_qrc_real.hex")
        val body = """{"code":0,"req":{"code":0,"data":{"lyric":"$hex","trans":"[00:00.00]晴天 (Live)"}}}"""

        val info = QqMusicApi.parseLyricInfo(body)

        assertNotNull(info)
        assertTrue(info!!.qrc.contains("[ti:晴天]"))
        assertEquals("[00:00.00]晴天 (Live)", info.translation)
        val lyrics = QrcParser.parse(info.qrc)
        assertEquals("晴天 - 周杰伦 (Jay Chou)", lyrics.lines.first().text)
        assertTrue(lyrics.lines.first().words.size >= 8)
    }

    @Test
    fun `parseLyricInfo rejects error responses and undecryptable payloads`() {
        assertNull(QqMusicApi.parseLyricInfo(""))
        assertNull(QqMusicApi.parseLyricInfo("not json"))
        assertNull(QqMusicApi.parseLyricInfo("""{"code":1000}"""))
        assertNull(QqMusicApi.parseLyricInfo("""{"req":{"code":400,"data":{"lyric":"00"}}}"""))
        assertNull(QqMusicApi.parseLyricInfo("""{"req":{"code":0,"data":{"lyric":"00"}}}"""))
        assertNull(QqMusicApi.parseLyricInfo("""{"req":{"code":0,"data":{}}}"""))
    }

    @Test
    fun `lyricInfoUrl points at the anonymous musicu GetPlayLyricInfo endpoint`() {
        val url = QqMusicApi.lyricInfoUrl("0039MnYb0qxYhV")

        assertTrue(url, url.startsWith("https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data="))
        assertTrue(url, url.contains("music.musichallSong.PlayLyricInfo"))
        assertTrue(url, url.contains("GetPlayLyricInfo"))
        assertTrue(url, url.contains("songMID"))
        assertTrue(url, url.contains("0039MnYb0qxYhV"))
        assertTrue(url, url.contains("%22qrc%22%3A1"))
        assertTrue(url, url.contains("%22crypt%22%3A1"))
    }

    @Test
    fun `estimated fallback keeps translations and line boundaries`() {
        val payload = LyricPayload(
            lrc = "[00:01.00]Hello\n[00:02.50]World",
            translation = "[00:01.00]你好\n[00:02.50]世界",
        )

        val lyrics = ProviderLyricParsers.parse(payload, NeteaseApi.SOURCE, estimateMissingWords = true)

        assertEquals("netease:estimated", lyrics.source)
        assertEquals("你好", lyrics.lines[0].translation)
        assertEquals("世界", lyrics.lines[1].translation)
        assertEquals(1_000L, lyrics.lines[0].words.first().startMs)
        assertEquals(2_500L, lyrics.lines[0].words.last().endMs)
        assertEquals(2_500L, lyrics.lines[1].startMs)
    }
}
