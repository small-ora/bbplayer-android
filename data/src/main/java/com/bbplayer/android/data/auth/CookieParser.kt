package com.bbplayer.android.data.auth

import java.net.URLDecoder

/**
 * B 站登录 Cookie 的纯函数解析工具（无 Android / 网络依赖，可单测）。
 *
 * 只保留登录必需的 [AUTH_COOKIE_NAMES]：`SESSDATA` / `bili_jct` / `DedeUserID` /
 * `DedeUserID__ckMd5` / `sid`。其余 Cookie（`buvid3` 由 [com.bbplayer.android.data.BiliApi]
 * 自行管理，`Path` / `Domain` 等属性也不是凭据）一律丢弃，避免把无关数据写入加密存储。
 *
 * 任何日志都不应打印返回值（凭据红线）。
 */
internal object CookieParser {

    /** 登录态需要的 Cookie 名（大小写敏感，B 站使用固定写法）。 */
    val AUTH_COOKIE_NAMES: Set<String> = linkedSetOf(
        "SESSDATA",
        "bili_jct",
        "DedeUserID",
        "DedeUserID__ckMd5",
        "sid",
    )

    /** `Set-Cookie` 里常见但不是 Cookie 本身的属性名（手动粘贴时可能混入）。 */
    private val ATTRIBUTE_NAMES = setOf(
        "path", "domain", "expires", "max-age", "httponly", "secure", "samesite", "priority",
    )

    /**
     * 解析手动粘贴的 `k=v; k=v` 串（也容忍换行分隔、多余空格、值中出现 `=`）。
     *
     * 重复键以**最后一次**出现的值为准；缺少 `=` / 空 key / Cookie 属性一律跳过。
     * 只返回 [AUTH_COOKIE_NAMES] 中的键。
     */
    fun parse(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val text = raw.trim().let { trimmed ->
            if (trimmed.startsWith("Cookie:", ignoreCase = true)) {
                trimmed.substringAfter(':')
            } else {
                trimmed
            }
        }
        val result = LinkedHashMap<String, String>()
        text.split(';', '\n', '\r').forEach { part ->
            val item = part.trim()
            if (item.isEmpty()) return@forEach
            val separator = item.indexOf('=')
            if (separator <= 0) return@forEach
            val name = item.substring(0, separator).trim()
            val value = item.substring(separator + 1).trim()
            if (name.isEmpty() || name.lowercase() in ATTRIBUTE_NAMES) return@forEach
            if (name in AUTH_COOKIE_NAMES) result[name] = value
        }
        return result
    }

    /**
     * 从一次 HTTP 响应的多个 `Set-Cookie` 头中提取登录 Cookie。
     *
     * 每个头只取第一个 `;` 之前的部分（`SESSDATA=...; Path=/; HttpOnly`），
     * 同名 Cookie 以最后一个响应头为准。
     */
    fun fromSetCookie(headers: List<String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        headers.forEach { header ->
            val pair = header.substringBefore(';').trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            if (name in AUTH_COOKIE_NAMES) result[name] = value
        }
        return result
    }

    /**
     * 从扫码成功回调的 `data.url` 查询串中提取登录 Cookie。
     *
     * 形如 `https://passport.biligame.com/crossDomain?DedeUserID=42&SESSDATA=xxx%2Cyyy&...`。
     * `+` 按字面量处理（URL 解码默认会把它当空格，Cookie 值里不应被改写）。
     */
    fun fromUrl(url: String): Map<String, String> {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val name = decode(pair.substring(0, separator))
            if (name !in AUTH_COOKIE_NAMES) return@forEach
            result[name] = decode(pair.substring(separator + 1))
        }
        return result
    }

    /** 多个来源合并（后者覆盖前者）；保持 [AUTH_COOKIE_NAMES] 的稳定顺序。 */
    fun merge(vararg sources: Map<String, String>): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        sources.forEach { source -> source.forEach { (name, value) -> merged[name] = value } }
        return merged.filterKeys { it in AUTH_COOKIE_NAMES }
    }

    /** 拼成请求头值（仅用于内存中的请求，不写日志）。 */
    fun toHeader(cookies: Map<String, String>): String =
        cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }.getOrDefault(value)
}
