package com.bbplayer.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * v9.2（P2-1）动效 token：全应用唯一的时长/缓动来源。
 *
 * - 时长三档：Fast 120 / Base 220 / Slow 320（转场类用 Base，反馈类用 Fast，展开类用 Slow）；
 * - 缓动：标准 [StandardEasing]（近似 M3 FastOutSlowIn）+ 展开用弹簧 [Springy]；
 * - 页面转场 / tab 切换 / 播放态 / 进度 / 列表增删都必须引用这里，不再写魔法数字。
 */
object BbMotion {
    const val FastMs: Int = 120
    const val BaseMs: Int = 220
    const val SlowMs: Int = 320

    /** 根 Tab 切换的淡入淡出时长（180–220ms 区间）。 */
    const val TabFadeMs: Int = 180

    /** 标准缓动：近似 Material FastOutSlowIn。 */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 进入/展开用（Base 档）。 */
    fun <T> enterSpec(durationMs: Int = BaseMs): FiniteAnimationSpec<T> =
        tween(durationMs, easing = StandardEasing)

    /** 退出/收起用（Fast 档，退出快于进入）。 */
    fun <T> exitSpec(durationMs: Int = FastMs): FiniteAnimationSpec<T> =
        tween(durationMs, easing = StandardEasing)

    /** 展开/回弹（封面、dock、图标 morph）。 */
    val Springy = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
