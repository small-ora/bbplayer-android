package com.bbplayer.android.lyrics

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Minimal OkHttp helper shared by the QQ / NetEase providers. Every call has an 8s timeout and a desktop UA
 * (both QQ and NetEase reject requests that look like bots); cancelling the coroutine cancels the HTTP call.
 */
internal object LyricsHttp {

    private const val TIMEOUT_MS = 8_000L

    const val DESKTOP_UA: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun getString(client: OkHttpClient, url: String, referer: String): String {
        val request = try {
            Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", DESKTOP_UA)
                .header("Referer", referer)
                .header("Accept", "application/json, text/plain, */*")
                .build()
        } catch (error: IllegalArgumentException) {
            throw IOException("非法请求地址: $url", error)
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val outcome = runCatching {
                        response.use {
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            response.body?.string() ?: throw IOException("响应为空")
                        }
                    }
                    if (continuation.isCancelled) return
                    outcome.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }
            })
        }
    }
}
