package com.bbplayer.android.data.auth

import android.content.Context
import com.bbplayer.android.core.contracts.AuthCookieProvider
import com.bbplayer.android.data.cache.sha256Hex

/**
 * 进程级登录态注册表：登录成功后由 [BiliAuthRepository] 写入，[com.bbplayer.android.data.BiliApi]
 * 读取它把 Cookie 注入请求，同时用 [identityTag] 给接口缓存做登录身份隔离。
 *
 * 为什么需要它：各仓储每个都 `BiliApi(context)`，没有共享的“当前登录 Cookie”来源；
 * 与 [com.bbplayer.android.data.cache.BiliCacheRegistry] 一样按进程单例，保证登录/退出
 * 立刻对所有请求生效。
 *
 * 红线：本类不落盘、不打日志；只持有内存中的 Cookie。
 */
class AuthRegistry internal constructor() : AuthCookieProvider {

    private val lock = Any()

    private var cookies: Map<String, String> = emptyMap()

    /** 登录成功 / 启动恢复时写入（只保存 [CookieParser.AUTH_COOKIE_NAMES]）。 */
    internal fun update(rawCookies: Map<String, String>) {
        val filtered = rawCookies.filterKeys { it in CookieParser.AUTH_COOKIE_NAMES }
        synchronized(lock) { cookies = filtered }
    }

    /** 退出登录 / 校验失效时清空内存态（不影响加密存储，由仓储决定）。 */
    internal fun clear() {
        synchronized(lock) { cookies = emptyMap() }
    }

    internal fun snapshot(): Map<String, String> = synchronized(lock) { cookies }

    /**
     * 缓存隔离标签：登录后为 `u:<mid>`（或 Cookie 短 hash），未登录固定 `anon`。
     * 不含任何 Cookie 明文。
     */
    fun identityTag(): String {
        val current = snapshot()
        current["DedeUserID"]?.takeIf { it.isNotBlank() }?.let { return "u:$it" }
        current["SESSDATA"]?.takeIf { it.isNotBlank() }?.let { return "h:${sha256Hex(it).take(12)}" }
        return ANONYMOUS
    }

    override fun cookieHeader(): String? =
        snapshot().takeIf { it.isNotEmpty() }?.let(CookieParser::toHeader)

    override fun cookieValue(name: String): String? = snapshot()[name]

    override fun csrfToken(): String? = cookieValue("bili_jct")

    override fun isLoggedIn(): Boolean = !cookieValue("SESSDATA").isNullOrBlank()

    companion object {

        const val ANONYMOUS = "anon"

        @Volatile
        private var instance: AuthRegistry? = null

        /** 进程内共享实例（首次调用创建）。 */
        fun shared(@Suppress("UNUSED_PARAMETER") context: Context): AuthRegistry =
            instance ?: synchronized(this) {
                instance ?: AuthRegistry().also { instance = it }
            }

        /** 已创建时返回共享实例（供不想持有 Context 的组件使用）。 */
        fun sharedOrNull(): AuthRegistry? = instance
    }
}
