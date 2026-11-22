package com.bbplayer.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v9 中性色 token 的可读性断言（SPEC AC4 / AC5）。
 *
 * 纯 Kotlin 计算，不依赖 android.*，可在 JVM 上稳定运行。
 */
class ThemeTokensTest {

    private fun ratio(fg: Long, bg: Long): Double =
        ColorMath.contrastRatio(fg.toInt(), bg.toInt())

    @Test
    fun lightSubTextIsReadableOnSurface() {
        assertTrue(
            "subText 对白面应 ≥ 6:1，实际 ${ratio(ThemeTokens.LightSubText, ThemeTokens.LightSurface)}",
            ratio(ThemeTokens.LightSubText, ThemeTokens.LightSurface) >= 6.0,
        )
        assertTrue(
            "subText 对页面底应 ≥ 4.5:1",
            ratio(ThemeTokens.LightSubText, ThemeTokens.LightBackground) >= 4.5,
        )
    }

    @Test
    fun lightTextHasHighContrast() {
        assertTrue(ratio(ThemeTokens.LightText, ThemeTokens.LightSurface) >= 12.0)
    }

    @Test
    fun lightSurfaceIsLighterThanBackground() {
        // 白面卡片必须比页面底亮，否则会读作「压下去的面板」
        assertTrue(
            ColorMath.relativeLuminance(ThemeTokens.LightSurface.toInt()) >
                ColorMath.relativeLuminance(ThemeTokens.LightBackground.toInt()),
        )
    }

    @Test
    fun inkSurfaceDiffersFromBackground() {
        // 纯白主题卡片不能和页面底同为 #FFFFFF（当前实现卡片 1.00:1 消失）
        assertTrue(ThemeTokens.InkSurface != ThemeTokens.InkBackground)
        // 墨水屏卡片是 #F4F5F6 微灰面（不是纯白），subText 对它的对比度按 WCAG 正文下限 4.5:1 断言
        assertTrue(
            "ink subText 对微灰面应 ≥ 4.5:1，实际 ${ratio(ThemeTokens.InkSubText, ThemeTokens.InkSurface)}",
            ratio(ThemeTokens.InkSubText, ThemeTokens.InkSurface) >= 4.5,
        )
    }

    @Test
    fun hairlineUsesTwelvePercentAlpha() {
        assertEquals(0x1F, ColorMath.alphaOf(ThemeTokens.LightStroke.toInt()))
        assertEquals(0x1F, ColorMath.alphaOf(ThemeTokens.InkStroke.toInt()))
        assertEquals(0x1F, ColorMath.alphaOf(ThemeTokens.DarkStroke.toInt()))
    }

    @Test
    fun hairlineIsVisibleOnCard() {
        val stroke = ColorMath.compositeOver(
            ThemeTokens.LightStroke.toInt(),
            ThemeTokens.LightSurface.toInt(),
        )
        assertTrue(ColorMath.contrastRatio(stroke, ThemeTokens.LightSurface.toInt()) >= 1.25)
    }
}
