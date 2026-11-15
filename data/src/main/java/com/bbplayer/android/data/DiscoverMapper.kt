package com.bbplayer.android.data

import kotlinx.serialization.json.JsonObject

/**
 * 搜索建议 / 热搜的纯函数映射（可单测，不依赖网络）。
 */
internal object DiscoverMapper {

    /** 联想词最多返回条数（AC D1 要求 ≥3 条）。 */
    const val MAX_SUGGESTIONS = 8

    /** 热搜最多返回条数。 */
    const val MAX_HOT_KEYWORDS = 10

    /**
     * `s.search.bilibili.com/main/suggest` 响应：
     * `{ code, result: { tag: [ { value, term, name } ] } }`
     */
    fun suggestions(root: JsonObject): List<String> =
        root.objOrNull("result")
            ?.arrOrNull("tag")
            .toObjectList()
            .mapNotNull { tag ->
                val raw = firstNotBlank(tag, "value", "term", "name")
                HtmlText.clean(raw).takeIf { it.isNotBlank() }
            }
            .distinct()
            .take(MAX_SUGGESTIONS)

    /**
     * `x/web-interface/search/square` 的 `data` 节点：
     * `{ trending: { list: [ { keyword, show_name } ] } }`
     */
    fun hotKeywords(data: JsonObject): List<String> =
        data.objOrNull("trending")
            ?.arrOrNull("list")
            .toObjectList()
            .mapNotNull { item ->
                val raw = firstNotBlank(item, "keyword", "show_name")
                HtmlText.clean(raw).takeIf { it.isNotBlank() }
            }
            .distinct()
            .take(MAX_HOT_KEYWORDS)

    /** 依次尝试多个字段名，跳过空白值。 */
    private fun firstNotBlank(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.strOrNull(key)?.takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }
}
