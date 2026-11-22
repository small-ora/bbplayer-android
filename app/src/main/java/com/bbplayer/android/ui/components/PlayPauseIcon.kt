package com.bbplayer.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.bbplayer.android.ui.theme.BbMotion

/**
 * v9.2：播放/暂停图标通过 [AnimatedContent] 交叉淡切，避免硬切。
 * 图标本身无内容描述，语义由外层按钮提供。
 */
@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            fadeIn(tween(BbMotion.FastMs)) togetherWith fadeOut(tween(BbMotion.FastMs))
        },
        label = "playPauseIcon",
        modifier = modifier.size(size),
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}
