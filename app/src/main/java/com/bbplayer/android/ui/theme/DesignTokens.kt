package com.bbplayer.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme

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

    /** 封面上/视频卡片圆角（发现页 16:9 卡）。 */
    val CoverCorner: Dp = 12.dp

    /** 迷你播放器封面圆角。 */
    val MiniCoverCorner: Dp = 8.dp

    // ---------------- 底部 chrome
    /** Material 3 NavigationBar 容器高度。 */
    val BottomNavHeight: Dp = 80.dp

    /** 迷你播放器内容行高度（另有 2dp 进度条 = 总高 64dp）。 */
    val MiniPlayerRowHeight: Dp = 62.dp

    /** 迷你播放器总高（内容行 + 2dp 进度条），需求要求 56–64dp。 */
    val MiniPlayerHeight: Dp = 64.dp

    /** 迷你播放器左右边距。 */
    val MiniPlayerSideMargin: Dp = 12.dp

    /** 迷你播放器与底部导航间距。 */
    val MiniPlayerGap: Dp = 6.dp

    /** 迷你播放器封面尺寸。 */
    val MiniCoverSize: Dp = 40.dp

    /** 迷你播放器控制键最小触控热区。 */
    val MinTouchTarget: Dp = 48.dp

    // ---------------- 内容防遮挡
    /** 根 Tab 页：底部导航 80 + 迷你 64 + 间距 6 + 余量 6。 */
    val RootContentBottomPadding: Dp = 156.dp

    /** 二级页：只有迷你播放器 + 手势条，需求口径约 140dp。 */
    val SecondaryContentBottomPadding: Dp = 140.dp

    // ---------------- 字阶（需求：20sp Bold / 14sp Regular / 12sp 60%）
    val TitleLargeSize = 20.sp
    val BodySize = 14.sp
    val CaptionSize = 12.sp

    /** 辅助说明文字用 60% 主题灰。 */
    const val CaptionAlpha = 0.6f

    /** 页面卡片形状。 */
    val CardShape = RoundedCornerShape(CardCorner)
}

/** 卡片表面色：#EDF2F7（浅色）/ surfaceContainer（深色）。 */
@Composable
fun bbCardSurface(): Color = SaltTheme.colors.subBackground

/** 辅助说明文字颜色：主题灰 × 60%。 */
@Composable
fun bbCaptionColor(): Color = SaltTheme.colors.subText.copy(alpha = BbTokens.CaptionAlpha)
