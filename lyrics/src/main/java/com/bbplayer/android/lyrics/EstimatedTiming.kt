package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.LyricLine
import com.bbplayer.android.core.model.LyricWord
import com.bbplayer.android.core.model.Lyrics

/**
 * Uniform word-level timing fallback for provider lyrics that only carry line timestamps (plain LRC).
 *
 * The UI can already render a per-character karaoke fill, but it only does so for lines with `words`. Some tracks
 * have no real verbatim track at all: QQ/NetEase can return plain LRC only. For those, this object synthesises a
 * **word timeline** by spreading each line's duration across its characters (CJK one character per word) and
 * ASCII words (grouped runs), proportionally to the character count so the karaoke fill advances at a constant
 * character rate.
 *
 * **The result is an estimate, not the studio timing.** To keep it distinguishable everywhere (UI, cache, logs),
 * [apply] appends [SOURCE_SUFFIX] to [Lyrics.source], e.g. `"qq:estimated"`, and [isEstimated] detects it. Real
 * verbatim tracks (YRC/QRC/SPL) are never touched and keep their unmarked source.
 *
 * The core model is intentionally unchanged: no new field is added, so this stays a lyrics-module-only feature.
 */
object EstimatedTiming {

    /** Marker appended to [Lyrics.source] when at least one line got estimated word timings. */
    const val SOURCE_SUFFIX = ":estimated"

    /** True when [lyrics] was produced (or already extended) by [apply] and therefore carries estimated timing. */
    fun isEstimated(lyrics: Lyrics): Boolean = isEstimatedSource(lyrics.source)

    /** True when [source] carries the estimate marker. */
    fun isEstimatedSource(source: String): Boolean = source.endsWith(SOURCE_SUFFIX)

    /** The source label with the estimate marker (`""` becomes `unknown:estimated`). */
    fun markedSource(source: String): String =
        if (isEstimatedSource(source)) source else (source.ifBlank { "unknown" }) + SOURCE_SUFFIX

    /**
     * Returns [lyrics] with every wordless line extended by an estimated word timeline.
     *
     * Lines that already have words, blank lines, and lines without a positive duration are returned unchanged. When
     * nothing needed estimation the very same [lyrics] instance is returned; otherwise [Lyrics.source] is marked with
     * [SOURCE_SUFFIX] and the word-bearing lines are replaced.
     */
    fun apply(lyrics: Lyrics): Lyrics {
        if (lyrics.lines.isEmpty()) return lyrics
        var changed = false
        val lines = lyrics.lines.map { line ->
            if (line.words.isNotEmpty()) return@map line
            val words = wordsFor(line)
            if (words.isEmpty()) {
                line
            } else {
                changed = true
                line.copy(words = words)
            }
        }
        if (!changed) return lyrics
        return lyrics.copy(lines = lines, source = markedSource(lyrics.source))
    }

    /**
     * Builds an estimated word timeline for a single [line]; empty when the line has no text or no positive duration.
     *
     * Word boundaries are proportional to the number of characters in each unit, so the sum of the word durations
     * equals the line duration and the last word ends exactly at [LyricLine.endMs].
     */
    fun wordsFor(line: LyricLine): List<LyricWord> {
        if (line.words.isNotEmpty()) return line.words
        if (line.text.isBlank() || line.endMs <= line.startMs) return emptyList()
        val units = splitUnits(line.text)
        if (units.isEmpty()) return emptyList()
        val span = line.endMs - line.startMs
        val totalWeight = units.sumOf { it.length }.coerceAtLeast(1)
        val words = ArrayList<LyricWord>(units.size)
        var cumulative = 0
        for ((index, unit) in units.withIndex()) {
            val start = line.startMs + span * cumulative / totalWeight
            cumulative += unit.length
            val end = line.startMs + span * cumulative / totalWeight
            val last = index == units.lastIndex
            words += LyricWord(
                startMs = start,
                endMs = maxOf(if (last) line.endMs else end, start + 1L),
                text = unit,
            )
        }
        return words
    }

    /**
     * Splits [text] into timing units whose concatenation is exactly [text]: CJK characters (Han, kana, Hangul) are
     * individual units, ASCII/other non-space runs (English words, numbers, punctuation) are one unit each, and
     * whitespace characters are individual units so the character budget stays exact.
     */
    fun splitUnits(text: String): List<String> {
        val units = ArrayList<String>()
        val run = StringBuilder()
        fun flush() {
            if (run.isNotEmpty()) {
                units += run.toString()
                run.setLength(0)
            }
        }
        for (char in text) {
            when {
                isCjk(char) -> {
                    flush()
                    units += char.toString()
                }

                char.isWhitespace() -> {
                    flush()
                    units += char.toString()
                }

                else -> run.append(char)
            }
        }
        flush()
        return units
    }

    private fun isCjk(char: Char): Boolean = when (char.code) {
        in 0x2E80..0x2FFF, // CJK radicals / Kangxi
        in 0x3000..0x303F, // CJK punctuation: keep per character so the fill stays even
        in 0x3040..0x30FF, // kana
        in 0x3400..0x4DBF, // CJK extension A
        in 0x4E00..0x9FFF, // CJK unified ideographs
        in 0xAC00..0xD7AF, // Hangul syllables
        in 0xF900..0xFAFF, // CJK compatibility ideographs
        -> true

        else -> false
    }
}
