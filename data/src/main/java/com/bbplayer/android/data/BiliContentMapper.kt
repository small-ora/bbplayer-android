package com.bbplayer.android.data

import com.bbplayer.android.core.model.BiliCollection
import com.bbplayer.android.core.model.FavoriteFolder
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.model.TrackPage
import com.bbplayer.android.core.model.UploaderInfo
import kotlinx.serialization.json.JsonObject

/**
 * 收藏夹 / 合集 / 稍后再看 / UP 主接口响应的纯函数映射（可单测，不依赖 Android 与网络）。
 *
 * B 站同一含义的字段在不同接口里命名不一致（`cover` / `pic`、`cnt_info.play` / `stat.view`、
 * `duration` 秒数 / `length` `mm:ss`），统一在这里做兼容；所有封面走
 * [BiliMapper.normalizeImage] 补全协议。
 */
internal object BiliContentMapper {

    // ---------------------------------------------------------------- 收藏夹

    /** `list-all` 的 list[] 项；缺 id 的脏数据丢弃。默认收藏夹 `attr != 0`。 */
    fun favoriteFolder(obj: JsonObject): FavoriteFolder? {
        val id = obj.longOrNull("id")?.takeIf { it > 0L } ?: return null
        return FavoriteFolder(
            id = id,
            title = HtmlText.clean(obj.strOrNull("title")).ifBlank { "收藏夹" },
            coverUrl = BiliMapper.normalizeImage(obj.strOrNull("cover", "pic")),
            mediaCount = obj.intOrNull("media_count", "mediaCount") ?: 0,
            isDefault = (obj.intOrNull("attr") ?: 0) != 0,
        )
    }

    fun favoriteFolders(data: JsonObject): List<FavoriteFolder> =
        data.arrOrNull("list").toObjectList().mapNotNull(::favoriteFolder).distinctBy { it.id }

    /**
     * `list-all?rid=&type=2` → 包含该视频的收藏夹 id。
     *
     * 该接口带 `rid` 时通常**只返回包含该视频的收藏夹**，但部分灰度响应会返回全部收藏夹并
     * 附 `fav_state`。这里兼容两种形状：有状态字段就按状态过滤，没有就把返回项全部视为包含。
     */
    fun favoriteStateIds(data: JsonObject): List<Long> {
        val folders = data.arrOrNull("list").toObjectList()
        if (folders.isEmpty()) return emptyList()
        val withMarker = folders.filter { hasFavoriteMarker(it) }
        val source = withMarker.ifEmpty { folders }
        return source
            .filter { folder ->
                val marker = folder.longOrNull("fav_state", "favState")
                    ?: folder.booleanOrNull("favoured")?.let { if (it) 1L else 0L }
                marker == null || marker != 0L
            }
            .mapNotNull { it.longOrNull("id")?.takeIf { id -> id > 0L } }
            .distinct()
    }

    private fun hasFavoriteMarker(obj: JsonObject): Boolean =
        obj.containsKey("fav_state") || obj.containsKey("favState") || obj.containsKey("favoured")

    // ------------------------------------------------------------ 视频条目

    /** 收藏夹 `medias[]` / 合集 `medias[]` / `archives[]` 共用的条目映射。 */
    fun mediaTrack(obj: JsonObject): Track? {
        val bvid = obj.strOrNull("bvid")?.trim().orEmpty()
        if (bvid.isEmpty()) return null
        val title = HtmlText.clean(obj.strOrNull("title")).ifBlank { "未知标题" }
        // 失效视频仍会出现在收藏夹里（bvid 常保留），直接过滤掉避免点了不能播。
        if (title == "已失效视频") return null
        val upper = obj.objOrNull("upper", "owner")
        return Track(
            bvid = bvid,
            cid = obj.longOrNull("cid") ?: 0L,
            title = title,
            uploader = HtmlText.clean(upper?.strOrNull("name")).ifBlank { "未知UP主" },
            uploaderMid = upper?.longOrNull("mid") ?: obj.longOrNull("mid") ?: 0L,
            coverUrl = BiliMapper.normalizeImage(obj.strOrNull("cover", "pic")),
            durationMs = durationMsOf(obj),
            playCount = playCountOf(obj),
            pubDateSec = obj.longOrNull("pubtime", "ctime", "created", "pubdate") ?: 0L,
        )
    }

