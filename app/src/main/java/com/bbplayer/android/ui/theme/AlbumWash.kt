package com.bbplayer.android.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracts the album-art wash colours for the player background. Salt Player uses a heavy
 * blurred artwork wash with white text; we sample the artwork down to a tiny bitmap for speed
 * and keep the swatches saturated so the wash still looks like the cover.
 */
object CoverWash {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
            )
        }
        .build()

    private val cache = LruCache<String, List<Color>>(32)

    suspend fun colors(context: Context, url: String): List<Color> {
        if (url.isBlank()) return emptyList()
        cache.get(url)?.let { return it }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url.replace("http://", "https://")).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bytes = response.body?.bytes() ?: return@use null
                    // Decode at a small scale — palette extraction does not need full resolution.
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@use null
                    val palette = Palette.from(bitmap).clearFilters().generate()
                    val dominant = palette.dominantSwatch
                    val vibrant = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: dominant
                    val muted = palette.mutedSwatch ?: dominant
                    buildList {
                        vibrant?.let { add(boost(it.rgb, 1.15f, 1.25f)) }
                        dominant?.let { add(boost(it.rgb, 0.85f, 1.15f)) }
                        muted?.let { add(boost(it.rgb, 0.6f, 1.1f)) }
                    }.ifEmpty {
                        listOf(Color(0xFF4A6070), Color(0xFF2C3A45), Color(0xFF161B20))
                    }
                }
            }.getOrNull()
        } ?: emptyList()
        if (result.isNotEmpty()) cache.put(url, result)
        return result
    }

    /** Slightly saturate / darken a swatch so the wash reads as colour instead of grey. */
    private fun boost(rgb: Int, valueScale: Float, saturationScale: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = (hsv[1] * saturationScale).coerceIn(0.12f, 1f)
        hsv[2] = (hsv[2] * valueScale).coerceIn(0.12f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

@Composable
fun rememberAlbumWash(url: String): List<Color> {
    val context = androidx.compose.ui.platform.LocalContext.current
    var colors by remember(url) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(url) {
        colors = runCatching { CoverWash.colors(context, url) }.getOrDefault(emptyList())
    }
    return colors
}
