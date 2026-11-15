package com.bbplayer.android.data.auth

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.model.QrPollState
import com.bbplayer.android.data.BiliHttpClient
import com.bbplayer.android.data.BiliJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [BiliAuthRepository] 纯 JVM 单测：OkHttp 拦截器伪造 passport / nav 响应，
 * 加密存储用内存 fake，不联网、不碰 AndroidKeyStore。
 */
class BiliAuthRepositoryTest {

    // ------------------------------------------------------------ 假响应

    private companion object {
        const val QR_GENERATE =
            """{"code":0,"data":{"url":"https://passport.bilibili.com/h5-app/passport/login/scan?navhide=1&qrcode_key=KEY123","qrcode_key":"KEY123"}}"""

        const val NAV_OK =
            """{"code":0,"message":"0","data":{"isLogin":true,"mid":42,"uname":"tester","face":"//i0.hdslb.com/bfs/face/a.jpg","level_info":{"current_level":6},"vipStatus":1,"sign":"hello"}}"""

        const val NAV_OK_RENAMED =
            """{"code":0,"data":{"isLogin":true,"mid":42,"uname":"renamed","face":"","level_info":{"current_level":3},"vipStatus":0}}"""

        const val NAV_NOT_LOGIN =
            """{"code":-101,"message":"账号未登录","data":{"isLogin":false}}"""

        const val POLL_SUCCESS =
            """{"code":0,"message":"0","data":{"url":"https://passport.biligame.com/crossDomain?DedeUserID=42&DedeUserID__ckMd5=ckmd5-url&Expires=1700000000&SESSDATA=sess%2Cdata%2C123&bili_jct=jct123&gourl=https%3A%2F%2Fwww.bilibili.com&sid=sid-url","refresh_token":"r","timestamp":1,"code":0,"message":""}}"""

        val POLL_SET_COOKIES = listOf(
            "SESSDATA=sess-cookie; Path=/; Domain=.biligame.com; HttpOnly",
            "bili_jct=jct-cookie; Path=/",
            "DedeUserID=42; Path=/",
            "DedeUserID__ckMd5=ckmd5-cookie; Path=/",
            "sid=sid-cookie; Path=/; HttpOnly",
        )

        const val POLL_WAITING =
            """{"code":0,"data":{"url":"","code":86101,"message":"未扫码"}}"""
        const val POLL_SCANNED =
            """{"code":0,"data":{"url":"","code":86090,"message":"已扫码"}}"""
        const val POLL_EXPIRED =
            """{"code":0,"data":{"url":"","code":86038,"message":"二维码已过期"}}"""
        const val POLL_UNKNOWN =
            """{"code":0,"data":{"url":"","code":12345,"message":"未知状态"}}"""
    }

    private fun newRepository(
        server: FakeBiliServer,
        store: SecureCookieStore = memoryCookieStore(),
        registry: AuthRegistry = AuthRegistry(),
        now: () -> Long = { 1_000_000L },
    ): BiliAuthRepository = BiliAuthRepository(
        store = store,
        client = server.client,
        auth = registry,
        json = BiliJson,
        scope = null,
        nowMs = now,
    )

    // ------------------------------------------------------------ 二维码生成

    @Test
    fun `生成二维码解析 url key 与 180 秒有效期`() = runTest {
        val server = FakeBiliServer { biliJsonResponse(QR_GENERATE) }
        val repo = newRepository(server, now = { 5_000L })

        val info = repo.generateQrCode().getOrThrow()

        assertEquals("KEY123", info.qrKey)
        assertTrue(info.url.contains("qrcode_key=KEY123"))
        assertEquals(5_000L + 180_000L, info.expiresAtMs)
    }

    @Test
    fun `生成二维码使用桌面 UA 与 Referer`() = runTest {
        val server = FakeBiliServer { biliJsonResponse(QR_GENERATE) }
        newRepository(server).generateQrCode().getOrThrow()

        val request = server.lastRequestTo("/x/passport-login/web/qrcode/generate")!!
        assertEquals("GET", request.method)
        assertEquals(BiliHttpClient.DESKTOP_UA, request.header("User-Agent"))
        assertEquals("https://www.bilibili.com/", request.header("Referer"))
    }

    @Test
    fun `生成二维码业务错误返回 failure`() = runTest {
        val server = FakeBiliServer { biliJsonResponse("""{"code":-412,"message":"被拦截"}""") }
        val error = newRepository(server).generateQrCode().exceptionOrNull()
        assertTrue(error is AppException)
        assertEquals("请求被B站安全策略拦截，请稍后重试", (error as AppException).userMessage)
    }

