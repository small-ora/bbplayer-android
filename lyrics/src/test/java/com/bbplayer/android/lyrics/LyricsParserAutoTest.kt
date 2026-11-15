package com.bbplayer.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the unified [LyricsParser] entry point and AUTO format detection. */
class LyricsParserAutoTest {

    @Test
    fun `auto detection picks spl for word level markers`() {
        assertEquals(LyricsFormat.SPL, LyricsParser.detectFormat("[00:01.00]你<00:02.00>好"))
        assertEquals(LyricsFormat.SPL, LyricsParser.detectFormat("[00:01.00]你[00:02.00]好"))
        assertEquals(LyricsFormat.LRC, LyricsParser.detectFormat("[00:01.00]plain lrc\n[00:02.00]line"))
        assertEquals(LyricsFormat.LRC, LyricsParser.detectFormat("[ti:song]\n[00:01.00]plain"))
    }

    @Test
    fun `auto routes word level text to the spl parser`() {
        val lyrics = LyricsParser.parse("[00:01.00]你<00:02.00>好")

        assertEquals(1, lyrics.lines.size)
        assertEquals("你好", lyrics.lines[0].text)
        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals(1_000L, lyrics.lines[0].words[0].startMs)
        assertEquals(2_000L, lyrics.lines[0].words[1].startMs)
    }

    @Test
    fun `auto keeps lrc semantics for plain lrc`() {
        val text = "[00:01.00]A\n[00:02.00]B"

        val auto = LyricsParser.parse(text)
        val lrc = LrcParser.parse(text)

        assertEquals(lrc.lines, auto.lines)
        assertEquals(2_000L, auto.lines[0].endMs) // LRC: next line start
        assertEquals(5_000L, auto.lines[1].endMs) // LRC last line: start + 3 s (not SPL's 10 s)
        assertTrue(auto.lines.none { it.words.isNotEmpty() })
    }

    @Test
    fun `explicit format overrides auto detection`() {
        val text = "[00:01.00]A"

        assertEquals(11_000L, LyricsParser.parse(text, LyricsFormat.SPL).lines[0].endMs)
        assertEquals(4_000L, LyricsParser.parse(text, LyricsFormat.LRC).lines[0].endMs)
    }

    @Test
    fun `empty and metadata only text parse to an empty result`() {
        assertTrue(LyricsParser.parse("").lines.isEmpty())
        assertTrue(LyricsParser.parse("[ti:only metadata]").lines.isEmpty())
        assertEquals(0, LyricsParser.parse("   ").lines.size)
    }
}
