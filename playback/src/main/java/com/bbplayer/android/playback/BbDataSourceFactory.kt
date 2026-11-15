package com.bbplayer.android.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * Data source chain used by ExoPlayer for Bilibili audio:
 *
 * ```
 * ExoPlayer -> [BbFallbackDataSource]
 *                 └─ ResolvingDataSource(lazy bbplayer:// -> https CDN URL)
 *                       └─ OkHttpDataSource (desktop UA + Referer)
 * ```
 *
 * The CDN URL is resolved at `open()` time through [BiliStreamResolver] so a
 * queued track never carries an expired URL. When the CDN answers 403/410 (or
 * any retryable IO failure) the resolver cache is invalidated and the open /
 * read is retried exactly once with a freshly resolved (or backup) URL.
 */
class BbDataSourceFactory(
    private val streamResolver: BiliStreamResolver,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
) : DataSource.Factory {
    private val upstreamFactory: DataSource.Factory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent(PlaybackConfig.DESKTOP_USER_AGENT)
        .setDefaultRequestProperties(PlaybackConfig.STREAM_HEADERS)

    /** Plain OkHttp factory used by the notification bitmap loader. */
    val artworkDataSourceFactory: DataSource.Factory get() = upstreamFactory

    override fun createDataSource(): DataSource =
        BbFallbackDataSource(upstreamFactory, streamResolver)

    private class BbFallbackDataSource(
        private val upstreamFactory: DataSource.Factory,
        private val streamResolver: BiliStreamResolver,
        private var retriesLeft: Int = MAX_RETRIES,
        private var nextAttempt: Int = 0,
    ) : DataSource {
        private var transferListener: TransferListener? = null
        private var delegate: DataSource? = null
        private var requestSpec: DataSpec? = null
        private var bytesConsumed: Long = 0L

        override fun addTransferListener(transferListener: TransferListener) {
            this.transferListener = transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            closeQuietly()
            requestSpec = dataSpec
            bytesConsumed = 0L
            retriesLeft = MAX_RETRIES
            nextAttempt = 0
            while (true) {
                val source = createSource(nextAttempt)
                try {
                    val remaining = source.open(dataSpec)
                    delegate = source
                    return remaining
                } catch (error: IOException) {
                    runCatching { source.close() }
                    if (retriesLeft > 0 && isRetryable(error)) {
                        retriesLeft -= 1
                        nextAttempt += 1
                        invalidate(dataSpec)
                        Log.w(TAG, "stream open failed (${error.javaClass.simpleName}: ${error.message}); re-resolving once")
                        continue
                    }
                    throw error
                }
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val source = delegate ?: error("DataSource is not open")
            return try {
                source.read(buffer, offset, length).also { read ->
                    if (read > 0) bytesConsumed += read
                }
            } catch (error: IOException) {
                val spec = requestSpec
                if (spec == null || retriesLeft <= 0 || !isRetryable(error)) throw error
                retriesLeft -= 1
                runCatching { source.close() }
                invalidate(spec)
                Log.w(TAG, "stream read failed at ${spec.position + bytesConsumed}ms; re-resolving once")
                val retrySpec = buildRetrySpec(spec)
                val reopened = createSource(nextAttempt + 1)
                // A failure here propagates: only one retry per data source.
                reopened.open(retrySpec)
                nextAttempt += 1
                delegate = reopened
                reopened.read(buffer, offset, length).also { read ->
                    if (read > 0) bytesConsumed += read
                }
            }
        }

        override fun getUri(): Uri? = delegate?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            delegate?.responseHeaders ?: emptyMap()

        override fun close() {
            closeQuietly()
            requestSpec = null
            bytesConsumed = 0L
        }

        private fun closeQuietly() {
            runCatching { delegate?.close() }
            delegate = null
        }

        private fun createSource(attempt: Int): DataSource {
            val upstream = upstreamFactory.createDataSource()
            transferListener?.let(upstream::addTransferListener)
            return ResolvingDataSource(upstream, BbLazyResolver(streamResolver, attempt))
        }

        /** Continue a partially read resource at the current byte offset. */
        private fun buildRetrySpec(spec: DataSpec): DataSpec {
            val builder = spec.buildUpon().setPosition(spec.position + bytesConsumed)
            if (spec.length >= 0L) {
                builder.setLength((spec.length - bytesConsumed).coerceAtLeast(0L))
            }
            return builder.build()
        }

        private fun invalidate(spec: DataSpec) {
            val ref = TrackUri.parse(spec.uri.toString()) ?: return
            streamResolver.invalidate(ref.trackKey)
        }
    }

    /** Resolves `bbplayer://track/...` into a fresh CDN URL at open time. */
    private class BbLazyResolver(
        private val streamResolver: BiliStreamResolver,
        private val attempt: Int,
    ) : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val ref = TrackUri.parse(dataSpec.uri.toString()) ?: return dataSpec
            val stream = try {
                runBlocking {
                    streamResolver.resolve(
                        track = ref.toTrack(),
                        quality = TrackUri.qualityOf(ref),
                        forceRefresh = attempt > 0,
                    )
                }
            } catch (error: Throwable) {
                Log.w(TAG, "resolving stream failed for bv=${ref.bvid} cid=${ref.cid}", error)
                throw IOException(
                    "解析音频流失败（bv=${ref.bvid}, cid=${ref.cid}）",
                    error,
                )
            }
            // First failure retries preferred CDN mirrors before the primary URL.
            val url = if (attempt > 0) {
                stream.backupUrls.firstOrNull()?.takeIf { it.isNotBlank() } ?: stream.url
            } else {
                stream.url
            }
            return dataSpec
                .withUri(Uri.parse(url))
                .withAdditionalHeaders(PlaybackConfig.STREAM_HEADERS)
        }
    }

    companion object {
        private const val TAG = "BBPlayback"

        /** One retry per open/read failure (SPEC C8). */
        private const val MAX_RETRIES = StreamRetryPolicy.MAX_RETRIES

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun isRetryable(error: IOException): Boolean = when (error) {
            is HttpDataSource.InvalidResponseCodeException -> isRetryableStatus(error.responseCode)
            is HttpDataSource.HttpDataSourceException -> error.cause is IOException
            else -> true
        }

        fun isRetryableStatus(statusCode: Int): Boolean =
            StreamRetryPolicy.isRetryableStatus(statusCode)
    }
}
