package com.bbplayer.android.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BBPlayer 设计 token（需求 §6 视觉规范）。
 *
 * 背景/卡片色在 [BbTheme] 里通过 SaltColors 覆盖（浅色 #F8F9FA / #EDF2F7），
 * 这里集中管理新骨架与新页面用到的尺寸、圆角和字阶，避免各页面各写一套魔法数字。
 */
object BbTokens {
    // ---------------- 卡片 / 圆角
    /** 全应用卡片统一圆角。 */
    val CardCorner: Dp = 16.dp

    /** 视频卡片圆角（发现页 16:9 卡）。 */
    val CoverCorner: Dp = 12.dp

    /** 迷你播放器封面圆角。 */
    val MiniCoverCorner: Dp = 8.dp

    // ---------------- 底部 chrome（唯一事实源；内容安全内边距由它们推导）
    /** Material 3 NavigationBar 容器高度。 */
    val BottomNavHeight: Dp = 80.dp

    /** 迷你播放器内容行高度。 */
    val MiniPlayerRowHeight: Dp = 62.dp

    /** 迷你播放器底部 2dp 进度条。 */
    val MiniPlayerProgressHeight: Dp = 2.dp

    /** 迷你播放器总高（内容行 + 进度条），需求要求 56–64dp。 */
    val MiniPlayerHeight: Dp = MiniPlayerRowHeight + MiniPlayerProgressHeight

    /** 迷你播放器左右边距。 */
    val MiniPlayerSideMargin: Dp = 12.dp

    /** 迷你播放器与底部导航间距。 */
    val MiniPlayerGap: Dp = 6.dp

    /** 迷你播放器封面尺寸。 */
    val MiniCoverSize: Dp = 40.dp

    /** 迷你播放器控制键最小触控热区。 */
    val MinTouchTarget: Dp = 48.dp

    // ---------------- 内容防遮挡
    /** 根 Tab 页 chrome：底部导航 + 迷你播放器 + 间距 + 6dp 余量（系统 inset 另加，见下）。 */
    val RootContentBottomPadding: Dp =
        BottomNavHeight + MiniPlayerHeight + MiniPlayerGap + 6.dp

    /** 二级页：只有迷你播放器 + 手势条，需求口径约 140dp。 */
    val SecondaryContentBottomPadding: Dp = 140.dp

    // ---------------- 字阶（需求：20sp Bold / 14sp Regular / 12sp 60%）
    val TitleLargeSize = 20.sp
    val BodySize = 14.sp
    val CaptionSize = 12.sp

    /** 辅助说明文字用 60% 主题灰。 */
    const val CaptionAlpha = 0.6f
}

/**
 * 根 Tab 页列表的底部安全内边距 = 150dp chrome + 6dp 余量 + 系统导航栏 inset。
 *
 * edge-to-edge 下页面列表直到底边，必须把 navigationBars inset 算进去，
 * 否则 AOSP 手势导航（~24dp）/ 三键导航（~48dp）的设备上最后一项会被迷你播放器压住。
 */
@Composable
fun bbRootContentBottomPadding(): Dp =
    BbTokens.RootContentBottomPadding +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
