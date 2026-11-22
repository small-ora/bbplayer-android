package com.bbplayer.android.ui.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v9.1 字阶 token 断言（SPEC AC1 / AC2）。
 *
 * 断言对象是 **真实的 [BbType] TextStyle API**（不是测试内手写值）：
 * 删/改任一档字号、行高、字重或 tnum 变体，本测试都会红。
 * Compose TextStyle 在 JVM 上只做纯值构造，无需 Android 运行时。
 */
class BbTypeTest {

    private val display get() = BbType.Display
    private val title get() = BbType.Title
    private val section get() = BbType.Section
    private val body get() = BbType.Body
    private val label get() = BbType.Label
    private val caption get() = BbType.Caption
    private val micro get() = BbType.Micro

    @Test
    fun sizesMatchSpec() {
        assertEquals(28f, display.fontSize.value, 0.01f)
        assertEquals(22f, title.fontSize.value, 0.01f)
        assertEquals(18f, section.fontSize.value, 0.01f)
        assertEquals(15f, body.fontSize.value, 0.01f)
        assertEquals(13f, label.fontSize.value, 0.01f)
        assertEquals(12f, caption.fontSize.value, 0.01f)
        assertEquals(11f, micro.fontSize.value, 0.01f)
    }

    @Test
    fun lineHeightsMatchSpecAndExceedFontSize() {
        assertEquals(34f, display.lineHeight.value, 0.01f)
        assertEquals(28f, title.lineHeight.value, 0.01f)
        assertEquals(24f, section.lineHeight.value, 0.01f)
        assertEquals(22f, body.lineHeight.value, 0.01f)
        assertEquals(18f, label.lineHeight.value, 0.01f)
        assertEquals(17f, caption.lineHeight.value, 0.01f)
        assertEquals(16f, micro.lineHeight.value, 0.01f)
        listOf(display, title, section, body, label, caption, micro).forEach {
            assertTrue("行高应大于字号", it.lineHeight.value > it.fontSize.value)
        }
    }

    @Test
    fun weightsMatchSpec() {
        assertEquals(FontWeight.Bold, display.fontWeight)
        assertEquals(FontWeight.SemiBold, title.fontWeight)
        assertEquals(FontWeight.SemiBold, section.fontWeight)
        assertEquals(FontWeight.Normal, body.fontWeight)
        assertEquals(FontWeight.Medium, label.fontWeight)
        assertEquals(FontWeight.Normal, caption.fontWeight)
        assertEquals(FontWeight.Medium, micro.fontWeight)
    }

    @Test
    fun tnumVariantsAreDeclaredWithFeature() {
        assertEquals(BB_TNUM_FEATURE, BbType.TitleTnum.fontFeatureSettings)
        assertEquals(BB_TNUM_FEATURE, BbType.CaptionTnum.fontFeatureSettings)
        assertEquals(BB_TNUM_FEATURE, BbType.MicroTnum.fontFeatureSettings)
        // tnum 变体只应改 feature，不应改字号/行高
        assertEquals(title.fontSize, BbType.TitleTnum.fontSize)
        assertEquals(title.lineHeight, BbType.TitleTnum.lineHeight)
        assertEquals(caption.fontSize, BbType.CaptionTnum.fontSize)
        assertEquals(micro.fontSize, BbType.MicroTnum.fontSize)
    }

    @Test
    fun tiersAreStrictlyDescending() {
        val sizes = listOf(display, title, section, body, label, caption, micro).map { it.fontSize.value }
        sizes.zipWithNext().forEach { (a, b) -> assertTrue("$a 应大于 $b", a > b) }
    }
}