    /** 收藏夹内容页 → TrackPage（`has_more` 优先，缺失时按页大小兜底）。 */
    fun favoriteTracks(data: JsonObject, page: Int, pageSize: Int): TrackPage =
        trackPage(data, listOf("medias"), page, pageSize)

    /** 合集内容页（`medias[]` 老形状 / `archives[]` 新形状都兼容）。 */
    fun collectionTracks(data: JsonObject, page: Int, pageSize: Int): TrackPage =
        trackPage(data, listOf("medias", "archives"), page, pageSize)

    private fun trackPage(
        data: JsonObject,
        arrayKeys: List<String>,
        page: Int,
        pageSize: Int,
    ): TrackPage {
        val items = arrayKeys
            .mapNotNull { key -> data.arrOrNull(key)?.toObjectList() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .mapNotNull(::mediaTrack)
        val total = data.objOrNull("page")?.intOrNull("total")
            ?: data.intOrNull("total")
            ?: data.objOrNull("info")?.intOrNull("media_count", "mediaCount")
            ?: 0
        val hasMore = boolish(data, "has_more", "hasMore")
            ?: (items.isNotEmpty() && items.size >= pageSize)
        return TrackPage(items = items, page = page, hasMore = hasMore, total = total)
    }

    // ---------------------------------------------------------------- 合集

    /** `collected/list` 收藏夹项 / `seasons_series_list` 合集·系列项统一映射。 */
    fun collection(obj: JsonObject): BiliCollection? {
        val meta = obj.objOrNull("meta")
        val id = obj.longOrNull("id", "season_id", "seasonId", "series_id", "seriesId", "media_id")
            ?.takeIf { it > 0L }
            ?: return null
        return BiliCollection(
            id = id,
            title = HtmlText.clean(obj.strOrNull("title", "name") ?: meta?.strOrNull("name"))
                .ifBlank { "未命名合集" },
            coverUrl = BiliMapper.normalizeImage(
                obj.strOrNull("cover", "pic") ?: meta?.strOrNull("cover", "pic"),
            ),
            count = obj.intOrNull("media_count", "mediaCount", "count", "total")
                ?: meta?.intOrNull("total", "media_count")
                ?: 0,
            description = HtmlText.clean(
                obj.strOrNull("description", "intro") ?: meta?.strOrNull("description", "intro"),
            ),
        )
    }

    fun collections(data: JsonObject): List<BiliCollection> =
        data.arrOrNull("list").toObjectList().mapNotNull(::collection).distinctBy { it.id }

    /** 空间「合集和系列」：`items_lists.seasons_list` + `items_lists.series_list`。 */
    fun spaceCollections(data: JsonObject): List<BiliCollection> {
        val items = data.objOrNull("items_lists")
        val seasons = items?.arrOrNull("seasons_list").toObjectList()
        val series = items?.arrOrNull("series_list").toObjectList()
        val flat = data.arrOrNull("list").toObjectList()
        return (seasons + series + flat).mapNotNull(::collection).distinctBy { it.id }
    }

    // ------------------------------------------------------------ 稍后再看

    /** `toview` 的 list[] 项（`progress` 与 Track 无关，播放进度由本地续播记录）。 */
    fun watchLaterTrack(obj: JsonObject): Track? {
        val bvid = obj.strOrNull("bvid")?.trim().orEmpty()
        if (bvid.isEmpty()) return null
        val owner = obj.objOrNull("owner", "upper")
        return Track(
            bvid = bvid,
            cid = obj.longOrNull("cid") ?: 0L,
            title = HtmlText.clean(obj.strOrNull("title")).ifBlank { "未知标题" },
            uploader = HtmlText.clean(owner?.strOrNull("name")).ifBlank { "未知UP主" },
            uploaderMid = owner?.longOrNull("mid") ?: 0L,
            coverUrl = BiliMapper.normalizeImage(obj.strOrNull("cover", "pic")),
            durationMs = durationMsOf(obj),
            playCount = playCountOf(obj),
            pubDateSec = obj.longOrNull("pubtime", "ctime", "pubdate") ?: 0L,
        )
    }

    fun watchLaterTracks(data: JsonObject): List<Track> =
        data.arrOrNull("list").toObjectList().mapNotNull(::watchLaterTrack)

    // ---------------------------------------------------------------- UP 主

    /** `acc/info` 的 data → UploaderInfo；[followed] 来自 `/x/relation`，[follower] 可外部兜底。 */
    fun uploaderInfo(
        data: JsonObject,
        followed: Boolean,
        follower: Long? = null,
    ): UploaderInfo? {
        val mid = data.longOrNull("mid")?.takeIf { it > 0L } ?: return null
        return UploaderInfo(
            mid = mid,
            name = HtmlText.clean(data.strOrNull("name", "uname")).ifBlank { "未知UP主" },
            avatarUrl = BiliMapper.normalizeImage(data.strOrNull("face", "avatar")),
            sign = HtmlText.clean(data.strOrNull("sign")),
            follower = follower ?: data.longOrNull("follower", "fans") ?: 0L,
            followed = followed,
        )
    }

    /** `/x/relation` 的 data：`attribute` 2 / 6 表示已关注（6 = 互关）。 */
    fun relationFollowed(data: JsonObject): Boolean {
        val attribute = data.longOrNull("attribute")
            ?: data.objOrNull("relation")?.longOrNull("attribute")
        if (attribute != null) return attribute == 2L || attribute == 6L
        return data.booleanOrNull("following", "followed") ?: false
    }

    /** `arc/search` 的 `data.list.vlist[]` → TrackPage；`total` 来自 `data.page.total`。 */
    fun uploaderVideos(
        data: JsonObject,
        page: Int,
        pageSize: Int,
        mid: Long = 0L,
    ): TrackPage {
        val vlist = data.objOrNull("list")?.arrOrNull("vlist", "archives").toObjectList()
        val items = vlist.mapNotNull { obj -> uploaderVideo(obj, mid) }
        val total = data.objOrNull("page")?.intOrNull("total")
            ?: data.intOrNull("total")
            ?: 0
        val hasMore = when {
            total > 0 -> page * pageSize < total
            else -> items.isNotEmpty() && items.size >= pageSize
        }
        return TrackPage(items = items, page = page, hasMore = hasMore, total = total)
    }

    private fun uploaderVideo(obj: JsonObject, mid: Long): Track? {
        val bvid = obj.strOrNull("bvid")?.trim().orEmpty()
        if (bvid.isEmpty()) return null
        // vlist 的 length 是 "mm:ss" 字符串，duration 是秒；优先 length（更精确到秒）。
        val duration = obj.strOrNull("length")?.takeIf { it.contains(':') }?.let(DurationParser::parseMs)
            ?: DurationParser.secondsToMs(obj.longOrNull("duration"))
        return Track(
            bvid = bvid,
            cid = obj.longOrNull("cid") ?: 0L,
            title = HtmlText.clean(obj.strOrNull("title")).ifBlank { "未知标题" },
            uploader = HtmlText.clean(obj.strOrNull("author")).ifBlank { "" },
            uploaderMid = obj.longOrNull("mid") ?: mid,
            coverUrl = BiliMapper.normalizeImage(obj.strOrNull("pic", "cover")),
            durationMs = duration,
            playCount = obj.longOrNull("play", "view") ?: 0L,
            pubDateSec = obj.longOrNull("created", "pubdate") ?: 0L,
        )
    }

    // ---------------------------------------------------------------- 工具

    /** 时长字段兼容：数字按秒，`mm:ss` / `hh:mm:ss` 字符串按时间串解析。 */
    private fun durationMsOf(obj: JsonObject): Long {
        val raw = obj.strOrNull("duration", "length")
        return when {
            raw.isNullOrBlank() -> 0L
            raw.contains(':') -> DurationParser.parseMs(raw)
            else -> DurationParser.secondsToMs(raw.toLongOrNull())
        }
    }

    private fun playCountOf(obj: JsonObject): Long =
        obj.objOrNull("cnt_info")?.longOrNull("play")
            ?: obj.objOrNull("stat")?.longOrNull("view", "play", "vv")
            ?: obj.longOrNull("play")
            ?: 0L

    /** JSON 布尔 / 0-1 数字统一成 Boolean；缺失返回 null。 */
    private fun boolish(obj: JsonObject, vararg keys: String): Boolean? {
        obj.booleanOrNull(*keys)?.let { return it }
        obj.longOrNull(*keys)?.let { return it != 0L }
        return null
    }
}
