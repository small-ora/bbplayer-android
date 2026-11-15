package com.bbplayer.android.playback.download

import com.bbplayer.android.core.model.Track

/**
 * File-name rules for the offline cache and for exports to the public Music
 * folder. Pure Kotlin (no Android types) so every rule is covered by JVM tests.
 *
 * Internal cache files intentionally use an opaque `<bvid>_<cid>.m4s` name so
 * they can never collide and never need the (user editable) title; exported
 * files use the human readable `<title> - <uploader>.m4a` pattern from SPEC C4.
 */
object DownloadNames {
    const val DOWNLOAD_DIR = "downloads"
    const val INDEX_FILE = "downloads.json"
    const val INTERNAL_EXTENSION = "m4s"
    const val EXPORT_MIME_TYPE = "audio/mp4"
    const val EXPORT_EXTENSION = "m4a"
    const val LRC_EXTENSION = "lrc"
    const val PUBLIC_SUBDIR = "BBPlayer"

    /** Keep enough room for the `.m4a` suffix and for MediaStore de-duplication. */
    const val MAX_BASE_NAME_LENGTH = 80

    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Replaces characters that are invalid on FAT/exFAT (and path separators),
     * collapses whitespace, strips leading/trailing dots+spaces and caps the
     * length. Returns `track` when nothing usable is left (e.g. input `"///"`).
     */
    fun sanitize(raw: String, maxLength: Int = MAX_BASE_NAME_LENGTH): String {
        // Collapse whitespace first so tab/newline turn into a space rather than "_".
        val collapsed = WHITESPACE.replace(raw, " ")
        val replaced = ILLEGAL_CHARS.replace(collapsed, "_")
        val trimmed = replaced.trim().trim('.', ' ')
        val fallback = trimmed.ifEmpty { "track" }
        if (fallback.length <= maxLength) return fallback
        return fallback.take(maxLength).trim().trim('.', ' ').ifEmpty { "track" }
    }

    /** Opaque cache name: `<sanitized bvid>_<cid>.m4s`. */
    fun internalFileName(track: Track): String =
        "${sanitize(track.bvid, maxLength = 64)}_${track.cid}.m4s"

    /** `<title> - <uploader>` (uploader omitted when blank), sanitized. */
    fun exportBaseName(track: Track): String {
        val title = sanitize(track.title.ifBlank { track.displayTitle })
        if (track.uploader.isBlank()) return title
        return "$title - ${sanitize(track.uploader)}"
    }

    fun exportFileName(track: Track): String = exportBaseName(track) + ".m4a"

    /** Same base name as [exportFileName] so players/lyric apps auto-pair them. */
    fun exportLrcFileName(track: Track): String = exportBaseName(track) + ".lrc"

    /** Public relative directory used on API 29+ (`RELATIVE_PATH`). */
    fun publicRelativePath(): String = "Music/$PUBLIC_SUBDIR"
}
