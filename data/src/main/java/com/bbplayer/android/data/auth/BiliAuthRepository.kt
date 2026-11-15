package com.bbplayer.android.data.auth

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthRepository
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.QrCodeInfo
import com.bbplayer.android.core.model.QrPollResult
import com.bbplayer.android.core.model.QrPollState
import com.bbplayer.android.data.BiliHttpClient
import com.bbplayer.android.data.BiliJson
import com.bbplayer.android.data.BiliMapper
import com.bbplayer.android.data.apiMessage
import com.bbplayer.android.data.booleanOrNull
import com.bbplayer.android.data.httpMessage
import com.bbplayer.android.data.intOrNull
import com.bbplayer.android.data.longOrNull
import com.bbplayer.android.data.objOrNull
import com.bbplayer.android.data.repoResult
import com.bbplayer.android.data.strOrNull
import com.bbplayer.android.data.cache.SecurityCooldown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * B 站账号登录实现（扫码主流程 + 手动 Cookie 兜底）。
 *
 * 凭据红线：
 * - Cookie 只发往 `passport.bilibili.com` / `api.bilibili.com`；
 * - 只以 [SecureCookieStore]（Keystore AES-GCM）落盘，**从不打印 Cookie**；
 * - 退出登录立即清空内存注册表与加密存储；
 * - 登录成功的 Cookie 写入 [AuthRegistry]，供 [com.bbplayer.android.data.BiliApi] 注入请求并做缓存隔离。
 *
 * 构造：生产用 `BiliAuthRepository(context, appScope)`；单测用 internal 构造注入
 * fake [OkHttpClient]（拦截器伪造响应）、[SecureCookieStore] 与 [AuthRegistry]。
 */
