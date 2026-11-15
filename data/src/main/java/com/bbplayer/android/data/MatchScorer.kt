package com.bbplayer.android.data

import com.bbplayer.android.core.model.ExternalTrack
import com.bbplayer.android.core.model.ImportMatch
import com.bbplayer.android.core.model.Track
import kotlin.math.abs

/**
 * 外部曲目 → B 站搜索结果的匹配打分（纯函数，可单测）。
 *
 * 总分 = 标题相似度 × 0.6 + UP主/歌手相似度 × 0.2 + 时长接近度 × 0.2；
 * 只有总分 ≥ [THRESHOLD] 才认为匹配成功，否则 `matched = null` 交给 UI 手动替换。
 */
internal object MatchScorer {

    /** 匹配阈值（AC H2：低于该分数视为未匹配）。 */
    const val THRESHOLD = 0.45f

    const val TITLE_WEIGHT = 0.6f
    const val ARTIST_WEIGHT = 0.2f
    const val DURATION_WEIGHT = 0.2f

    /** 时长差超过该值不计分。 */
    const val DURATION_TOLERANCE_MS = 20_000L

    private val BRACKET_CONTENT = Regex("""[【\[（(][^】\]）)]*[】\]）)]""")
    private val NON_WORD = Regex("""[^0-9a-z\u4e00-\u9fff]+""")

    /**
     * 在候选列表中挑最高分的一条。分数相同时保留搜索排序靠前的候选。
     * 无论是否达到阈值都会返回实际最高分（便于 UI 展示「时长差/分数」）。
     */
    fun match(index: Int, external: ExternalTrack, candidates: List<Track>): ImportMatch {
        var best: Track? = null
        var bestScore = 0f
        for (candidate in candidates) {
            val score = score(external, candidate)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        val matched = best?.takeIf { bestScore >= THRESHOLD }
        return ImportMatch(
            index = index,
            external = external,
            matched = matched,
            score = bestScore,
        )
    }

    fun score(external: ExternalTrack, candidate: Track): Float =
        TITLE_WEIGHT * titleSimilarity(external.title, candidate.title) +
            ARTIST_WEIGHT * artistSimilarity(external.artist, candidate.uploader) +
            DURATION_WEIGHT * durationScore(external.durationMs, candidate.durationMs)

    /** 标题相似度：先去装饰（【4K修复】/括号内容），再优先判断包含关系，最后编辑距离。 */
    fun titleSimilarity(left: String, right: String): Float =
        similarity(normalizeTitle(left), normalizeTitle(right))

    /** UP主 / 歌手相似度：B 站 UP 主名常包含歌手名，包含关系给高分。 */
    fun artistSimilarity(left: String, right: String): Float {
        val a = normalizeTitle(left)
        val b = normalizeTitle(right)
        if (a.isEmpty() || b.isEmpty()) return 0f
        return similarity(a, b)
    }

    /** 时长接近度：|差| > 20s 记 0；相等记 1；中间线性衰减。任一方未知记 0。 */
    fun durationScore(externalMs: Long, candidateMs: Long): Float {
        if (externalMs <= 0L || candidateMs <= 0L) return 0f
        val diff = abs(externalMs - candidateMs)
        if (diff > DURATION_TOLERANCE_MS) return 0f
        return 1f - diff.toFloat() / DURATION_TOLERANCE_MS.toFloat()
    }

    // ---------------------------------------------------------------- 工具

    fun normalizeTitle(raw: String?): String {
        val text = raw?.let(HtmlText::clean)?.lowercase().orEmpty()
        if (text.isEmpty()) return ""
        return NON_WORD.replace(BRACKET_CONTENT.replace(text, " "), "")
    }

    /** 0..1 的字符串相似度：相等=1，包含=0.82~1，其余用编辑距离比例。 */
    fun similarity(left: String, right: String): Float {
        if (left.isEmpty() || right.isEmpty()) return 0f
        if (left == right) return 1f
        val shorter = if (left.length <= right.length) left else right
        val longer = if (left.length <= right.length) right else left
        if (longer.contains(shorter)) {
            val ratio = shorter.length.toFloat() / longer.length.toFloat()
            return 0.82f + 0.18f * ratio
        }
        val distance = levenshtein(left, right)
        val maxLength = maxOf(left.length, right.length)
        return ((maxLength - distance).toFloat() / maxLength.toFloat()).coerceIn(0f, 1f)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
