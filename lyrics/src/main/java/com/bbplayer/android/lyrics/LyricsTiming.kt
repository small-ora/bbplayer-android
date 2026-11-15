package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.LyricLine
import com.bbplayer.android.core.model.LyricWord
import com.bbplayer.android.core.model.Lyrics
import kotlin.math.abs

/**
 * Shared timing model used by the SPL / NetEase YRC / QQ QRC parsers.
 *
 * Each parser only needs to produce [RawLine]s with absolute start times (and optional explicit end times / word
 * timings); this object applies the common finalization rules:
 *  - lines are sorted by start time;
 *  - a line ends at its explicit end, else at the next line start, else at `start + 10 000 ms` (upstream SPL rule);
 *  - a word ends at its explicit end, else at the next word start, else at the line end;
 *  - a separate translation track (LRC, enhanced LRC or YRC text) is aligned by start time (±10 ms) when present.
 *
 * Pure Kotlin, no Android dependencies; never throws on malformed input.
 */
internal object LyricsTiming {

    /** Lines whose start times differ by at most this many ms are treated as main + translation. */
    const val TRANSLATION_TOLERANCE_MS: Long = 10L

    /** Fallback duration of the last line when the format gives no explicit end (upstream SPL constant). */
    const val DEFAULT_LAST_LINE_MS: Long = 10_000L

    /** One word/segment with absolute timestamps. A non-positive [endMs] means "computed later". */
    data class RawWord(val startMs: Long, val endMs: Long = 0L, val text: String)

    /** One lyric line with absolute timestamps. [endMs] is only set when the format states it explicitly. */
    data class RawLine(
        val startMs: Long,
        val text: String,
        val endMs: Long? = null,
        val words: List<RawWord> = emptyList(),
        val translation: String? = null,
    )

    /** Finalizes [raw] into the frozen core model. */
    fun finalize(
        raw: List<RawLine>,
        offsetMs: Int = 0,
        source: String = "",
        plainText: String = "",
    ): Lyrics {
        val usable = raw
            .filter { it.startMs >= 0L && (it.text.isNotBlank() || it.words.isNotEmpty()) }
            .sortedBy { it.startMs }
        if (usable.isEmpty()) return Lyrics(emptyList(), offsetMs, source, plainText)
        val lines = ArrayList<LyricLine>(usable.size)
        for (index in usable.indices) {
            val line = usable[index]
            val nextStart = usable.getOrNull(index + 1)?.startMs
            val endMs = line.endMs?.takeIf { it > line.startMs }
                ?: nextStart
                ?: (line.startMs + DEFAULT_LAST_LINE_MS)
            val words = if (line.words.isEmpty()) {
                emptyList()
            } else {
                line.words.mapIndexed { wordIndex, word ->
                    val nextWordStart = line.words.getOrNull(wordIndex + 1)?.startMs
                    val wordEnd = when {
                        word.endMs > word.startMs -> word.endMs
                        nextWordStart != null -> nextWordStart
                        else -> endMs
                    }
                    LyricWord(
                        startMs = word.startMs,
                        endMs = maxOf(wordEnd, word.startMs + 1L),
                        text = word.text,
                    )
                }
            }
            lines += LyricLine(
                startMs = line.startMs,
                endMs = maxOf(endMs, line.startMs + 1L),
                text = line.text,
                translation = line.translation,
                words = words,
            )
        }
        return Lyrics(lines = lines, offsetMs = offsetMs, source = source, plainText = plainText)
    }

    /**
     * Merges an optional separate translation track into [lines] by start time. The translation may be plain LRC,
     * enhanced LRC or YRC text; malformed input is ignored (the main lines are returned unchanged).
     */
    fun mergeTranslation(lines: List<RawLine>, translationText: String?): List<RawLine> {
        if (lines.isEmpty() || translationText.isNullOrBlank()) return lines
        val translations = translationTimeline(translationText)
        if (translations.isEmpty()) return lines
        val used = BooleanArray(translations.size)
        return lines.map { line ->
            if (line.translation != null) return@map line
            var bestIndex = -1
            var bestDiff = Long.MAX_VALUE
            for (index in translations.indices) {
                if (used[index]) continue
                val diff = abs(translations[index].first - line.startMs)
                if (diff <= TRANSLATION_TOLERANCE_MS && diff < bestDiff) {
                    bestIndex = index
                    bestDiff = diff
                }
            }
            if (bestIndex < 0) {
                line
            } else {
                used[bestIndex] = true
                line.copy(translation = translations[bestIndex].second)
            }
        }
    }

    private fun translationTimeline(text: String): List<Pair<Long, String>> {
        val yrcLines = YrcParser.parse(text).lines
        val lines = if (yrcLines.isNotEmpty()) yrcLines else LrcParser.parse(text).lines
        return lines.asSequence()
            .filter { it.text.isNotBlank() }
            .map { it.startMs to it.text }
            .toList()
    }
}
