package com.bbplayer.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放页背景亮度守卫的回归测试（SPEC AC1）。
 *
 * 覆盖极端封面取色：亮黄 / 白 / 青 / 红 / 绿 / 黑 / 灰。
 * 断言：白色文字对「洗色 + 40% 模糊封面 + 最浅 scrim」的对比度 ≥ 4.5:1。
 */
class PlayerBackdropTest {

    private val extremes = mapOf(
        "亮黄" to 0xFFFFD700.toInt(),
        "纯白" to 0xFFFFFFFF.toInt(),
        "青色" to 0xFF00FFFF.toInt(),
        "红色" to 0xFFFF0000.toInt(),
        "绿色" to 0xFF00FF00.toInt(),
        "纯黑" to 0xFF000000.toInt(),
        "中灰" to 0xFF808080.toInt(),
        "荧光粉" to 0xFFFF00FF.toInt(),
    )

    @Test
    fun whiteTextPassesAtEveryScrimStopForAllExtremeCovers() {
        for ((name, raw) in extremes) {
            val wash = PlayerBackdrop.washForBackground(raw)
            val top = PlayerBackdrop.worstCaseContrast(wash, PlayerBackdrop.SCRIM_TOP)
            val mid = PlayerBackdrop.worstCaseContrast(wash, PlayerBackdrop.SCRIM_MID)
            val bottom = PlayerBackdrop.worstCaseContrast(wash, PlayerBackdrop.SCRIM_BOTTOM)
            assertTrue(
                "$name: 顶部 scrim 应 ≥4.5:1，实际 $top",
                top >= PlayerBackdrop.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "$name: 中部 scrim 应 ≥4.5:1，实际 $mid",
                mid >= PlayerBackdrop.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "$name: 底部 scrim 应 ≥4.5:1，实际 $bottom",
                bottom >= PlayerBackdrop.MIN_TEXT_CONTRAST,
            )
        }
    }

    /**
     * 独立复算：不复用 [PlayerBackdrop.worstCaseContrast]，按 SPEC 写死 40% 封面 / 三段 scrim，
     * 防止「实现和断言共用同一函数」导致回归测不出来。
     */
    private fun independentBackground(wash: Int, scrimAlpha: Float): Int {
        val image = ColorMath.compositeOver(
            ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.40f),
            wash,
        )
        return ColorMath.compositeOver(
            ColorMath.withAlpha(0xFF000000.toInt(), scrimAlpha),
            image,
        )
    }

    @Test
    fun independentPipelineKeepsPrimaryAndSecondaryTextReadable() {
        val scrims = listOf(PlayerBackdrop.SCRIM_TOP, PlayerBackdrop.SCRIM_MID, PlayerBackdrop.SCRIM_BOTTOM)
        for ((name, raw) in extremes) {
            val wash = PlayerBackdrop.washForBackground(raw)
            for (scrim in scrims) {
                val bg = independentBackground(wash, scrim)
                val primary = ColorMath.contrastRatio(0xFFFFFFFF.toInt(), bg)
                val secondary = ColorMath.contrastRatio(PlayerBackdrop.ON_PLAYER_SECONDARY_ARGB, bg)
                assertTrue("$name @scrim=$scrim 主文字 $primary", primary >= 4.5)
                assertTrue("$name @scrim=$scrim 次级文字 $secondary", secondary >= 4.5)
            }
        }
    }

    @Test
    fun washRespectsValueLowerAndUpperBounds() {
        for ((name, raw) in extremes) {
            val wash = ColorMath.rgbToHsv(PlayerBackdrop.washForBackground(raw))
            assertTrue(
                "$name: 明度应 ≥ ${PlayerBackdrop.MIN_WASH_VALUE}，实际 ${wash.value}",
                wash.value >= PlayerBackdrop.MIN_WASH_VALUE - 0.01f,
            )
            assertTrue(
                "$name: 明度应 ≤ ${PlayerBackdrop.MAX_WASH_VALUE}，实际 ${wash.value}",
                wash.value <= PlayerBackdrop.MAX_WASH_VALUE + 0.01f,
            )
        }
    }

    @Test
    fun secondaryTextContrastHelperMatchesIndependentMath() {
        for ((name, raw) in extremes) {
            val wash = PlayerBackdrop.washForBackground(raw)
            val expected = ColorMath.contrastRatio(
                PlayerBackdrop.ON_PLAYER_SECONDARY_ARGB,
                independentBackground(wash, PlayerBackdrop.SCRIM_MID),
            )
            assertEquals(expected, PlayerBackdrop.secondaryTextContrast(wash), 1e-9)
        }
    }

    @Test
    fun solidBackgroundWithoutCoverImagePasses() {
        for ((name, raw) in extremes) {
            val wash = PlayerBackdrop.washForBackground(raw)
            val contrast = PlayerBackdrop.worstCaseContrast(
                wash,
                PlayerBackdrop.SCRIM_MID,
                coverImageAlpha = 0f,
            )
            assertTrue("$name: SOLID 背景应 ≥4.5:1，实际 $contrast", contrast >= 4.5)
        }
    }

    @Test
    fun washStaysWithinSaturationAndValueBudget() {
        for ((name, raw) in extremes) {
            val wash = ColorMath.rgbToHsv(PlayerBackdrop.washForBackground(raw))
            assertTrue(
                "$name: 饱和度应 ≤ ${PlayerBackdrop.MAX_WASH_SATURATION}，实际 ${wash.saturation}",
                wash.saturation <= PlayerBackdrop.MAX_WASH_SATURATION + 0.01f,
            )
            assertTrue(
                "$name: 明度应 ≤ ${PlayerBackdrop.MAX_WASH_VALUE}，实际 ${wash.value}",
                wash.value <= PlayerBackdrop.MAX_WASH_VALUE + 0.01f,
            )
        }
    }

    @Test
    fun washKeepsCoverHue() {
        val yellow = ColorMath.rgbToHsv(PlayerBackdrop.washForBackground(0xFFFFD700.toInt()))
        assertEquals(51.6f, yellow.hue, 2f) // #FFD700 的色相约 51°
        val red = ColorMath.rgbToHsv(PlayerBackdrop.washForBackground(0xFFFF0000.toInt()))
        assertEquals(0f, red.hue, 1f)
    }

    @Test
    fun guardedWashIsNotBright() {
        for ((name, raw) in extremes) {
            val wash = PlayerBackdrop.washForBackground(raw)
            assertTrue("$name: 守卫后的背景不应被判为亮色", !PlayerBackdrop.isBright(wash))
        }
    }
}
