package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.Lyrics

/**
 * Turns a provider [LyricPayload] into the frozen core [Lyrics] model, preferring per-word (verbatim) tracks:
 *
 * 1. NetEase YRC (`yrc`) → [YrcParser];
 * 2. QQ QRC (`qrc`) → [QrcParser];
 * 3. fallback to the plain LRC / SPL track → [LyricsParser] (AUTO), with the separate translation merged.
 *
 * A verbatim track that parses to zero lines (malformed / unsupported) never fails the request: it falls back to the
 * plain LRC track. Exceptions are contained here so a provider payload can never crash playback.
 */
internal object ProviderLyricParsers {

    fun parse(payload: LyricPayload, source: String): Lyrics {
        val translation = payload.translation
        val verbatim = runCatching { parseVerbatim(payload, translation, source) }.getOrNull()
        if (verbatim != null && verbatim.lines.isNotEmpty()) return verbatim
        return runCatching {
            LyricsParser.parse(payload.lrc, LyricsFormat.AUTO, translation, source)
        }.getOrElse { Lyrics(emptyList(), 0, source, "") }
    }

    /** Verbatim (word-timed) track only; null when the payload has none or it parses to nothing. */
    fun parseVerbatim(payload: LyricPayload, translation: String?, source: String): Lyrics? {
        payload.yrc?.takeIf { it.isNotBlank() }?.let { yrc ->
            val parsed = YrcParser.parse(yrc, translation, source)
            if (parsed.lines.isNotEmpty()) return parsed
        }
        payload.qrc?.takeIf { it.isNotBlank() }?.let { qrc ->
            val parsed = QrcParser.parse(qrc, translation, source)
            if (parsed.lines.isNotEmpty()) return parsed
        }
        return null
    }
}
