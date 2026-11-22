package com.bbplayer.android.ui.theme

/**
 * 播放页沉浸背景的「亮度守卫」（SPEC AC1）。
 *
 * 背景由三层组成：
 *  1. 封面取色的「洗色」底（[washForBackground]，低饱和、低明度）；
 *  2. 模糊封面图（[COVER_IMAGE_ALPHA]，保留封面气质但只占 40%）；
 *  3. 黑色 scrim 渐变（[SCRIM_TOP] / [SCRIM_MID] / [SCRIM_BOTTOM]）。
 *
 * 约定：**白色文字对「最坏情况」（纯白封面 + 最浅的一层 scrim）的对比度必须 ≥ 4.5:1**。
 * 这个不变量由 [washForBackground] 在取色阶段就把颜色压暗来保证，并由
 * `PlayerBackdropTest` 对亮黄/白/青/红/绿/黑等极端封面逐条断言。
 */
object PlayerBackdrop {

    /** 洗色允许的最大饱和度（保留封面色相，但不要荧光感）。 */
    const val MAX_WASH_SATURATION = 0.55f

    /** 洗色允许的最大明度。 */
    const val MAX_WASH_VALUE = 0.42f

    /** 洗色下限（纯黑封面也会留一点层次，避免死黑）。 */
    const val MIN_WASH_VALUE = 0.08f

    /** 模糊封面图的不透明度。 */
    const val COVER_IMAGE_ALPHA = 0.40f

    /** 顶部 scrim。 */
    const val SCRIM_TOP = 0.42f

    /** 中部 scrim（最浅的一层，是亮度守卫的约束点）。 */
    const val SCRIM_MID = 0.38f

    /** 底部 scrim。 */
    const val SCRIM_BOTTOM = 0.62f

    /** 正文白字的最低对比度。 */
    const val MIN_TEXT_CONTRAST = 4.5

    /** 播放页主文字：不透明白（避免半透明在最坏背景下跌破 4.5:1）。 */
    val ON_PLAYER_PRIMARY_ARGB: Int = 0xFFFFFFFF.toInt()

    /** 播放页次级文字：不透明近白（对最坏背景 ≥4.5:1；层级靠字号/字重，不靠 alpha）。 */
    val ON_PLAYER_SECONDARY_ARGB: Int = 0xFFEDEDED.toInt()

    private val WHITE: Int = ON_PLAYER_PRIMARY_ARGB
    private val BLACK: Int = 0xFF000000.toInt()

    /**
     * 把封面取色处理成可安全承载白字的背景色。
     * 先夹紧饱和度/明度，再逐步压暗，直到「最坏情况对比度」达标。
     */
    fun washForBackground(rawArgb: Int): Int {
        val hsv = ColorMath.rgbToHsv(rawArgb)
        val saturation = hsv.saturation.coerceIn(0f, MAX_WASH_SATURATION)
        var value = hsv.value.coerceIn(MIN_WASH_VALUE, MAX_WASH_VALUE)
        var guard = 0
        while (guard < 80 && value > 0.02f) {
            val candidate = ColorMath.hsvToArgb(hsv.hue, saturation, value)
            if (worstCaseContrast(candidate) >= MIN_TEXT_CONTRAST) return candidate
            value -= 0.02f
            guard++
        }
        return ColorMath.hsvToArgb(hsv.hue, saturation, value.coerceAtLeast(0.02f))
    }

    /**
     * 最坏情况背景：纯白封面图以 [coverImageAlpha] 叠在洗色上，再叠加 [scrimAlpha] 的黑色 scrim。
     */
    fun textBackground(
        color: Int,
        scrimAlpha: Float = SCRIM_MID,
        coverImageAlpha: Float = COVER_IMAGE_ALPHA,
    ): Int {
        val withImage = ColorMath.compositeOver(
            ColorMath.withAlpha(WHITE, coverImageAlpha),
            color,
        )
        return ColorMath.compositeOver(
            ColorMath.withAlpha(BLACK, scrimAlpha),
            withImage,
        )
    }

    /** 最坏情况下的白字对比度。 */
    fun worstCaseContrast(
        color: Int,
        scrimAlpha: Float = SCRIM_MID,
        coverImageAlpha: Float = COVER_IMAGE_ALPHA,
    ): Double = ColorMath.contrastRatio(WHITE, textBackground(color, scrimAlpha, coverImageAlpha))

    /** 最坏情况下的次级文字对比度（[ON_PLAYER_SECONDARY_ARGB] 不透明近白）。 */
    fun secondaryTextContrast(
        color: Int,
        scrimAlpha: Float = SCRIM_MID,
        coverImageAlpha: Float = COVER_IMAGE_ALPHA,
    ): Double = ColorMath.contrastRatio(
        ON_PLAYER_SECONDARY_ARGB,
        textBackground(color, scrimAlpha, coverImageAlpha),
    )

    /** 背景是否亮到需要深色状态栏图标（守卫后通常为 false，仅纯白主题为 true）。 */
    fun isBright(washArgb: Int, scrimAlpha: Float = SCRIM_TOP): Boolean =
        ColorMath.relativeLuminance(textBackground(washArgb, scrimAlpha)) > 0.5
}
