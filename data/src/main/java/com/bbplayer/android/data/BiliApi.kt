package com.bbplayer.android.data

import com.bbplayer.android.core.input.InputResolver

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.data.auth.AuthRegistry
import com.bbplayer.android.data.cache.BiliCacheRegistry
import com.bbplayer.android.data.cache.BiliCacheTtl
import com.bbplayer.android.data.cache.CacheKey
import com.bbplayer.android.data.cache.CachedRequestExecutor
import com.bbplayer.android.data.cache.HttpCacheStore
import com.bbplayer.android.data.cache.InFlightDeduplicator
import com.bbplayer.android.data.cache.RequestThrottle
import com.bbplayer.android.data.cache.SecurityCooldown
import com.bbplayer.android.data.cache.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
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
            // Defence in depth: never forward the account Cookie to a non-bilibili host,
            // even if the server answers with a cross-host redirect (OkHttp only strips
            // Authorization, not a manually-added Cookie header).
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                if (request.header("Cookie") != null &&
                    !request.url.host.endsWith("bilibili.com", ignoreCase = true)
                ) {
                    chain.proceed(request.newBuilder().removeHeader("Cookie").build())
                } else {
                    chain.proceed(request)
                }
            }
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
 *
 * ## 缓存 / 节流 / 并发合并（v3）
 *
 * 只读 GET 请求统一经过 [CachedRequestExecutor]：
 * 1. 先查 [HttpCacheStore]（TTL 见 [BiliCacheTtl]），**命中直接返回，不再发请求**；
 * 2. 未命中时用 [InFlightDeduplicator] 合并相同 key 的并发请求（只发一次）；
 * 3. 真正发请求前经 [RequestThrottle] 节流（350ms ± 100ms 抖动，全局串行）；
 * 4. 仅 `code == 0` 的成功结果写缓存；网络异常 / 非 2xx / `code != 0` 一律不缓存。
 *
 * 默认复用进程级共享实例（[BiliCacheRegistry]），因此多个仓储共用同一份缓存，
 * `BiliNetworkCache.clear()` 能清掉这里写入的所有条目。
 * 构造参数均可显式注入（测试 / 单实例隔离用）。
 *
 * ## 登录态（v5）
 *
 * 未显式传入 [authProvider] 时默认使用进程级 [AuthRegistry]；登录后请求会自动附带
 * 登录 Cookie（`SESSDATA` / `bili_jct` / `DedeUserID` 等），并与 buvid3 合并成一个 `Cookie` 头。
 * **登录态下的 GET 不写接口缓存**：这些响应是按账号个性化的，落盘既可能串号，
 * 也会在收藏/稍后再看等写操作后读到过期数据；仅保留并发合并与节流。
 * 缓存 key 仍附带登录身份标签（`u:<mid>` / Cookie 短 hash / `anon`），未登录缓存
 * 永远不会被登录态复用（反向亦然）。
 */
