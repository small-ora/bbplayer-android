package com.bbplayer.android

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.bbplayer.android.di.AppGraph
import com.bbplayer.android.di.RealAppGraph
import com.bbplayer.android.playback.PlaybackHistoryTracker
import com.bbplayer.android.playback.PlaybackReportTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class BbPlayerApp : Application() {
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val graph: AppGraph by lazy { RealAppGraph(this, appScope) }

    private val historyTracker: PlaybackHistoryTracker by lazy { PlaybackHistoryTracker(graph, appScope) }

    private val reportTracker: PlaybackReportTracker by lazy { PlaybackReportTracker(graph, appScope) }

    /** Shared HTTP client for artwork; the Bilibili CDN expects a desktop UA + Referer. */
    val imageHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", DESKTOP_UA)
                        .header("Referer", "https://www.bilibili.com/")
                        .build()
                )
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
                }
                .crossfade(true)
                .build()
        }
        historyTracker.start()
        reportTracker.start()
    }

    companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
