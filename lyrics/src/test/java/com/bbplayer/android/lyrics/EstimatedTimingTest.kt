package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.LyricLine
import com.bbplayer.android.core.model.LyricWord
import com.bbplayer.android.core.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [EstimatedTiming], the uniform word-timeline fallback used when a provider only returns LRC. */
class EstimatedTimingTest {

    private fun line(
        start: Long,
        end: Long,
        text: String,
        words: List<LyricWord> = emptyList(),
    ) = LyricLine(startMs = start, endMs = end, text = text, words = words)

    @Test
    fun `cjk lines get one word per character spread across the line`() {
        val lyrics = Lyrics(lines = listOf(line(0, 4_000, "你好世界")), source = "netease")

        val estimated = EstimatedTiming.apply(lyrics)
        val words = estimated.lines[0].words

        assertEquals(listOf("你", "好", "世", "界"), words.map { it.text })
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), words.map { it.startMs })
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L), words.map { it.endMs })
        assertEquals("netease:estimated", estimated.source)
        assertTrue(EstimatedTiming.isEstimated(estimated))
    }

    @Test
    fun `ascii words are grouped and weighted by character count`() {
        val lyrics = Lyrics(lines = listOf(line(0, 1_100, "Hello world")), source = "qq")

        val words = EstimatedTiming.apply(lyrics).lines[0].words

        assertEquals(listOf("Hello", " ", "world"), words.map { it.text })
        assertEquals(listOf(0L, 500L, 600L), words.map { it.startMs })
        assertEquals(listOf(500L, 600L, 1_100L), words.map { it.endMs })
        assertEquals("Hello world", words.joinToString("") { it.text })
    }

    @Test
    fun `unit splitting never loses characters`() {
        val text = "你Hello, world! 好"
        val units = EstimatedTiming.splitUnits(text)

        assertEquals(text, units.joinToString(""))
        assertEquals(listOf("你", "Hello,", " ", "world!", " ", "好"), units)
    }

    @Test
    fun `real verbatim words are preserved and never marked as estimated`() {
        val verbatim = listOf(LyricWord(0, 500, "你"), LyricWord(500, 1_000, "好"))
        val lyrics = Lyrics(lines = listOf(line(0, 1_000, "你好", verbatim)), source = "qq")

        val result = EstimatedTiming.apply(lyrics)

        assertSame(lyrics, result)
        assertFalse(EstimatedTiming.isEstimated(result))
        assertEquals("qq", result.source)
        assertEquals(verbatim, result.lines[0].words)
    }

    @Test
    fun `blank and zero duration lines are left untouched`() {
        val lyrics = Lyrics(
            lines = listOf(
                line(0, 1_000, "有字"),
                line(1_000, 1_000, "零时长"),
                line(2_000, 3_000, "   "),
            ),
            source = "netease",
        )

        val estimated = EstimatedTiming.apply(lyrics)

        assertTrue(estimated.lines[0].words.isNotEmpty())
        assertTrue(estimated.lines[1].words.isEmpty())
        assertTrue(estimated.lines[2].words.isEmpty())
        assertEquals("netease:estimated", estimated.source)
    }

    @Test
    fun `nothing to estimate returns the same instance and source`() {
        val lyrics = Lyrics(lines = listOf(line(0, 1_000, "整行无逐字")), source = "")

        // A line without words DOES get estimated, so build one that cannot be: no lines at all.
        val empty = Lyrics(lines = emptyList(), source = "qq")
        assertSame(empty, EstimatedTiming.apply(empty))
        assertFalse(EstimatedTiming.isEstimated(empty))

        assertTrue(EstimatedTiming.apply(lyrics).lines[0].words.isNotEmpty())
    }

    @Test
    fun `marker helpers are idempotent`() {
        assertEquals("qq:estimated", EstimatedTiming.markedSource("qq"))
        assertEquals("qq:estimated", EstimatedTiming.markedSource("qq:estimated"))
        assertEquals("unknown:estimated", EstimatedTiming.markedSource(""))
        assertTrue(EstimatedTiming.isEstimatedSource("netease:estimated"))
        assertFalse(EstimatedTiming.isEstimatedSource("netease"))
    }

    @Test
    fun `short lines keep positive word durations and end at the line end`() {
        val lyrics = Lyrics(lines = listOf(line(10, 13, "你好")), source = "qq")

        val words = EstimatedTiming.apply(lyrics).lines[0].words

        assertEquals(2, words.size)
        assertTrue(words.all { it.endMs > it.startMs })
        assertEquals(13L, words.last().endMs)
        assertEquals(10L, words.first().startMs)
    }
}