class BiliApi(
    context: Context,
    private val client: OkHttpClient = BiliHttpClient.instance,
    private val json: Json = BiliJson,
    private val baseUrl: String = BASE_URL,
    cache: HttpCacheStore? = null,
    private val throttle: RequestThrottle = BiliCacheRegistry.sharedThrottle(),
    deduplicator: InFlightDeduplicator = BiliCacheRegistry.sharedDeduplicator(),
    private val authProvider: AuthCookieProvider? = null,
) {

    private val appContext = context.applicationContext

    /**
     * 实际使用的 Cookie 来源：显式注入优先，否则回退进程级 [AuthRegistry]。
     * 这样即使调用方只写 `BiliApi(context)`，登录后也会自动带上 Cookie。
     */
    private val effectiveAuth: AuthCookieProvider = authProvider ?: AuthRegistry.shared(appContext)

    private val buvidFile = File(appContext.filesDir, "buvid3.txt")
    private val buvidMutex = Mutex()

    /** 接口缓存（默认进程级共享：与 [com.bbplayer.android.data.cache.BiliNetworkCache] 同一份）。 */
    private val cacheStore: HttpCacheStore = cache ?: BiliCacheRegistry.shared(appContext)
    private val requests = CachedRequestExecutor(
        cache = cacheStore,
        throttle = throttle,
        deduplicator = deduplicator,
    )

    /** 供同模块仓储共享缓存实例（HttpFetcher 等），不对外暴露。 */
    internal val httpCache: HttpCacheStore get() = cacheStore

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
        // buvid3 接口同样属于 api.bilibili.com，纳入全局节流
        throttle.acquire()
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
     * 只读接口自动走「缓存 → 并发合并 → 节流 → 请求」：
     * 命中缓存不产生任何网络请求；`code != 0` / 非 2xx / 网络异常不写缓存。
     *
     * [messageFor] 允许调用方按业务场景覆盖错误码文案（如评论/字幕），
     * 默认使用通用的 [apiMessage]。
     */
    /** True while the risk-control cool-down is active. */
    fun isBlocked(): Boolean = SecurityCooldown.remainingMs() > 0L

    suspend fun getRaw(
        path: String,
        params: Map<String, String> = emptyMap(),
        messageFor: (Long, String?) -> String = ::apiMessage,
    ): JsonElement = withContext(Dispatchers.IO) {
        SecurityCooldown.remainingMs().takeIf { it > 0L }?.let { remaining ->
            throw AppException("请求被B站安全策略限制，请约 ${remaining / 1000} 秒后重试", retryable = true)
        }
        // 登录态响应按账号个性化，不落盘（避免串号 + 写操作后读到旧数据）。
        val cacheable = !isAuthenticated()
        requests.run(
            // 身份进入 key：登录 / 未登录 / 不同账号的缓存互不复用。
            key = CacheKey.of(path, params, identity = authIdentity()),
            ttlMs = if (cacheable) BiliCacheTtl.forBiliPath(path) else null,
            decode = { raw -> runCatching { json.parseToJsonElement(raw) }.getOrNull() },
            encode = { it.toString() },
        ) {
            fetchRaw(path, params, messageFor)
        }
    }

    /**
     * 表单 POST（写操作：收藏 / 稍后再看 / 关注等）。
     *
     * 与 [getRaw] 不同，**永不读写缓存**，也**不参与并发合并**（写操作不可合并），
     * 只保留全局节流；登录 Cookie 与 GET 一致自动附带。
     * `csrf` 默认由调用方放进表单；传 `withCsrf = true` 时从 `bili_jct` 自动注入。
     */
    suspend fun postForm(
        path: String,
        form: Map<String, String>,
        messageFor: (Long, String?) -> String = ::apiMessage,
        withCsrf: Boolean = false,
    ): JsonObject = withContext(Dispatchers.IO) {
        SecurityCooldown.remainingMs().takeIf { it > 0L }?.let { remaining ->
            throw AppException("请求被B站安全策略限制，请约 ${remaining / 1000} 秒后重试", retryable = true)
        }
        throttle.acquire()
        val effectiveForm = if (withCsrf) {
            val csrf = effectiveAuth.csrfToken()?.takeIf { it.isNotBlank() }
                ?: throw AppException("账号未登录，请先登录", retryable = false)
            form + ("csrf" to csrf)
        } else {
            form
        }
        val body = FormBody.Builder().apply {
            effectiveForm.forEach { (key, value) -> add(key, value) }
        }.build()
        val builder = Request.Builder().url((baseUrl + path).toHttpUrl()).post(body)
        cookieHeader()?.let { builder.header("Cookie", it) }
        executeJson(builder.build(), messageFor) as? JsonObject ?: JsonObject(emptyMap())
    }

    /** True 表示当前带登录 Cookie 请求（个性化响应，不走持久化缓存）。 */
    private fun isAuthenticated(): Boolean = runCatching {
        effectiveAuth.isLoggedIn() && !effectiveAuth.cookieHeader().isNullOrBlank()
    }.getOrDefault(false)

    /** 当前是否已登录（只读，不触发网络）。 */
    fun isLoggedIn(): Boolean = effectiveAuth.isLoggedIn()

    /** `bili_jct`：调用方自拼 POST 表单时可显式注入 `csrf`。 */
    internal fun csrfToken(): String? = effectiveAuth.csrfToken()

    /** 当前缓存身份标签（`u:<mid>` / Cookie 短 hash / `anon`，不含 Cookie 明文）。 */
    internal fun authIdentity(): String = cacheIdentityFor(effectiveAuth)

    /** 合并 buvid3 与登录 Cookie；没有登录态时只带 buvid3（与 v4 行为一致）。 */
    private suspend fun cookieHeader(): String? {
        val buvid = runCatching { ensureBuvid() }.getOrNull()?.takeIf { it.isNotBlank() }
        val authCookie = runCatching { effectiveAuth.cookieHeader() }.getOrNull()?.trim().orEmpty()
        return when {
            authCookie.isBlank() -> buvid?.let { "buvid3=$it" }
            authCookie.contains("buvid3=") -> authCookie
            buvid == null -> authCookie
            else -> "buvid3=$buvid; $authCookie"
        }
    }

    /** 真正发请求（只在缓存未命中时被调用）；成功才可能被写入缓存。 */
    private suspend fun fetchRaw(
        path: String,
        params: Map<String, String>,
        messageFor: (Long, String?) -> String,
    ): JsonElement {
        val urlBuilder = (baseUrl + path).toHttpUrl().newBuilder()
        params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
        cookieHeader()?.let { requestBuilder.header("Cookie", it) }
        return executeJson(requestBuilder.build(), messageFor)
    }

    /** 统一执行请求：校验 2xx / `code`，返回 `data` 节点；错误映射成 [AppException]。 */
    private fun executeJson(
        request: Request,
        messageFor: (Long, String?) -> String,
    ): JsonElement {
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
                if (resp.code == 412 || resp.code == 429) SecurityCooldown.markBlocked()
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
            if (code == -412L) SecurityCooldown.markBlocked()
            if (code != 0L) {
                throw AppException(
                    messageFor(code, root.strOrNull("message")),
                    retryable = code !in NON_RETRYABLE_CODES,
                )
            }
            return root.get("data") ?: JsonObject(emptyMap())
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

    /** 跟随 b23.tv 短链重定向，返回最终 URL 与落地页 HTML（24h 缓存，同 URL 只解析一次）。 */
    internal suspend fun resolveShortLink(url: String): ShortLinkResolution? = withContext(Dispatchers.IO) {
        val normalized = InputResolver.normalizeUrl(url)
        requests.run(
            key = CacheKey.ofUrl("b23", normalized),
            ttlMs = BiliCacheTtl.SHORT_LINK,
            decode = ::decodeShortLink,
            encode = ::encodeShortLink,
        ) {
            fetchShortLink(normalized)
        }
    }

    private fun fetchShortLink(normalized: String): ShortLinkResolution {
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
            return ShortLinkResolution(finalUrl, html)
        }
    }

    private fun decodeShortLink(raw: String): ShortLinkResolution? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val finalUrl = root.strOrNull("finalUrl") ?: return null
        ShortLinkResolution(finalUrl = finalUrl, html = root.strOrNull("html").orEmpty())
    }.getOrNull()

    private fun encodeShortLink(value: ShortLinkResolution): String = buildJsonObject {
        put("finalUrl", value.finalUrl)
        put("html", value.html)
    }.toString()

    companion object {
        const val BASE_URL = "https://api.bilibili.com"

        private const val MAX_SHORT_LINK_BODY = 256L * 1024

        /** 参数错误 / 无权限 / 不存在：重试没有意义。 */
        private val NON_RETRYABLE_CODES = setOf(-400L, -403L, -404L)
    }
}

/**
 * 缓存身份标签（不含 Cookie 明文）：登录身份 `u:<mid>`，无 mid 时用 Cookie 短 hash，
 * 未登录固定 `anon`。保证登录/未登录、不同账号的接口缓存互不复用。
 */
internal fun cacheIdentityFor(provider: AuthCookieProvider): String {
    (provider as? AuthRegistry)?.let { return it.identityTag() }
    provider.cookieValue("DedeUserID")?.takeIf { it.isNotBlank() }?.let { return "u:$it" }
    provider.cookieHeader()?.takeIf { it.isNotBlank() }?.let { return "h:" + sha256Hex(it).take(12) }
    return AuthRegistry.ANONYMOUS
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
