package com.bbplayer.android.data.cache

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 缓存 / 去重 key 的规范化工具。
 *
 * 同一个接口 + 语义相同的参数必须得到同一个 key，否则缓存永远不命中：
 * - 参数按 key 排序（调用方传 Map 的顺序不影响结果）；
 * - key / value 统一 URL 编码（中文、空格等差异被归一）；
 * - `buvid3` / `buvid4` / `cookie` / `csrf` 等身份参数不进 key（每次启动都会变，
 *   且与内容无关，写进 key 会让缓存永久失效）。
 */
internal object CacheKey {

    private val IGNORED_PARAMS = setOf("buvid3", "buvid4", "cookie", "csrf", "csrf_token")

    /** `endpoint + 规范化查询串`，例如 `/x/web-interface/view?bvid=BV1xx`。 */
    fun of(path: String, params: Map<String, String>): String {
        val query = params.entries
            .filter { it.key.isNotBlank() && it.key.lowercase() !in IGNORED_PARAMS }
            .sortedBy { it.key }
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return if (query.isEmpty()) path else "$path?$query"
    }

    /** 完整 URL 场景（外站接口 / 短链），`scheme` 用于区分请求类型。 */
    fun ofUrl(scheme: String, url: String): String = "$scheme:$url"

    /** 磁盘文件名：`<sha256(key)>.json`，避免特殊字符与超长文件名。 */
    fun fileName(key: String): String = sha256Hex(key) + ".json"

    private fun encode(raw: String): String = URLEncoder.encode(raw, "UTF-8")
}

/** SHA-256 小写十六进制摘要。 */
internal fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    val builder = StringBuilder(digest.size * 2)
    for (byte in digest) {
        builder.append(HEX[(byte.toInt() shr 4) and 0x0f])
        builder.append(HEX[byte.toInt() and 0x0f])
    }
    return builder.toString()
}

private const val HEX = "0123456789abcdef"
