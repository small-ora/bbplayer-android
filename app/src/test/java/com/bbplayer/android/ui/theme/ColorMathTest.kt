package com.bbplayer.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ColorMath] 的基础正确性（对比度公式 / 合成 / HSV 往返）。 */
class ColorMathTest {

    @Test
    fun blackOnWhiteIsTwentyOne() {
        assertEquals(
            21.0,
            ColorMath.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
            0.01,
        )
    }

    @Test
    fun whiteOnBrightYellowIsLow() {
        // 播放页白字 1.5:1 的根因：亮黄背景
        assertTrue(ColorMath.contrastRatio(0xFFFFFFFF.toInt(), 0xFFFFD700.toInt()) < 2.0)
    }

    @Test
    fun halfWhiteOverBlackIsMidGrey() {
        val fg = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.5f)
        assertEquals(0xFF808080.toInt(), ColorMath.compositeOver(fg, 0xFF000000.toInt()))
    }

    @Test
    fun hsvRoundTripKeepsPrimaryColours() {
        val red = ColorMath.rgbToHsv(0xFFFF0000.toInt())
        assertEquals(0f, red.hue, 0.01f)
        assertEquals(1f, red.saturation, 0.01f)
        assertEquals(1f, red.value, 0.01f)
        assertEquals(0xFFFF0000.toInt(), ColorMath.hsvToArgb(red.hue, red.saturation, red.value))
    }

    @Test
    fun compositeOverOpaqueReturnsOpaque() {
        val out = ColorMath.compositeOver(0x80FF0000.toInt(), 0xFF000000.toInt())
        assertEquals(255, ColorMath.alphaOf(out))
    }
}
