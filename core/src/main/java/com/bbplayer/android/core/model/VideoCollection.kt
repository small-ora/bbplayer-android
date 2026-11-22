package com.bbplayer.android.core.model

import kotlinx.serialization.Serializable

/** 「合集」的来源：多 P（分集）视频 或 B 站合集（ugc_season）。 */
@Serializable
enum class VideoCollectionKind {
    /** 同一个 BV 下的多个分 P（`view.pages[]`），例如 100 P 的歌单合集。 */
    MULTI_PAGE,

    /** B 站「合集」/ season（`view.ugc_season.sections[].episodes[]`），每集是独立 BV。 */
    SEASON,
}

/**
 * 一个可当作歌单播放的视频合集（匿名解析，无需登录）。
 *
 * [tracks] 至少 2 首；普通单视频不会构造本对象——[com.bbplayer.android.core.contracts.MusicRepository.getVideoCollection]
 * 对单视频返回 `Result.success(null)`。
 */
@Serializable
data class VideoCollection(
    /** 作为入口的视频 BV（多 P 视频本身 / season 中用户点开的某一集）。 */
    val bvid: String,
    /** 歌单展示名：多 P 用视频标题，season 用合集标题。 */
    val title: String,
    val uploader: String = "",
    val coverUrl: String = "",
    val kind: VideoCollectionKind = VideoCollectionKind.MULTI_PAGE,
    /** 有序曲目：多 P 按 P 序号，season 按 section/episode 顺序。 */
    val tracks: List<Track> = emptyList(),
    /**
     * season 兜底分页失败 / 未能补全 `ep_count` 时为 true：曲目可能不完整，UI 需要提示。
     * 多 P 合集永远为 false。
     */
    val partial: Boolean = false,
) {
    val size: Int get() = tracks.size

    /** 只有 2 首及以上才值得当作歌单展示；单首按普通视频处理。 */
    val isCollection: Boolean get() = tracks.size > 1
}
