package com.bbplayer.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SplParser] (AC G7): line timing, word-level timing, translation lines, offset metadata,
 * multiple leading timestamps, explicit end tags, time-fraction rounding and malformed-input degradation.
 */
class SplParserTest {

    @Test
    fun `parses line level spl with ten second fallback end`() {
        val lyrics = SplParser.parse("[00:01.00]第一行\n[00:05.00]第二行")

        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines[0].startMs)
        assertEquals("第一行", lyrics.lines[0].text)
        assertEquals(5_000L, lyrics.lines[0].endMs)
        assertEquals(5_000L, lyrics.lines[1].startMs)
        // The last line falls back to start + 10 s (upstream SPL rule, unlike LRC's 3 s).
        assertEquals(15_000L, lyrics.lines[1].endMs)
        assertTrue(lyrics.lines[0].words.isEmpty())
    }

    @Test
    fun `parses angle bracket word level timings into lyric words`() {
        val lyrics = SplParser.parse("[00:10.00]<00:10.00>Hello <00:11.00>world<00:12.00>")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("Hello world", line.text)
        assertEquals(10_000L, line.startMs)
        assertEquals(12_000L, line.endMs) // explicit trailing end tag
        assertEquals(2, line.words.size)
        assertEquals(10_000L, line.words[0].startMs)
        assertEquals(11_000L, line.words[0].endMs)
        assertEquals("world", line.words[1].text)
        assertEquals(11_000L, line.words[1].startMs)
        assertEquals(12_000L, line.words[1].endMs)
    }

    @Test
    fun `parses square bracket inline word level timings`() {
        val lyrics = SplParser.parse("[00:01.00]你[00:01.50]好[00:02.00]")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("你好", line.text)
        assertEquals(2_000L, line.endMs)
        assertEquals(2, line.words.size)
        assertEquals("你", line.words[0].text)
        assertEquals(1_000L, line.words[0].startMs)
        assertEquals(1_500L, line.words[0].endMs)
        assertEquals("好", line.words[1].text)
        assertEquals(1_500L, line.words[1].startMs)
        assertEquals(2_000L, line.words[1].endMs)
    }

    @Test
    fun `same timestamp second line becomes translation`() {
        val lyrics = SplParser.parse(
            """
            [00:01.00]Hello
            [00:01.00]你好
            [00:02.00]World
            """.trimIndent(),
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("你好", lyrics.lines[0].translation)
        assertNull(lyrics.lines[1].translation)
    }

    @Test
    fun `continuation line without timestamp inherits previous line as translation`() {
        val lyrics = SplParser.parse(
            """
            [00:01.00]Hello
            你好
            [00:02.00]World
            """.trimIndent(),
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("你好", lyrics.lines[0].translation)
        assertEquals(2_000L, lyrics.lines[0].endMs)
        assertEquals("", lyrics.plainText)
    }

    @Test
    fun `separate translation track is merged by timestamp`() {
        val lyrics = SplParser.parse(
            "[00:01.00]Hello\n[00:02.00]World",
            translationText = "[00:01.00]你好\n[00:02.00]世界",
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals("你好", lyrics.lines[0].translation)
        assertEquals("世界", lyrics.lines[1].translation)
    }

    @Test
    fun `offset metadata is stored and shifts the active line`() {
        val lyrics = SplParser.parse("[ti:测试]\n[offset:+500]\n[00:01.00]A")

        assertEquals(1, lyrics.lines.size)
        assertEquals(500, lyrics.offsetMs)
        assertEquals(0, lyrics.activeIndex(500L))
        assertEquals(-1, lyrics.activeIndex(499L))
    }

    @Test
    fun `multiple leading timestamps expand and are sorted`() {
        val lyrics = SplParser.parse("[00:05.00][00:01.00]副歌")

        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines[0].startMs)
        assertEquals(5_000L, lyrics.lines[0].endMs)
        assertEquals(5_000L, lyrics.lines[1].startMs)
        assertEquals("副歌", lyrics.lines[0].text)
        assertEquals("副歌", lyrics.lines[1].text)
        // Same text at different timestamps is a repeating chorus, not a translation.
        assertNull(lyrics.lines[0].translation)
    }

    @Test
    fun `explicit trailing tag sets the line end`() {
        val lyrics = SplParser.parse("[00:01.00]Hello[00:03.00]\n[00:05.00]Next")

        assertEquals(2, lyrics.lines.size)
        assertEquals(3_000L, lyrics.lines[0].endMs)
        assertEquals(15_000L, lyrics.lines[1].endMs)
    }

    @Test
    fun `metadata is ignored and untimed line degrades to plain text`() {
        val lyrics = SplParser.parse(
            """
            [ti:标题]
            [ar:歌手]
            [al:专辑]
            纯文本行
            [00:01.00]正文
            """.trimIndent(),
        )

        assertEquals(1, lyrics.lines.size)
        assertEquals("正文", lyrics.lines[0].text)
        assertEquals("纯文本行", lyrics.plainText)
    }

    @Test
    fun `time fractions of one to six digits are accepted and negatives clamp to zero`() {
        assertEquals(100L, SplParser.parse("[00:00.1]A").lines[0].startMs)
        assertEquals(20L, SplParser.parse("[00:00.02]B").lines[0].startMs)
        assertEquals(123L, SplParser.parse("[00:00.123456]C").lines[0].startMs)
        assertEquals(1_500L, SplParser.parse("[00:01.500]D").lines[0].startMs)
        assertEquals(0L, SplParser.parse("[00:-01.00]E").lines[0].startMs)
        assertEquals(0L, SplParser.parse("[-01:00.00]F").lines[0].startMs)
    }

    @Test
    fun `out of order word tag is ignored instead of crashing`() {
        val lyrics = SplParser.parse("[00:10.00]<00:09.00>bad<00:11.00>ok")

        assertEquals(1, lyrics.lines.size)
        assertEquals("badok", lyrics.lines[0].text)
        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals(10_000L, lyrics.lines[0].words[0].startMs)
        assertEquals("ok", lyrics.lines[0].words[1].text)
        assertEquals(11_000L, lyrics.lines[0].words[1].startMs)
    }

    @Test
    fun `malformed input never throws`() {
        assertTrue(SplParser.parse("").lines.isEmpty())
        assertTrue(SplParser.parse("[00:01.00]").lines.isEmpty())
        assertTrue(SplParser.parse("完全乱码 [hello] <>> 00:xx").lines.isEmpty())
        assertEquals("完全乱码 [hello] <>> 00:xx", SplParser.parse("完全乱码 [hello] <>> 00:xx").plainText)
        assertTrue(SplParser.parse("\uFEFF\r\n[00:01.00]A\r\n").lines.isNotEmpty())
    }

    @Test
    fun `word level detection distinguishes spl from plain lrc`() {
        assertTrue(SplParser.hasWordLevelTags("[00:01.00]你<00:02.00>好"))
        assertTrue(SplParser.hasWordLevelTags("[00:01.00]你[00:02.00]好"))
        assertTrue(!SplParser.hasWordLevelTags("[00:01.00]plain\n[00:02.00]lrc"))
        assertTrue(!SplParser.hasWordLevelTags("[ti:song]\n[00:01.00]plain"))
    }
}
