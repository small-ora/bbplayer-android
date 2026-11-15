package com.bbplayer.android.data

import com.bbplayer.android.core.model.SubtitleCue
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.roundToLong

/** 一条可用字幕的元信息（语言 + 下载地址）。 */
internal data class SubtitleRef(
    val language: String,
    val languageLabel: String,
    val url: String,
)

/**
 * B 站字幕的纯函数映射（可单测，不依赖网络）。
 *
 * 安全约束：`subtitle_url` 必须是 https 且 host 以 `.hdslb.com` / `.bilibili.com` 结尾，
 * 否则拒绝（防止字幕接口被篡改成任意外链）。
 */
internal object SubtitleMapper {

    private val PREFERRED_LANGUAGES = listOf("zh-CN", "zh-Hans")

    /** `x/player/v2` 的 `data` 节点 → 选中的字幕（无字幕返回 null）。 */
    fun pickSubtitle(data: JsonObject): SubtitleRef? {
        val candidates = data.objOrNull("subtitle")
            ?.arrOrNull("subtitles")
            .toObjectList()
            .mapNotNull { item ->
                val url = normalizeUrl(item.strOrNull("subtitle_url")) ?: return@mapNotNull null
                if (!isTrustedSubtitleUrl(url)) return@mapNotNull null
                val language = item.strOrNull("lan")?.trim().orEmpty()
                SubtitleRef(
                    language = language,
                    languageLabel = item.strOrNull("lan_doc")?.trim().orEmpty().ifBlank { language },
                    url = url,
                )
            }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.language in PREFERRED_LANGUAGES }
            ?: candidates.firstOrNull { it.language.startsWith("zh", ignoreCase = true) }
            ?: candidates.first()
    }

    /** 字幕 JSON（`{ body: [ { from, to, content } ] }`，时间单位为秒）→ 毫秒 cues。 */
    fun parseCues(root: JsonObject): List<SubtitleCue> =
        root.arrOrNull("body")
            .toObjectList()
            .mapNotNull { line ->
                val content = line.strOrNull("content")?.trim().orEmpty()
                if (content.isEmpty()) return@mapNotNull null
                val from = line.doubleOrNull("from") ?: return@mapNotNull null
                val to = line.doubleOrNull("to") ?: return@mapNotNull null
                val startMs = (from * 1000.0).roundToLong().coerceAtLeast(0L)
                val endMs = (to * 1000.0).roundToLong().coerceAtLeast(startMs)
                SubtitleCue(startMs = startMs, endMs = endMs, text = content)
            }
            .sortedBy { it.startMs }

    /** 只信任 B 站 CDN 的 https 地址。 */
    fun isTrustedSubtitleUrl(raw: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return false
        val url = text.toHttpUrlOrNull() ?: return false
        if (!url.scheme.equals("https", ignoreCase = true)) return false
        val host = url.host.lowercase()
        return host.endsWith(".hdslb.com") || host.endsWith(".bilibili.com")
    }

    private fun normalizeUrl(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return if (text.startsWith("//")) "https:$text" else text
    }
}
