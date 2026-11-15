package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.Lyrics

/** Lyric text formats understood by [LyricsParser]. */
enum class LyricsFormat {
    /** Detect the format from the content ([detectFormat]). */
    AUTO,

    /** Standard / enhanced LRC ([LrcParser]). */
    LRC,

    /** Salt Player Lyrics ([SplParser]). */
    SPL;

    companion object {
        /** AUTO detection: SPL when any line carries inline word-level timing tags, LRC otherwise. */
        fun detect(text: String): LyricsFormat = if (SplParser.hasWordLevelTags(text)) SPL else LRC
    }
}

/**
 * Unified entry point for parsing lyric text, replacing direct [LrcParser] / [SplParser] calls.
 *
 * ```kotlin
 * LyricsParser.parse(raw)                             // AUTO: SPL when word-level tags are present
 * LyricsParser.parse(raw, LyricsFormat.SPL)           // force SPL
 * LyricsParser.parse(raw, translationText = tlyric)   // merge a separate translation track
 * LyricsParser.parseProvider(payload, source)         // NetEase YRC / QQ QRC aware helper
 * ```
 *
 * `AUTO` only switches to SPL when the text actually contains inline `<mm:ss.xx>` / `[mm:ss.xx]` word markers, so
 * plain LRC keeps the v1 [LrcParser] behavior (3 s minimum last line, etc.) byte for byte.
 */
object LyricsParser {

    /** Parses [text] in [format], optionally merging a separate [translationText] track. Never throws. */
    fun parse(
        text: String,
        format: LyricsFormat = LyricsFormat.AUTO,
        translationText: String? = null,
        source: String = "",
    ): Lyrics = when (format) {
        LyricsFormat.LRC -> LrcParser.parse(text, translationText, source)
        LyricsFormat.SPL -> SplParser.parse(text, translationText, source)
        LyricsFormat.AUTO -> when (detectFormat(text)) {
            LyricsFormat.SPL -> SplParser.parse(text, translationText, source)
            else -> LrcParser.parse(text, translationText, source)
        }
    }

    /** The format AUTO would pick for [text]. */
    fun detectFormat(text: String): LyricsFormat = LyricsFormat.detect(text)
}
