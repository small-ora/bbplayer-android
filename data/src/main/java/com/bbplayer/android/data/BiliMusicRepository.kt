package com.bbplayer.android.data

import com.bbplayer.android.core.input.InputResolver
import com.bbplayer.android.core.input.VideoInput
import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.contracts.toUserMessage
import kotlinx.coroutines.CoroutineScope
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.AudioStream
import com.bbplayer.android.core.model.SearchPage
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.model.VideoCollection
import com.bbplayer.android.core.model.VideoCollectionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * [BiliMusicRepository] 依赖的最小传输抽象。
 *
 * 生产实现是 [BiliApiMusicAdapter]（包装 [BiliApi]，缓存 / 节流 / Cookie / 错误映射都在其中）；
 * 单测注入假实现，即可离线验证仓储的解析与 season 兜底分页逻辑。
 */
internal interface BiliMusicApi {
    suspend fun getObject(path: String, params: Map<String, String> = emptyMap()): JsonObject

    suspend fun getArray(path: String, params: Map<String, String> = emptyMap()): JsonArray

    suspend fun ensureBuvid(): String?

    fun isLoggedIn(): Boolean

    suspend fun resolveShortLink(url: String): ShortLinkResolution?
}

/** [BiliApi] → [BiliMusicApi] 适配器。 */
internal class BiliApiMusicAdapter(private val api: BiliApi) : BiliMusicApi {

    override suspend fun getObject(path: String, params: Map<String, String>): JsonObject =
        api.getObject(path, params)

    override suspend fun getArray(path: String, params: Map<String, String>): JsonArray =
        api.getArray(path, params)

    override suspend fun ensureBuvid(): String? = api.ensureBuvid()

    override fun isLoggedIn(): Boolean = api.isLoggedIn()

    override suspend fun resolveShortLink(url: String): ShortLinkResolution? = api.resolveShortLink(url)
}

/**
 * [MusicRepository] 的匿名 B 站实现。
 *
 * 端点全部只读、无需登录：
 * - 搜索 `/x/web-interface/search/type`
 * - 详情 `/x/web-interface/view`
 * - 分 P `/x/player/pagelist`
 * - 音频流 `/x/player/playurl`
 * - 排行 `/x/web-interface/ranking/v2?rid=3`
 * - 相关 `/x/web-interface/archive/related`
 * - season 兜底分页 `/x/polymer/web-space/seasons_archives_list`
 *
 * 错误统一转成 [Result.failure] 中的 [AppException]；协程取消原样抛出。
 */
