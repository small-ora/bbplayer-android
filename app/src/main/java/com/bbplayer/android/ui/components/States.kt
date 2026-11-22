package com.bbplayer.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbRadius
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/**
 * v9.1 P1-5 状态层：
 * - [EmptyState]：120dp 单色符号 + 标题 + 说明 + 48dp 主按钮（主张 + 行动）
 * - [ErrorState]：内联错误卡片，不打断列表布局，重试按钮 48dp
 * - [LoadingState]：只用于「加载更多」等局部加载；首屏一律用 [SkeletonTrackList] / [SkeletonFeed]
 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Rounded.LibraryMusic,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BbSpacing.Section, vertical = BbSpacing.Hero),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 120dp 单色符号：低透明度单色，不做装饰插画。
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SaltTheme.colors.subText.copy(alpha = 0.30f),
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(BbSpacing.Lg))
        Text(
            text = title,
            color = SaltTheme.colors.text,
            style = BbType.Section,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Spacer(Modifier.height(BbSpacing.Sm))
            Text(
                text = it,
                color = SaltTheme.colors.subText,
                style = BbType.Label,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(BbSpacing.Xl))
            BbPrimaryButton(text = action, onClick = onAction)
        }
    }
}

/** 内联错误卡片：保留列表上下文，不整页替换。 */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val cardShape = RoundedCornerShape(BbRadius.Card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Md)
            .clip(cardShape)
            .background(SaltTheme.colors.subBackground)
            .border(0.5.dp, SaltTheme.colors.stroke, cardShape)
            .padding(BbSpacing.Lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = SaltTheme.colors.subText,
                modifier = Modifier.size(BbIconSize.Md),
            )
            Spacer(Modifier.width(BbSpacing.Md))
            Text(
                text = message,
                color = SaltTheme.colors.text,
                style = BbType.Label,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(BbSpacing.Md))
        BbPrimaryButton(
            text = "重试",
            onClick = onRetry,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** 局部加载（加载更多 / 次级页）：首屏不要用它，用骨架屏。 */
@Composable
fun LoadingState(modifier: Modifier = Modifier, text: String = "加载中…") {
    Box(modifier = modifier.fillMaxWidth().padding(BbSpacing.Section), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = SaltTheme.colors.highlight,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(BbSpacing.Md))
            Text(text = text, color = SaltTheme.colors.subText, style = BbType.Caption)
        }
    }
}

/** 主按钮：48dp 高、全圆角、accent 填充（P1-5 空态/错误态行动点）。 */
@Composable
fun BbPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(BbRadius.FullShape)
            .background(SaltTheme.colors.highlight)
            .clickable(onClick = onClick)
            .padding(horizontal = BbSpacing.Xl, vertical = BbSpacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = SaltTheme.colors.onHighlight, style = BbType.Label)
    }
}

// ---------------- 骨架屏（P1-5）：行高按真实 64dp、12dp 圆角、alpha 呼吸 ----------------

/** 骨架屏共用的 alpha 呼吸（每个骨架列表只开一条 InfiniteTransition，避免几十个动画各自打点）。 */
@Composable
private fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

@Composable
private fun SkeletonBlock(alpha: Float, modifier: Modifier = Modifier, corner: Dp = BbRadius.Small) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(SaltTheme.colors.text.copy(alpha = alpha)),
    )
}

/** 列表首屏骨架：行高 64dp + 48dp 封面 + 两行文字，按真实版式占位。 */
@Composable
fun SkeletonTrackList(
    modifier: Modifier = Modifier,
    rows: Int = 7,
) {
    val alpha = skeletonAlpha()
    Column(modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = BbSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(alpha, Modifier.size(48.dp), corner = BbRadius.Small)
                Spacer(Modifier.width(BbSpacing.Md))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(alpha, Modifier.fillMaxWidth(0.62f).height(14.dp))
                    Spacer(Modifier.height(BbSpacing.Sm))
                    SkeletonBlock(alpha, Modifier.fillMaxWidth(0.38f).height(11.dp))
                }
            }
        }
    }
}

/** 发现页首屏骨架：搜索条 + 两段横向卡片区。 */
@Composable
fun SkeletonFeed(modifier: Modifier = Modifier) {
    val alpha = skeletonAlpha()
    Column(modifier.fillMaxWidth().padding(horizontal = BbSpacing.Lg)) {
        SkeletonBlock(
            alpha,
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            corner = BbRadius.Sheet,
        )
        repeat(2) { section ->
            Spacer(Modifier.height(BbSpacing.Xl))
            SkeletonBlock(alpha, Modifier.fillMaxWidth(0.28f).height(18.dp))
            Spacer(Modifier.height(BbSpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(BbSpacing.Md)) {
                repeat(3) {
                    Column(Modifier.weight(1f)) {
                        // 自适应宽度：320dp 窄屏也不会横向溢出（固定 112dp ×3 会超）。
                        SkeletonBlock(
                            alpha,
                            Modifier.fillMaxWidth().aspectRatio(1f),
                            corner = BbRadius.Media,
                        )
                        Spacer(Modifier.height(BbSpacing.Sm))
                        SkeletonBlock(alpha, Modifier.fillMaxWidth(0.8f).height(13.dp))
                    }
                }
            }
            if (section == 1) Spacer(Modifier.height(BbSpacing.Xxl))
        }
    }
}
