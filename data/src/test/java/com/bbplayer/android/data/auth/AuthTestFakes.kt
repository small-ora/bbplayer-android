package com.bbplayer.android.data.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 登录层单测的纯 JVM 假件：不依赖 AndroidKeyStore / SharedPreferences / 真实网络。
 */

/** 可注入的假加密：`12 字节前缀 + 反转明文`，可在加密/解密时模拟 Keystore 失效。 */
internal class FakeCookieCipher(
    private val failEncrypt: Boolean = false,
    private val failDecrypt: Boolean = false,
) : CookieCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        if (failEncrypt) throw IllegalStateException("keystore unavailable")
        return PREFIX + plaintext.reversedArray()
    }

    override fun decrypt(payload: ByteArray): ByteArray {
        if (failDecrypt) throw IllegalStateException("key invalidated")
        require(payload.size >= PREFIX.size) { "bad payload" }
        return payload.copyOfRange(PREFIX.size, payload.size).reversedArray()
    }

    var resets = 0
        private set

    override fun reset() {
        resets++
    }

    private companion object {
        val PREFIX = ByteArray(12) { 7 }
    }
}

/** 内存版加密串存储；可模拟读/写失败。 */
internal class MemorySecureStorage(
    var value: String? = null,
    private val failRead: Boolean = false,
    private val failWrite: Boolean = false,
) : SecureStorage {

    var writes = 0
        private set
    var erases = 0
        private set

    override fun read(): String? {
        if (failRead) throw IllegalStateException("prefs unavailable")
        return value
    }

    override fun write(value: String) {
        if (failWrite) throw IllegalStateException("disk full")
        this.value = value
        writes++
    }

    override fun erase() {
        value = null
        erases++
    }
}

internal fun memoryCookieStore(
    storage: MemorySecureStorage = MemorySecureStorage(),
    cipher: CookieCipher = FakeCookieCipher(),
): SecureCookieStore = SecureCookieStore(storage, cipher)

/** 记录请求并用 [handler] 伪造响应的 OkHttp 客户端（纯 JVM）。 */
internal class FakeBiliServer(private val handler: (Request) -> Response) {

    private val recorded = mutableListOf<Request>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            synchronized(recorded) { recorded += request }
            handler(request).newBuilder().request(request).build()
        }
        .build()

    val requests: List<Request> get() = synchronized(recorded) { recorded.toList() }

    fun requestsTo(path: String): List<Request> =
        requests.filter { it.url.encodedPath == path }

    fun lastRequestTo(path: String): Request? = requestsTo(path).lastOrNull()
}

internal fun biliJsonResponse(
    body: String,
    statusCode: Int = 200,
    setCookies: List<String> = emptyList(),
): Response {
    val builder = Response.Builder()
        .request(Request.Builder().url("https://api.bilibili.com/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message(if (statusCode == 200) "OK" else "ERR")
        .body(body.toResponseBody("application/json".toMediaType()))
    setCookies.forEach { builder.addHeader("Set-Cookie", it) }
    return builder.build()
}
