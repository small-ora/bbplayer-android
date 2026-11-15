package com.bbplayer.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbplayer.android.core.model.PlaybackState
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/**
 * Salt Player style mini player: a capsule sitting inside the floating bottom dock,
 * with a 2 dp progress line across its top edge.
 */
@Composable
fun MiniPlayerBar(
    state: PlaybackState,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    Column(modifier = modifier.fillMaxWidth()) {
        ProgressLine(fraction = fraction.coerceIn(0f, 1f), modifier = Modifier.padding(horizontal = 14.dp).padding(top = 6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clickable(onClick = onOpenPlayer)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(url = track.coverUrl, modifier = Modifier.size(40.dp), corner = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = track.displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp2(),
                )
                Text(
                    text = track.uploader,
                    color = SaltTheme.colors.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = SaltTheme.textStyles.sub.fontSize,
                )
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = SaltTheme.colors.highlight,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = SaltTheme.colors.text,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clickable(onClick = onOpenQueue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = "播放队列",
                    tint = SaltTheme.colors.subText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

internal fun Int.sp2() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
