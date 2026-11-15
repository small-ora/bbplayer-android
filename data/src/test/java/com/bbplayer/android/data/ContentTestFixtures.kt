package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AuthCookieProvider
import com.bbplayer.android.data.wbi.WbiNavSource
import com.bbplayer.android.data.wbi.WbiSigner
import kotlinx.serialization.json.JsonObject
import java.time.ZoneOffset

/**
 * 内容类仓储单测的公共假件（纯 JVM，不联网、不碰 Android）。
 */

/** 可配置登录态的 [AuthCookieProvider]。 */
internal class FakeAuth(
    private val loggedIn: Boolean = true,
    val cookie: String? = DEFAULT_COOKIE,
    val csrf: String? = "csrf-token-123",
    val mid: String? = "10086",
) : AuthCookieProvider {

    var loginChecks = 0
        private set

    override fun cookieHeader(): String? = cookie

    override fun cookieValue(name: String): String? = when (name) {
        "DedeUserID" -> mid
        "bili_jct" -> csrf
        else -> null
    }

    override fun csrfToken(): String? = csrf

    override fun isLoggedIn(): Boolean {
        loginChecks++
        return loggedIn
    }

    companion object {
        const val DEFAULT_COOKIE = "SESSDATA=sess; bili_jct=csrf-token-123; DedeUserID=10086"
    }
}

/** 记录调用的假传输层：GET / POST 分别记录路径与参数。 */
internal class FakeContentApi(
    private val onGet: suspend (String, Map<String, String>) -> JsonObject = { _, _ -> JsonObject(emptyMap()) },
    private val onPost: suspend (String, Map<String, String>) -> JsonObject = { _, _ -> JsonObject(emptyMap()) },
) : BiliContentApi {

    data class Call(val path: String, val params: Map<String, String>)

    val gets = mutableListOf<Call>()
    val posts = mutableListOf<Call>()

    override suspend fun getObject(path: String, params: Map<String, String>): JsonObject {
        gets += Call(path, params)
        return onGet(path, params)
    }

    override suspend fun postForm(path: String, form: Map<String, String>): JsonObject {
        posts += Call(path, form)
        return onPost(path, form)
    }
}

/** 按调用顺序返回 nav 响应的假 [WbiNavSource]（永远返回最后一个）。 */
internal class FakeWbiNav(private val responses: List<JsonObject?>) : WbiNavSource {

    var calls = 0
        private set

    override suspend fun fetch(): JsonObject? {
        val index = calls.coerceAtMost(responses.lastIndex.coerceAtLeast(0))
        calls++
        return responses.getOrNull(index)
    }
}

internal fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text) as JsonObject

/** 真实形状的 nav 响应（未登录 code=-101，但 data.wbi_img 仍存在）。 */
internal fun navJson(
    img: String = "7cd084941338484aae1ad9425b84077c",
    sub: String = "4932caff0ff746eab6f01bf08b70ac45",
): JsonObject = json(
    """
    {
      "code": -101,
      "message": "账号未登录",
      "data": {
        "isLogin": false,
        "wbi_img": {
          "img_url": "https://i0.hdslb.com/bfs/wbi/$img.png",
          "sub_url": "https://i0.hdslb.com/bfs/wbi/$sub.png"
        }
      }
    }
    """.trimIndent(),
)

/** 固定时钟 + UTC 的 [WbiSigner]（wts 固定为 1700000000）。 */
internal fun wbiSigner(
    vararg navResponses: JsonObject?,
    clock: () -> Long = { 1_700_000_000_000L },
): WbiSigner = WbiSigner(
    nav = FakeWbiNav(if (navResponses.isEmpty()) listOf(navJson()) else navResponses.toList()),
    clock = clock,
    zone = ZoneOffset.UTC,
)
