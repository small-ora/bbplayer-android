package com.bbplayer.android.data

import com.bbplayer.android.core.input.InputResolver

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.data.cache.BiliCacheRegistry
import com.bbplayer.android.data.cache.BiliCacheTtl
import com.bbplayer.android.data.cache.CacheKey
import com.bbplayer.android.data.cache.CachedRequestExecutor
import com.bbplayer.android.data.cache.HttpCacheStore
import com.bbplayer.android.data.cache.RequestThrottle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 非 B 站接口（网易云 / QQ 音乐 / GitHub Releases / 字幕 CDN）共用的 OkHttp 客户端。
 *
 * 与 [BiliHttpClient] 分开：后者会给所有请求强制注入 B 站 Referer/Origin，
 * 打第三方接口时会被当成非法来源。
 */
internal object ExternalHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

/** 常用请求头（桌面 UA，各站点自己的 Referer）。 */
internal object HttpHeaders {
    val BILIBILI: Map<String, String> = mapOf(
        "User-Agent" to BiliHttpClient.DESKTOP_UA,
        "Referer" to "https://www.bilibili.com/",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
    )

    val NETEASE: Map<String, String> = mapOf(
        "User-Agent" to BiliHttpClient.DESKTOP_UA,
        "Referer" to "https://music.163.com/",
        "Accept" to "application/json, text/plain, */*",
    )

    val QQ: Map<String, String> = mapOf(
        "User-Agent" to BiliHttpClient.DESKTOP_UA,
        "Referer" to "https://y.qq.com/",
        "Accept" to "application/json, text/plain, */*",
    )
}

/**
 * 轻量 HTTP 读取器：GET 文本 / JSON，跟随重定向。
 * 网络与 HTTP 错误统一转成 [AppException]（中文文案 + retryable）。
 *
 * ## v3 缓存
 *
 * GET JSON 与重定向结果按 [BiliCacheTtl] 做持久化缓存（suggest 5m / 字幕 JSON 24h /
 * 网易云·QQ 歌单 30m / GitHub 更新检查 10m …），命中不重复请求；
 * POST（网易云批量歌曲详情）不缓存。缓存默认复用进程级共享 store
 * （首个 `BiliApi(context)` 创建），可通过 [cache] 显式注入。
 */
