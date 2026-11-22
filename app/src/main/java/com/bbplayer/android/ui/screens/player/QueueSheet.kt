package com.bbplayer.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SaltTheme.colors.background,
        contentColor = SaltTheme.colors.text,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = BbSpacing.Xl, vertical = BbSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("播放队列", style = BbType.Section, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("${queue.size} 首", color = SaltTheme.colors.subText, style = BbType.Label)
                Spacer(Modifier.weight(1f))
                if (queue.isNotEmpty()) {
                    Box(Modifier.clickable(onClick = onClear).padding(BbSpacing.Sm)) {
                        Text("清空", color = SaltTheme.colors.subText, style = BbType.Label)
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().height(if (queue.size > 6) 460.dp else (queue.size * 60 + 20).dp)) {
                items(count = queue.size, key = { "queue-${queue[it].key}-$it" }) { index ->
                    val track = queue[index]
                    val current = index == currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clickable { onJumpTo(index) }
                            .padding(horizontal = BbSpacing.Xl, vertical = BbSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(track.coverUrl, Modifier.size(40.dp), corner = 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.displayTitle,
                                color = if (current) SaltTheme.colors.highlight else SaltTheme.colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = BbType.Body,
                            )
                            Text(
                                track.uploader,
                                color = SaltTheme.colors.subText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = BbType.Caption,
                            )
                        }
                        Text(Format.duration(track.durationMs), color = SaltTheme.colors.subText, style = BbType.CaptionTnum)
                        IconAction(Icons.Rounded.Close, "移出队列", onClick = { onRemove(index) }, tint = SaltTheme.colors.subText, iconSize = BbIconSize.Sm)
                    }
                }
            }
        }
    }
}
