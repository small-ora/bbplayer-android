package com.bbplayer.android.data

import com.bbplayer.android.core.input.InputResolver
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.model.ExternalPlaylist
import com.bbplayer.android.core.model.ExternalSource
import com.bbplayer.android.core.model.ExternalTrack
import kotlinx.serialization.json.JsonObject

/** 外部歌单输入解析结果。 */
internal sealed interface ExternalInput {
    /** 已经把来源和歌单 ID 解析出来。 */
    data class Ref(val source: ExternalSource, val id: String) : ExternalInput

    /** 网易云 `163cn.tv` 短链：需要先跟随重定向。 */
    data class ShortLink(val url: String) : ExternalInput

    /** 无法识别 / 缺 ID。 */
    data class Invalid(val reason: String) : ExternalInput
}

/**
 * 网易云 / QQ 音乐歌单的纯函数解析：链接/ID → 来源+ID，接口 JSON → [ExternalPlaylist]。
 * 不发起网络请求（短链由仓库层跟随重定向后再回调 [parse]）。
 */
internal object ExternalPlaylistParser {

    private val NETEASE_ID_REGEX = Regex("""[?&]id=(\d+)""")
    private val NETEASE_PATH_REGEX = Regex("""/playlist/(\d+)""", RegexOption.IGNORE_CASE)
    private val QQ_PATH_REGEX = Regex("""/playlist/(\d+)""", RegexOption.IGNORE_CASE)
    private val QQ_ID_REGEX = Regex("""[?&]id=(\d+)""")
    private val SHORT_LINK_REGEX = Regex("""(?:https?://)?163cn\.tv/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)
    private val DIGITS_REGEX = Regex("""^\d+$""")

    /** 单曲匹配查询词：`<标题> <歌手>`（与上游一致）。 */
    fun matchQuery(track: ExternalTrack): String =
        listOf(track.title.trim(), track.artist.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /** 解析用户输入（可能夹带分享文案）。 */
    fun parse(raw: String): ExternalInput {
        val text = raw.trim()
        if (text.isEmpty()) return ExternalInput.Invalid("请输入歌单链接或ID")

        // 网易云短链必须优先处理：它的落地页才是真正的歌单链接。
        if (text.contains("163cn.tv", ignoreCase = true)) {
            val short = SHORT_LINK_REGEX.find(text)?.value
                ?: return ExternalInput.Invalid("网易云短链格式不正确")
            return ExternalInput.ShortLink(InputResolver.normalizeUrl(short))
        }

        if (DIGITS_REGEX.matches(text)) {
            // 纯数字默认按网易云歌单 ID 处理（需求约定）。
            return ExternalInput.Ref(ExternalSource.NETEASE, text)
        }

        val source = detectSource(text)
            ?: return ExternalInput.Invalid("无法识别的歌单链接，请输入网易云或QQ音乐歌单链接/ID")
        val id = when (source) {
            ExternalSource.NETEASE ->
                NETEASE_ID_REGEX.find(text)?.groupValues?.get(1)
                    ?: NETEASE_PATH_REGEX.find(text)?.groupValues?.get(1)

            ExternalSource.QQ_MUSIC ->
                QQ_PATH_REGEX.find(text)?.groupValues?.get(1)
                    ?: QQ_ID_REGEX.find(text)?.groupValues?.get(1)
        }
        return if (id.isNullOrBlank()) {
            ExternalInput.Invalid("没有从链接中找到歌单ID，请检查链接是否完整")
        } else {
            ExternalInput.Ref(source, id)
        }
    }

    fun detectSource(text: String): ExternalSource? {
        val lower = text.lowercase()
        return when {
            lower.contains("163.com") || lower.contains("163cn.tv") -> ExternalSource.NETEASE
            lower.contains("qq.com") -> ExternalSource.QQ_MUSIC
            else -> null
        }
    }

    // ------------------------------------------------------------- 网易云

    /**
     * `music.163.com/api/v6/playlist/detail` 响应：
     * `{ code:200, playlist:{ name, coverImgUrl, description, trackCount, tracks:[...] } }`
     */
    fun parseNetease(root: JsonObject, sourceId: String): ExternalPlaylist {
        val code = root.longOrNull("code")
        if (code != null && code != 0L && code != 200L) {
            throw AppException("网易云歌单不存在或已下架", retryable = false)
        }
        val playlist = root.objOrNull("playlist")
            ?: throw AppException("网易云歌单不存在或已下架", retryable = false)
        val tracks = playlist.arrOrNull("tracks").toObjectList().mapNotNull(::neteaseTrack)
        if (tracks.isEmpty()) {
            throw AppException("该网易云歌单没有可导入的歌曲", retryable = false)
        }
        return ExternalPlaylist(
            source = ExternalSource.NETEASE,
            sourceId = sourceId,
            name = HtmlText.clean(playlist.strOrNull("name")).ifBlank { "网易云歌单 $sourceId" },
            coverUrl = BiliMapper.normalizeImage(playlist.strOrNull("coverImgUrl", "coverImg")),
            description = HtmlText.clean(playlist.strOrNull("description")),
            tracks = tracks,
        )
    }

    /**
     * 网易云 `playlist/detail` 匿名响应里 `tracks` 只有前 10 首，
     * `trackIds` 才是全量（按歌单顺序）。返回完整 id 列表用于二次拉取详情。
     */
    fun neteaseTrackIds(root: JsonObject): List<Long> {
        val playlist = root.objOrNull("playlist") ?: return emptyList()
        val ids = playlist.arrOrNull("trackIds").toObjectList()
            .mapNotNull { it.longOrNull("id") }
            .filter { it > 0L }
        val tracks = playlist.arrOrNull("tracks").toObjectList()
            .mapNotNull { it.longOrNull("id") }
        // 极少数情况下 trackIds 缺失，退化为 tracks（仅前 10 首可用）。
        return ids.ifEmpty { tracks }
    }

    /** 网易云 `api/v3/song/detail` 批量详情：`{ code:200, songs:[...] }`。 */
    fun parseNeteaseSongs(root: JsonObject): List<ExternalTrack> =
        parseNeteaseSongsById(root).values.toList()

    /** 同 [parseNeteaseSongs]，但保留歌曲 id → 便于按 trackIds 原顺序还原。 */
    fun parseNeteaseSongsById(root: JsonObject): Map<Long, ExternalTrack> {
        val code = root.longOrNull("code")
        if (code != null && code != 0L && code != 200L) {
            throw AppException("获取网易云歌曲详情失败", retryable = true)
        }
        val result = LinkedHashMap<Long, ExternalTrack>()
        root.arrOrNull("songs").toObjectList().forEach { raw ->
            val id = raw.longOrNull("id") ?: return@forEach
            neteaseTrack(raw)?.let { result[id] = it }
        }
        return result
    }

    internal fun neteaseTrack(raw: JsonObject): ExternalTrack? {
        val title = HtmlText.clean(raw.strOrNull("name")).takeIf { it.isNotBlank() } ?: return null
        val artists = raw.arrOrNull("ar")
            ?: raw.arrOrNull("artists")
        val artist = artists.toObjectList()
            .mapNotNull { HtmlText.clean(it.strOrNull("name")).takeIf(String::isNotBlank) }
            .joinToString("、")
        val album = HtmlText.clean(
            raw.objOrNull("al")?.strOrNull("name") ?: raw.objOrNull("album")?.strOrNull("name"),
        )
        val duration = raw.longOrNull("dt", "duration") ?: 0L
        return ExternalTrack(
            title = title,
            artist = artist,
            durationMs = duration.coerceAtLeast(0L),
            album = album,
        )
    }

    // ------------------------------------------------------------- QQ 音乐

    /**
     * `c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg` 响应：
     * `{ code:0, data:{ cdlist:[ { dissname, logo, desc, songnum, songlist:[...] } ] } }`
     */
    fun parseQq(root: JsonObject, sourceId: String): ExternalPlaylist {
        val code = root.longOrNull("code")
        if (code != null && code != 0L && code != 200L) {
            throw AppException("QQ音乐歌单不存在或已下架", retryable = false)
        }
        val cd = root.objOrNull("data")?.arrOrNull("cdlist").toObjectList().firstOrNull()
            ?: throw AppException("QQ音乐歌单不存在或已下架", retryable = false)
        val tracks = (cd.arrOrNull("songlist") ?: cd.arrOrNull("songList"))
            .toObjectList()
            .mapNotNull(::qqTrack)
        if (tracks.isEmpty()) {
            throw AppException("该QQ音乐歌单没有可导入的歌曲", retryable = false)
        }
        return ExternalPlaylist(
            source = ExternalSource.QQ_MUSIC,
            sourceId = sourceId,
            name = HtmlText.clean(cd.strOrNull("dissname", "dirname")).ifBlank { "QQ音乐歌单 $sourceId" },
            coverUrl = BiliMapper.normalizeImage(cd.strOrNull("logo", "ifpicurl")),
            description = HtmlText.clean(cd.strOrNull("desc")),
            tracks = tracks,
        )
    }

    private fun qqTrack(raw: JsonObject): ExternalTrack? {
        // 实测 `fcg_v8_playlist_cp.fcg` 的 songlist[] 用 name/album 对象；
        // 部分老接口/分享页返回 songname/albumname，这里都兼容。
        val title = HtmlText.clean(raw.strOrNull("songname", "songorig", "name", "title"))
            .takeIf { it.isNotBlank() }
            ?: return null
        val artist = raw.arrOrNull("singer").toObjectList()
            .mapNotNull { HtmlText.clean(it.strOrNull("name")).takeIf(String::isNotBlank) }
            .joinToString("、")
        val interval = raw.longOrNull("interval", "duration") ?: 0L
        val album = raw.strOrNull("albumname", "albumname_hilight")
            ?: raw.objOrNull("album")?.strOrNull("name")
        return ExternalTrack(
            title = title,
            artist = artist,
            durationMs = (interval * 1000L).coerceAtLeast(0L),
            album = HtmlText.clean(album),
        )
    }
}
