package com.bbplayer.android.ui.screens.player

import com.bbplayer.android.ui.theme.BbType
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.model.LyricLine
import com.bbplayer.android.core.model.LyricVisualStyle
import com.bbplayer.android.core.model.Lyrics
import com.moriafly.salt.ui.Text

/**
 * Salt Player style lyrics.
 *
 * - [LyricVisualStyle.MODERN]: left-aligned, only the active line is promoted.
 * - [LyricVisualStyle.OLD_SCHOOL]: centered, uniform size.
 * - [verbatim]: karaoke fill for lines that carry word-level timing (enhanced LRC / SPL / YRC).
 */
@Composable
fun LyricsPanel(
    lyrics: Lyrics?,
    positionMs: Long,
    fontScale: Float,
    modifier: Modifier = Modifier,
    style: LyricVisualStyle = LyricVisualStyle.MODERN,
    verbatim: Boolean = true,
    showTranslation: Boolean = true,
    onSeekTo: (Long) -> Unit,
    /** 歌词文字基色：播放页深色背景用白色，纯白墨水屏背景用深色。 */
    contentColor: Color = Color.White,
    /** 次级文字基色（不透明）：翻译行/空态提示用，保证 ≥4.5:1。 */
    secondaryContentColor: Color = contentColor,
) {
    if (lyrics == null || lyrics.isEmpty) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                color = secondaryContentColor,
                style = BbType.Body,
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
                style = style,
                verbatim = verbatim,
                showTranslation = showTranslation,
                positionMs = positionMs,
                onClick = { onSeekTo(line.startMs - lyrics.offsetMs) },
                contentColor = contentColor,
                secondaryContentColor = secondaryContentColor,
            )
        }
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    active: Boolean,
    fontScale: Float,
    style: LyricVisualStyle,
    verbatim: Boolean,
    showTranslation: Boolean,
    positionMs: Long,
    onClick: () -> Unit,
    contentColor: Color,
    secondaryContentColor: Color = contentColor,
) {
    val modern = style == LyricVisualStyle.MODERN
    val baseSize = (if (modern) 20f else 17f) * fontScale
    val size by animateFloatAsState(if (active && modern) baseSize * 1.15f else baseSize, label = "lyricSize")
    val color by animateColorAsState(
        targetValue = when {
            active -> contentColor
            modern -> contentColor.copy(alpha = 0.55f)
            else -> contentColor.copy(alpha = 0.65f)
        },
        label = "lyricColor",
    )
    val align = if (modern) TextAlign.Start else TextAlign.Center
    val karaokeProgress = if (active && verbatim) charProgress(line, positionMs) else 0f

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = if (modern) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (active && verbatim && line.words.isNotEmpty()) {
            KaraokeLine(
                text = line.text,
                progress = karaokeProgress,
                activeSize = size,
                activeColor = color,
                contentColor = contentColor,
                textAlign = align,
                activeWeight = FontWeight.Bold,
            )
        } else {
            Text(
                text = line.text,
                color = color,
                fontSize = size.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                lineHeight = (size * 1.35f).sp,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showTranslation) {
            line.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                Spacer(Modifier.height(3.dp))
                Text(
                    text = translation,
                    color = if (active) secondaryContentColor else contentColor.copy(alpha = 0.45f),
                    fontSize = (baseSize * 0.72f).sp,
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Base text + a highlighted copy clipped to the current karaoke position. */
@Composable
private fun KaraokeLine(
    text: String,
    progress: Float,
    activeSize: Float,
    activeColor: Color,
    contentColor: Color,
    textAlign: TextAlign,
    activeWeight: FontWeight,
) {
    var textWidth by remember(text) { mutableFloatStateOf(1f) }
    Box {
        Text(
            text = text,
            color = contentColor.copy(alpha = 0.62f),
            fontSize = activeSize.sp,
            fontWeight = activeWeight,
            lineHeight = (activeSize * 1.35f).sp,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            color = activeColor,
            fontSize = activeSize.sp,
            fontWeight = activeWeight,
            lineHeight = (activeSize * 1.35f).sp,
            textAlign = textAlign,
            onTextLayout = { result ->
                if (result.lineCount > 0) textWidth = result.getLineRight(0) - result.getLineLeft(0)
            },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val width = if (textWidth <= 1f) size.width else textWidth
                    clipRect(right = width * progress.coerceIn(0f, 1f)) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}

/** Character-level progress inside a line, based on word timings when available. */
private fun charProgress(line: LyricLine, positionMs: Long): Float {
    val total = line.text.length.coerceAtLeast(1)
    if (line.words.isEmpty()) {
        val span = (line.endMs - line.startMs).coerceAtLeast(1L)
        return ((positionMs - line.startMs).toFloat() / span).coerceIn(0f, 1f)
    }
    var consumed = 0
    for (word in line.words) {
        val length = word.text.length.coerceAtLeast(1)
        if (positionMs < word.startMs) return consumed.toFloat() / total
        if (positionMs <= word.endMs) {
            val local = ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L))
            return ((consumed + local * length) / total).coerceIn(0f, 1f)
        }
        consumed += length
    }
    return 1f
}
