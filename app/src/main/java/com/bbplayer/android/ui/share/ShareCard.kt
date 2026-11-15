package com.bbplayer.android.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.ui.theme.CoverWash
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Renders a shareable lyric/song card (1080x1440 PNG): album-art gradient background,
 * rounded cover, title/artist and the current lyric line, then hands it to the system
 * share sheet (or saves it to the gallery).
 */
object ShareCard {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120 Safari/537.36")
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
            )
        }
        .build()

    suspend fun share(
        context: Context,
        track: Track,
        lyricLine: String,
        translation: String?,
    ): Boolean = runCatching {
        val bitmap = render(context, track, lyricLine, translation)
        val file = File(context.cacheDir, "shared").apply { mkdirs() }
            .resolve("bbplayer-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享歌词卡片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    /** Save a card into the gallery (Pictures/BBPlayer). */
    suspend fun saveToGallery(context: Context, track: Track, lyricLine: String, translation: String?): Boolean = runCatching {
        val bitmap = render(context, track, lyricLine, translation)
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "bbplayer-${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BBPlayer")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    private suspend fun render(context: Context, track: Track, lyricLine: String, translation: String?): Bitmap {
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val wash = runCatching { CoverWash.colors(context, track.coverUrl) }.getOrDefault(emptyList())
        val top = wash.firstOrNull()?.toArgb() ?: Color.rgb(30, 34, 42)
        val bottom = wash.getOrNull(2)?.toArgb() ?: Color.rgb(12, 14, 18)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(top, blend(top, Color.BLACK, 0.45f), bottom),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // cover art
        val cover = loadBitmap(track.coverUrl)
        val coverSize = 560f
        val coverLeft = (width - coverSize) / 2f
        val coverTop = 150f
        val coverRect = RectF(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize)
        if (cover != null) {
            val rounded = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.save()
            canvas.clipPath(android.graphics.Path().apply { addRoundRect(coverRect, 36f, 36f, android.graphics.Path.Direction.CW) })
            canvas.drawBitmap(cover, null, coverRect, rounded)
            canvas.restore()
        } else {
            paint.shader = null
            paint.color = top
            canvas.drawRoundRect(coverRect, 36f, 36f, paint)
        }

        val white = Color.WHITE
        paint.shader = null
        paint.color = white
        paint.textSize = 56f
        paint.isFakeBoldText = true
        val title = track.displayTitle.take(40)
        drawCenteredWrapped(canvas, title, width / 2f, 830f, paint, width - 160f, 68f)
        paint.isFakeBoldText = false
        paint.color = Color.argb(200, 255, 255, 255)
        paint.textSize = 34f
        drawCentered(canvas, track.uploader, width / 2f, 940f, paint)

        // lyric line
        paint.color = white
        paint.textSize = 52f
        paint.isFakeBoldText = true
        drawCenteredWrapped(canvas, lyricLine.ifBlank { track.title }, width / 2f, 1090f, paint, width - 160f, 64f)
        paint.isFakeBoldText = false
        translation?.takeIf { it.isNotBlank() }?.let {
            paint.color = Color.argb(180, 255, 255, 255)
            paint.textSize = 32f
            drawCentered(canvas, it.take(60), width / 2f, 1170f, paint)
        }

        paint.color = Color.argb(150, 255, 255, 255)
        paint.textSize = 26f
        drawCentered(canvas, "BBPlayer · B站音频", width / 2f, 1340f, paint)
        return bitmap
    }

    private fun drawCentered(canvas: Canvas, text: String, cx: Float, baseline: Float, paint: Paint) {
        val w = paint.measureText(text)
        canvas.drawText(text, cx - w / 2f, baseline, paint)
    }

    private fun drawCenteredWrapped(canvas: Canvas, text: String, cx: Float, baseline: Float, paint: Paint, maxWidth: Float, lineHeight: Float) {
        val lines = wrap(text, paint, maxWidth)
        lines.forEachIndexed { index, line ->
            drawCentered(canvas, line, cx, baseline + index * lineHeight, paint)
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        text.forEach { ch ->
            current.append(ch)
            if (paint.measureText(current.toString()) > maxWidth) {
                current.deleteCharAt(current.length - 1)
                lines.add(current.toString())
                current = StringBuilder().append(ch)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun loadBitmap(url: String): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            val request = Request.Builder().url(url.replace("http://", "https://")).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun blend(color: Int, other: Int, ratio: Float): Int {
        fun channel(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun mix(a: Int, b: Int) = (a * (1 - ratio) + b * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(
            mix(channel(color, 16), channel(other, 16)),
            mix(channel(color, 8), channel(other, 8)),
            mix(channel(color, 0), channel(other, 0)),
        )
    }
}
