package com.bbplayer.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.model.Track
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/**
 * List row: square artwork + one metadata line. The entire text of the playing row turns the
 * accent colour — a signature Salt Player detail (no equalizer icon, no row highlight).
 */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val textColor = if (isCurrent) SaltTheme.colors.highlight else SaltTheme.colors.text
    val subColor = if (isCurrent) SaltTheme.colors.highlight.copy(alpha = 0.75f) else SaltTheme.colors.subText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = track.coverUrl, modifier = Modifier.size(48.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = track.displayTitle,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            val meta = buildString {
                append(if (track.pageTitle.isNotBlank() && track.pageTitle != track.title) track.pageTitle else track.uploader)
                if (track.playCount > 0) append(" · ${Format.count(track.playCount)}")
            }
            Text(
                text = meta.ifBlank { track.uploader },
                color = subColor,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (track.durationMs > 0) {
            Text(
                text = Format.duration(track.durationMs),
                color = SaltTheme.colors.subText,
                fontSize = SaltTheme.textStyles.sub.fontSize,
            )
        }
        if (onMoreClick != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多",
                    tint = SaltTheme.colors.subText,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (trailing != null) {
            trailing()
        }
    }
}
