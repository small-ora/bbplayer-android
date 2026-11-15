package com.bbplayer.android.playback.download

import com.bbplayer.android.core.model.DownloadEntry
import com.bbplayer.android.core.model.DownloadStatus
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-disk download index (`filesDir/downloads.json`).
 *
 * The index is a cache, not the source of truth: any entry that no longer makes
 * sense after a restart (killed while downloading, deleted file, duplicated
 * key, corrupt progress) is repaired or dropped by [DownloadIndexSanitizer]
 * instead of crashing the download screen.
 */
@Serializable
data class DownloadIndex(
    val version: Int = CURRENT_VERSION,
    val entries: List<DownloadEntry> = emptyList(),
) {
    companion object {
        /** v2 introduced the download index; unknown fields are ignored on read. */
        const val CURRENT_VERSION: Int = 2
        val EMPTY: DownloadIndex = DownloadIndex()
    }
}

/**
 * Repairs [DownloadEntry] rows loaded from disk.
 *
 * Migration rules (SPEC C1/C2 + PLAN v2):
 * - `QUEUED` / `DOWNLOADING` -> `PAUSED` (the process died mid-transfer; the
 *   partial file is kept so a later enqueue can Range-resume);
 * - `progress` clamped to `0..1`, negative byte counters clamped to 0;
 * - `COMPLETED` rows whose file vanished are demoted to `FAILED` with an
 *   actionable message instead of pretending to be playable offline;
 * - duplicate `track.key` rows keep the last occurrence (index append order);
 * - rows without a usable bvid are dropped.
 */
object DownloadIndexSanitizer {
    const val MSG_INTERRUPTED = "下载中断，可重新下载"
    const val MSG_FILE_MISSING = "下载文件已丢失，请重新下载"

    fun sanitize(
        entries: List<DownloadEntry>,
        fileExists: (String) -> Boolean = { it.isNotBlank() && File(it).isFile },
    ): List<DownloadEntry> {
        val deduped = LinkedHashMap<String, DownloadEntry>()
        for (raw in entries) {
            val track = raw.track
            if (track.bvid.isBlank()) continue
            val interrupted = raw.status == DownloadStatus.QUEUED ||
                raw.status == DownloadStatus.DOWNLOADING
            var status = when (raw.status) {
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> DownloadStatus.PAUSED
                else -> raw.status
            }
            var errorMessage = raw.errorMessage
            if (interrupted) errorMessage = errorMessage ?: MSG_INTERRUPTED

            var progress = if (raw.progress.isNaN() || raw.progress.isInfinite()) {
                0f
            } else {
                raw.progress.coerceIn(0f, 1f)
            }
            var downloadedBytes = raw.downloadedBytes.coerceAtLeast(0L)
            val totalBytes = raw.totalBytes.coerceAtLeast(0L)

            if (status == DownloadStatus.COMPLETED) {
                if (!fileExists(raw.filePath)) {
                    status = DownloadStatus.FAILED
                    errorMessage = MSG_FILE_MISSING
                    progress = 0f
                    downloadedBytes = 0L
                } else {
                    progress = 1f
                    if (totalBytes > 0L) downloadedBytes = totalBytes
                }
            }
            deduped[track.key] = raw.copy(
                status = status,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                errorMessage = errorMessage,
            )
        }
        return deduped.values.toList()
    }
}

/** Atomic JSON store for [DownloadIndex] (tmp + rename, same as the resume snapshot). */
class DownloadIndexStore(
    private val file: File,
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fileExists: (String) -> Boolean = { it.isNotBlank() && File(it).isFile },
) {
    val filePath: String get() = file.path

    /** Missing / corrupt / empty index -> empty list (never throws). */
    suspend fun load(): List<DownloadEntry> = withContext(ioDispatcher) {
        if (!file.isFile || file.length() == 0L) return@withContext emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return@withContext emptyList()
        val decoded = runCatching {
            json.decodeFromString(DownloadIndex.serializer(), text)
        }.getOrNull() ?: return@withContext emptyList()
        DownloadIndexSanitizer.sanitize(decoded.entries, fileExists)
    }

    suspend fun save(entries: List<DownloadEntry>) = withContext(ioDispatcher) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val payload = json.encodeToString(
            DownloadIndex.serializer(),
            DownloadIndex(entries = entries),
        )
        val tmp = File(file.path + ".tmp")
        tmp.writeText(payload)
        if (!tmp.renameTo(file)) {
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                file.writeText(payload)
                tmp.delete()
            }
        }
    }

    companion object {
        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

        fun defaultFile(filesDir: File): File =
            File(filesDir, DownloadNames.INDEX_FILE)
    }
}
