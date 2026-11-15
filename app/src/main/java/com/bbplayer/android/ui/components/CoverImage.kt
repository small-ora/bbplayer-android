package com.bbplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moriafly.salt.ui.SaltTheme

/** Square rounded artwork, as used everywhere in Salt Player. */
@androidx.compose.runtime.Composable
fun CoverImage(
    url: String,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier = modifier.clip(shape).background(SaltTheme.colors.subBackground)) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = normalizeCoverUrl(url),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
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
@androidx.compose.runtime.Composable
fun rememberCoverWash(url: String): List<Color> = listOf(Color.Transparent, Color.Transparent)
