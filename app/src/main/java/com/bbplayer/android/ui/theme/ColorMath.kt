package com.bbplayer.android.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 纯 Kotlin 颜色数学（ARGB Int），不依赖 android.*，可在 JVM 单元测试中直接调用。
 *
 * 用途：
 * - 主题 token 的 WCAG 对比度断言（[ThemeTokensTest]）；
 * - 播放页封面取色的亮度守卫（[PlayerBackdrop]）。
 */
object ColorMath {

    fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF
    fun redOf(argb: Int): Int = (argb ushr 16) and 0xFF
    fun greenOf(argb: Int): Int = (argb ushr 8) and 0xFF
    fun blueOf(argb: Int): Int = argb and 0xFF

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xFF) shl 24) or
            ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or
            (blue and 0xFF)

    fun withAlpha(argb: Int, alpha: Int): Int =
        (argb and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

    fun withAlpha(argb: Int, alpha: Float): Int =
        withAlpha(argb, (alpha.coerceIn(0f, 1f) * 255f).roundToInt())

    /** 去掉 alpha（视作不透明）。 */
    fun opaque(argb: Int): Int = withAlpha(argb, 255)

    /** 前置层 fg（可含 alpha）合成到不透明背景 bg 上，返回不透明 ARGB。 */
    fun compositeOver(fg: Int, bg: Int): Int {
        val a = alphaOf(fg) / 255.0
        val r = (redOf(fg) * a + redOf(bg) * (1 - a)).roundToInt()
        val g = (greenOf(fg) * a + greenOf(bg) * (1 - a)).roundToInt()
        val b = (blueOf(fg) * a + blueOf(bg) * (1 - a)).roundToInt()
        return argb(255, r, g, b)
    }

    /** WCAG 2.1 相对亮度。 */
    fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val color = opaque(argb)
        return 0.2126 * channel(redOf(color)) +
            0.7152 * channel(greenOf(color)) +
            0.0722 * channel(blueOf(color))
    }

    /** WCAG 2.1 对比度；前景含 alpha 时先合成到背景上再计算。 */
    fun contrastRatio(foreground: Int, background: Int): Double {
        val bg = opaque(background)
        val fg = if (alphaOf(foreground) < 255) compositeOver(foreground, bg) else foreground
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    data class Hsv(val hue: Float, val saturation: Float, val value: Float)

    /** RGB → HSV（hue 0..360，saturation/value 0..1）。 */
    fun rgbToHsv(argb: Int): Hsv {
        val r = redOf(argb) / 255f
        val g = greenOf(argb) / 255f
        val b = blueOf(argb) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        val hue = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val saturation = if (maxC == 0f) 0f else delta / maxC
        return Hsv(if (hue < 0f) hue + 360f else hue, saturation, maxC)
    }

    /** HSV → RGB。 */
    fun hsvToArgb(hue: Float, saturation: Float, value: Float, alpha: Int = 255): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(
            alpha,
            ((r1 + m) * 255f).roundToInt(),
            ((g1 + m) * 255f).roundToInt(),
            ((b1 + m) * 255f).roundToInt(),
        )
    }
}
