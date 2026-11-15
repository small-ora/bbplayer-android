package com.bbplayer.android.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SleepFadeMathTest {
    @Test
    fun `volume is linear inside the ten second window and zero at expiry`() {
        assertEquals(0.8f, SleepFadeMath.volumeAt(10_000L, 0.8f), 0.0001f)
        assertEquals(0.4f, SleepFadeMath.volumeAt(5_000L, 0.8f), 0.0001f)
        assertEquals(0.08f, SleepFadeMath.volumeAt(1_000L, 0.8f), 0.0001f)
        assertEquals(0f, SleepFadeMath.volumeAt(0L, 0.8f), 0.0001f)
        assertEquals(0f, SleepFadeMath.volumeAt(-200L, 0.8f), 0.0001f)
    }

    @Test
    fun `volume is unchanged before the window and clamped to a valid range`() {
        assertEquals(1f, SleepFadeMath.volumeAt(30_000L, 1f), 0.0001f)
        assertEquals(0.5f, SleepFadeMath.volumeAt(20_000L, 0.5f), 0.0001f)
        assertEquals(0.5f, SleepFadeMath.volumeAt(5_000L, 3f), 0.0001f) // base clamped to 1
        assertEquals(0f, SleepFadeMath.volumeAt(5_000L, -1f), 0.0001f) // base clamped to 0
    }

    @Test
    fun `fade consists of fifty 200ms steps from 98 percent to silence`() {
        val steps = SleepFadeMath.fadeSteps(baseVolume = 1f)
        assertEquals(SleepFadeMath.STEPS, steps.size)
        assertEquals(50, steps.size)
        assertEquals(0.98f, steps.first(), 0.0001f)
        assertEquals(0f, steps.last(), 0.0001f)
        steps.zipWithNext().forEach { (previous, next) ->
            assertTrue("fade must be monotonic: $previous -> $next", next <= previous)
        }
    }

    @Test
    fun `window boundaries`() {
        assertTrue(SleepFadeMath.isInFadeWindow(10_000L))
        assertTrue(SleepFadeMath.isInFadeWindow(1L))
        assertFalse(SleepFadeMath.isInFadeWindow(10_001L))
        assertFalse(SleepFadeMath.isInFadeWindow(0L))
        assertEquals(0f, SleepFadeMath.volumeAt(5_000L, 1f, windowMs = 0L), 0.0001f)
    }
}

class StatusBarLyricLineTest {
    @Before
    fun setUp() = StatusBarLyricLine.reset()

    @After
    fun tearDown() = StatusBarLyricLine.reset()

    @Test
    fun `format trims and collapses whitespace so the notification row stays one line`() {
        assertEquals("hello world", StatusBarLyricLine.format("  hello\n\t world  "))
        assertNull(StatusBarLyricLine.format("   "))
        assertNull(StatusBarLyricLine.format(null))
    }

    @Test
    fun `long lyric lines are ellipsized to the notification budget`() {
        val longLine = "词".repeat(StatusBarLyricLine.MAX_LINE_LENGTH + 40)
        val formatted = StatusBarLyricLine.format(longLine)!!
        assertEquals(StatusBarLyricLine.MAX_LINE_LENGTH, formatted.length)
        assertTrue(formatted.endsWith("…"))
    }

    @Test
    fun `current returns the provider line and respects the enabled toggle`() {
        StatusBarLyricLine.setProvider { "  第一行歌词  " }
        assertEquals("第一行歌词", StatusBarLyricLine.current())
        StatusBarLyricLine.setEnabled(false)
        assertNull(StatusBarLyricLine.current())
        StatusBarLyricLine.setEnabled(true)
        assertEquals("第一行歌词", StatusBarLyricLine.current())
        StatusBarLyricLine.setProvider(null)
        assertNull(StatusBarLyricLine.current())
    }

    @Test
    fun `blank provider content is treated as no lyric`() {
        StatusBarLyricLine.setProvider { "   " }
        assertNull(StatusBarLyricLine.current())
        StatusBarLyricLine.setProvider { "" }
        assertNull(StatusBarLyricLine.current())
    }
}
