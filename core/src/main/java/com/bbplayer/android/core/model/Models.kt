package com.bbplayer.android.core.model

import kotlinx.serialization.Serializable

/**
 * A playable audio item: one Bilibili video page (分 P) played as audio.
 */
@Serializable
data class Track(
    val bvid: String,
    val cid: Long,
    val title: String,
    val uploader: String = "",
    val uploaderMid: Long = 0L,
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val playCount: Long = 0L,
    val pageTitle: String = "",
    val pubDateSec: Long = 0L,
    val qualityId: Int = 0,
) {
    /** Stable identity used by queue / history / lyrics cache / favorites. */
    val key: String get() = "$bvid:$cid"

    /** "Title" or "Title · P2 part" when the video has multiple pages. */
    val displayTitle: String
        get() = if (pageTitle.isNotBlank() && pageTitle != title) "$title · $pageTitle" else title
}

@Serializable
data class AudioStream(
    val url: String,
    val backupUrls: List<String> = emptyList(),
    val qualityId: Int,
    val mimeType: String = "audio/mp4",
    val measuredLoudnessDb: Double? = null,
)

@Serializable
enum class AudioQuality(val id: Int, val label: String) {
    Q64(30216, "64K"),
    Q128(30232, "128K"),
    Q192(30280, "192K"),
    DOLBY(30250, "杜比全景声"),
    HI_RES(30251, "Hi-Res 无损");

    companion object {
        fun fromId(id: Int): AudioQuality = entries.firstOrNull { it.id == id } ?: Q192
        /** Highest anonymous-safe quality. */
        val DEFAULT: AudioQuality = Q192
    }
}

@Serializable
data class SearchPage(
    val items: List<Track>,
    val page: Int,
    val hasMore: Boolean,
    val totalPages: Int = 0,
) {
    companion object {
        fun empty(page: Int = 1) = SearchPage(emptyList(), page, false, 0)
    }
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** 最近一次从该歌单开始播放的时间（0 = 从未播放），首页「最近播放的歌单」用。 */
    val lastPlayedAt: Long = 0L,
    val tracks: List<Track> = emptyList(),
) {
    val coverUrls: List<String> get() = tracks.asSequence().map { it.coverUrl }.filter { it.isNotBlank() }.take(4).toList()
}

@Serializable
data class FavoriteEntry(
    val track: Track,
    val addedAt: Long,
)

@Serializable
data class HistoryEntry(
    val track: Track,
    val playedAt: Long,
    val listenedMs: Long,
    val completed: Boolean,
)

@Serializable
data class DailyStat(
    val date: String,
    val listenedMs: Long,
    val playCount: Int,
)

data class ListeningSummary(
    val totalListenedMs: Long = 0L,
    val totalPlayCount: Int = 0,
    val dailyStats: List<DailyStat> = emptyList(),
)

@Serializable
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

@Serializable
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList(),
)

@Serializable
data class Lyrics(
    val lines: List<LyricLine>,
    val offsetMs: Int = 0,
    val source: String = "",
    val plainText: String = "",
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    fun activeIndex(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val t = positionMs + offsetMs
        var low = 0
        var high = lines.size - 1
        var ans = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].startMs <= t) {
                ans = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return ans
    }
}

@Serializable
data class LyricCandidate(
    val title: String,
    val artist: String,
    val source: String,
    val sourceId: String,
    val lrc: String = "",
)

enum class PlayMode(val label: String) {
    SEQUENCE("顺序播放"),
    REPEAT_ALL("列表循环"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机播放");

    fun next(): PlayMode = when (this) {
        SEQUENCE -> REPEAT_ALL
        REPEAT_ALL -> REPEAT_ONE
        REPEAT_ONE -> SHUFFLE
        SHUFFLE -> SEQUENCE
    }
}

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"), PURE_WHITE("纯白") }

@Serializable
data class PlaybackState(
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val speed: Float = 1f,
    val sleepTimerEndsAt: Long = 0L,
    val errorMessage: String? = null,
)
