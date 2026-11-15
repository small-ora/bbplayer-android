package com.bbplayer.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.model.LyricLine
import com.bbplayer.android.core.model.Lyrics
import com.moriafly.salt.ui.Text

/**
 * Salt Player style lyrics: left-aligned lines, only the active line is promoted
 * (bigger, bolder, white); neighbours fade to ~40% opacity. Auto-scroll keeps the active
 * line near the vertical centre, and pausing on a manual scroll is respected.
 */
@Composable
fun LyricsPanel(
    lyrics: Lyrics?,
    positionMs: Long,
    fontScale: Float,
    modifier: Modifier = Modifier,
    onSeekTo: (Long) -> Unit,
) {
    if (lyrics == null || lyrics.isEmpty) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
            )
        }
        return
    }
    val activeIndex = lyrics.activeIndex(positionMs)
    val listState = rememberLazyListState()
    var userScrolling by remember { mutableStateOf(false) }
    var programmaticScroll by remember { mutableStateOf(false) }

    // Follow the active line unless the user is scrolling by hand.
    LaunchedEffect(activeIndex, userScrolling) {
        if (activeIndex >= 0 && !userScrolling) {
            programmaticScroll = true
            try {
                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
            } finally {
                programmaticScroll = false
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                if (!programmaticScroll) userScrolling = true
            } else if (userScrolling) {
                // Resume following shortly after the user stops scrolling.
                kotlinx.coroutines.delay(2500)
                userScrolling = false
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(count = lyrics.lines.size) { index ->
            val line = lyrics.lines[index]
            LyricLineItem(
                line = line,
                active = index == activeIndex,
                fontScale = fontScale,
                onClick = { onSeekTo(line.startMs - lyrics.offsetMs) },
            )
        }
    }
}

@Composable
private fun LyricLineItem(line: LyricLine, active: Boolean, fontScale: Float, onClick: () -> Unit) {
    val baseSize = 20f * fontScale
    val size by animateFloatAsState(if (active) baseSize * 1.15f else baseSize, label = "lyricSize")
    val color by animateColorAsState(
        targetValue = if (active) Color.White else Color.White.copy(alpha = 0.42f),
        label = "lyricColor",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = line.text,
            color = color,
            fontSize = size.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            lineHeight = (size * 1.35f).sp,
            textAlign = TextAlign.Start,
        )
        line.translation?.takeIf { it.isNotBlank() }?.let { translation ->
            Spacer(Modifier.height(3.dp))
            Text(
                text = translation,
                color = if (active) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.28f),
                fontSize = (baseSize * 0.72f).sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}
