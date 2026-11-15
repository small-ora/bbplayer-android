package com.bbplayer.android.data.wbi

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.data.HttpFetcher
import com.bbplayer.android.data.HttpHeaders
import com.bbplayer.android.data.objOrNull
import com.bbplayer.android.data.strOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** `nav` 响应里的签名素材：两个原始 key 与派生的 mixinKey。 */
internal data class WbiKeys(
    val imgKey: String,
    val subKey: String,
    val mixinKey: String,
)

/** `nav` 数据来源；测试注入假实现即可完全离线。 */
internal fun interface WbiNavSource {
    suspend fun fetch(): JsonObject?
}

/** 生产实现：GET `/x/web-interface/nav`，**不校验 code**（匿名时 -101 也有 wbi_img）。 */
internal class HttpWbiNavSource : WbiNavSource {

    private val http = HttpFetcher()

    override suspend fun fetch(): JsonObject? = runCatching {
        http.getJsonObject(WbiSigner.NAV_URL, HttpHeaders.BILIBILI)
    }.getOrNull()
}

/**
 * B 站 WBI 签名（`w_rid` / `wts`）。
 *
 * 算法（与 bilibili-API-collect 一致）：
 * 1. `nav` 的 `data.wbi_img.img_url` / `sub_url` 取文件名去扩展名 → imgKey / subKey；
 * 2. mixinKey = 64 项置换表依次取 (imgKey+subKey) 的字符，取前 32 位；
 * 3. 参数加 `wts`（秒），按 key 升序，值去掉 `!'()*` 后逐个 URL 编码拼成 query；
 * 4. `w_rid = md5(query + mixinKey)`，返回参数 + `wts` + `w_rid`。
 *
 * key 按**本地日期**缓存：同一天只拉一次 nav，跨天（或首次）重新拉取；
 * 跨天拉取失败时退回上一次的 key（宁可签名用旧 key，也不要整页打不开）。
 */
class WbiSigner internal constructor(
    private val nav: WbiNavSource,
    private val clock: () -> Long,
    private val zone: ZoneId,
) {

    /** 生产用：真实 nav + 系统时钟 + 本地时区。 */
    constructor() : this(HttpWbiNavSource(), System::currentTimeMillis, ZoneId.systemDefault())

    @Volatile
    private var cachedKeys: WbiKeys? = null

    @Volatile
    private var cachedDay: LocalDate? = null

    private val mutex = Mutex()

    /** 给 [params] 补上 `wts` 与 `w_rid`（不修改入参）。 */
    suspend fun sign(params: Map<String, String>): Map<String, String> {
        val keys = currentKeys()
        return signWithKey(params, keys.mixinKey, clock() / 1000)
    }

    /** 当前有效 key（同一天复用，跨天重拉）。 */
    internal suspend fun currentKeys(): WbiKeys = mutex.withLock {
        val today = today()
        val existing = cachedKeys
        if (existing != null && cachedDay == today) return existing
        val fetched = nav.fetch()?.let(::parseKeys)
        if (fetched != null) {
            cachedKeys = fetched
            cachedDay = today
            return fetched
        }
        // 刷新失败：有旧 key 就继续用，没有才报错。
        existing ?: throw AppException("B站签名服务不可用，请稍后重试", retryable = true)
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    companion object {

        internal const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"

        /** bilibili-API-collect 的 64 项 mixinKey 置换表。 */
        internal val MIXIN_KEY_TABLE = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
        )

        private const val MIXIN_KEY_LENGTH = 32
        private const val HEX = "0123456789abcdef"

        /** 百分号编码用大写十六进制（与 `encodeURIComponent` / 服务端一致）。 */
        private const val HEX_UPPER = "0123456789ABCDEF"

        /** 置换表取字符，取前 32 位；key 长度不足时按可用字符截断。 */
        internal fun mixinKey(imgKey: String, subKey: String): String {
            val raw = imgKey + subKey
            if (raw.isEmpty()) return ""
            return MIXIN_KEY_TABLE
                .asList()
                .mapNotNull { raw.getOrNull(it) }
                .take(MIXIN_KEY_LENGTH)
                .joinToString("")
        }

        /** `https://i0.hdslb.com/bfs/wbi/7cd0....png` → `7cd0...`（去目录、去扩展名）。 */
        internal fun keyFromUrl(url: String?): String? {
            val text = url?.trim().orEmpty()
            if (text.isEmpty()) return null
            val fileName = text.substringBefore('?').substringBefore('#').substringAfterLast('/')
            return fileName.substringBeforeLast('.', fileName).takeIf { it.isNotBlank() }
        }

        /** 从 nav 响应解析 key；字段缺失返回 null。 */
        internal fun parseKeys(nav: JsonObject): WbiKeys? {
            val wbiImg = nav.objOrNull("data")?.objOrNull("wbi_img") ?: return null
            val imgKey = keyFromUrl(wbiImg.strOrNull("img_url", "imgUrl")) ?: return null
            val subKey = keyFromUrl(wbiImg.strOrNull("sub_url", "subUrl")) ?: return null
            return WbiKeys(imgKey, subKey, mixinKey(imgKey, subKey))
        }

        /** 参数加 wts → 排序 → 过滤 → 编码 → md5，返回新 map（含 `w_rid`）。 */
        internal fun signWithKey(
            params: Map<String, String>,
            mixinKey: String,
            wts: Long,
        ): Map<String, String> {
            val signed = params.toMutableMap()
            signed["wts"] = wts.toString()
            val query = canonicalQuery(signed)
            signed["w_rid"] = md5Hex(query + mixinKey)
            return signed
        }

        /** 按 key 升序，值去掉 `!'()*`，逐个 UTF-8 百分号编码后 `k=v&...` 拼接。 */
        internal fun canonicalQuery(params: Map<String, String>): String =
            params.entries
                .sortedBy { it.key }
                .joinToString("&") { (key, value) ->
                    "${encodeComponent(key)}=${encodeComponent(filterValue(value))}"
                }

        /** WBI 要求值里去掉这 5 个字符（否则签名对不上）。 */
        internal fun filterValue(value: String): String = value.filter { it !in "!'()*" }

        /** RFC 3986 百分号编码（空格 → `%20`，与浏览器 `encodeURIComponent` 一致）。 */
        internal fun encodeComponent(value: String): String {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val builder = StringBuilder(bytes.size)
            for (byte in bytes) {
                val code = byte.toInt() and 0xFF
                if (isUnreserved(code)) {
                    builder.append(code.toChar())
                } else {
                    builder.append('%')
                    builder.append(HEX_UPPER[code shr 4])
                    builder.append(HEX_UPPER[code and 0x0F])
                }
            }
            return builder.toString()
        }

        internal fun md5Hex(text: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            val builder = StringBuilder(digest.size * 2)
            for (byte in digest) {
                builder.append(HEX[(byte.toInt() shr 4) and 0x0F])
                builder.append(HEX[byte.toInt() and 0x0F])
            }
            return builder.toString()
        }

        private fun isUnreserved(code: Int): Boolean =
            code in 'a'.code..'z'.code ||
                code in 'A'.code..'Z'.code ||
                code in '0'.code..'9'.code ||
                code == '-'.code || code == '_'.code || code == '.'.code || code == '~'.code
    }
}
