package com.bbplayer.android.core.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- comments

@Serializable
data class CommentItem(
    val id: Long,
    val userName: String,
    val avatarUrl: String,
    val content: String,
    val likeCount: Long,
    val createdAtSec: Long,
    val replyCount: Int,
    val replies: List<CommentItem> = emptyList(),
)

@Serializable
data class CommentPage(
    val items: List<CommentItem>,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val total: Int = 0,
)

// ------------------------------------------------- external playlist import

@Serializable
enum class ExternalSource(val label: String) {
    NETEASE("网易云音乐"),
    QQ_MUSIC("QQ 音乐");

    companion object {
        fun detect(input: String): ExternalSource? = when {
            input.contains("music.163.com") || input.contains("163cn.tv") -> NETEASE
            input.contains("y.qq.com") || input.contains("qq.com") -> QQ_MUSIC
            else -> null
        }
    }
}

@Serializable
data class ExternalTrack(
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
    val album: String = "",
)

@Serializable
data class ExternalPlaylist(
    val source: ExternalSource,
    val sourceId: String,
    val name: String,
    val coverUrl: String = "",
    val description: String = "",
    val tracks: List<ExternalTrack>,
)

@Serializable
data class ImportMatch(
    val index: Int,
    val external: ExternalTrack,
    val matched: Track? = null,
    val score: Float = 0f,
)

@Serializable
data class ImportProgress(
    val done: Int,
    val total: Int,
    val matches: List<ImportMatch> = emptyList(),
    val finished: Boolean = false,
)

// ------------------------------------------------------ subtitles / podcast

@Serializable
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

@Serializable
data class VideoSubtitle(
    val language: String,
    val languageLabel: String,
    val cues: List<SubtitleCue>,
)

// --------------------------------------------------------------- downloads

@Serializable
enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

@Serializable
data class DownloadEntry(
    val track: Track,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String = "",
    val qualityId: Int = 0,
    val createdAt: Long = 0L,
    val errorMessage: String? = null,
) {
    val key: String get() = track.key
}

// ---------------------------------------------------------------- rankings

@Serializable
data class ListeningRank(
    val track: Track,
    val playCount: Int,
    val listenedMs: Long,
)

@Serializable
data class DailyHistory(
    val date: String,
    val entries: List<HistoryEntry>,
    val listenedMs: Long,
)

// ------------------------------------------------------------ app updates

@Serializable
data class ReleaseInfo(
    val version: String,
    val tagName: String,
    val url: String,
    val notes: String = "",
    val apkUrl: String? = null,
    val publishedAt: String = "",
)

// ------------------------------------------------------------ appearance

enum class PlayerBackgroundStyle(val label: String) {
    GRADIENT("封面渐变"),
    SOLID("纯色"),
    PURE_WHITE("纯白（墨水屏）"),
}

enum class MiniPlayerStyle(val label: String) { DOCK("底部停靠"), FLOAT("悬浮胶囊") }

enum class LyricVisualStyle(val label: String) { MODERN("现代（左对齐）"), OLD_SCHOOL("经典（居中）") }

enum class AccentPreset(val label: String, val light: Long, val dark: Long) {
    SALT("椒盐蓝", 0xFF0470E6, 0xFF0088FF),
    MINT("薄荷绿", 0xFF2E7D63, 0xFF5CCBA5),
    SUNSET("落日橙", 0xFFB3541E, 0xFFFF9F6B),
    VIOLET("暮云紫", 0xFF6750A4, 0xFFC4B5FD),
    ROSE("樱粉", 0xFFB0405E, 0xFFFF9EB8),
}
