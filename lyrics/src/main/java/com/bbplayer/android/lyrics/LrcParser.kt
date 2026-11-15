package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.LyricLine
import com.bbplayer.android.core.model.LyricWord
import com.bbplayer.android.core.model.Lyrics

/**
 * Parser for standard LRC and enhanced (word-timed) LRC. Pure Kotlin, no Android dependencies.
 *
 * Supported syntax:
 *  - `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]` (also tolerates the NetEase quirk `[mm:ss:xx]`)
 *  - multiple leading timestamps on one line: `[00:01.00][00:05.00]chorus` expands into one line per timestamp
 *  - metadata `[ti:]`, `[ar:]`, `[al:]`, `[by:]`, ... are ignored; `[offset:+/-ms]` is stored in [Lyrics.offsetMs]
 *  - enhanced LRC inline word tags: `[00:01.00]<00:01.00>你<00:01.50>好`
 *  - blank lines are filtered
 *  - line end time = next line start; the last line = start + max(3s, text.length * 200ms)
 *  - last word end = next line start, or line start + 500ms for the very last line
 *  - translation merge: same timestamp (+/-10ms) second line in the same text, or a separate NetEase `tlyric` text
 *
 * Plain-text policy (documented on purpose): a non-empty line without a valid leading timestamp is NOT rendered as
 * a timed lyric line. It is collected (in original order) into [Lyrics.plainText]. When the whole input has no timed
 * lines at all, [Lyrics.lines] is empty and [Lyrics.plainText] holds the raw non-timestamp lines so the UI can still
 * show the raw text. This mirrors upstream SPL behavior where untimed lines are usually metadata/notes, not lyrics.
 *
 * Offset semantics follow the frozen contract: the raw `[offset:]` value is written into [Lyrics.offsetMs] verbatim;
 * line timestamps are NOT rewritten. `Lyrics.activeIndex(positionMs)` adds the offset to the position, so a positive
 * offset makes lyrics appear earlier, which is the LRC convention.
 */
object LrcParser {

    /** Lines whose start times differ by at most this many ms are treated as main + translation. */
    const val TRANSLATION_TOLERANCE_MS: Long = 10L

    /** Minimum duration for the last line of a file. */
    const val MIN_LAST_LINE_MS: Long = 3_000L

    /** Extra duration per character for the last line (long lines stay visible longer). */
    const val MS_PER_TEXT_CHAR: Long = 200L

    /** Fallback duration of the last word in the last enhanced line. */
    const val LAST_WORD_FALLBACK_MS: Long = 500L

    // Deliberately not anchored with `^`: Kotlin's Regex.find(input, startIndex) keeps Java anchoring bounds, so
    // `^` would only match at index 0 and one-line-multiple-timestamps ([00:01][00:05]text) would break.
    private val LEADING_TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val METADATA_TAG = Regex("""^\[([A-Za-z][A-Za-z0-9_#]*):(.*)]$""")
    private val INLINE_WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    private data class WordSlice(val offsetMs: Long, val text: String)

    private data class ParsedLine(
        val startMs: Long,
        val text: String,
        val words: List<LyricWord>?,
        val translation: String? = null,
    )

    private data class ParsedDocument(
        val lines: List<ParsedLine>,
        val offsetMs: Int,
        val plainLines: List<String>,
    )

    /**
     * Parse [lrcText], optionally merging a separate translation track ([translationText], e.g. NetEase `tlyric`).
     * Never throws for malformed input; unparseable lines degrade to [Lyrics.plainText].
     */
    fun parse(
        lrcText: String,
        translationText: String? = null,
        source: String = "",
    ): Lyrics {
        val normalized = normalizeInput(lrcText)
        if (normalized.isBlank()) {
            return Lyrics(lines = emptyList(), offsetMs = 0, source = source, plainText = "")
        }
        val document = parseDocument(normalized)
        var lines: List<ParsedLine> = document.lines.sortedBy { it.startMs }
        lines = mergeSameTimestampLines(lines)
        if (!translationText.isNullOrBlank()) {
            val translationLines = parseDocument(normalizeInput(translationText)).lines
            lines = mergeTranslations(lines, translationLines)
        }
        val finalized = finalizeTiming(lines)
        return Lyrics(
            lines = finalized,
            offsetMs = document.offsetMs,
            source = source,
            plainText = document.plainLines.joinToString("\n"),
        )
    }

    private fun normalizeInput(text: String): String =
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

