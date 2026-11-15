package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.Lyrics

/**
 * Parser for QQ Music QRC (逐字) lyrics.
 *
 * QRC payloads are either the plain text form or the XML wrapper QQ returns from its lyric endpoints; the lyric body
 * lives in `LyricContent="..."` and uses this per-line grammar (`.ai/recon/upstream-features.md` §5.5):
 * ```
 * [ti:title]
 * [startMs,durationMs]<wordStartMs,wordDurationMs>字<wordStartMs,wordDurationMs>字...
 * ```
 * Word timestamps are absolute ms from the song start; values smaller than the line start are treated as line-relative
 * offsets (lenient). HTML entities used by QQ inside the wrapper (`&#10;` newlines, `&quot;`, ...) are decoded.
 *
 * Malformed input never throws: unparseable lines are skipped and callers fall back to LRC when the result is empty.
 */
object QrcParser {

    private val LINE = Regex("""^\s*\[(\d{1,9})(?:,(\d{1,9}))?]\s*(.*)$""")
    private val WORD = Regex("""<(\d{1,9}),(\d{1,9})(?:,-?\d+)?>""")
    private val METADATA = Regex("""^\[([A-Za-z][A-Za-z0-9_#]*):(.*)]$""")
    private const val LYRIC_CONTENT_MARKER = "LyricContent=\""

    private data class Document(val lines: List<LyricsTiming.RawLine>, val offsetMs: Int)

    /** Parses [text]; [translationText] (QQ `trans`, usually LRC) is merged by start time. */
    fun parse(text: String, translationText: String? = null, source: String = ""): Lyrics {
        if (text.isBlank()) return Lyrics(emptyList(), 0, source, "")
        val document = runCatching { parseDocument(extractLyricContent(text)) }.getOrDefault(Document(emptyList(), 0))
        val merged = LyricsTiming.mergeTranslation(document.lines, translationText)
        return LyricsTiming.finalize(raw = merged, offsetMs = document.offsetMs, source = source)
    }

    /**
     * Extracts the lyric body from QQ's XML wrapper (`<Lyric_1 LyricContent="..."/>`), decoding HTML entities.
     * Plain QRC text (no marker) is returned unchanged.
     */
    fun extractLyricContent(text: String): String {
        val markerIndex = text.indexOf(LYRIC_CONTENT_MARKER)
        if (markerIndex < 0) return normalize(text)
        val start = markerIndex + LYRIC_CONTENT_MARKER.length
        val end = text.indexOf('"', start)
        if (end < 0) return normalize(text.substring(start))
        val body = text.substring(start, end)
        return normalize(QqMusicApi.unescapeHtmlEntities(body))
    }

    private fun parseDocument(text: String): Document {
        val lines = ArrayList<LyricsTiming.RawLine>()
        var offsetMs = 0
        for (rawLine in normalize(text).split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val metadata = METADATA.matchEntire(line)
            if (metadata != null) {
                if (metadata.groupValues[1].equals("offset", ignoreCase = true)) {
                    metadata.groupValues[2].trim().toIntOrNull()?.let { offsetMs = it }
                }
                continue
            }

            val header = LINE.matchEntire(line) ?: continue
            val lineStart = header.groupValues[1].toLongOrNull() ?: continue
            val lineDuration = header.groupValues[2].toLongOrNull() ?: 0L
            val content = header.groupValues[3]
            if (content.isBlank()) continue

            val lineEnd = (lineStart + lineDuration).takeIf { it > lineStart }
            val matches = WORD.findAll(content).toList()
            if (matches.isEmpty()) {
                lines += LyricsTiming.RawLine(startMs = lineStart, text = content.trim(), endMs = lineEnd)
                continue
            }

            val words = ArrayList<LyricsTiming.RawWord>(matches.size)
            for ((index, match) in matches.withIndex()) {
                val rawWordStart = match.groupValues[1].toLongOrNull() ?: continue
                val wordDuration = match.groupValues[2].toLongOrNull() ?: 0L
                val wordStart = if (rawWordStart >= lineStart) rawWordStart else lineStart + rawWordStart
                val textEnd = matches.getOrNull(index + 1)?.range?.first ?: content.length
                val wordText = content.substring(match.range.last + 1, textEnd)
                if (wordText.isEmpty()) continue
                val wordEnd = (wordStart + wordDuration).takeIf { wordDuration > 0L } ?: 0L
                words += LyricsTiming.RawWord(startMs = wordStart, endMs = wordEnd, text = wordText)
            }
            val lineText = words.joinToString("") { it.text }.trim()
            if (lineText.isEmpty()) continue
            lines += LyricsTiming.RawLine(startMs = lineStart, text = lineText, endMs = lineEnd, words = words)
        }
        return Document(lines = lines, offsetMs = offsetMs)
    }

    private fun normalize(text: String): String =
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
}
