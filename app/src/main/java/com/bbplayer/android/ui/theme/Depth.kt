package com.bbplayer.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme

/**
 * v9.1 P1-4 深度体系（全应用只允许这三处有阴影）：
 *
 * 1. **卡片**：veil/白面 + 0.5dp hairline（[BbTheme] 的 stroke token），**不投影**；
 * 2. **封面**：[bbCoverShadow] 24dp 环境阴影 + 同图低透明度光晕（光晕在调用侧画 radiaGradient，不进 Blur）；
 * 3. **dock**：[bbDockDepth] 16dp 软阴影 + 1dp 顶边 hairline。
 */
@Composable
fun Modifier.bbCoverShadow(corner: Dp = BbRadius.Card): Modifier = shadow(
    elevation = 24.dp,
    shape = RoundedCornerShape(corner),
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.10f),
    spotColor = Color.Black.copy(alpha = 0.16f),
)

/** 宽屏悬浮迷你条 16dp 软阴影（与 dock 同一档，移动端 dock 模式下不使用）。 */
@Composable
fun Modifier.bbFloatingMiniDepth(shape: Shape = RoundedCornerShape(BbRadius.Card)): Modifier = shadow(
    elevation = 16.dp,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.10f),
    spotColor = Color.Black.copy(alpha = 0.14f),
)

/** dock 16dp 软阴影 + 1dp 顶边 hairline（顶边用来在浅色/纯白主题里勾出容器边界）。 */
@Composable
fun Modifier.bbDockDepth(shape: Shape = RoundedCornerShape(BbRadius.Dock)): Modifier {
    val hairline = SaltTheme.colors.stroke
    return this
        .shadow(
            elevation = 16.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.14f),
        )
        .drawBehind {
            drawLine(
                color = hairline,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
            )
        }
}
