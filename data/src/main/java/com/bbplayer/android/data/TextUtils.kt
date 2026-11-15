package com.bbplayer.android.data

/** B 站搜索标题里的 HTML 清洗工具（`<em class="keyword">` 高亮等）。 */
internal object HtmlText {

    private val TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val NUMERIC_ENTITY_REGEX = Regex("&#(x?)([0-9a-fA-F]+);", RegexOption.IGNORE_CASE)

    private val NAMED_ENTITIES = listOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#39;" to "'",
        "&nbsp;" to " ",
    )

    /** 去标签 + 反转义 + 折叠空白。 */
    fun clean(raw: String?): String {
        val source = raw ?: return ""
        if (source.isEmpty()) return ""
        return WHITESPACE_REGEX.replace(decodeEntities(TAG_REGEX.replace(source, "")), " ").trim()
    }

    /** 解码常见 HTML 实体，`&amp;` 最后处理避免二次解码。 */
    fun decodeEntities(raw: String?): String {
        val source = raw ?: return ""
        if (source.isEmpty()) return ""
        if (!source.contains('&')) return source
        var text = source
        for ((entity, replacement) in NAMED_ENTITIES) {
            text = text.replace(entity, replacement)
        }
        text = NUMERIC_ENTITY_REGEX.replace(text) { match ->
            val radix = if (match.groupValues[1].isEmpty()) 10 else 16
            val codePoint = match.groupValues[2].toIntOrNull(radix)
            if (codePoint == null || !isValidCodePoint(codePoint)) {
                match.value
            } else {
                String(Character.toChars(codePoint))
            }
        }
        return text.replace("&amp;", "&")
    }

    private fun isValidCodePoint(codePoint: Int): Boolean =
        codePoint in 1..Character.MAX_CODE_POINT && codePoint !in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code
}

/**
 * 时长解析：
 * - 搜索结果通常是 `mm:ss`（如 `3:48`）；超过 1 小时可能出现 `hh:mm:ss`。
 * - 排行/详情接口给的是秒数（见 [secondsToMs]）。
 */
internal object DurationParser {

    private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$""")

    fun parseMs(raw: String?): Long {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return 0L
        val match = TIME_REGEX.matchEntire(text)
        if (match != null) {
            val first = match.groupValues[1].toLongOrNull() ?: return 0L
            val second = match.groupValues[2].toLongOrNull() ?: return 0L
            val third = match.groupValues[3].toLongOrNull()
            return if (third == null) {
                (first * 60 + second) * 1000L
            } else {
                (first * 3600 + second * 60 + third) * 1000L
            }
        }
        // 纯数字按秒处理，负值视为非法。
        val seconds = text.toLongOrNull() ?: return 0L
        return if (seconds >= 0) seconds * 1000L else 0L
    }

    fun secondsToMs(seconds: Long?): Long = (seconds ?: 0L).coerceAtLeast(0L) * 1000L
}
