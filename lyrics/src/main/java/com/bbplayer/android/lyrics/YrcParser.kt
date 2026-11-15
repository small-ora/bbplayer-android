package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.Lyrics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Parser for NetEase Cloud Music YRC (逐字/verbatim) lyrics used by the upstream BBPlayer
 * (`.ai/recon/upstream-features.md` §5.4).
 *
 * Two payload shapes are accepted, both produced by the NetEase lyric endpoints:
 *
 * 1. text form — one lyric line per `\n`:
 *    ```
 *    [startMs,durationMs](wordStartMs,wordDurationMs,flag)字(wordStartMs,wordDurationMs,flag)字...
 *    ```
 *    Word timestamps are absolute ms from the song start (a value smaller than the line start is treated as a
 *    relative offset, which keeps lenient third-party payloads working). The line text is the concatenation of the
 *    word texts; each word becomes a core `LyricWord`.
 * 2. JSON form — the `{"t":ms,"c":[{"tx":"字"},...]}` array variant (per-word timings are absent there, so only
 *    line-level timing is produced).
 *
 * Malformed input never throws: unparseable lines are skipped, and the caller falls back to LRC when the result is
 * empty ([ProviderLyricParsers]).
 */
object YrcParser {

    private val LINE = Regex("""^\s*\[(\d{1,9}),(\d{1,9})]\s*(.*)$""")
    private val WORD = Regex("""\((\d{1,9}),(\d{1,9}),(-?\d+)\)""")
    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses [text]; [translationText] (e.g. NetEase `ytlrc`/`tlyric`) is merged by start time. */
    fun parse(text: String, translationText: String? = null, source: String = ""): Lyrics {
        if (text.isBlank()) return Lyrics(emptyList(), 0, source, "")
        val raw = runCatching {
            if (looksLikeJson(text)) parseJson(text) else parseText(text)
        }.getOrDefault(emptyList())
        val merged = LyricsTiming.mergeTranslation(raw, translationText)
        return LyricsTiming.finalize(raw = merged, offsetMs = 0, source = source)
    }

    private fun looksLikeJson(text: String): Boolean = text.trimStart().startsWith("[{")

    private fun parseText(text: String): List<LyricsTiming.RawLine> {
        val out = ArrayList<LyricsTiming.RawLine>()
        for (rawLine in normalize(text).split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val header = LINE.matchEntire(line) ?: continue
            val lineStart = header.groupValues[1].toLongOrNull() ?: continue
            val lineDuration = header.groupValues[2].toLongOrNull() ?: 0L
            val content = header.groupValues[3]
            if (content.isBlank()) continue

            val lineEnd = (lineStart + lineDuration).takeIf { it > lineStart }
            val matches = WORD.findAll(content).toList()
            if (matches.isEmpty()) {
                out += LyricsTiming.RawLine(startMs = lineStart, text = content.trim(), endMs = lineEnd)
                continue
            }

            val words = ArrayList<LyricsTiming.RawWord>(matches.size)
            for ((index, match) in matches.withIndex()) {
                val rawWordStart = match.groupValues[1].toLongOrNull() ?: continue
                val wordDuration = match.groupValues[2].toLongOrNull() ?: 0L
                // NetEase emits absolute word timestamps; tolerate line-relative payloads.
                val wordStart = if (rawWordStart >= lineStart) rawWordStart else lineStart + rawWordStart
                val textEnd = matches.getOrNull(index + 1)?.range?.first ?: content.length
                val wordText = content.substring(match.range.last + 1, textEnd)
                if (wordText.isEmpty()) continue
                val wordEnd = (wordStart + wordDuration).takeIf { wordDuration > 0L } ?: 0L
                words += LyricsTiming.RawWord(startMs = wordStart, endMs = wordEnd, text = wordText)
            }
            val lineText = words.joinToString("") { it.text }.trim()
            if (lineText.isEmpty()) continue
            out += LyricsTiming.RawLine(startMs = lineStart, text = lineText, endMs = lineEnd, words = words)
        }
        return out
    }

    private fun parseJson(text: String): List<LyricsTiming.RawLine> {
        val array = runCatching { JSON.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        val out = ArrayList<LyricsTiming.RawLine>(array.size)
        for (element in array) {
            val line = element as? JsonObject ?: continue
            val start = line["t"].asLongOrNull() ?: continue
            val content = line["c"].asArrayOrNull()
                ?.mapNotNull { it.asObjectOrNull()?.get("tx").asTextOrNull() }
                ?.joinToString("")
                .orEmpty()
                .trim()
            if (content.isEmpty()) continue
            out += LyricsTiming.RawLine(startMs = start.coerceAtLeast(0L), text = content)
        }
        return out
    }

    private fun normalize(text: String): String =
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
}
