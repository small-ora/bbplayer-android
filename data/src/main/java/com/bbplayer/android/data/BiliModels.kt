package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.AudioStream
import com.bbplayer.android.core.model.SearchPage
import com.bbplayer.android.core.model.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 一条可选的音频流（dash.audio / dolby / flac / durl 统一表示）。 */
internal data class AudioCandidate(
    val qualityId: Int,
    val url: String,
    val backupUrls: List<String> = emptyList(),
)

/** 选流规则：请求档位存在则用之，否则取 id 最大（最高）的可用档。 */
internal object AudioStreamSelector {
    fun select(candidates: List<AudioCandidate>, requestedQualityId: Int): AudioCandidate {
        require(candidates.isNotEmpty()) { "candidates must not be empty" }
        return candidates.firstOrNull { it.qualityId == requestedQualityId }
            ?: candidates.maxByOrNull { it.qualityId }
            ?: candidates.first()
    }
}

/**
 * B 站响应 → core 模型的纯函数映射（可单测，不依赖 Android / 网络）。
 * 所有字段读取都兼容 camelCase 与 snake_case 两种命名。
 */
internal object BiliMapper {

    private const val DEFAULT_MIME = "audio/mp4"

    // ---------------------------------------------------------------- 搜索

    fun searchPage(data: JsonObject, page: Int): SearchPage {
        val items = data.arrOrNull("result").toObjectList().mapNotNull(::searchItem)
        val totalPages = data.intOrNull("numPages", "num_pages")?.takeIf { it > 0 } ?: 0
        val hasMore = if (totalPages > 0) page < totalPages else items.size >= 20
        return SearchPage(items = items, page = page, hasMore = hasMore, totalPages = totalPages)
    }

    /** 搜索结果条目；过滤 `type == ketang`（课堂）与 bvid 为空的脏数据。 */
    fun searchItem(raw: JsonElement): Track? {
        val obj = raw as? JsonObject ?: return null
        if (obj.strOrNull("type") == "ketang") return null
        val bvid = obj.strOrNull("bvid")?.trim().orEmpty()
        if (bvid.isBlank()) return null
        return Track(
            bvid = bvid,
            cid = obj.longOrNull("cid") ?: 0L,
            title = HtmlText.clean(obj.strOrNull("title")),
            uploader = HtmlText.clean(obj.strOrNull("author")).ifBlank { "未知UP主" },
            uploaderMid = obj.longOrNull("mid") ?: 0L,
            coverUrl = normalizeImage(obj.strOrNull("pic")),
            durationMs = DurationParser.parseMs(obj.strOrNull("duration")),
            playCount = obj.longOrNull("play") ?: 0L,
            pubDateSec = obj.longOrNull("pubdate") ?: 0L,
        )
    }

    // ---------------------------------------------------------------- 详情

    /** `view` 接口 → 单个 Track（取第一 P）。 */
    fun viewTrack(view: JsonObject): Track {
        val pages = view.arrOrNull("pages").toObjectList()
        val firstPage = pages.firstOrNull()
        val durationSeconds = firstPage?.longOrNull("duration") ?: view.longOrNull("duration")
        return Track(
            bvid = view.strOrNull("bvid")?.trim().orEmpty(),
            cid = firstPage?.longOrNull("cid") ?: view.longOrNull("cid") ?: 0L,
            title = HtmlText.clean(view.strOrNull("title")),
            uploader = HtmlText.clean(view.objOrNull("owner")?.strOrNull("name")).ifBlank { "未知UP主" },
            uploaderMid = view.objOrNull("owner")?.longOrNull("mid") ?: 0L,
            coverUrl = normalizeImage(view.strOrNull("pic")),
            durationMs = DurationParser.secondsToMs(durationSeconds),
            playCount = view.objOrNull("stat")?.longOrNull("view") ?: 0L,
            pubDateSec = view.longOrNull("pubdate") ?: 0L,
        )
    }

    /** `view` 接口的 pages[] → 每 P 一个 Track（多 P 时补 `P2 分P标题` 用于展示）。 */
    fun videoPages(view: JsonObject): List<Track> {
        val pages = view.arrOrNull("pages").toObjectList()
        if (pages.isEmpty()) return emptyList()
        val base = viewTrack(view)
        val multiPage = pages.size > 1
        return pages.mapNotNull { page ->
            val cid = page.longOrNull("cid") ?: return@mapNotNull null
            val pageNo = page.intOrNull("page") ?: 0
            val part = HtmlText.clean(page.strOrNull("part"))
            val pageTitle = buildPageTitle(multiPage, pageNo, part, base.title)
            base.copy(
                cid = cid,
                pageTitle = pageTitle,
                coverUrl = normalizeImage(page.strOrNull("first_frame")).ifBlank { base.coverUrl },
                durationMs = DurationParser.secondsToMs(page.longOrNull("duration") ?: view.longOrNull("duration")),
            )
        }
    }

    private fun buildPageTitle(multiPage: Boolean, pageNo: Int, part: String, videoTitle: String): String {
        if (!multiPage) return ""
        if (part.isBlank() || part == videoTitle) return if (pageNo > 0) "P$pageNo" else ""
        return if (pageNo > 0) "P$pageNo $part" else part
    }

    // ------------------------------------------------------- 排行 / 相关列表

