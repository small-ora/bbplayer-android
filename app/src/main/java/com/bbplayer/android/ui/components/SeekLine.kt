package com.bbplayer.android.ui.components

import com.bbplayer.android.ui.theme.BbType
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bbplayer.android.core.Format
import com.bbplayer.android.ui.theme.BbMotion
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/**
 * Salt Player style progress: a 2 dp line, thumb only while dragging, times *below* the bar.
 */
@Composable
fun SeekLine(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = SaltTheme.colors.highlight,
    trackColor: Color = SaltTheme.colors.subText.copy(alpha = 0.18f),
    showTimes: Boolean = true,
    timeColor: Color = SaltTheme.colors.subText,
    /** 进度条可拖动热区高度（≥44dp，SPEC AC8）。 */
    touchHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val density = LocalDensity.current.density
    var widthPx by remember { mutableFloatStateOf(1f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val duration = durationMs.coerceAtLeast(1L)
    val fraction = if (dragging) dragFraction else (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    // v9.2：非拖动时进度平滑推进（250ms 线性），拖动时跟手不补间
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (dragging) tween(0) else tween(BbMotion.ProgressMs, easing = LinearEasing),
        label = "seekProgress",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(touchHeight)
                .onSizeChanged {
                    size = it
                    widthPx = it.width.toFloat()
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            onSeek((dragFraction * duration).toLong())
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                            change.consume()
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(trackColor, CircleShape)
            )
            Box(
                Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(2.dp)
                    .background(activeColor, CircleShape)
            )
            if (dragging) {
                Box(
                    Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                ((size.width * fraction) - 12f * density).toInt(),
                                0,
                            )
                        }
                        .size(12.dp)
                        .background(activeColor, CircleShape)
                )
            }
        }
        if (showTimes) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                val shown = if (dragging) (dragFraction * duration).toLong() else positionMs
                Text(text = Format.duration(shown), color = timeColor, style = BbType.CaptionTnum)
                Text(text = Format.duration(durationMs), color = timeColor, style = BbType.CaptionTnum)
            }
        }
    }
}

/** Simpler non-interactive progress line (mini player / list). */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = SaltTheme.colors.highlight) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(SaltTheme.colors.subText.copy(alpha = 0.15f), CircleShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(2.dp)
                .background(color, CircleShape)
        )
    }
}
