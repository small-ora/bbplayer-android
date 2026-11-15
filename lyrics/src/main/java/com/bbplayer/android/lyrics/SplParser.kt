package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.Lyrics
import kotlin.math.roundToLong

/**
 * Parser for the SPL (Salt Player Lyrics) format used by the upstream BBPlayer, as specified in
 * `.ai/recon/upstream-features.md` §5.1.
 *
 * Grammar (upstream `packages/splash/src/parser/index.ts` + `spans.ts` + `utils/time.ts`):
 * ```
 * SPL   := (meta | line)*
 * meta  := '[' [A-Za-z]+ ':' any ']'                       // metadata (offset is honoured)
 * line  := timeTag+ content
 * timeTag := '[' [-]?MM ':' [-]?SS '.' F{1,6} ']' | '<' same '>'       // also accepted inline for word timing
 * ```
 * Rules implemented:
 *  - a line that is only `[letters:value]` is metadata, never a lyric;
 *  - multiple leading time tags expand into one lyric line per timestamp (sorted ascending);
 *  - a line without a leading timestamp inherits the previous line's timestamps — this is how multi-line
 *    translations are written; same-timestamp lines become main text + [translation];
 *  - `[offset:+/-ms]` populates [Lyrics.offsetMs] verbatim (line timestamps are not rewritten);
 *  - inline `<mm:ss.xx>` / `[mm:ss.xx]` tags delimit word (karaoke) segments, absolute timestamps;
 *  - a line ending with a bare tag gets that tag as its end time, otherwise the end is the next line start,
 *    otherwise `start + 10 000 ms`;
 *  - line timestamps are accepted in `[...]` form only; `<...>` at the start of a line is word-level timing (the first
 *    karaoke segment often starts exactly at the line start, so consuming it as a line tag would lose a word);
 *  - malformed lines (no usable timestamp / no predecessor) degrade to [Lyrics.plainText]; parsing never throws.
 *
 * Fraction digits of 1-6 are accepted: `.1` = 100 ms, `.02` = 20 ms, `.123456` -> 123 ms (rounded).
 */
object SplParser {

    /** Line end fallback when the format gives no explicit end (upstream `parser/index.ts:104-113`). */
    const val DEFAULT_LINE_DURATION_MS: Long = LyricsTiming.DEFAULT_LAST_LINE_MS

    /** Lines whose start times differ by at most this many ms are treated as main + translation. */
    const val TRANSLATION_TOLERANCE_MS: Long = LyricsTiming.TRANSLATION_TOLERANCE_MS

    private const val TIME_BODY = """(-?\d{1,3}):(-?\d{1,2})(?:\.(\d{1,6}))?"""
    // Deliberately not anchored with `^`: parseLeadingTags scans one line multiple times, and Java anchoring bounds
    // mean `^` would only match at index 0.
    private val TIME_TAG = Regex("""[\[<]$TIME_BODY[\]>]""")
    // Line-level timestamps are only accepted in square-bracket form: a line that starts with `<mm:ss.xx>` is a
    // word-timed line (the first karaoke segment may start exactly at the line start), and consuming it as a line tag
    // would silently swallow the first word.
    private val LINE_TIME_TAG = Regex("""\[$TIME_BODY]""")
    private val METADATA_TAG = Regex("""^\[([A-Za-z][A-Za-z0-9_#]*):(.*)]$""")

    private data class Document(
        val lines: List<LyricsTiming.RawLine>,
        val offsetMs: Int,
        val plainLines: List<String>,
    )

    private data class SpanResult(
        val text: String,
        val words: List<LyricsTiming.RawWord>,
        val explicitEndMs: Long?,
    )

    /**
     * Parses [text]. [translationText] is an optional separate translation track (e.g. a NetEase `tlyric`) that is
     * merged by start time. Never throws for malformed input.
     */
    fun parse(text: String, translationText: String? = null, source: String = ""): Lyrics {
        if (text.isBlank()) return Lyrics(emptyList(), 0, source, "")
        val document = parseDocument(normalizeInput(text))
        var lines = document.lines.sortedBy { it.startMs }
        lines = mergeSameTimestampLines(lines)
        lines = LyricsTiming.mergeTranslation(lines, translationText)
        return LyricsTiming.finalize(
            raw = lines,
            offsetMs = document.offsetMs,
            source = source,
            plainText = document.plainLines.joinToString("\n"),
        )
    }

    /**
     * `true` when [text] contains at least one inline (non-leading) time tag, i.e. word-level timing.
     * `LyricsParser` uses this for AUTO format detection.
     */
    internal fun hasWordLevelTags(text: String): Boolean {
        for (rawLine in normalizeInput(text).split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (METADATA_TAG.matches(line)) continue
            var rest = line
            while (true) {
                val match = LINE_TIME_TAG.find(rest, 0) ?: break
                if (match.range.first != 0) break
                rest = rest.substring(match.range.last + 1)
            }
            if (TIME_TAG.containsMatchIn(rest)) return true
        }
        return false
    }

    private fun normalizeInput(text: String): String =
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

