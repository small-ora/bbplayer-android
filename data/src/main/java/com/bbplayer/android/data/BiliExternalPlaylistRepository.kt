package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.ExternalPlaylistRepository
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.model.ExternalPlaylist
import com.bbplayer.android.core.model.ExternalTrack
import com.bbplayer.android.core.model.ExternalSource
import com.bbplayer.android.core.model.ImportMatch
import com.bbplayer.android.core.model.ImportProgress
import kotlinx.coroutines.delay

/**
 * [ExternalPlaylistRepository] 实现：网易云 / QQ 音乐歌单导入 + 逐曲匹配 B 站视频。
 *
 * - 网易云：`api/v6/playlist/detail`；QQ：`fcg_v8_playlist_cp.fcg`（均匿名可用）；
 * - 逐曲调用 [MusicRepository.search]，两曲之间至少间隔 [minIntervalMs]（默认 700ms），
 *   通过 [ImportProgress] 回调导入进度，避免一次性打满搜索接口。
 */
class BiliExternalPlaylistRepository internal constructor(
    private val musicRepository: MusicRepository,
    private val http: HttpFetcher,
    private val minIntervalMs: Long,
    private val sleep: suspend (Long) -> Unit,
) : ExternalPlaylistRepository {

    constructor(musicRepository: MusicRepository) : this(
        musicRepository = musicRepository,
        http = HttpFetcher(),
        minIntervalMs = DEFAULT_INTERVAL_MS,
        sleep = { delay(it) },
    )

    /** 便捷构造：内部自建 B 站仓库（推荐在 AppContainer 里复用同一个 musicRepository）。 */
    constructor(context: Context) : this(BiliMusicRepository(context))

    override suspend fun fetchPlaylist(input: String): Result<ExternalPlaylist> = repoResult {
        val ref = resolveRef(input)
        when (ref.source) {
            ExternalSource.NETEASE -> {
                val root = http.getJsonObject(neteaseUrl(ref.id), HttpHeaders.NETEASE)
                val base = ExternalPlaylistParser.parseNetease(root, ref.id)
                val trackIds = ExternalPlaylistParser.neteaseTrackIds(root)
                if (trackIds.size <= base.tracks.size) {
                    base
                } else {
                    // `playlist/detail` 匿名只返回前 10 首；用 trackIds + 批量详情补齐全量。
                    base.copy(tracks = fetchNeteaseTracks(trackIds, base.tracks))
                }
            }

            ExternalSource.QQ_MUSIC -> {
                val root = http.getJsonObject(qqUrl(ref.id), HttpHeaders.QQ)
                ExternalPlaylistParser.parseQq(root, ref.id)
            }
        }
    }

    override suspend fun match(
        playlist: ExternalPlaylist,
        onProgress: (ImportProgress) -> Unit,
    ): Result<List<ImportMatch>> = repoResult {
        val total = playlist.tracks.size
        if (total == 0) {
            onProgress(ImportProgress(done = 0, total = 0, matches = emptyList(), finished = true))
            return@repoResult emptyList()
        }
        val matches = ArrayList<ImportMatch>(total)
        var lastSearchStartedAt = 0L
        var consecutiveFailures = 0
        playlist.tracks.forEachIndexed { index, external ->
            if (index > 0) {
                // 从上一曲搜索「开始时间」起算间隔，保证搜索请求频率 ≤ 1/700ms。
                val elapsed = System.currentTimeMillis() - lastSearchStartedAt
                val wait = minIntervalMs - elapsed
                if (wait > 0L) sleep(wait)
            }
            lastSearchStartedAt = System.currentTimeMillis()
            val query = ExternalPlaylistParser.matchQuery(external)
            val searchResult = musicRepository.search(query, 1)
            val candidates = searchResult.getOrNull()?.items.orEmpty()
            if (searchResult.isFailure) {
                consecutiveFailures++
                // 偶发失败按未匹配处理继续导入；连续失败说明网络/风控异常，尽早中止。
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    throw searchResult.exceptionOrNull()
                        ?: AppException("B站搜索失败，请检查网络后重试", retryable = true)
                }
            } else {
                consecutiveFailures = 0
            }
            matches += MatchScorer.match(index, external, candidates)
            onProgress(
                ImportProgress(
                    done = index + 1,
                    total = total,
                    matches = matches.toList(),
                    finished = index == total - 1,
                ),
            )
        }
        matches
    }

    // ------------------------------------------------------------ 内部实现

    private suspend fun resolveRef(raw: String): ExternalInput.Ref = when (val parsed = ExternalPlaylistParser.parse(raw)) {
        is ExternalInput.Ref -> parsed
        is ExternalInput.ShortLink -> {
            val finalUrl = http.followRedirect(parsed.url, HttpHeaders.NETEASE)
            when (val again = ExternalPlaylistParser.parse(finalUrl)) {
                is ExternalInput.Ref -> again
                else -> throw AppException("短链解析失败，请检查链接是否有效", retryable = false)
            }
        }

        is ExternalInput.Invalid -> throw AppException(parsed.reason, retryable = false)
    }

    /**
     * 按 `trackIds` 顺序批量拉取完整歌曲详情（每批 [NETEASE_DETAIL_BATCH] 首）。
     *
     * 网易云匿名 `playlist/detail` 的 `tracks` 只有前 10 首，这是补齐 100+ 首歌单的关键。
     */
    private suspend fun fetchNeteaseTracks(
        trackIds: List<Long>,
        fallback: List<ExternalTrack>,
    ): List<ExternalTrack> {
        val byId = LinkedHashMap<Long, ExternalTrack>()
        for (chunk in trackIds.chunked(NETEASE_DETAIL_BATCH)) {
            val body = chunk.joinToString(prefix = "[", postfix = "]") { "{\"id\":$it}" }
            val root = try {
                http.postFormJsonObject(NETEASE_DETAIL_URL, mapOf("c" to body), HttpHeaders.NETEASE)
            } catch (e: AppException) {
                if (byId.isEmpty()) throw AppException("获取完整歌单失败，请重试", e, retryable = true)
                continue
            }
            byId.putAll(ExternalPlaylistParser.parseNeteaseSongsById(root))
        }
        if (byId.isEmpty()) return fallback
        val ordered = trackIds.mapNotNull { byId[it] }
        return ordered.ifEmpty { fallback }
    }

    private fun neteaseUrl(id: String): String =
        "https://music.163.com/api/v6/playlist/detail?id=$id&n=1000"

    private fun qqUrl(id: String): String =
        "https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg" +
            "?id=$id&format=json&newsong=1&platform=jqspaframe.json"

    companion object {
        /** 逐曲搜索的最小间隔（毫秒）。 */
        const val DEFAULT_INTERVAL_MS = 700L

        /** 连续搜索失败达到该次数后中止导入（避免无网络时空跑一遍歌单）。 */
        private const val MAX_CONSECUTIVE_FAILURES = 3

        /** 网易云批量歌曲详情接口与每批数量（补齐全量歌单用）。 */
        private const val NETEASE_DETAIL_URL = "https://music.163.com/api/v3/song/detail"
        private const val NETEASE_DETAIL_BATCH = 100
    }
}