class BiliMusicRepository internal constructor(
    private val api: BiliMusicApi,
    /** 设置里的「高音质」开关（默认关闭）；仅在已登录时生效。 */
    private val highQualityAudio: Flow<Boolean> = flowOf(false),
) : MusicRepository {

    /** 便捷构造：由 AppContainer / 既有调用方直接传入 [BiliApi]。 */
    constructor(
        api: BiliApi,
        highQualityAudio: Flow<Boolean> = flowOf(false),
    ) : this(BiliApiMusicAdapter(api), highQualityAudio)

    /** 便捷构造：由 AppContainer 直接调用。 */
    constructor(
        context: Context,
        highQualityAudio: Flow<Boolean> = flowOf(false),
    ) : this(BiliApi(context), highQualityAudio)

    /**
     * 预热 buvid3（App 启动时调用可减少首次搜索的等待）。
     * 失败静默忽略，后续请求仍会自动重试获取。
     */
    fun warmUp(scope: CoroutineScope) {
        scope.launch { runCatching { api.ensureBuvid() } }
    }

    override suspend fun search(query: String, page: Int): Result<SearchPage> = result {
        val keyword = query.trim()
        val safePage = page.coerceAtLeast(1)
        if (keyword.isEmpty()) return@result SearchPage.empty(safePage)
        val data = api.getObject(
            path = "/x/web-interface/search/type",
            params = mapOf(
                "search_type" to "video",
                "keyword" to keyword,
                "page" to safePage.toString(),
                "page_size" to PAGE_SIZE.toString(),
            ),
        )
        BiliMapper.searchPage(data, safePage)
    }

    override suspend fun resolveInput(input: String): Result<List<Track>> = result {
        when (val parsed = InputResolver.resolve(input)) {
            is VideoInput.Bvid -> tracksOfBvid(parsed.bvid)
            is VideoInput.Aid -> tracksOfAid(parsed.aid)
            is VideoInput.ShortLink -> resolveShortLink(parsed.url)
            is VideoInput.Keyword -> {
                val page = search(parsed.query).getOrThrow()
                val first = page.items.firstOrNull()
                    ?: throw AppException("没有找到相关视频，换个关键词试试", retryable = false)
                // 关键词只取首条，再展开该视频的分 P 列表（失败时至少返回首条）。
                runCatching { tracksOfBvid(first.bvid) }.getOrElse { listOf(first) }
            }
        }
    }

    override suspend fun getTrending(): Result<List<Track>> = result {
        val data = api.getObject(
            path = "/x/web-interface/ranking/v2",
            params = mapOf("rid" to MUSIC_ZONE_RID.toString(), "type" to "all"),
        )
        BiliMapper.videoList(data.arrOrNull("list"))
    }

    override suspend fun getRelated(bvid: String): Result<List<Track>> = result {
        val safeBvid = requireBvid(bvid)
        BiliMapper.videoList(
            api.getArray("/x/web-interface/archive/related", mapOf("bvid" to safeBvid)),
        )
    }

    override suspend fun getVideoPages(bvid: String): Result<List<Track>> = result {
        val safeBvid = requireBvid(bvid)
        val view = api.getObject("/x/web-interface/view", mapOf("bvid" to safeBvid))
        BiliMapper.videoPages(view).ifEmpty { listOf(BiliMapper.viewTrack(view)) }
    }

    override suspend fun getAudioStream(track: Track, quality: AudioQuality?): Result<AudioStream> = result {
        val requested = quality ?: AudioQuality.fromId(
            track.qualityId.takeIf { it > 0 } ?: AudioQuality.DEFAULT.id,
        )
        // 高音质只在「已登录 + 用户开启」时尝试 FLAC/Dolby，否则保持最高 AAC。
        val preferHighQuality = api.isLoggedIn() &&
            runCatching { highQualityAudio.first() }.getOrDefault(false)
        // 搜索结果 / 排行数据的 Track 可能没有 cid，播放时用分 P 接口兜底。
        val cid = if (track.cid > 0) track.cid else resolveFirstCid(track.bvid)
        val data = api.getObject(
            path = "/x/player/playurl",
            params = mapOf(
                "bvid" to track.bvid,
                "cid" to cid.toString(),
                "fnval" to "4048",
                "fnver" to "0",
                "fourk" to "1",
                "qlt" to requested.id.toString(),
            ),
        )
        BiliMapper.audioStream(data, requested.id, preferHighQuality = preferHighQuality)
    }

    override suspend fun getVideoDetail(bvid: String): Result<Track> = result {
        val safeBvid = requireBvid(bvid)
        BiliMapper.viewTrack(api.getObject("/x/web-interface/view", mapOf("bvid" to safeBvid)))
    }

    /**
     * 判定一个视频是否是「合集」（多 P 或 season），是则返回可直接当歌单播放的合集。
     *
     * 主路径只发一次 `view`（走 [BiliApi] 既有缓存/节流）；season 在 view 内 episodes
     * 少于 `ep_count` 时，才追加匿名兜底分页 `seasons_archives_list` 补全。
     * 兜底失败时保留已解析的 view episodes，整体仍成功——不因补全失败让主路径失败，
     * 单视频返回 `Result.success(null)`，view 本身失败则照常传播为 `Result.failure`。
     */
    override suspend fun getVideoCollection(bvid: String): Result<VideoCollection?> = result {
        val safeBvid = requireBvid(bvid)
        val view = api.getObject("/x/web-interface/view", mapOf("bvid" to safeBvid))
        val collection = BiliMapper.videoCollection(view) ?: return@result null
        if (collection.kind != VideoCollectionKind.SEASON) return@result collection
        val expected = BiliMapper.seasonEpisodeCount(view)
        if (collection.tracks.size >= expected) return@result collection
        appendSeasonArchives(view, collection)
    }

    // ------------------------------------------------------------ 内部实现

    /**
     * season 兜底分页：`seasons_archives_list` 按页补全 view 内被截断的 episodes。
     *
     * 只做追加：按 bvid 去重，最多 [MAX_SEASON_FALLBACK_PAGES] 页；每页请求失败或返回空
     * 立即停止并保留已解析结果（调用方仍按成功返回）。
     */
    private suspend fun appendSeasonArchives(
        view: JsonObject,
        parsed: VideoCollection,
    ): VideoCollection {
        val season = view.objOrNull("ugc_season") ?: return parsed
        val seasonId = season.longOrNull("id") ?: return parsed
        val mid = view.objOrNull("owner")?.longOrNull("mid")
            ?: season.longOrNull("mid")
            ?: return parsed
        val target = BiliMapper.seasonEpisodeCount(view).coerceAtLeast(parsed.tracks.size)
        val seen = parsed.tracks.mapTo(mutableSetOf()) { it.bvid }
        val merged = parsed.tracks.toMutableList()
        var page = 1
        while (page <= MAX_SEASON_FALLBACK_PAGES && merged.size < target) {
            val archives = try {
                api.getObject(
                    path = PATH_SEASON_ARCHIVES,
                    params = mapOf(
                        "mid" to mid.toString(),
                        "season_id" to seasonId.toString(),
                        "sort_reverse" to "false",
                        "page_num" to page.toString(),
                        "page_size" to SEASON_PAGE_SIZE.toString(),
                    ),
                ).arrOrNull("archives").toObjectList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 兜底失败不影响主路径：保留 view 内已解析的 episodes。
                break
            }
            if (archives.isEmpty()) break
            archives.mapNotNull { BiliContentMapper.mediaTrack(it) }
                .filter { seen.add(it.bvid) }
                .forEach(merged::add)
            page++
        }
        return parsed.copy(tracks = merged)
    }

    /** b23.tv 短链：跟随重定向，从最终 URL / canonical / 落地页 HTML 中提取视频 ID。 */
    private suspend fun resolveShortLink(url: String): List<Track> {
        val resolution = api.resolveShortLink(url)
            ?: throw AppException("短链解析失败，请稍后重试", retryable = true)
        val videoRef = InputResolver.extractVideoRef(resolution.finalUrl)
            ?: InputResolver.extractVideoRef(InputResolver.canonicalHref(resolution.html))
            ?: throw AppException("没有从短链中找到视频，请检查链接是否有效", retryable = false)
        return when (videoRef) {
            is VideoInput.Bvid -> tracksOfBvid(videoRef.bvid)
            is VideoInput.Aid -> tracksOfAid(videoRef.aid)
            else -> throw AppException("没有从短链中找到视频，请检查链接是否有效", retryable = false)
        }
    }

    private suspend fun tracksOfBvid(bvid: String): List<Track> {
        val view = api.getObject("/x/web-interface/view", mapOf("bvid" to bvid))
        return BiliMapper.videoPages(view).ifEmpty { listOf(BiliMapper.viewTrack(view)) }
    }

    private suspend fun tracksOfAid(aid: Long): List<Track> {
        val view = api.getObject("/x/web-interface/view", mapOf("aid" to aid.toString()))
        return BiliMapper.videoPages(view).ifEmpty { listOf(BiliMapper.viewTrack(view)) }
    }

    private suspend fun resolveFirstCid(bvid: String): Long {
        val pages = api.getArray("/x/player/pagelist", mapOf("bvid" to bvid))
        return pages.toObjectList().firstOrNull()?.longOrNull("cid")
            ?: throw AppException("无法解析视频分P，视频可能已被删除", retryable = false)
    }

    private fun requireBvid(raw: String): String {
        val bvid = raw.trim()
        if (bvid.isBlank()) throw AppException("视频ID不能为空", retryable = false)
        return bvid
    }

    /** 统一错误包装：业务/网络异常 → [AppException]，取消异常透传。 */
    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(
            if (e is AppException) e else AppException(e.toUserMessage(), e, retryable = true),
        )
    }

    companion object {
        /** B 站音乐区（rid=3）。 */
        private const val MUSIC_ZONE_RID = 3
        private const val PAGE_SIZE = 20

        /** season 兜底分页接口（匿名只读，无需登录）。 */
        private const val PATH_SEASON_ARCHIVES = "/x/polymer/web-space/seasons_archives_list"
        private const val SEASON_PAGE_SIZE = 30

        /** 兜底页数上限：防接口异常时无限翻页（正常 100 集也只需 4 页）。 */
        private const val MAX_SEASON_FALLBACK_PAGES = 20
    }
}
