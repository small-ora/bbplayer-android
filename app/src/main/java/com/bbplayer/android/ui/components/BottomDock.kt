package com.bbplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bbplayer.android.core.model.PlaybackState
import com.moriafly.salt.ui.SaltTheme

/** One floating dock: capsule mini player + icon-only navigation row (Salt Player layout). */
@Composable
fun BottomDock(
    tabs: List<DockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    playbackState: PlaybackState,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(SaltTheme.colors.popup)
            .border(0.5.dp, SaltTheme.colors.stroke, RoundedCornerShape(28.dp)),
    ) {
        if (playbackState.track != null) {
            MiniPlayerBar(
                state = playbackState,
                onOpenPlayer = onOpenPlayer,
                onTogglePlay = onTogglePlay,
                onOpenQueue = onOpenQueue,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(0.5.dp)
                    .background(SaltTheme.colors.stroke)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

data class DockTab(val label: String, val icon: ImageVector)

internal fun com.moriafly.salt.ui.SaltColors.isDarkTheme(): Boolean = background.luminance() < 0.5f

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
