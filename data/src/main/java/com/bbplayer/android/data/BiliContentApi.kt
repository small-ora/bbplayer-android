package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import kotlinx.serialization.json.JsonObject

/**
 * 内容类登录接口的最小传输抽象。
 *
 * 生产实现是 [BiliApi]（[BiliApiContentApi] 适配）；测试可注入记录调用参数的假实现，
 * 因此仓储逻辑（登录校验 / 表单组装 / 响应映射 / 分页）可以完全离线单测。
 */
internal interface BiliContentApi {

    suspend fun getObject(path: String, params: Map<String, String> = emptyMap()): JsonObject

    suspend fun postForm(path: String, form: Map<String, String>): JsonObject
}

/** [BiliApi] → [BiliContentApi] 适配器（Cookie / 缓存 / 节流 / 错误映射都在 BiliApi 内）。 */
internal class BiliApiContentApi(private val api: BiliApi) : BiliContentApi {

    override suspend fun getObject(path: String, params: Map<String, String>): JsonObject =
        api.getObject(path, params)

    override suspend fun postForm(path: String, form: Map<String, String>): JsonObject =
        api.postForm(path, form)
}

/** 未登录统一文案（所有内容类接口共用）。 */
internal const val LOGIN_REQUIRED_MESSAGE = "请先登录 Bilibili 账号"

/** 已登录但缺 `bili_jct` / `DedeUserID` 时的统一文案。 */
internal const val SESSION_EXPIRED_MESSAGE = "登录状态已失效，请重新登录"

/** 需要登录的接口统一入口：未登录直接抛不可重试的 [AppException]。 */
internal fun AuthCookieProvider.requireLogin() {
    if (!isLoggedIn()) throw AppException(LOGIN_REQUIRED_MESSAGE, retryable = false)
}

/** 取当前登录账号 mid（`DedeUserID`）；缺失视为登录态失效。 */
internal fun AuthCookieProvider.requireMid(): Long =
    cookieValue("DedeUserID")?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        ?: throw AppException(SESSION_EXPIRED_MESSAGE, retryable = false)

/** 取写操作所需的 `bili_jct`；缺失说明 Cookie 不完整。 */
internal fun AuthCookieProvider.requireCsrf(): String =
    csrfToken()?.trim()?.takeIf { it.isNotBlank() }
        ?: throw AppException(SESSION_EXPIRED_MESSAGE, retryable = false)