    // ------------------------------------------------------------ 轮询状态

    @Test
    fun `轮询 86101 86090 86038 映射`() = runTest {
        val cases = listOf(
            POLL_WAITING to QrPollState.WAITING,
            POLL_SCANNED to QrPollState.SCANNED,
            POLL_EXPIRED to QrPollState.EXPIRED,
        )
        for ((body, expected) in cases) {
            val server = FakeBiliServer { biliJsonResponse(body) }
            val state = newRepository(server).pollQrCode("KEY123").getOrThrow().state
            assertEquals(expected, state)
        }
    }

    @Test
    fun `轮询未知状态映射为 ERROR`() = runTest {
        val server = FakeBiliServer { biliJsonResponse(POLL_UNKNOWN) }
        val result = newRepository(server).pollQrCode("KEY123").getOrThrow()
        assertEquals(QrPollState.ERROR, result.state)
        assertEquals("未知状态", result.message)
    }

    @Test
    fun `轮询请求携带 qrcode_key 且不携带登录 Cookie`() = runTest {
        val server = FakeBiliServer { biliJsonResponse(POLL_WAITING) }
        newRepository(server).pollQrCode("key with space")

        val request = server.lastRequestTo("/x/passport-login/web/qrcode/poll")!!
        assertEquals("key with space", request.url.queryParameter("qrcode_key"))
        assertNull("未登录时不应带 Cookie 头", request.header("Cookie"))
    }

    // ------------------------------------------------------------ 扫码成功

    @Test
    fun `扫码成功从 URL 与 Set-Cookie 提取凭据并校验 nav`() = runTest {
        val store = memoryCookieStore()
        val registry = AuthRegistry()
        val server = FakeBiliServer { request ->
            when (request.url.encodedPath) {
                "/x/passport-login/web/qrcode/poll" ->
                    biliJsonResponse(POLL_SUCCESS, setCookies = POLL_SET_COOKIES)
                "/x/web-interface/nav" -> biliJsonResponse(NAV_OK)
                else -> biliJsonResponse("""{"code":-404}""", statusCode = 404)
            }
        }
        val repo = newRepository(server, store, registry)

        val result = repo.pollQrCode("KEY123").getOrThrow()

        assertEquals(QrPollState.SUCCESS, result.state)
        val user = result.user!!
        assertEquals(42L, user.mid)
        assertEquals("tester", user.name)
        assertEquals("https://i0.hdslb.com/bfs/face/a.jpg", user.avatarUrl)
        assertEquals(6, user.level)
        assertTrue(user.vip)
        assertEquals("hello", user.sign)

        val saved = store.load()
        assertEquals("sess-cookie", saved["SESSDATA"])
        assertEquals("jct-cookie", saved["bili_jct"])
        assertEquals("42", saved["DedeUserID"])
        assertEquals("ckmd5-cookie", saved["DedeUserID__ckMd5"])
        assertEquals("sid-cookie", saved["sid"])
        assertTrue(registry.isLoggedIn())
        assertEquals("jct-cookie", registry.csrfToken())
        assertEquals(42L, repo.session().first()!!.mid)

        val nav = server.lastRequestTo("/x/web-interface/nav")!!
        val cookie = nav.header("Cookie").orEmpty()
        assertTrue(cookie.contains("SESSDATA=sess-cookie"))
        assertTrue(cookie.contains("bili_jct=jct-cookie"))
        assertTrue(cookie.contains("DedeUserID=42"))
    }

    @Test
    fun `只有 URL 没有 Set-Cookie 时也能登录`() = runTest {
        val store = memoryCookieStore()
        val server = FakeBiliServer { request ->
            when (request.url.encodedPath) {
                "/x/passport-login/web/qrcode/poll" -> biliJsonResponse(POLL_SUCCESS)
                else -> biliJsonResponse(NAV_OK)
            }
        }
        val repo = newRepository(server, store)

        val result = repo.pollQrCode("KEY123").getOrThrow()

        assertEquals(QrPollState.SUCCESS, result.state)
        // URL 参数按 URL 语义解码；Set-Cookie 才是原样值。
        assertEquals("sess,data,123", store.load()["SESSDATA"])
    }

