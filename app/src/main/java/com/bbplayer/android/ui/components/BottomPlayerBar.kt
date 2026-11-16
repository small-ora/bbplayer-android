package com.bbplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.model.MiniPlayerStyle
import com.bbplayer.android.core.model.PlaybackState
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/**
 * Bottom bar of the main screen: **music transport controls only** (Salt Player capsule):
 * cover / title / artist / previous / play-pause / next / queue + a 2 dp progress line at the
 * bottom edge. Navigation lives in [AppDrawerSheet] instead of here.
 *
 * [MiniPlayerStyle.DOCK]  = full-width bar attached to the bottom edge (rounded top corners).
 * [MiniPlayerStyle.FLOAT] = floating capsule with side margins.
 */
@Composable
fun BottomPlayerBar(
    state: PlaybackState,
    style: MiniPlayerStyle,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val fraction = if (state.durationMs > 0) {
        state.positionMs.toFloat() / state.durationMs
    } else {
        0f
    }
    val floating = style == MiniPlayerStyle.FLOAT
    val density = androidx.compose.ui.platform.LocalDensity.current
    // 左滑下一首、右滑上一首（点按仍然是进入播放页）。
    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val shape = if (floating) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (floating) Modifier.padding(horizontal = 12.dp) else Modifier)
            .clip(shape)
            .background(SaltTheme.colors.popup)
            .then(if (floating) Modifier.border(0.5.dp, SaltTheme.colors.stroke, shape) else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (floating) 64.dp else 62.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total <= -swipeThresholdPx -> onNext()
                                total >= swipeThresholdPx -> onPrevious()
                            }
                            total = 0f
                        },
                        onDragCancel = { total = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            total += dragAmount
                            change.consume()
                        },
                    )
                }
                .clickable(onClick = onOpenPlayer)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(url = track.coverUrl, modifier = Modifier.size(44.dp), corner = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = track.displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.uploader,
                    color = SaltTheme.colors.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
            }
            TransportButton(
                icon = Icons.Rounded.SkipPrevious,
                description = "上一首",
                onClick = onPrevious,
            )
            PlayPauseButton(state = state, onClick = onTogglePlay)
            TransportButton(
                icon = Icons.Rounded.SkipNext,
                description = "下一首",
                onClick = onNext,
            )
        }
        ProgressLine(
            fraction = fraction.coerceIn(0f, 1f),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun PlayPauseButton(state: PlaybackState, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            // Keep the label stable while the buffering spinner replaces the icon (a11y + tests).
            .semantics { contentDescription = if (state.isPlaying) "暂停" else "播放" },
        contentAlignment = Alignment.Center,
    ) {
        if (state.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                color = SaltTheme.colors.highlight,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = SaltTheme.colors.text,
                modifier = Modifier.size(27.dp),
            )
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = SaltTheme.colors.subText,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(23.dp),
        )
    }
}
