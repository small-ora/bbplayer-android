package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.SubtitleRepository
import com.bbplayer.android.core.model.VideoSubtitle

/**
 * [SubtitleRepository] 的匿名 B 站实现。
 *
 * 1. `/x/player/v2?bvid=&cid=` 取 `data.subtitle.subtitles[]`（优先中文字幕）；
 * 2. 只允许 https + `.hdslb.com` / `.bilibili.com` 的字幕地址；
 * 3. 拉取字幕 JSON（`body[].from/to/content`，秒）并转成毫秒。
 *
 * 绝大多数音乐视频没有字幕，此时返回 `Result.success(null)`（不是失败）。
 */
class BiliSubtitleRepository internal constructor(
    private val api: BiliApi,
    private val http: HttpFetcher,
) : SubtitleRepository {

    constructor(api: BiliApi) : this(api, HttpFetcher())

    /** 便捷构造：由 AppContainer 直接调用。 */
    constructor(context: Context) : this(BiliApi(context))

    override suspend fun fetchSubtitle(bvid: String, cid: Long): Result<VideoSubtitle?> = repoResult {
        val safeBvid = bvid.trim()
        if (safeBvid.isEmpty()) throw AppException("视频ID不能为空", retryable = false)
        if (cid <= 0L) throw AppException("视频分P信息不完整，无法加载字幕", retryable = false)
        val data = api.getObject(
            path = "/x/player/v2",
            params = mapOf("bvid" to safeBvid, "cid" to cid.toString()),
        )
        val ref = SubtitleMapper.pickSubtitle(data) ?: return@repoResult null
        val root = http.getJsonObject(ref.url, HttpHeaders.BILIBILI)
        val cues = SubtitleMapper.parseCues(root)
        if (cues.isEmpty()) {
            null
        } else {
            VideoSubtitle(
                language = ref.language,
                languageLabel = ref.languageLabel,
                cues = cues,
            )
        }
    }
}