    @Test
    fun `扫码成功缺少 SESSDATA 返回 ERROR 且不请求 nav`() = runTest {
        val store = memoryCookieStore()
        val server = FakeBiliServer {
            biliJsonResponse("""{"code":0,"data":{"url":"https://passport.biligame.com/crossDomain?DedeUserID=42","code":0}}""")
        }
        val repo = newRepository(server, store)

        val result = repo.pollQrCode("KEY123").getOrThrow()

        assertEquals(QrPollState.ERROR, result.state)
        assertTrue(server.requestsTo("/x/web-interface/nav").isEmpty())
        assertFalse(repo.isLoggedIn())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `扫码成功后 nav 校验失败返回 ERROR 且不保存`() = runTest {
        val store = memoryCookieStore()
        val registry = AuthRegistry()
        val server = FakeBiliServer { request ->
            when (request.url.encodedPath) {
                "/x/passport-login/web/qrcode/poll" -> biliJsonResponse(POLL_SUCCESS)
                else -> biliJsonResponse(NAV_NOT_LOGIN)
            }
        }
        val repo = newRepository(server, store, registry)

        val result = repo.pollQrCode("KEY123").getOrThrow()

        assertEquals(QrPollState.ERROR, result.state)
        assertFalse(registry.isLoggedIn())
        assertTrue(store.load().isEmpty())
        assertNull(repo.session().first())
    }

    // ------------------------------------------------------------ Cookie 登录

    @Test
    fun `粘贴合法 Cookie 校验成功后加密保存`() = runTest {
        val store = memoryCookieStore()
        val registry = AuthRegistry()
        val server = FakeBiliServer { biliJsonResponse(NAV_OK) }
        val repo = newRepository(server, store, registry)

        val user = repo.loginWithCookie(
            " SESSDATA = sess-value ; bili_jct = jct-value ; DedeUserID = 42 ",
        ).getOrThrow()

        assertEquals(42L, user.mid)
        assertEquals("tester", user.name)
        assertEquals("sess-value", store.load()["SESSDATA"])
        assertEquals("jct-value", store.load()["bili_jct"])
        assertTrue(registry.isLoggedIn())
        val nav = server.lastRequestTo("/x/web-interface/nav")!!
        assertTrue(nav.header("Cookie").orEmpty().contains("SESSDATA=sess-value"))
    }

    @Test
    fun `非法 Cookie 返回明确错误且不保存`() = runTest {
        val store = memoryCookieStore()
        val registry = AuthRegistry()
        val server = FakeBiliServer { biliJsonResponse(NAV_NOT_LOGIN) }
        val repo = newRepository(server, store, registry)

        val error = repo.loginWithCookie("SESSDATA=expired").exceptionOrNull()

        assertTrue(error is AppException)
        assertEquals("Cookie 无效或已过期", (error as AppException).userMessage)
        assertFalse(registry.isLoggedIn())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `缺少 SESSDATA 直接失败且不发请求`() = runTest {
        val server = FakeBiliServer { error("不应发起请求") }
        val repo = newRepository(server)

        val error = repo.loginWithCookie("bili_jct=only-csrf").exceptionOrNull()

        assertTrue(error is AppException)
        assertTrue((error as AppException).userMessage.contains("SESSDATA"))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `粘贴的 Cookie 只保留登录键`() = runTest {
        val store = memoryCookieStore()
        val server = FakeBiliServer { biliJsonResponse(NAV_OK) }
        val repo = newRepository(server, store)

        repo.loginWithCookie(
            "SESSDATA=s; bili_jct=j; DedeUserID=42; buvid3=drop; Path=/; foo=bar",
        ).getOrThrow()

        val saved = store.load()
        assertFalse(saved.containsKey("buvid3"))
        assertFalse(saved.containsKey("foo"))
        assertFalse(saved.containsKey("Path"))
    }

    @Test
    fun `nav 解析 vipStatus 0 与缺失等级`() = runTest {
        val server = FakeBiliServer { biliJsonResponse(NAV_OK_RENAMED) }
        val repo = newRepository(server)

        val user = repo.loginWithCookie("SESSDATA=s").getOrThrow()

        assertEquals("renamed", user.name)
        assertEquals(3, user.level)
        assertFalse(user.vip)
        assertEquals("", user.avatarUrl)
        assertEquals("", user.sign)
    }

    // ------------------------------------------------------------ 刷新 / 登出 / 恢复

    @Test
    fun `refreshUser 在登录态下刷新资料`() = runTest {
        val store = memoryCookieStore()
        val registry = AuthRegistry()
        val cookies = mapOf("SESSDATA" to "s", "DedeUserID" to "42")
        store.save(cookies)
        registry.update(cookies)
        val server = FakeBiliServer { biliJsonResponse(NAV_OK_RENAMED) }
        val repo = newRepository(server, store, registry)

        val user = repo.refreshUser().getOrThrow()

        assertEquals("renamed", user!!.name)
        assertEquals(42L, repo.session().first()!!.mid)
    }

    @Test
    fun `refreshUser 校验失效时自动登出`() = runTest {
        val store = memoryCookieStore()
        val registry = AuthRegistry()
        val cookies = mapOf("SESSDATA" to "s", "DedeUserID" to "42")
        store.save(cookies)
        registry.update(cookies)
        val server = FakeBiliServer { biliJsonResponse(NAV_NOT_LOGIN) }
        val repo = newRepository(server, store, registry)

        val user = repo.refreshUser().getOrThrow()

        assertNull(user)
        assertFalse(registry.isLoggedIn())
        assertTrue(store.load().isEmpty())
        assertNull(repo.session().first())
    }

    @Test
    fun `refreshUser 未登录时直接返回 null 不发请求`() = runTest {
        val server = FakeBiliServer { error("不应发起请求") }
        val repo = newRepository(server)

        assertNull(repo.refreshUser().getOrThrow())
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `logout 清空内存 加密存储与会话`() = runTest {
        val store = memoryCookieStore()
        val registry = AuthRegistry()
        val cookies = mapOf("SESSDATA" to "s", "DedeUserID" to "42")
        store.save(cookies)
        registry.update(cookies)
        val server = FakeBiliServer { biliJsonResponse(NAV_OK) }
        val repo = newRepository(server, store, registry)
        repo.loginWithCookie("SESSDATA=s; DedeUserID=42").getOrThrow()

        repo.logout()

        assertFalse(registry.isLoggedIn())
        assertNull(registry.cookieHeader())
        assertTrue(store.load().isEmpty())
        assertNull(repo.session().first())
    }

    @Test
    fun `启动恢复有效 Cookie 进入登录态`() = runTest {
        val store = memoryCookieStore()
        val cookies = mapOf("SESSDATA" to "s", "DedeUserID" to "42")
        store.save(cookies)
        val registry = AuthRegistry()
        val server = FakeBiliServer { biliJsonResponse(NAV_OK) }
        val repo = newRepository(server, store, registry)

        repo.restoreSession()

        assertEquals(42L, repo.session().first()!!.mid)
        assertTrue(registry.isLoggedIn())
        assertNotNull(server.lastRequestTo("/x/web-interface/nav"))
    }

    @Test
    fun `启动恢复无效 Cookie 自动登出并清除密文`() = runTest {
        val store = memoryCookieStore()
        store.save(mapOf("SESSDATA" to "expired", "DedeUserID" to "42"))
        val registry = AuthRegistry()
        val server = FakeBiliServer { biliJsonResponse(NAV_NOT_LOGIN) }
        val repo = newRepository(server, store, registry)

        repo.restoreSession()

        assertNull(repo.session().first())
        assertFalse(registry.isLoggedIn())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `启动恢复网络异常时保留密文但不进入登录态`() = runTest {
        val store = memoryCookieStore()
        val cookies = mapOf("SESSDATA" to "s", "DedeUserID" to "42")
        store.save(cookies)
        val registry = AuthRegistry()
        val server = FakeBiliServer { throw IOException("offline") }
        val repo = newRepository(server, store, registry)

        repo.restoreSession()

        assertNull(repo.session().first())
        assertFalse(registry.isLoggedIn())
        assertEquals(cookies, store.load())
    }

    @Test
    fun `启动时没有已存 Cookie 不发起网络请求`() = runTest {
        val server = FakeBiliServer { error("不应发起请求") }
        val repo = newRepository(server)

        repo.restoreSession()

        assertTrue(server.requests.isEmpty())
        assertNull(repo.session().first())
    }

    @Test
    fun `nav 接口返回 HTTP 500 时 Cookie 登录失败但不崩溃`() = runTest {
        val server = FakeBiliServer { biliJsonResponse("{}", statusCode = 500) }
        val repo = newRepository(server)

        val error = repo.loginWithCookie("SESSDATA=s").exceptionOrNull()

        assertTrue(error is AppException)
        assertTrue((error as AppException).retryable)
    }
}