    private fun parseDocument(text: String): Document {
        val lines = ArrayList<LyricsTiming.RawLine>()
        val plainLines = ArrayList<String>()
        var offsetMs = 0
        var lastTimestamps: List<Long> = emptyList()

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

            val leading = parseLeadingTags(line)
            val timestamps: List<Long>
            val content: String
            if (leading.first.isEmpty()) {
                if (lastTimestamps.isEmpty()) {
                    plainLines += line
                    continue
                }
                // Continuation of the previous lyric line: upstream inherits the previous timestamps. Used for
                // multi-line translations, which then merge into the previous line as `translation`.
                timestamps = lastTimestamps
                content = line
            } else {
                timestamps = leading.first
                content = leading.second
            }
            if (timestamps.isEmpty() || content.isBlank()) continue
            lastTimestamps = timestamps

            val base = timestamps.first()
            val spans = splitSpans(content, base)
            if (spans.text.isBlank() && spans.words.isEmpty()) continue
            for (timestamp in timestamps) {
                val shift = timestamp - base
                val words = spans.words.map { word ->
                    word.copy(
                        startMs = word.startMs + shift,
                        endMs = if (word.endMs > 0L) word.endMs + shift else 0L,
                    )
                }
                lines += LyricsTiming.RawLine(
                    startMs = timestamp,
                    text = spans.text,
                    endMs = spans.explicitEndMs?.let { end -> (end + shift).takeIf { it > timestamp } },
                    words = words,
                )
            }
        }
        return Document(lines = lines, offsetMs = offsetMs, plainLines = plainLines)
    }

    /** Collects leading line time tags (`[mm:ss.xx]`), returns the sorted timestamps and the remaining content. */
    private fun parseLeadingTags(line: String): Pair<List<Long>, String> {
        val timestamps = ArrayList<Long>()
        var index = 0
        while (index < line.length) {
            val match = LINE_TIME_TAG.find(line, index) ?: break
            if (match.range.first != index) break
            timestamps += timeTagToMs(match)
            index = match.range.last + 1
        }
        return timestamps.sorted() to line.substring(index)
    }

    /**
     * Splits the content of one lyric line into word segments delimited by inline `<mm:ss.xx>` / `[mm:ss.xx]` tags.
     * The returned word start times are absolute (given [baseMs] as the line start). When the content ends with a
     * bare tag, that tag is returned as the explicit line end. A tag earlier than the current position is ignored
     * (upstream warns and keeps going), which also safely degrades "relative" word timings to a single segment.
     */
    private fun splitSpans(content: String, baseMs: Long): SpanResult {
        val matches = TIME_TAG.findAll(content).toList()
        if (matches.isEmpty()) return SpanResult(content.trim(), emptyList(), null)

        val words = ArrayList<LyricsTiming.RawWord>(matches.size + 1)
        var cursor = 0
        var currentTime = baseMs
        var lastTagTime: Long? = null
        var lastTagEnd = -1
        for (match in matches) {
            val tagTime = timeTagToMs(match)
            val between = content.substring(cursor, match.range.first)
            if (between.isNotEmpty()) words += LyricsTiming.RawWord(currentTime, 0L, between)
            cursor = match.range.last + 1
            if (tagTime < currentTime) continue // out-of-order tag: ignore (upstream behavior)
            currentTime = tagTime
            lastTagTime = tagTime
            lastTagEnd = match.range.last + 1
        }
        val tail = content.substring(cursor)
        if (tail.isNotEmpty()) words += LyricsTiming.RawWord(currentTime, 0L, tail)

        val text = TIME_TAG.replace(content, "").trim()
        val explicitEnd = if (tail.isEmpty() && lastTagTime != null && lastTagEnd >= 0) lastTagTime else null
        return SpanResult(text, words, explicitEnd)
    }

    private fun mergeSameTimestampLines(sorted: List<LyricsTiming.RawLine>): List<LyricsTiming.RawLine> {
        val out = ArrayList<LyricsTiming.RawLine>(sorted.size)
        var index = 0
        while (index < sorted.size) {
            val first = sorted[index]
            var next = index + 1
            while (next < sorted.size && sorted[next].startMs - first.startMs <= TRANSLATION_TOLERANCE_MS) next++
            var translation = first.translation
            for (candidate in index + 1 until next) {
                if (translation == null && sorted[candidate].text.isNotBlank() && sorted[candidate].text != first.text) {
                    translation = sorted[candidate].text
                }
            }
            out += first.copy(translation = translation)
            index = next
        }
        return out
    }

    private fun timeTagToMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fractionMs = fractionToMs(match.groupValues[3])
        return (minutes * 60_000L + seconds * 1_000L + fractionMs).coerceAtLeast(0L)
    }

    /** `.1` -> 100 ms, `.02` -> 20 ms, `.123` -> 123 ms, `.123456` -> 123 ms (rounded). */
    private fun fractionToMs(fraction: String): Long {
        if (fraction.isEmpty()) return 0L
        if (fraction.length <= 3) return fraction.padEnd(3, '0').toLongOrNull() ?: 0L
        val value = fraction.toLongOrNull() ?: 0L
        val divisor = when (fraction.length) {
            4 -> 10.0
            5 -> 100.0
            else -> 1000.0
        }
        return (value / divisor).roundToLong()
    }
}
