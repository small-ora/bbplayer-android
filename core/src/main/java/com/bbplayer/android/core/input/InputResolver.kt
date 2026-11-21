package com.bbplayer.android.core.input

/** 用户输入（搜索框 / 剪贴板 / 分享文本）的解析结果。 */
sealed interface VideoInput {
    /** 标准 BV 号，如 `BV1xx411c7mD`。 */
    data class Bvid(val bvid: String) : VideoInput

    /** AV 号，如 `av170001` 中的 170001。 */
    data class Aid(val aid: Long) : VideoInput

    /** b23.tv 短链，需要跟随重定向后再解析。 */
    data class ShortLink(val url: String) : VideoInput

    /** 无法识别的普通关键词。 */
    data class Keyword(val query: String) : VideoInput
}

/**
 * 纯函数输入解析：BV / AV / 完整链接 / b23.tv 短链 / 关键词。
 * 不发起任何网络请求，短链由数据层跟随重定向后再回调 [extractVideoRef]。
 *
 * 放在 core 模块：数据层（resolveInput）和搜索 UI（区分「直接视频引用」与「关键词」，
 * 关键词走列表搜索、BV/AV/链接走视频解析）共用同一套判断，避免两处正则漂移。
 */
object InputResolver {

    private val BV_REGEX = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
    private val AV_REGEX = Regex("(?<![0-9A-Za-z])av(\\d+)", RegexOption.IGNORE_CASE)
    private val SHORT_LINK_REGEX =
        Regex("""^(?:https?://)?(?:www\.)?b23\.tv/[A-Za-z0-9]+(?:\?\S*)?$""", RegexOption.IGNORE_CASE)
    private val SHORT_LINK_IN_TEXT_REGEX =
        Regex("""(?:https?://)?(?:www\.)?b23\.tv/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)
    private val CANONICAL_TAG_REGEX =
        Regex("""<link[^>]*rel=["']canonical["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val HREF_REGEX = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val BILI_VIDEO_URL_REGEX =
        Regex("""https?://(?:www\.|m\.)?bilibili\.com/video/[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    /** 解析原始输入；空串按空关键词处理。 */
    fun resolve(raw: String): VideoInput {
        val text = raw.trim()
        if (text.isEmpty()) return VideoInput.Keyword("")
        extractVideoRef(text)?.let { return it }
        if (SHORT_LINK_REGEX.matches(text) || SHORT_LINK_IN_TEXT_REGEX.containsMatchIn(text)) {
            return VideoInput.ShortLink(normalizeUrl(text))
        }
        return VideoInput.Keyword(text)
    }

    /** 从任意文本（URL / 短链重定向结果 / 页面 HTML）中提取 BV 或 AV。 */
    fun extractVideoRef(text: String?): VideoInput? {
        if (text.isNullOrBlank()) return null
        BV_REGEX.find(text)?.let { return VideoInput.Bvid(normalizeBvid(it.value)) }
        AV_REGEX.find(text)?.let { match ->
            match.groupValues[1].toLongOrNull()?.let { return VideoInput.Aid(it) }
        }
        return null
    }

    /** 从 b23.tv 落地页 HTML 中读取 canonical 链接（或首个视频链接）。 */
    fun canonicalHref(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val tag = CANONICAL_TAG_REGEX.find(html)?.value
        if (tag != null) {
            HREF_REGEX.find(tag)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return BILI_VIDEO_URL_REGEX.find(html)?.value
    }

    /** BV 号大小写敏感，前缀统一为 `BV`，其余保持原样。 */
    fun normalizeBvid(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length < 3) return trimmed
        return "BV" + trimmed.substring(2)
    }

    /** 补全协议前缀。 */
    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }
}

/**
 * 搜索框提交后的路由结果。
 *
 * 回归背景：旧搜索页对所有输入都先调用 `MusicRepository.resolveInput`，而它对关键词只取
 * 首条视频展开分 P，导致「搜什么都只有一条结果」。关键词必须走搜索列表接口，
 * 只有 BV / AV / b23.tv / 完整视频链接才走视频解析。
 */
sealed interface SearchInput {
    /** 普通关键词 → 走列表搜索，返回全部结果。 */
    data class KeywordQuery(val query: String) : SearchInput

    /** BV / AV / b23.tv / 完整视频链接 → 走视频解析（含分 P）。 */
    data class DirectVideo(val raw: String) : SearchInput
}

/** 解析搜索框输入并给出路由结果（纯函数，可单测）。 */
fun parseSearchInput(raw: String): SearchInput =
    when (val resolved = InputResolver.resolve(raw)) {
        is VideoInput.Keyword -> SearchInput.KeywordQuery(resolved.query)
        else -> SearchInput.DirectVideo(raw)
    }