    private fun parseDocument(text: String): ParsedDocument {
        val lines = ArrayList<ParsedLine>()
        val plainLines = ArrayList<String>()
        var offsetMs = 0
        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val metadata = METADATA_TAG.matchEntire(line)
            if (metadata != null) {
                if (metadata.groupValues[1].equals("offset", ignoreCase = true)) {
                    metadata.groupValues[2].trim().toIntOrNull()?.let { offsetMs = it }
                }
                continue
            }
            val parsed = parseTimedLine(line)
            if (parsed == null) {
                plainLines += line
            } else {
                lines += parsed
            }
        }
        return ParsedDocument(lines = lines, offsetMs = offsetMs, plainLines = plainLines)
    }

    /** Returns one or more timed lines (multiple leading time tags expand to multiple lines), or null. */
    private fun parseTimedLine(line: String): List<ParsedLine>? {
        val times = ArrayList<Long>()
        var index = 0
        while (true) {
            val match = LEADING_TIME_TAG.find(line, index) ?: break
            if (match.range.first != index) break
            times += timeTagToMs(match)
            index = match.range.last + 1
        }
        if (times.isEmpty()) return null

        val content = line.substring(index)
        val base = times.first()
        val slices = splitWordSlices(content, base)
        val text = if (slices == null) {
            content.trim()
        } else {
            slices.joinToString("") { it.text }.trim()
        }
        if (text.isEmpty()) return null // timestamp-only line: filtered as blank

        return times.map { timestamp ->
            val words = slices?.mapNotNull { slice ->
                val wordText = slice.text.trim()
                if (wordText.isEmpty()) return@mapNotNull null
                LyricWord(startMs = timestamp + slice.offsetMs, endMs = 0L, text = wordText)
            }?.takeIf { it.isNotEmpty() }
            ParsedLine(startMs = timestamp, text = text, words = words)
        }
    }

    /**
     * Splits enhanced-LRC content into word slices. Returns null when there is no inline word tag
     * (i.e. a plain timed line, which keeps `words = emptyList()`).
     */
    private fun splitWordSlices(content: String, baseMs: Long): List<WordSlice>? {
        val matches = INLINE_WORD_TAG.findAll(content).toList()
        if (matches.isEmpty()) return null
        val slices = ArrayList<WordSlice>(matches.size + 1)
        var cursor = 0
        var currentOffsetMs = 0L
        for (match in matches) {
            val between = content.substring(cursor, match.range.first)
            if (between.isNotEmpty()) slices += WordSlice(currentOffsetMs, between)
            currentOffsetMs = (timeTagToMs(match) - baseMs).coerceAtLeast(0L)
            cursor = match.range.last + 1
        }
        val tail = content.substring(cursor)
        if (tail.isNotEmpty()) slices += WordSlice(currentOffsetMs, tail)
        return slices
    }

    private fun mergeSameTimestampLines(sorted: List<ParsedLine>): List<ParsedLine> {
        val out = ArrayList<ParsedLine>(sorted.size)
        var index = 0
        while (index < sorted.size) {
            val first = sorted[index]
            var next = index + 1
            while (next < sorted.size && sorted[next].startMs - first.startMs <= TRANSLATION_TOLERANCE_MS) {
                next++
            }
            var translation: String? = null
            for (candidate in index + 1 until next) {
                val line = sorted[candidate]
                if (translation == null && line.text != first.text) translation = line.text
            }
            out += first.copy(translation = translation)
            index = next
        }
        return out
    }

    private fun mergeTranslations(
        main: List<ParsedLine>,
        translations: List<ParsedLine>,
    ): List<ParsedLine> {
        val usable = translations.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return main
        val used = BooleanArray(usable.size)
        return main.map { line ->
            if (line.translation != null) return@map line
            var bestIndex = -1
            var bestDiff = Long.MAX_VALUE
            for (index in usable.indices) {
                if (used[index]) continue
                val diff = kotlin.math.abs(usable[index].startMs - line.startMs)
                if (diff <= TRANSLATION_TOLERANCE_MS && diff < bestDiff) {
                    bestIndex = index
                    bestDiff = diff
                }
            }
            if (bestIndex < 0) {
                line
            } else {
                used[bestIndex] = true
                line.copy(translation = usable[bestIndex].text)
            }
        }
    }

    private fun finalizeTiming(lines: List<ParsedLine>): List<LyricLine> {
        val out = ArrayList<LyricLine>(lines.size)
        for (index in lines.indices) {
            val line = lines[index]
            val nextStart: Long? = lines.getOrNull(index + 1)?.startMs
            val endMs = nextStart
                ?: (line.startMs + maxOf(MIN_LAST_LINE_MS, line.text.length * MS_PER_TEXT_CHAR))
            val rawWords = line.words.orEmpty()
            val words = if (rawWords.isEmpty()) {
                emptyList()
            } else {
                rawWords.mapIndexed { wordIndex, word ->
                    val wordEnd = if (wordIndex < rawWords.lastIndex) {
                        rawWords[wordIndex + 1].startMs
                    } else {
                        nextStart ?: (line.startMs + LAST_WORD_FALLBACK_MS)
                    }
                    word.copy(endMs = maxOf(wordEnd, word.startMs + 1L))
                }
            }
            out += LyricLine(
                startMs = line.startMs,
                endMs = endMs,
                text = line.text,
                translation = line.translation,
                words = words,
            )
        }
        return out
    }

    private fun timeTagToMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }
}