class BiliAuthRepository internal constructor(
    private val store: SecureCookieStore,
    private val client: OkHttpClient = BiliHttpClient.instance,
    private val auth: AuthRegistry = AuthRegistry(),
    private val json: Json = BiliJson,
    scope: CoroutineScope? = null,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val passportBase: String = PASSPORT_BASE,
    private val apiBase: String = API_BASE,
) : AuthRepository {

    private val _session = MutableStateFlow<BiliUser?>(null)

    /** 当前登录用户（只读，供 UI 直接 collect）。 */
    val sessionState: StateFlow<BiliUser?> = _session.asStateFlow()

    init {
        scope?.launch { restoreSession() }
    }

    /** 生产构造：启动时在 [scope] 里异步恢复登录态。 */
    constructor(context: Context, scope: CoroutineScope) : this(
        store = SecureCookieStore(context),
        auth = AuthRegistry.shared(context),
        scope = scope,
    )

    // ------------------------------------------------------------ 只读凭据

    override fun cookieHeader(): String? = auth.cookieHeader()

    override fun cookieValue(name: String): String? = auth.cookieValue(name)

    override fun csrfToken(): String? = auth.csrfToken()

    override fun isLoggedIn(): Boolean = auth.isLoggedIn()

    override fun session(): Flow<BiliUser?> = sessionState

    // ------------------------------------------------------------ 扫码登录

    override suspend fun generateQrCode(): Result<QrCodeInfo> = repoResult {
        val root = parseObject(get("$passportBase$QR_GENERATE_PATH").body)
        requireCodeZero(root)
        val data = root.objOrNull("data")
            ?: throw AppException("二维码生成失败，请重试", retryable = true)
        val url = data.strOrNull("url")?.takeIf { it.isNotBlank() }
            ?: throw AppException("二维码生成失败，请重试", retryable = true)
        val key = data.strOrNull("qrcode_key")?.takeIf { it.isNotBlank() }
            ?: throw AppException("二维码生成失败，请重试", retryable = true)
        QrCodeInfo(url = url, qrKey = key, expiresAtMs = nowMs() + QR_TTL_MS)
    }

    override suspend fun pollQrCode(qrKey: String): Result<QrPollResult> = repoResult {
        if (qrKey.isBlank()) throw AppException("二维码已失效，请重新生成", retryable = false)
        val encodedKey = URLEncoder.encode(qrKey, "UTF-8")
        val response = get("$passportBase$QR_POLL_PATH?qrcode_key=$encodedKey")
        val root = parseObject(response.body)
        val rootCode = root.longOrNull("code") ?: 0L
        if (rootCode != 0L) {
            return@repoResult QrPollResult(
                QrPollState.ERROR,
                apiMessage(rootCode, root.strOrNull("message")),
            )
        }
        val data = root.objOrNull("data")
            ?: throw AppException("二维码状态解析失败，请重试", retryable = true)
        val state = when (data.intOrNull("code") ?: UNKNOWN_QR_CODE) {
            QR_SUCCESS -> QrPollState.SUCCESS
            QR_WAITING -> QrPollState.WAITING
            QR_SCANNED -> QrPollState.SCANNED
            QR_EXPIRED -> QrPollState.EXPIRED
            else -> QrPollState.ERROR
        }
        if (state != QrPollState.SUCCESS) {
            return@repoResult QrPollResult(state, data.strOrNull("message").orEmpty())
        }

        val cookies = CookieParser.merge(
            CookieParser.fromUrl(data.strOrNull("url").orEmpty()),
            CookieParser.fromSetCookie(response.setCookies),
        )
        if (cookies["SESSDATA"].isNullOrBlank()) {
            return@repoResult QrPollResult(QrPollState.ERROR, "登录信息解析失败，请重试")
        }
        val user = navUserOrNull(cookies)
            ?: return@repoResult QrPollResult(QrPollState.ERROR, "登录校验失败，请重试")
        persist(cookies, user)
        QrPollResult(QrPollState.SUCCESS, "登录成功", user)
    }

    override suspend fun loginWithCookie(rawCookie: String): Result<BiliUser> = repoResult {
        val cookies = CookieParser.parse(rawCookie)
        if (cookies["SESSDATA"].isNullOrBlank()) {
            throw AppException("Cookie 中缺少 SESSDATA，请检查后重新复制", retryable = false)
        }
        val user = navUserOrNull(cookies)
            ?: throw AppException(INVALID_COOKIE_MESSAGE, retryable = false)
        persist(cookies, user)
        user
    }

    // ------------------------------------------------------------ 刷新 / 登出

    override suspend fun refreshUser(): Result<BiliUser?> = repoResult {
        val cookies = auth.snapshot()
        if (cookies["SESSDATA"].isNullOrBlank()) return@repoResult null
        val user = navUserOrNull(cookies)
        if (user == null) {
            logout()
            null
        } else {
            _session.value = user
            user
        }
    }

    override suspend fun logout() {
        auth.clear()
        withContext(Dispatchers.IO) { store.clear() }
        _session.value = null
    }

    // ------------------------------------------------------------ 启动恢复

    /**
     * 启动时载入加密 Cookie 并校验 nav。
     *
     * - nav 明确返回未登录（-101/-111 或 isLogin=false）→ 自动登出（清除本地凭据）；
     * - 网络异常 → 保留加密凭据供下次启动重试，但本次不进入登录态（不在离线时销毁凭据）。
     */
    internal suspend fun restoreSession() {
        val cookies = withContext(Dispatchers.IO) { store.load() }
        if (cookies["SESSDATA"].isNullOrBlank()) return
        auth.update(cookies)
        val user = try {
            navUserOrNull(cookies)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 离线 / 接口临时异常：不销毁密文，只清内存态。
            auth.clear()
            return
        }
        if (user == null) {
            logout()
        } else {
            _session.value = user
        }
    }

    // ------------------------------------------------------------ 内部实现

    /** 只在 nav 校验成功后调用；写入内存、加密存储与 session。 */
    private suspend fun persist(cookies: Map<String, String>, user: BiliUser) {
        auth.update(cookies)
        withContext(Dispatchers.IO) { store.save(cookies) }
        _session.value = user
    }

    /** nav 校验；明确未登录返回 null，网络 / 业务错误抛 [AppException]。 */
    private suspend fun navUserOrNull(cookies: Map<String, String>): BiliUser? {
        if (cookies["SESSDATA"].isNullOrBlank()) return null
        val root = parseObject(get("$apiBase$NAV_PATH", cookies).body)
        val code = root.longOrNull("code") ?: 0L
        if (code == CODE_NOT_LOGGED_IN || code == CODE_CHECK_FAILED) return null
        if (code != 0L) throw AppException(apiMessage(code, root.strOrNull("message")), retryable = code !in PERMANENT_CODES)
        val data = root.objOrNull("data") ?: return null
        if (data.booleanOrNull("isLogin") != true) return null
        val mid = data.longOrNull("mid")?.takeIf { it > 0L } ?: return null
        val vip = (data.longOrNull("vipStatus") ?: 0L) > 0L ||
            (data.objOrNull("vip")?.longOrNull("status") ?: 0L) > 0L
        return BiliUser(
            mid = mid,
            name = data.strOrNull("uname").orEmpty(),
            avatarUrl = BiliMapper.normalizeImage(data.strOrNull("face")),
            level = data.objOrNull("level_info")?.intOrNull("current_level") ?: 0,
            vip = vip,
            sign = data.strOrNull("sign").orEmpty(),
        )
    }

    private suspend fun get(url: String, cookies: Map<String, String>? = null): HttpResponse =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
                .header("User-Agent", BiliHttpClient.DESKTOP_UA)
                .header("Referer", BiliHttpClient.REFERER)
                .header("Origin", BiliHttpClient.ORIGIN)
                .header("Accept", "application/json, text/plain, */*")
            if (!cookies.isNullOrEmpty()) {
                builder.header("Cookie", CookieParser.toHeader(cookies))
            }
            val response = try {
                client.newCall(builder.build()).execute()
            } catch (e: IOException) {
                throw AppException(e.toUserMessage(), e, retryable = true)
            }
            response.use { resp ->
                if (resp.code == 412 || resp.code == 429) SecurityCooldown.markBlocked()
                if (!resp.isSuccessful) {
                    throw AppException(
                        httpMessage(resp.code),
                        retryable = resp.code >= 500 || resp.code == 412 || resp.code == 429,
                    )
                }
                val body = try {
                    resp.body?.string().orEmpty()
                } catch (e: IOException) {
                    throw AppException(e.toUserMessage(), e, retryable = true)
                }
                HttpResponse(body = body, setCookies = resp.headers.values("Set-Cookie"))
            }
        }

    private fun parseObject(body: String): JsonObject =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw AppException("B站返回数据解析失败，请重试", retryable = true)

    private fun requireCodeZero(root: JsonObject) {
        val code = root.longOrNull("code") ?: 0L
        if (code != 0L) throw AppException(apiMessage(code, root.strOrNull("message")))
    }

    private data class HttpResponse(val body: String, val setCookies: List<String>)

    private companion object {
        const val PASSPORT_BASE = "https://passport.bilibili.com"
        const val API_BASE = "https://api.bilibili.com"
        const val QR_GENERATE_PATH = "/x/passport-login/web/qrcode/generate"
        const val QR_POLL_PATH = "/x/passport-login/web/qrcode/poll"
        const val NAV_PATH = "/x/web-interface/nav"

        const val QR_TTL_MS = 180_000L
        const val UNKNOWN_QR_CODE = -1
        const val QR_SUCCESS = 0
        const val QR_WAITING = 86101
        const val QR_SCANNED = 86090
        const val QR_EXPIRED = 86038

        const val CODE_NOT_LOGGED_IN = -101L
        const val CODE_CHECK_FAILED = -111L
        val PERMANENT_CODES = setOf(-400L, -403L, -404L)

        const val INVALID_COOKIE_MESSAGE = "Cookie 无效或已过期"
    }
}
