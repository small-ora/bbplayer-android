package com.bbplayer.android.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * v9.1（P1-1）字阶：全应用唯一的 7 档字号 / 行高来源。
 *
 * 数值集中在本文件的 [BbTypeScale]（纯 Kotlin 常量，可在 JVM 单测断言），
 * [BbType] 负责转成 Compose [TextStyle]。页面/组件层只允许引用 [BbType]，
 * 不再写 `fontSize = N.sp` 字面量（SPEC AC1）。
 *
 * 映射口径（从 v9.0 的散落字面量收敛）：
 * - 9/10/11 → Micro；12 → Caption；13 → Label；14/15 → Body；
 * - 16/17/18 → Section；20/24 → Title；26+ → Display。
 */
object BbTypeScale {
    const val DisplaySize = 28f
    const val DisplayLine = 34f
    const val TitleSize = 22f
    const val TitleLine = 28f
    const val SectionSize = 18f
    const val SectionLine = 24f
    const val BodySize = 15f
    const val BodyLine = 22f
    const val LabelSize = 13f
    const val LabelLine = 18f
    const val CaptionSize = 12f
    const val CaptionLine = 17f
    const val MicroSize = 11f
    const val MicroLine = 16f
}

/** 等宽数字（tnum）字体特性：时间 / 时长 / 计数必须用它，避免数字跳动。 */
const val BB_TNUM_FEATURE: String = "tnum"

object BbType {
    /** 详情页大标题 / 英雄数字。 */
    val Display = TextStyle(
        fontSize = BbTypeScale.DisplaySize.sp,
        lineHeight = BbTypeScale.DisplayLine.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 页面一级标题。 */
    val Title = TextStyle(
        fontSize = BbTypeScale.TitleSize.sp,
        lineHeight = BbTypeScale.TitleLine.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 区块标题。 */
    val Section = TextStyle(
        fontSize = BbTypeScale.SectionSize.sp,
        lineHeight = BbTypeScale.SectionLine.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 正文 / 列表标题。 */
    val Body = TextStyle(
        fontSize = BbTypeScale.BodySize.sp,
        lineHeight = BbTypeScale.BodyLine.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 按钮 / 值 / 辅助。 */
    val Label = TextStyle(
        fontSize = BbTypeScale.LabelSize.sp,
        lineHeight = BbTypeScale.LabelLine.sp,
        fontWeight = FontWeight.Medium,
    )

    /** meta 说明。 */
    val Caption = TextStyle(
        fontSize = BbTypeScale.CaptionSize.sp,
        lineHeight = BbTypeScale.CaptionLine.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 徽标 / 极小字。 */
    val Micro = TextStyle(
        fontSize = BbTypeScale.MicroSize.sp,
        lineHeight = BbTypeScale.MicroLine.sp,
        fontWeight = FontWeight.Medium,
    )

    // ---- 等宽数字变体（SPEC AC2）：用于时间 / 时长 / 计数
    val DisplayTnum: TextStyle = Display.tnum()
    val TitleTnum: TextStyle = Title.tnum()
    val SectionTnum: TextStyle = Section.tnum()
    val BodyTnum: TextStyle = Body.tnum()
    val LabelTnum: TextStyle = Label.tnum()
    val CaptionTnum: TextStyle = Caption.tnum()
    val MicroTnum: TextStyle = Micro.tnum()
}

/** 给任意 [TextStyle] 打开 `tnum`（数字等宽）。 */
fun TextStyle.tnum(): TextStyle = copy(fontFeatureSettings = BB_TNUM_FEATURE)
