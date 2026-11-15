package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** b23.tv 短链解析结果：重定向后的最终 URL 与落地页 HTML（用于读取 canonical）。 */
internal data class ShortLinkResolution(val finalUrl: String, val html: String)

/** 全局共享的 OkHttp 客户端：桌面 Chrome UA + B 站 Referer/Origin，连接 10s / 读取 20s。 */
internal object BiliHttpClient {

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val REFERER = "https://www.bilibili.com/"
    const val ORIGIN = "https://www.bilibili.com"

    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", DESKTOP_UA)
                    .header("Referer", REFERER)
                    .header("Origin", ORIGIN)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}

/**
 * 匿名 B 站 Web API 客户端。
 *
 * - 所有请求带桌面 UA / Referer / Origin / `Cookie: buvid3=...`；
 * - 启动时 GET `/x/frontend/finger/spi` 取 `data.b_3` 作为 buvid3，内存 + `filesDir/buvid3.txt` 缓存；
 * - `code != 0` 与网络异常统一抛 [AppException]（中文文案 + retryable）。
 */
class BiliApi(
    context: Context,
    private val client: OkHttpClient = BiliHttpClient.instance,
    private val json: Json = BiliJson,
    private val baseUrl: String = BASE_URL,
) {

    private val appContext = context.applicationContext
    private val buvidFile = File(appContext.filesDir, "buvid3.txt")
    private val buvidMutex = Mutex()

    @Volatile
    private var cachedBuvid: String? = null

    /** 取 buvid3（内存 → 文件 → 网络），失败返回 null（不阻断请求）。 */
    suspend fun ensureBuvid(): String? = buvidMutex.withLock {
        cachedBuvid?.let { return@withLock it }
        val persisted = withContext(Dispatchers.IO) {
            runCatching { if (buvidFile.exists()) buvidFile.readText().trim() else null }.getOrNull()
        }
        if (!persisted.isNullOrBlank()) {
            cachedBuvid = persisted
            return@withLock persisted
        }
        val fetched = runCatching { fetchBuvid() }.getOrNull()
        if (!fetched.isNullOrBlank()) {
            cachedBuvid = fetched
            withContext(Dispatchers.IO) {
                runCatching {
                    buvidFile.parentFile?.mkdirs()
                    buvidFile.writeText(fetched)
                }
            }
        }
        fetched
    }

    private suspend fun fetchBuvid(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/x/frontend/finger/spi").get().build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            return@withContext null
        }
        response.use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val text = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
            runCatching {
                (json.parseToJsonElement(text) as? JsonObject)
                    ?.jsonObject
                    ?.get("data")
                    ?.jsonObject
                    ?.strOrNull("b_3", "b3")
            }.getOrNull()
        }
    }

    /**
     * 发 GET 请求，校验 `code`，返回 `data` 节点（可能是对象/数组）。
     *
     * [messageFor] 允许调用方按业务场景覆盖错误码文案（如评论/字幕），
     * 默认使用通用的 [apiMessage]。
     */
    suspend fun getRaw(
        path: String,
        params: Map<String, String> = emptyMap(),
        messageFor: (Long, String?) -> String = ::apiMessage,
    ): JsonElement =
        withContext(Dispatchers.IO) {
            val buvid = ensureBuvid()
            val urlBuilder = (baseUrl + path).toHttpUrl().newBuilder()
            params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
            val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
            if (!buvid.isNullOrBlank()) {
                requestBuilder.header("Cookie", "buvid3=$buvid")
            }
            val response = try {
                client.newCall(requestBuilder.build()).execute()
            } catch (e: IOException) {
                throw AppException(e.toUserMessage(), e, retryable = true)
            }
            response.use { resp ->
                val text = try {
                    resp.body?.string().orEmpty()
                } catch (e: IOException) {
                    throw AppException(e.toUserMessage(), e, retryable = true)
                }
                if (!resp.isSuccessful) {
                    throw AppException(
                        httpMessage(resp.code),
                        retryable = resp.code >= 500 || resp.code == 412 || resp.code == 429,
                    )
                }
                val root = try {
                    json.parseToJsonElement(text) as? JsonObject
                } catch (e: SerializationException) {
                    null
                } ?: throw AppException("B站返回数据解析失败，请重试", retryable = true)
                val code = root.longOrNull("code") ?: 0L
                if (code != 0L) {
                    throw AppException(
                        messageFor(code, root.strOrNull("message")),
                        retryable = code !in NON_RETRYABLE_CODES,
                    )
                }
                root.get("data") ?: JsonObject(emptyMap())
            }
        }

    suspend fun getObject(
        path: String,
        params: Map<String, String> = emptyMap(),
        messageFor: (Long, String?) -> String = ::apiMessage,
    ): JsonObject =
        getRaw(path, params, messageFor) as? JsonObject ?: JsonObject(emptyMap())

    suspend fun getArray(
        path: String,
        params: Map<String, String> = emptyMap(),
        messageFor: (Long, String?) -> String = ::apiMessage,
    ): JsonArray =
        getRaw(path, params, messageFor) as? JsonArray ?: JsonArray(emptyList())

    /** 跟随 b23.tv 短链重定向，返回最终 URL 与落地页 HTML。 */
    internal suspend fun resolveShortLink(url: String): ShortLinkResolution? = withContext(Dispatchers.IO) {
        val normalized = InputResolver.normalizeUrl(url)
        val request = try {
            Request.Builder().url(normalized.toHttpUrl()).get().build()
        } catch (e: IllegalArgumentException) {
            throw AppException("链接格式不正确", e, retryable = false)
        }
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw AppException(e.toUserMessage(), e, retryable = true)
        }
        response.use { resp ->
            val finalUrl = resp.request.url.toString()
            val html = runCatching { resp.peekBody(MAX_SHORT_LINK_BODY).string() }.getOrDefault("")
            ShortLinkResolution(finalUrl, html)
        }
    }

    companion object {
        const val BASE_URL = "https://api.bilibili.com"

        private const val MAX_SHORT_LINK_BODY = 256L * 1024

        /** 参数错误 / 无权限 / 不存在：重试没有意义。 */
        private val NON_RETRYABLE_CODES = setOf(-400L, -403L, -404L)
    }
}

/** B 站业务 code → 中文提示。 */
internal fun apiMessage(code: Long, serverMessage: String?): String = when (code) {
    -101L -> "账号未登录，请先登录"
    -111L -> "账号校验失败，请重新登录"
    -400L -> "请求参数错误"
    -403L -> "访问被拒绝，视频可能需要登录或大会员"
    -404L -> "视频不存在或已被删除"
    -412L -> "请求被B站安全策略拦截，请稍后重试"
    -509L, -799L -> "请求过于频繁，请稍后重试"
    -10403L -> "该视频仅限特定地区播放"
    else -> serverMessage?.takeIf { it.isNotBlank() } ?: "B站接口错误（code=$code）"
}

/** HTTP 状态码 → 中文提示。 */
internal fun httpMessage(statusCode: Int): String = when {
    statusCode == 412 -> "请求被B站安全策略拦截，请稍后重试"
    statusCode == 429 -> "请求过于频繁，请稍后重试"
    statusCode >= 500 -> "B站服务暂时不可用，请稍后重试"
    statusCode == 404 -> "请求的接口不存在"
    else -> "网络请求失败（HTTP $statusCode），请重试"
}
