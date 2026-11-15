package com.bbplayer.android.lyrics

import java.text.Normalizer
import java.util.Locale

/**
 * Cleans a Bilibili track title into a search keyword for QQ Music / NetEase, and provides the normalization +
 * similarity primitives used for candidate matching. Pure Kotlin, no Android dependencies.
 *
 * Cleaning steps (in order):
 *  1. strip bracket groups (`【】`, `「」`, `『』`, `（）`, `()`, `[]`, `［］`): a group whose content is noise
 *     (MV / 官方 / 中日双语 / 4K / 1080P / 完整版 / 翻自 / PV / 字幕 / 中字 / 高音质 / 动态歌词 / 纯音乐 / LIVE / HD ...)
 *     is dropped entirely; a non-noise group keeps only its content (e.g. `「夜に駆ける」` -> `夜に駆ける`);
 *  2. drop `feat.` / `ft.` / `featuring` and everything after it;
 *  3. drop `翻自 ...` and everything after it;
 *  4. drop remaining standalone noise keywords / resolution tokens (`4K`, `1080P`, ...);
 *  5. split on `|`, `/`, `／`, `｜`, `丨` and take the first non-blank segment;
 *  6. collapse whitespace and trim leading/trailing separators.
 *
 * If cleaning removes everything, the original trimmed title is returned so the caller never searches for "".
 */
object TitleCleaner {

    /** Weight of the title similarity in [LyricMatcher.score]. */
    const val TITLE_WEIGHT: Double = 0.75

    /** Weight of the artist / uploader similarity in [LyricMatcher.score]. */
    const val ARTIST_WEIGHT: Double = 0.25

    private val BRACKET_GROUPS = listOf(
        Regex("""【([^】]*)】"""),
        Regex("""「([^」]*)」"""),
        Regex("""『([^』]*)』"""),
        Regex("""（([^）]*)）"""),
        Regex("""\(([^)]*)\)"""),
        Regex("""［([^］]*)］"""),
        Regex("""\[([^\]]*)]"""),
    )

    /** Latin noise words, matched as whole tokens so e.g. "Deliver" is not treated as "live". */
    private val LATIN_NOISE = Regex(
        """(?i)(?<![a-z0-9])(mv|pv|live|hd|hq|4k|8k|1080p|720p|2160p|official|full|ver)(?![a-z0-9])""",
    )

    private val CJK_NOISE = listOf(
        "官方", "中日双语", "双语", "完整版", "翻自", "动态歌词", "高音质", "纯音乐",
        "无损", "高清", "字幕", "中字", "现场", "试听", "字幕版",
    )

    private val FEAT_SUFFIX = Regex("""(?i)(?:^|[\s\-_/（(])(?:feat\.?|ft\.?|featuring)\b.*$""")
    private val COVER_FROM_SUFFIX = Regex("""(?i)\s*翻自\s*.*$""")
    private val RESOLUTION_TOKEN = Regex("""(?i)\b\d{3,4}\s*[pP]\b|\b[4-8]\s*[kK]\b""")
    private val SEPARATORS = charArrayOf('|', '/', '／', '｜', '丨')
    private val EDGE_TRIM = charArrayOf(
        ' ', '\t', '-', '_', '·', '~', '、', ',', '.', ':', ';', '!', '?', '，', '。', '！', '？',
        '"', '\'', '“', '”', '‘', '’', '【', '】', '「', '」', '/', '|',
    )

    /** Clean [raw] into a compact search keyword. Never returns a blank string unless [raw] itself is blank. */
    fun clean(raw: String): String {
        val original = raw.trim()
        if (original.isEmpty()) return ""
        var text = stripBrackets(original)
        text = FEAT_SUFFIX.replace(text, " ")
        text = COVER_FROM_SUFFIX.replace(text, " ")
        for (keyword in CJK_NOISE) {
            text = text.replace(keyword, " ")
        }
        text = LATIN_NOISE.replace(text, " ")
        text = RESOLUTION_TOKEN.replace(text, " ")
        val firstSegment = text.split(*SEPARATORS).firstOrNull { it.isNotBlank() } ?: ""
        val collapsed = collapseWhitespace(firstSegment).trim(*EDGE_TRIM)
        return collapsed.ifBlank { original }
    }

    /** Normalization used for matching: NFKC (full-width -> half-width), lowercase, keep letters/digits only. */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val builder = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            if (ch.isLetterOrDigit()) builder.append(ch)
        }
        return builder.toString()
    }

    /**
     * Title similarity in `[0, 1]`: exact match -> 1.0, containment -> `0.85 + 0.15 * lengthRatio`,
     * otherwise the longest-common-substring ratio.
     */
    fun titleScore(queryTitle: String, candidateTitle: String): Double =
        similarity(normalize(queryTitle), normalize(candidateTitle))

    /** Artist similarity in `[0, 1]`, same metric as [titleScore] (uploader vs. candidate singer). */
    fun artistScore(uploader: String, candidateArtist: String): Double =
        similarity(normalize(uploader), normalize(candidateArtist))

    private fun stripBrackets(input: String): String {
        var text = input
        for (group in BRACKET_GROUPS) {
            text = group.replace(text) { match ->
                val content = match.groupValues[1]
                if (isNoiseContent(content)) " " else content
            }
        }
        return text
    }

    private fun isNoiseContent(content: String): Boolean {
        val normalized = content.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return true
        if (LATIN_NOISE.containsMatchIn(normalized)) return true
        if (RESOLUTION_TOKEN.containsMatchIn(normalized)) return true
        if (FEAT_SUFFIX.containsMatchIn(normalized)) return true
        if (COVER_FROM_SUFFIX.containsMatchIn(normalized)) return true
        return CJK_NOISE.any { normalized.contains(it) }
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        val minLength = minOf(left.length, right.length)
        val maxLength = maxOf(left.length, right.length)
        val containment = if (left.contains(right) || right.contains(left)) {
            0.85 + 0.15 * (minLength.toDouble() / maxLength)
        } else {
            0.0
        }
        val lcs = longestCommonSubstringLength(left, right).toDouble() / maxLength
        return maxOf(containment, lcs)
    }

    private fun longestCommonSubstringLength(left: String, right: String): Int {
        if (left.isEmpty() || right.isEmpty()) return 0
        var previous = IntArray(right.length + 1)
        var current = IntArray(right.length + 1)
        var best = 0
        for (i in 1..left.length) {
            for (j in 1..right.length) {
                current[j] = if (left[i - 1] == right[j - 1]) previous[j - 1] + 1 else 0
                if (current[j] > best) best = current[j]
            }
            val swap = previous
            previous = current
            current = swap
            java.util.Arrays.fill(current, 0)
        }
        return best
    }

    private fun collapseWhitespace(text: String): String {
        val builder = StringBuilder(text.length)
        var lastWasSpace = false
        for (ch in text) {
            if (ch.isWhitespace()) {
                if (!lastWasSpace && builder.isNotEmpty()) builder.append(' ')
                lastWasSpace = true
            } else {
                builder.append(ch)
                lastWasSpace = false
            }
        }
        return builder.toString().trim()
    }
}