internal class HttpFetcher(
    private val client: OkHttpClient = ExternalHttp.client,
    private val json: Json = BiliJson,
    cache: HttpCacheStore? = null,
    throttle: RequestThrottle = BiliCacheRegistry.sharedThrottle(),
) {

    /** 显式注入的缓存；为 null 时请求时回退到进程级共享 store（可能尚未初始化）。 */
    private val explicitCache: HttpCacheStore? = cache

    private val requests: CachedRequestExecutor by lazy {
        CachedRequestExecutor(
            cache = explicitCache ?: BiliCacheRegistry.sharedOrNull(),
            throttle = throttle,
            deduplicator = BiliCacheRegistry.sharedDeduplicator(),
        )
    }

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val text = execute(url, headers).first
            text
        }

    /**
     * GET JSON，按 [BiliCacheTtl] 读穿缓存。
     *
     * [validate] 只在**未命中缓存、真正请求成功后**执行；抛异常表示业务层判定失败
     * （如 `code != 0` / 缺字段），此时结果不写缓存，避免把错误结果缓存住。
     */
    suspend fun getJsonObject(
        url: String,
        headers: Map<String, String> = emptyMap(),
        validate: ((JsonObject) -> Unit)? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        requests.run(
            key = CacheKey.ofUrl("GET", url),
            ttlMs = BiliCacheTtl.forUrl(url),
            decode = ::parseJsonObject,
            encode = { it.toString() },
            throttled = isBilibiliUrl(url),
        ) {
            val text = getText(url, headers)
            val parsed = parseJsonObject(text)
                ?: throw AppException("返回数据解析失败，请稍后重试", retryable = true)
            validate?.invoke(parsed)
            parsed
        }
    }

    /**
     * 表单 POST 并解析 JSON（网易云 `api/v3/song/detail` 批量歌曲详情用）。
     */
    suspend fun postFormJsonObject(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).post(
            okhttp3.FormBody.Builder().apply {
                form.forEach { (key, value) -> add(key, value) }
            }.build(),
        )
        headers.forEach { (key, value) -> builder.header(key, value) }
        val request = try {
            builder.build()
        } catch (e: IllegalArgumentException) {
            throw AppException("链接格式不正确", e, retryable = false)
        }
        val response = try {
            client.newCall(request).execute()
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
                    externalHttpMessage(resp.code),
                    retryable = resp.code >= 500 || resp.code == 429 || resp.code == 412,
                )
            }
            parseJsonObject(text)
                ?: throw AppException("返回数据解析失败，请稍后重试", retryable = true)
        }
    }

    /**
     * 跟随重定向并返回最终 URL（网易云 `163cn.tv` 短链解析用，24h 缓存）。
     *
     * [validate] 用于校验落地 URL 有效（如能否解析成歌单引用）；校验失败不写缓存。
     */
    suspend fun followRedirect(
        url: String,
        headers: Map<String, String> = emptyMap(),
        validate: ((String) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
        val normalized = InputResolver.normalizeUrl(url)
        requests.run(
            key = CacheKey.ofUrl("REDIRECT", normalized),
            ttlMs = BiliCacheTtl.forUrl(normalized),
            decode = { it },
            encode = { it },
            throttled = isBilibiliUrl(normalized),
        ) {
            val finalUrl = fetchRedirectFinalUrl(normalized, headers)
            validate?.invoke(finalUrl)
            finalUrl
        }
    }

    private fun fetchRedirectFinalUrl(url: String, headers: Map<String, String>): String {
        val request = buildRequest(url, headers)
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw AppException(e.toUserMessage(), e, retryable = true)
        }
        response.use { resp -> return resp.request.url.toString() }
    }

    private fun isBilibiliUrl(url: String): Boolean = url.contains("bilibili.com")

    /** 返回 (body, statusCode)；失败时直接抛 [AppException]。 */
    private fun execute(url: String, headers: Map<String, String>): Pair<String, Int> {
        val request = buildRequest(url, headers)
        val response = try {
            client.newCall(request).execute()
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
                    externalHttpMessage(resp.code),
                    retryable = resp.code >= 500 || resp.code == 429 || resp.code == 412,
                )
            }
            return text to resp.code
        }
    }

    private fun buildRequest(url: String, headers: Map<String, String>): Request {
        val builder = try {
            Request.Builder().url(url).get()
        } catch (e: IllegalArgumentException) {
            throw AppException("链接格式不正确", e, retryable = false)
        }
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
    }
}

/** 外站 HTTP 状态码 → 中文提示。 */
internal fun externalHttpMessage(statusCode: Int): String = when {
    statusCode == 412 -> "请求被服务端安全策略拦截，请稍后重试"
    statusCode == 429 -> "请求过于频繁，请稍后重试"
    statusCode == 404 -> "请求的内容不存在或已下架"
    statusCode >= 500 -> "服务暂时不可用，请稍后重试"
    else -> "网络请求失败（HTTP $statusCode），请重试"
}

/**
 * 外部 JSON 响应的业务 code 校验（B 站风格 `{code, message, data}`）。
 * `code == 0` 视为成功，否则抛 [AppException]。
 */
internal fun JsonObject.requireSuccess(defaultMessage: String) {
    val code = longOrNull("code") ?: 0L
    if (code == 0L) return
    val message = when (code) {
        -412L -> "请求被B站安全策略拦截，请稍后重试"
        -509L, -799L -> "请求过于频繁，请稍后重试"
        else -> strOrNull("message")?.takeIf { it.isNotBlank() } ?: defaultMessage
    }
    throw AppException(message, retryable = code !in NON_RETRYABLE_CODES)
}

private val NON_RETRYABLE_CODES = setOf(-400L, -403L, -404L)

/**
 * 统一 Result 包装：业务/网络异常 → [AppException] 放进 [Result.failure]，
 * 协程取消异常原样抛出（与 [BiliMusicRepository] 保持一致）。
 */
internal suspend fun <T> repoResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(
        if (e is AppException) e else AppException(e.toUserMessage(), e, retryable = true),
    )
}
