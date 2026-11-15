package com.bbplayer.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the verbatim (per-word) parsers: NetEase [YrcParser] and QQ Music [QrcParser] (AC G1/G7).
 */
class VerbatimLyricsParserTest {

    // ---- NetEase YRC ---------------------------------------------------------------------------------------

    @Test
    fun `parses yrc text form into words`() {
        val lyrics = YrcParser.parse("[1000,2000](1000,500,0)Hel(1500,500,0)lo")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("Hello", line.text)
        assertEquals(1_000L, line.startMs)
        assertEquals(3_000L, line.endMs)
        assertEquals(2, line.words.size)
        assertEquals("Hel", line.words[0].text)
        assertEquals(1_000L, line.words[0].startMs)
        assertEquals(1_500L, line.words[0].endMs)
        assertEquals("lo", line.words[1].text)
        assertEquals(1_500L, line.words[1].startMs)
        assertEquals(2_000L, line.words[1].endMs)
    }

    @Test
    fun `yrc keeps a separate translation track`() {
        val lyrics = YrcParser.parse(
            "[1000,2000](1000,500,0)Hello",
            translationText = "[00:01.00]你好",
        )

        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("你好", lyrics.lines[0].translation)
    }

    @Test
    fun `yrc json array form produces lines`() {
        val json = """[{"t":0,"c":[{"tx":"你"},{"tx":"好"}]},{"t":1000,"c":[{"tx":"世"},{"tx":"界"}]}]"""
        val lyrics = YrcParser.parse(json)

        assertEquals(2, lyrics.lines.size)
        assertEquals("你好", lyrics.lines[0].text)
        assertEquals(0L, lyrics.lines[0].startMs)
        assertEquals(1_000L, lyrics.lines[0].endMs)
        assertEquals("世界", lyrics.lines[1].text)
        assertEquals(11_000L, lyrics.lines[1].endMs) // last line fallback
        assertTrue(lyrics.lines[0].words.isEmpty())
    }

    @Test
    fun `yrc relative word offsets are accepted`() {
        val lyrics = YrcParser.parse("[10000,2000](0,500,0)相(500,500,0)爱")

        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals(10_000L, lyrics.lines[0].words[0].startMs)
        assertEquals(10_500L, lyrics.lines[0].words[1].startMs)
        assertEquals("相爱", lyrics.lines[0].text)
    }

    @Test
    fun `yrc line without word tags keeps line timing`() {
        val lyrics = YrcParser.parse("[1000,1000]plain text")

        assertEquals(1, lyrics.lines.size)
        assertEquals("plain text", lyrics.lines[0].text)
        assertEquals(2_000L, lyrics.lines[0].endMs)
        assertTrue(lyrics.lines[0].words.isEmpty())
    }

    @Test
    fun `malformed yrc yields empty lyrics`() {
        assertTrue(YrcParser.parse("").lines.isEmpty())
        assertTrue(YrcParser.parse("garbage\n[abc,def]").lines.isEmpty())
        assertTrue(YrcParser.parse("[1000,2000]").lines.isEmpty())
    }

    // ---- QQ QRC --------------------------------------------------------------------------------------------

    @Test
    fun `parses qrc text form into words`() {
        val lyrics = QrcParser.parse("[1000,2000]<1000,500>你<1500,500>好")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("你好", line.text)
        assertEquals(1_000L, line.startMs)
        assertEquals(3_000L, line.endMs)
        assertEquals(2, line.words.size)
        assertEquals("你", line.words[0].text)
        assertEquals(1_000L, line.words[0].startMs)
        assertEquals(1_500L, line.words[0].endMs)
        assertEquals(1_500L, line.words[1].startMs)
        assertEquals(2_000L, line.words[1].endMs)
    }

    @Test
    fun `qrc xml wrapper lyric content is extracted and unescaped`() {
        val xml = """<?xml version="1.0" encoding="utf-8"?><Qrc><Lyric_1 LyricType="1" """ +
            """LyricContent="[ti:测试]&#10;[0,1000]<0,500>Hello&#10;[1000,1000]<1000,500>世界"/>"""

        val lyrics = QrcParser.parse(xml)

        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("世界", lyrics.lines[1].text)
        assertEquals(1, lyrics.lines[1].words.size)
        assertEquals(1_000L, lyrics.lines[1].words[0].startMs)
    }

    @Test
    fun `qrc relative word offsets are accepted`() {
        val lyrics = QrcParser.parse("[5000,1000]<0,500>甲<500,500>乙")

        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals(5_000L, lyrics.lines[0].words[0].startMs)
        assertEquals(5_500L, lyrics.lines[0].words[1].startMs)
    }

    @Test
    fun `qrc metadata and translation are handled`() {
        val lyrics = QrcParser.parse(
            """
            [ti:歌名]
            [offset:-200]
            [1000,2000]<1000,500>Hello
            """.trimIndent(),
            translationText = "[00:01.00]你好",
        )

        assertEquals(1, lyrics.lines.size)
        assertEquals(-200, lyrics.offsetMs)
        assertEquals("你好", lyrics.lines[0].translation)
    }

    @Test
    fun `malformed or lrc qrc yields empty lyrics`() {
        assertTrue(QrcParser.parse("").lines.isEmpty())
        assertTrue(QrcParser.parse("garbage").lines.isEmpty())
        // Plain LRC is not QRC: callers fall back to the LRC parser.
        assertTrue(QrcParser.parse("[00:01.00]Hi\n[00:02.00]There").lines.isEmpty())
    }

    @Test
    fun `qrc line without word tags keeps text only`() {
        val lyrics = QrcParser.parse("[1000,1000]只此一句")

        assertEquals(1, lyrics.lines.size)
        assertEquals("只此一句", lyrics.lines[0].text)
        assertNull(lyrics.lines[0].translation)
        assertTrue(lyrics.lines[0].words.isEmpty())
    }
}
