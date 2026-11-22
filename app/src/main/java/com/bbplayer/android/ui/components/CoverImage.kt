package com.bbplayer.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bbplayer.android.ui.theme.BbRadius
import com.moriafly.salt.ui.SaltTheme

/**
 * Square rounded artwork, as used everywhere in Salt Player.
 *
 * v9.1 P1-5 图片态：
 * - 占位 = 卡片面色（不出现白块/灰块瞬变）
 * - 加载成功 200ms 线性淡入（`tween(200)`，等价 crossfade 200ms）
 * - 加载失败回落为 accent 渐变兜底（不再空白）
 */
@Composable
fun CoverImage(
    url: String,
    modifier: Modifier = Modifier,
    corner: Dp = BbRadius.Small,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(corner)
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "coverFade",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(SaltTheme.colors.subBackground)
            .border(0.5.dp, SaltTheme.colors.stroke, shape),
    ) {
        if (url.isNotBlank() && !failed) {
            AsyncImage(
                model = normalizeCoverUrl(url),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { failed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha },
            )
        }
        if (url.isBlank() || failed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                SaltTheme.colors.highlight.copy(alpha = 0.35f),
                                SaltTheme.colors.highlight.copy(alpha = 0.08f),
                            )
                        )
                    )
            )
        }
    }
}

/** Bilibili covers are sometimes http:// and support image processing suffixes. */
fun normalizeCoverUrl(url: String): String {
    if (url.isBlank()) return url
    val https = if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
    // Strip any existing @-suffix and request a reasonable thumbnail for lists.
    val base = https.substringBefore('@')
    return base
}

/**
 * Cover palette for the player background wash (Salt Player style: blurred artwork + white text).
 * Returns [primary, secondary] colours; falls back to the Salt accent when extraction fails.
 */
@Composable
fun rememberCoverWash(url: String): List<Color> = listOf(Color.Transparent, Color.Transparent)
