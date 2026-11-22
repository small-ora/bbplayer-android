package com.bbplayer.android.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * BBPlayer 设计 token（v9.1 P1-2 起为单一事实源）。
 *
 * 背景/文字色见 [ThemeTokens] / [BbTheme]；字阶见 [BbType]；
 * 这里只放**间距 / 圆角 / 图标尺寸 / 底部 chrome**。
 */
object BbTokens {
    // ---------------- 卡片 / 圆角（v9.1：圆角档位 8/12/16/28/full）
    /** 全应用卡片统一圆角。 */
    val CardCorner: Dp = BbRadius.Card

    /** 视频卡片圆角（发现页 16:9 卡）。 */
    val CoverCorner: Dp = BbRadius.Media

    /** 迷你播放器封面圆角。 */
    val MiniCoverCorner: Dp = BbRadius.Small

    // ---------------- 底部 chrome（唯一事实源；内容安全内边距由它们推导）
    /** Material 3 NavigationBar 容器高度（v9.1 S13 起由 dock 合并取代）。 */
    val BottomNavHeight: Dp = 80.dp

    /** 迷你播放器内容行高度。 */
    val MiniPlayerRowHeight: Dp = 62.dp

    /** 迷你播放器底部 2dp 进度条。 */
    val MiniPlayerProgressHeight: Dp = 2.dp

    /** 迷你播放器总高（内容行 + 进度条）。 */
    val MiniPlayerHeight: Dp = MiniPlayerRowHeight + MiniPlayerProgressHeight

    /** 迷你播放器左右边距。 */
    val MiniPlayerSideMargin: Dp = BbSpacing.Md

    /** 迷你播放器与底部导航间距。 */
    val MiniPlayerGap: Dp = 6.dp

    /** 迷你播放器封面尺寸。 */
    val MiniCoverSize: Dp = 40.dp

    /** 最小触控热区（AC：所有可点元素 ≥48dp）。 */
    val MinTouchTarget: Dp = BbIconSize.Touch

    // ---------------- 内容防遮挡
    /** 根 Tab 页 chrome：底部导航 + 迷你播放器 + 间距 + 6dp 余量。 */
    val RootContentBottomPadding: Dp =
        BottomNavHeight + MiniPlayerHeight + MiniPlayerGap + 6.dp

    /** 二级页：只有迷你播放器 + 手势条，需求口径约 140dp。 */
    val SecondaryContentBottomPadding: Dp = 140.dp
}

/**
 * 间距档位（P1-2）：4 / 8 / 12 / 16 / 20 / 24 / 32 / 48。
 *
 * 页面与组件只允许使用这些档位；贴边描边、进度条等结构值可豁免。
 */
object BbSpacing {
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    val Md: Dp = 12.dp
    val Lg: Dp = 16.dp
    val Xl: Dp = 20.dp
    val Xxl: Dp = 24.dp
    val Section: Dp = 32.dp
    val Hero: Dp = 48.dp
}

/** 圆角档位（P1-2）：8 / 12 / 16 / 28 / full。 */
object BbRadius {
    val Small: Dp = 8.dp
    val Media: Dp = 12.dp
    val Card: Dp = 16.dp
    val Sheet: Dp = 28.dp
    val Dock: Dp = 28.dp
    const val FullPercent: Int = 50

    /** 50% 圆角：胶囊 / 圆形按钮 / 头像。 */
    val FullShape: RoundedCornerShape = RoundedCornerShape(FullPercent)
}

/** 图标尺寸档位（P1-2）：16 / 20 / 24 + 48dp 热区；不再用 `size * 0.5f` 魔法比例。 */
object BbIconSize {
    val Sm: Dp = 16.dp
    val Md: Dp = 20.dp
    val Lg: Dp = 24.dp
    val Touch: Dp = 48.dp
}

/**
 * 根 Tab 页列表的底部安全内边距 = dock 高度 + 6dp 余量 + 系统导航栏 inset。
 *
 * edge-to-edge 下页面列表直到底边，必须把 navigationBars inset 算进去，
 * 否则 AOSP 手势导航（~24dp）/ 三键导航（~48dp）的设备上最后一项会被 dock 压住。
 */
@Composable
fun bbRootContentBottomPadding(): Dp =
    BbTokens.RootContentBottomPadding +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
