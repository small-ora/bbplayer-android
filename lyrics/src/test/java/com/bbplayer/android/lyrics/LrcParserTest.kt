package com.bbplayer.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure-Kotlin [LrcParser] (AC D1/D5). */
class LrcParserTest {

    @Test
    fun `parses centisecond and millisecond timestamps`() {
        val lyrics = LrcParser.parse(
            """
            [00:01.00]第一行
            [00:02.50]第二行
            [01:00.123]第三行
            """.trimIndent(),
        )

        assertEquals(3, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines[0].startMs)
        assertEquals(2_500L, lyrics.lines[1].startMs)
        assertEquals(60_123L, lyrics.lines[2].startMs)
    }

    @Test
    fun `parses timestamps without fraction`() {
        val lyrics = LrcParser.parse("[00:03]整秒行")
        assertEquals(1, lyrics.lines.size)
        assertEquals(3_000L, lyrics.lines[0].startMs)
        assertEquals("整秒行", lyrics.lines[0].text)
    }

    @Test
    fun `expands one line with multiple timestamps into multiple lines`() {
        val lyrics = LrcParser.parse("[00:01.00][00:05.00]副歌")

        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines[0].startMs)
        assertEquals(5_000L, lyrics.lines[1].startMs)
        assertEquals("副歌", lyrics.lines[0].text)
        assertEquals("副歌", lyrics.lines[1].text)
        assertEquals(5_000L, lyrics.lines[0].endMs)
        assertEquals(8_000L, lyrics.lines[1].endMs) // last line: start + max(3s, 2 chars * 200ms)
        assertNull(lyrics.lines[0].translation) // repeated chorus, not a translation
    }

    @Test
    fun `positive offset is stored and shifts active line earlier`() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:01.00]A")
        assertEquals(500, lyrics.offsetMs)
        assertEquals(0, lyrics.activeIndex(500L))
        assertEquals(-1, lyrics.activeIndex(499L))
    }

    @Test
    fun `negative offset is stored and shifts active line later`() {
        val lyrics = LrcParser.parse("[offset:-500]\n[00:01.00]A")
        assertEquals(-500, lyrics.offsetMs)
        assertEquals(-1, lyrics.activeIndex(1_499L))
        assertEquals(0, lyrics.activeIndex(1_500L))
    }

    @Test
    fun `metadata tags are ignored`() {
        val lyrics = LrcParser.parse(
            """
            [ti:夜に駆ける]
            [ar:YOASOBI]
            [al:夜に駆ける]
            [by:someone]
            [offset:0]
            [00:01.00]歌词
            """.trimIndent(),
        )

        assertEquals(1, lyrics.lines.size)
        assertEquals("歌词", lyrics.lines[0].text)
        assertEquals(0, lyrics.offsetMs)
        assertEquals("", lyrics.plainText)
    }

    @Test
    fun `blank lines and timestamp-only lines are filtered`() {
        val lyrics = LrcParser.parse(
            "\n[00:01.00]A\n\n   \n[00:02.00]\n[00:03.00]C\n",
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines[0].startMs)
        assertEquals(3_000L, lyrics.lines[0].endMs) // empty timed line at 2s does not become a line
        assertEquals(3_000L, lyrics.lines[1].startMs)
        assertEquals(6_000L, lyrics.lines[1].endMs)
    }

    @Test
    fun `line end is the next line start`() {
        val lyrics = LrcParser.parse("[00:01.00]A\n[00:04.00]B")
        assertEquals(4_000L, lyrics.lines[0].endMs)
    }

    @Test
    fun `last line duration is max of three seconds and text length times 200ms`() {
        val shortLine = LrcParser.parse("[00:01.00]你好")
        assertEquals(4_000L, shortLine.lines[0].endMs)

        val longText = "一二三四五六七八九十一二三四五六七八九十" // 20 chars -> 4000ms
        val longLine = LrcParser.parse("[00:01.00]$longText")
        assertEquals(5_000L, longLine.lines[0].endMs)
    }

    @Test
    fun `enhanced lrc produces per-word timing`() {
        val lyrics = LrcParser.parse("[00:01.00]<00:01.00>你<00:01.50>好")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("你好", line.text)
        assertEquals(2, line.words.size)
        assertEquals("你", line.words[0].text)
        assertEquals(1_000L, line.words[0].startMs)
        assertEquals(1_500L, line.words[0].endMs)
        assertEquals("好", line.words[1].text)
        assertEquals(1_500L, line.words[1].startMs)
        assertTrue(line.words[1].endMs >= line.words[1].startMs)
    }

    @Test
    fun `enhanced lrc last word ends at the next line start`() {
        val lyrics = LrcParser.parse(
            """
            [00:01.00]<00:01.00>你<00:01.50>好
            [00:03.00]下一行
            """.trimIndent(),
        )

        assertEquals(3_000L, lyrics.lines[0].words[1].endMs)
    }

    @Test
    fun `enhanced lrc keeps leading text before the first word tag`() {
        val lyrics = LrcParser.parse("[00:01.00]前<00:01.20>后")
        val line = lyrics.lines[0]

        assertEquals(2, line.words.size)
        assertEquals("前", line.words[0].text)
        assertEquals(1_000L, line.words[0].startMs)
        assertEquals(1_200L, line.words[0].endMs)
        assertEquals(1_200L, line.words[1].startMs)
        assertEquals(1_500L, line.words[1].endMs) // last word of the file: line start + 500ms
    }

    @Test
    fun `multiple timestamps shift enhanced word timings`() {
        val lyrics = LrcParser.parse("[00:01.00][00:05.00]<00:01.00>你<00:01.50>好")

        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines[0].words[0].startMs)
        assertEquals(1_500L, lyrics.lines[0].words[1].startMs)
        assertEquals(5_000L, lyrics.lines[1].words[0].startMs)
        assertEquals(5_500L, lyrics.lines[1].words[1].startMs)
    }

    @Test
    fun `same timestamp second line becomes translation`() {
        val lyrics = LrcParser.parse(
            """
            [00:01.00]Hello
            [00:01.00]你好
            [00:02.00]World
            """.trimIndent(),
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("你好", lyrics.lines[0].translation)
        assertEquals("World", lyrics.lines[1].text)
        assertNull(lyrics.lines[1].translation)
    }

    @Test
    fun `identical duplicated lines are deduplicated without a fake translation`() {
        val lyrics = LrcParser.parse("[00:01.00]A\n[00:01.00]A")
        assertEquals(1, lyrics.lines.size)
        assertNull(lyrics.lines[0].translation)
    }

    @Test
    fun `tlyric track is merged by matching timestamps`() {
        val lyrics = LrcParser.parse(
            lrcText = "[00:01.00]Hello\n[00:02.00]World",
            translationText = "[00:01.00]你好\n[00:02.00]世界",
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals("你好", lyrics.lines[0].translation)
        assertEquals("世界", lyrics.lines[1].translation)
    }

    @Test
    fun `tlyric merge tolerates up to ten milliseconds`() {
        val merged = LrcParser.parse(
            lrcText = "[00:01.00]Hello",
            translationText = "[00:01.005]你好",
        )
        assertEquals("你好", merged.lines[0].translation)

        val notMerged = LrcParser.parse(
            lrcText = "[00:01.00]Hello",
            translationText = "[00:01.02]你好",
        )
        assertNull(notMerged.lines[0].translation)
    }

    @Test
    fun `tlyric translation does not overwrite an inline translation`() {
        val lyrics = LrcParser.parse(
            lrcText = "[00:01.00]Hello\n[00:01.00]内联翻译",
            translationText = "[00:01.00]tlyric 翻译",
        )
        assertEquals("内联翻译", lyrics.lines[0].translation)
    }

    @Test
    fun `enhanced line keeps words after translation merge`() {
        val lyrics = LrcParser.parse(
            lrcText = "[00:01.00]<00:01.00>你<00:01.50>好\n[00:03.00]下一行",
            translationText = "[00:01.00]你好呀\n[00:03.00]下一行译文",
        )

        assertEquals("你好呀", lyrics.lines[0].translation)
        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals("下一行译文", lyrics.lines[1].translation)
    }

    @Test
    fun `plain text lines are collected into plainText`() {
        val lyrics = LrcParser.parse(
            """
            [00:01.00]A
            [00:02.00]B
            备注一行
            """.trimIndent(),
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals("备注一行", lyrics.plainText)
    }

    @Test
    fun `untimed text only input degrades to plainText`() {
        val lyrics = LrcParser.parse("hello\nworld")

        assertTrue(lyrics.lines.isEmpty())
        assertTrue(lyrics.isEmpty)
        assertEquals("hello\nworld", lyrics.plainText)
    }

    @Test
    fun `malformed timing tag degrades to plainText without throwing`() {
        val lyrics = LrcParser.parse("[00:xx]坏了\n[aa:bb]也不行")

        assertTrue(lyrics.lines.isEmpty())
        assertEquals("[00:xx]坏了\n[aa:bb]也不行", lyrics.plainText)
    }

    @Test
    fun `lines are sorted and exact duplicate lines are removed`() {
        val lyrics = LrcParser.parse(
            """
            [00:05.00]B
            [00:01.00]A
            [00:01.00]A
            """.trimIndent(),
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals("A", lyrics.lines[0].text)
        assertEquals("B", lyrics.lines[1].text)
        assertEquals(5_000L, lyrics.lines[0].endMs)
        assertEquals(8_000L, lyrics.lines[1].endMs)
    }

    @Test
    fun `empty input yields empty lyrics`() {
        val lyrics = LrcParser.parse("   \n\n")
        assertTrue(lyrics.isEmpty)
        assertEquals("", lyrics.plainText)
        assertEquals(0, lyrics.offsetMs)
        assertEquals(-1, lyrics.activeIndex(0L))
    }

    @Test
    fun `source is propagated`() {
        val lyrics = LrcParser.parse("[00:01.00]A", source = "qq")
        assertEquals("qq", lyrics.source)
    }

    @Test
    fun `crlf and byte order mark are handled`() {
        val lyrics = LrcParser.parse("\uFEFF[00:01.00]A\r\n[00:02.00]B\r\n")
        assertEquals(2, lyrics.lines.size)
        assertEquals("A", lyrics.lines[0].text)
        assertEquals("B", lyrics.lines[1].text)
        assertFalse(lyrics.lines[0].text.contains('\r'))
    }
}