    /** `ranking/v2` 的 list[] 与 `archive/related` 的 data[] 结构一致。 */
    fun videoList(items: JsonArray?): List<Track> = items.toObjectList().mapNotNull { obj ->
        val bvid = obj.strOrNull("bvid")?.trim().orEmpty()
        if (bvid.isBlank()) return@mapNotNull null
        Track(
            bvid = bvid,
            cid = obj.longOrNull("cid") ?: 0L,
            title = HtmlText.clean(obj.strOrNull("title")),
            uploader = HtmlText.clean(obj.objOrNull("owner")?.strOrNull("name")).ifBlank { "未知UP主" },
            uploaderMid = obj.objOrNull("owner")?.longOrNull("mid") ?: 0L,
            coverUrl = normalizeImage(obj.strOrNull("pic")),
            durationMs = DurationParser.secondsToMs(obj.longOrNull("duration")),
            playCount = obj.objOrNull("stat")?.longOrNull("view") ?: 0L,
            pubDateSec = obj.longOrNull("pubdate") ?: 0L,
        )
    }

    // ---------------------------------------------------------------- 音频流

    /**
     * `playurl` 接口 → AudioStream。
     *
     * - [preferHighQuality] = true（已登录且用户开启高音质）：`dash.flac.audio` →
     *   `dash.dolby.audio` → 常规 AAC 最高档 → durl（老视频）；
     * - false（未登录 / 未开启）：保持 v4 行为，只在 `dash.audio` 里取请求档位或最高 AAC；
     *   不会因为服务端返回了 flac/dolby 就擅自升级档位。
     *
     * 返回的 `qualityId` 始终是**实际选中的档位**（30251/30250/30280…）。
     */
    fun audioStream(
        data: JsonObject,
        requestedQualityId: Int,
        preferHighQuality: Boolean = false,
    ): AudioStream {
        val dash = data.objOrNull("dash")
        val loudness = data.objOrNull("volume")?.doubleOrNull("measured_i", "measuredI")
        val aac = dash?.arrOrNull("audio").toObjectList().mapNotNull { audioCandidate(it) }
        // 关闭高音质：按请求档位，不存在时取最高 AAC（与 v4 行为一致）。
        val aacChosen = aac.takeIf { it.isNotEmpty() }
            ?.let { AudioStreamSelector.select(it, requestedQualityId) }
        // 开启高音质：无损优先；无损不可用时也必须回退到「最高 AAC」而不是用户请求的低档。
        val highestAac = aac.maxByOrNull { it.qualityId }
        val chosen = if (preferHighQuality) {
            flacCandidates(dash).firstOrNull()
                ?: dolbyCandidates(dash).firstOrNull()
                ?: highestAac
        } else {
            // 保持 v4 行为：仅当服务端没有任何普通 AAC 时才回退 dolby/flac（老视频兼容）。
            aacChosen
                ?: dolbyCandidates(dash).firstOrNull()
                ?: flacCandidates(dash).firstOrNull()
        }

        if (chosen != null) {
            return AudioStream(
                url = chosen.url,
                backupUrls = chosen.backupUrls,
                qualityId = chosen.qualityId,
                mimeType = DEFAULT_MIME,
                measuredLoudnessDb = loudness,
            )
        }
        // 老视频没有 dash，只有整段 flv/mp4 的 durl。
        val durl = data.arrOrNull("durl").toObjectList().firstOrNull()
            ?: throw AppException("未找到可用音频流，视频可能已被删除或限制播放", retryable = false)
        val url = durl.strOrNull("url", "baseUrl", "base_url")?.takeIf { it.isNotBlank() }
            ?: throw AppException("未找到可用音频流，视频可能已被删除或限制播放", retryable = false)
        return AudioStream(
            url = url,
            backupUrls = durl.stringList("backup_url", "backupUrl"),
            qualityId = requestedQualityId,
            mimeType = DEFAULT_MIME,
            measuredLoudnessDb = loudness,
        )
    }

    /** `dash.flac.audio`（对象或数组）→ 无损候选；缺 id 时按 30251 标注。 */
    private fun flacCandidates(dash: JsonObject?): List<AudioCandidate> =
        candidatesOf(dash?.objOrNull("flac")?.get("audio"), AudioQuality.HI_RES.id)

    /** `dash.dolby.audio`（对象或数组）→ 杜比候选；缺 id 时按 30250 标注。 */
    private fun dolbyCandidates(dash: JsonObject?): List<AudioCandidate> =
        candidatesOf(dash?.objOrNull("dolby")?.get("audio"), AudioQuality.DOLBY.id)

    private fun candidatesOf(element: JsonElement?, fallbackQualityId: Int): List<AudioCandidate> =
        when (element) {
            is JsonObject -> listOfNotNull(audioCandidate(element, fallbackQualityId))
            is JsonArray -> element.toObjectList().mapNotNull { audioCandidate(it, fallbackQualityId) }
            else -> emptyList()
        }

    private fun audioCandidate(obj: JsonObject, fallbackQualityId: Int? = null): AudioCandidate? {
        val url = obj.strOrNull("baseUrl", "base_url", "url")?.takeIf { it.isNotBlank() } ?: return null
        return AudioCandidate(
            qualityId = obj.intOrNull("id")?.takeIf { it > 0 } ?: fallbackQualityId ?: 0,
            url = url,
            backupUrls = obj.stringList("backupUrl", "backup_url"),
        )
    }

    // ---------------------------------------------------------------- 工具

    /** 封面地址补全协议：`//i0.hdslb.com/...` → `https://...`，http 升级为 https。 */
    fun normalizeImage(raw: String?): String {
        val text = raw?.trim().orEmpty()
        return when {
            text.isEmpty() -> ""
            text.startsWith("//") -> "https:$text"
            text.startsWith("http://") -> "https://${text.removePrefix("http://")}"
            else -> text
        }
    }
}
