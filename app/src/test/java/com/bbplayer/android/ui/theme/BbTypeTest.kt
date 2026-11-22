package com.bbplayer.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v9.1 字阶 token 断言（SPEC AC1 / AC2）。
 *
 * 只断言纯 Kotlin 数值（[BbTypeScale]），不依赖 android.* / Compose 运行时。
 */
class BbTypeTest {

    private data class Tier(val name: String, val size: Float, val line: Float)

    private val tiers = listOf(
        Tier("Display", BbTypeScale.DisplaySize, BbTypeScale.DisplayLine),
        Tier("Title", BbTypeScale.TitleSize, BbTypeScale.TitleLine),
        Tier("Section", BbTypeScale.SectionSize, BbTypeScale.SectionLine),
        Tier("Body", BbTypeScale.BodySize, BbTypeScale.BodyLine),
        Tier("Label", BbTypeScale.LabelSize, BbTypeScale.LabelLine),
        Tier("Caption", BbTypeScale.CaptionSize, BbTypeScale.CaptionLine),
        Tier("Micro", BbTypeScale.MicroSize, BbTypeScale.MicroLine),
    )

    @Test
    fun hasExactlySevenTiers() {
        assertEquals("字阶必须恰好 7 档", 7, tiers.size)
    }

    @Test
    fun sizesMatchSpec() {
        assertEquals(28f, BbTypeScale.DisplaySize, 0.01f)
        assertEquals(22f, BbTypeScale.TitleSize, 0.01f)
        assertEquals(18f, BbTypeScale.SectionSize, 0.01f)
        assertEquals(15f, BbTypeScale.BodySize, 0.01f)
        assertEquals(13f, BbTypeScale.LabelSize, 0.01f)
        assertEquals(12f, BbTypeScale.CaptionSize, 0.01f)
        assertEquals(11f, BbTypeScale.MicroSize, 0.01f)
    }

    @Test
    fun lineHeightsMatchSpecAndExceedFontSize() {
        assertEquals(34f, BbTypeScale.DisplayLine, 0.01f)
        assertEquals(28f, BbTypeScale.TitleLine, 0.01f)
        assertEquals(24f, BbTypeScale.SectionLine, 0.01f)
        assertEquals(22f, BbTypeScale.BodyLine, 0.01f)
        assertEquals(18f, BbTypeScale.LabelLine, 0.01f)
        assertEquals(17f, BbTypeScale.CaptionLine, 0.01f)
        assertEquals(16f, BbTypeScale.MicroLine, 0.01f)
        tiers.forEach { assertTrue("${it.name} 行高应大于字号", it.line > it.size) }
    }

    @Test
    fun tiersAreStrictlyDescending() {
        tiers.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.name} 应大于 ${b.name}", a.size > b.size)
        }
    }

    @Test
    fun tnumFeatureIsDeclared() {
        assertEquals("tnum", BB_TNUM_FEATURE)
    }
}
