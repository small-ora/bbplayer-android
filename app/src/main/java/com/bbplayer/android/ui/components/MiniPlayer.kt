package com.bbplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlin.math.abs

/**
 * 悬浮胶囊迷你播放器（需求 §1）。
 *
 * - 播放器卡片左右各 12dp 边距、与底部导航保持 6dp 间距、圆角 16dp（高度 64dp = 62dp 内容行 + 2dp 进度条）
 * - 左侧 40×40dp 圆角 8dp 封面 / 中间标题（加粗、单行跑马灯）+ UP 主（次要色、单行）
 * - 右侧只保留播放/暂停 + 下一首两个核心控制键，触控热区 ≥48×48dp
 * - 卡片底部内置 2dp 播放进度指示条
 * - 点按进入全屏播放器；向上拖拽也进入播放器；保留左滑下一首 / 右滑上一首
 */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val fraction = if (state.durationMs > 0) {
        state.positionMs.toFloat() / state.durationMs
    } else {
        0f
    }.coerceIn(0f, 1f)

    val density = LocalDensity.current
    val upThreshold = with(density) { 48.dp.toPx() }
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val shape = RoundedCornerShape(BbTokens.CardCorner)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BbTokens.MiniPlayerSideMargin)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(SaltTheme.colors.popup)
            .border(0.5.dp, SaltTheme.colors.stroke, shape)
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        when {
                            // 向上拖拽 = 展开全屏播放器
                            total.y <= -upThreshold && abs(total.y) > abs(total.x) -> onOpenPlayer()
                            // 左滑下一首 / 右滑上一首（v7.3 既有手势）
                            total.x <= -swipeThreshold && abs(total.x) > abs(total.y) -> onNext()
                            total.x >= swipeThreshold && abs(total.x) > abs(total.y) -> onPrevious()
                        }
                        total = Offset.Zero
                    },
                    onDragCancel = { total = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    },
                )
            }
            .clickable(onClick = onOpenPlayer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BbTokens.MiniPlayerRowHeight)
                .padding(start = BbSpacing.Md, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = track.coverUrl,
                modifier = Modifier.size(BbTokens.MiniCoverSize),
                corner = BbTokens.MiniCoverCorner,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = track.displayTitle,
                    style = BbType.Body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    // 单行超长跑马灯
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.uploader,
                    color = SaltTheme.colors.subText,
                    style = BbType.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MiniPlayPauseButton(state = state, onClick = onTogglePlay)
            MiniTransportButton(
                icon = Icons.Rounded.SkipNext,
                description = "下一首",
                onClick = onNext,
            )
        }
        // 内置 2dp 播放进度指示条
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BbSpacing.Md)
                .height(2.dp)
                .clip(CircleShape)
                .background(SaltTheme.colors.subText.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(SaltTheme.colors.highlight),
            )
        }
    }
}

@Composable
private fun MiniPlayPauseButton(state: PlaybackState, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(BbTokens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            // 加载中由进度圈替代图标时保持语义不变
            .semantics { contentDescription = if (state.isPlaying) "暂停" else "播放" },
        contentAlignment = Alignment.Center,
    ) {
        if (state.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = SaltTheme.colors.highlight,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = SaltTheme.colors.text,
                modifier = Modifier.size(BbIconSize.Lg),
            )
        }
    }
}

@Composable
private fun MiniTransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(BbTokens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = SaltTheme.colors.text.copy(alpha = 0.8f),
            modifier = Modifier.size(BbIconSize.Lg),
        )
    }
}
