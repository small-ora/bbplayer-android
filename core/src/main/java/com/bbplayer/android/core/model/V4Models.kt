package com.bbplayer.android.core.model

import kotlinx.serialization.Serializable

/** Which online provider to try first when fetching lyrics. */
enum class LyricSource(val label: String) {
    AUTO("自动（QQ → 网易云）"),
    QQ("QQ 音乐优先"),
    NETEASE("网易云优先"),
}

/** Persisted import task so a long QQ/Netease playlist import can pause & resume. */
@Serializable
data class ImportTask(
    val input: String,
    val playlist: ExternalPlaylist,
    val matches: List<ImportMatch> = emptyList(),
    val nextIndex: Int = 0,
    val status: ImportStatus = ImportStatus.PAUSED,
    val updatedAt: Long = 0L,
    val errorMessage: String? = null,
) {
    val total: Int get() = playlist.tracks.size
    val done: Int get() = matches.size
    val isFinished: Boolean get() = nextIndex >= total
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

@Serializable
enum class ImportStatus { IDLE, RUNNING, PAUSED, DONE, FAILED }

/** Result of one matching batch (batching keeps the risk-control footprint small). */
@Serializable
data class ImportBatchResult(
    val task: ImportTask,
    val blocked: Boolean = false,
    val retryAfterMs: Long = 0L,
)
