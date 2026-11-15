package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.ExternalPlaylistRepository
import com.bbplayer.android.core.contracts.ImportBatchMatcher
import com.bbplayer.android.core.contracts.ImportTaskRepository
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.ExternalPlaylist
import com.bbplayer.android.core.model.ExternalTrack
import com.bbplayer.android.core.model.ExternalSource
import com.bbplayer.android.core.model.ImportBatchResult
import com.bbplayer.android.core.model.ImportMatch
import com.bbplayer.android.core.model.ImportProgress
import com.bbplayer.android.core.model.ImportStatus
import com.bbplayer.android.core.model.ImportTask
import com.bbplayer.android.data.cache.SecurityCooldown
import kotlinx.coroutines.delay

/**
 * [ExternalPlaylistRepository] 实现：网易云 / QQ 音乐歌单导入 + 逐曲匹配 B 站视频。
 *
 * - 网易云：`api/v6/playlist/detail`；QQ：`fcg_v8_playlist_cp.fcg`（均匿名可用）；
 * - 逐曲调用 [MusicRepository.search]，两曲之间至少间隔 [minIntervalMs]（默认 1200ms），
 *   通过 [ImportProgress] 回调导入进度，避免一次性打满搜索接口；
 * - 同时实现 [ImportBatchMatcher]：歌单导入按批匹配、逐批落盘，可暂停/续跑。
 */
class BiliExternalPlaylistRepository internal constructor(
    private val musicRepository: MusicRepository,
    private val http: HttpFetcher,
    private val minIntervalMs: Long,
    private val sleep: suspend (Long) -> Unit,
    /** 任务快照存储；为 null 时只返回结果、不落盘（兼容旧测试/旧接线）。 */
    private val importTaskRepository: ImportTaskRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    /** 风控剩余时间来源（默认进程级 [SecurityCooldown]，测试可注入）。 */
    private val securityCooldownRemainingMs: () -> Long = { SecurityCooldown.remainingMs() },
    /** 命中风控时标记冷却（默认至少 30s，测试可注入空实现避免全局副作用）。 */
    private val markSecurityBlocked: () -> Unit = {
        SecurityCooldown.markBlocked(penalty = SECURITY_BACKOFF_MS)
    },
) : ExternalPlaylistRepository, ImportBatchMatcher {

    constructor(musicRepository: MusicRepository) : this(
        musicRepository = musicRepository,
        http = HttpFetcher(),
        minIntervalMs = DEFAULT_INTERVAL_MS,
        sleep = { delay(it) },
    )

    /** AppContainer 接线用：复用 B 站仓库并持久化导入任务。 */
    constructor(
        musicRepository: MusicRepository,
        importTaskRepository: ImportTaskRepository?,
    ) : this(
        musicRepository = musicRepository,
        http = HttpFetcher(),
        minIntervalMs = DEFAULT_INTERVAL_MS,
        sleep = { delay(it) },
        importTaskRepository = importTaskRepository,
    )

    /** 便捷构造：内部自建 B 站仓库（推荐在 AppContainer 里复用同一个 musicRepository）。 */
    constructor(context: Context) : this(BiliMusicRepository(context))

    /** 便捷构造 + 任务持久化。 */
    constructor(
        context: Context,
        importTaskRepository: ImportTaskRepository?,
    ) : this(BiliMusicRepository(context), importTaskRepository)

    override suspend fun fetchPlaylist(input: String): Result<ExternalPlaylist> = repoResult {
        val ref = resolveRef(input)
        when (ref.source) {
            ExternalSource.NETEASE -> {
                // 校验放在缓存写入前：歌单不存在 / code 异常不写 30 分钟缓存。
                val root = http.getJsonObject(neteaseUrl(ref.id), HttpHeaders.NETEASE) { payload ->
                    ExternalPlaylistParser.parseNetease(payload, ref.id)
                }
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
                val root = http.getJsonObject(qqUrl(ref.id), HttpHeaders.QQ) { payload ->
                    ExternalPlaylistParser.parseQq(payload, ref.id)
                }
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
        var securityHits = 0
        playlist.tracks.forEachIndexed { index, external ->
            if (index > 0) {
                // 从上一曲搜索「开始时间」起算间隔，保证搜索请求频率 ≤ 1/700ms。
                val elapsed = System.currentTimeMillis() - lastSearchStartedAt
                // Jitter keeps a long import from looking like a machine-gun burst.
                val jitter = kotlin.random.Random.nextLong(0L, 400L)
                val wait = minIntervalMs + jitter - elapsed
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

    /**
     * 从 [task] 的 `nextIndex` 开始，最多匹配 [batchSize] 首（`<= 0` 时用
     * [DEFAULT_IMPORT_BATCH_SIZE]），每批结束把完整任务快照落盘。
     *
     * 语义（UI 可直接照此渲染）：
     * - 批内逐曲限速搜索（1200ms + 抖动），每曲回调一次 [ImportProgress]；
     * - 批内剩余不足一批 → 处理完即结束；全部曲目处理完 → `status=DONE`；
     * - 还有剩余曲目 → `status=PAUSED`（正常暂停态，继续调用即可续跑）；
     * - 命中风控 → **不抛异常**，返回 `blocked=true` + `retryAfterMs`，任务 `status=PAUSED`
     *   且 `nextIndex` 停在被打断的那首（下次从该首重试）；
     * - 普通网络失败连续 [MAX_CONSECUTIVE_FAILURES] 次 → `status=FAILED` + `errorMessage`，
     *   同样不抛异常，`nextIndex` 指向第一首失败曲目；
     * - 没有注入 [ImportTaskRepository] 时只返回结果、不落盘（兼容旧测试）。
     */
    override suspend fun matchBatch(
        task: ImportTask,
        batchSize: Int,
        onProgress: (ImportProgress) -> Unit,
    ): ImportBatchResult {
        val total = task.total
        val size = if (batchSize <= 0) DEFAULT_IMPORT_BATCH_SIZE else batchSize

        // 已完成的歌单：幂等返回 DONE，不重复搜索。
        if (task.isFinished) {
            val done = task.copy(
                nextIndex = total,
                status = ImportStatus.DONE,
                errorMessage = null,
                updatedAt = clock(),
            )
            persist(done)
            onProgress(ImportProgress(done.done, total, done.matches, finished = true))
            return ImportBatchResult(done)
        }

        val start = task.nextIndex.coerceIn(0, total)
        val remaining = total - start
        val endExclusive = if (size >= remaining) total else start + size
        val matches = task.matches.toMutableList()
        var current = task.copy(
            matches = matches.toList(),
            nextIndex = start,
            status = ImportStatus.RUNNING,
            errorMessage = null,
            updatedAt = clock(),
        )
        var processed = start
        var consecutiveFailures = 0
        var lastSearchStartedAt = 0L

        while (processed < endExclusive) {
            if (processed > start) {
                // 从上一曲搜索「开始时间」起算间隔，保证搜索请求频率 ≤ 1/minIntervalMs。
                val elapsed = clock() - lastSearchStartedAt
                // Jitter keeps a long import from looking like a machine-gun burst.
                val jitter = kotlin.random.Random.nextLong(0L, 400L)
                val wait = minIntervalMs + jitter - elapsed
                if (wait > 0L) sleep(wait)
            }
            lastSearchStartedAt = clock()
            val external = task.playlist.tracks[processed]
            val searchResult = musicRepository.search(ExternalPlaylistParser.matchQuery(external), 1)
            val error = searchResult.exceptionOrNull()

            if (error != null) {
                if (isRiskControl(error)) {
                    // 风控不硬刚：标记冷却、保存暂停点，把「多久后可以重试」交给 UI。
                    markSecurityBlocked()
                    val paused = current.copy(
                        matches = matches.toList(),
                        nextIndex = processed,
                        status = ImportStatus.PAUSED,
                        errorMessage = riskControlMessage(error),
                        updatedAt = clock(),
                    )
                    persist(paused)
                    onProgress(ImportProgress(matches.size, total, matches.toList(), finished = true))
                    return ImportBatchResult(
                        task = paused,
                        blocked = true,
                        retryAfterMs = securityCooldownRemainingMs(),
                    )
                }
                consecutiveFailures++
                // 偶发失败按未匹配处理继续导入；连续失败说明网络异常，尽早停止等待续跑。
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    val failed = current.copy(
                        matches = matches.toList(),
                        nextIndex = processed,
                        status = ImportStatus.FAILED,
                        errorMessage = error.toUserMessage(),
                        updatedAt = clock(),
                    )
                    persist(failed)
                    onProgress(ImportProgress(matches.size, total, matches.toList(), finished = true))
                    return ImportBatchResult(failed)
                }
                matches += MatchScorer.match(processed, external, emptyList())
            } else {
                consecutiveFailures = 0
                matches += MatchScorer.match(
                    processed,
                    external,
                    searchResult.getOrNull()?.items.orEmpty(),
                )
            }

            processed++
            current = current.copy(
                matches = matches.toList(),
                nextIndex = processed,
                status = ImportStatus.RUNNING,
                errorMessage = null,
                updatedAt = clock(),
            )
            onProgress(
                ImportProgress(
                    done = matches.size,
                    total = total,
                    matches = matches.toList(),
                    finished = processed >= total,
                ),
            )
        }

        // 一批结束：落盘后再返回，进程随时被杀也能从 nextIndex 续跑。
        val finished = current.nextIndex >= total
        val resultTask = current.copy(
            status = if (finished) ImportStatus.DONE else ImportStatus.PAUSED,
            errorMessage = null,
            updatedAt = clock(),
        )
        persist(resultTask)
        return ImportBatchResult(resultTask)
    }

    // ------------------------------------------------------------ 内部实现

    private suspend fun persist(task: ImportTask) {
        importTaskRepository?.save(task)
    }

    /** 风控识别：B 站文案「安全策略」或 code `-412`（含 cause 链）。 */
    private fun isRiskControl(error: Throwable): Boolean {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            val message = cause.message.orEmpty()
            if (message.contains("安全策略") || message.contains("-412")) return true
            cause = cause.cause
            depth++
        }
        return false
    }

    private fun riskControlMessage(error: Throwable): String =
        error.toUserMessage().takeIf { it.isNotBlank() }
            ?: "请求被B站安全策略拦截，请稍后重试"

    private suspend fun resolveRef(raw: String): ExternalInput.Ref = when (val parsed = ExternalPlaylistParser.parse(raw)) {
        is ExternalInput.Ref -> parsed
        is ExternalInput.ShortLink -> {
            // 落地页无法解析成歌单时视为失败，不写 24 小时缓存。
            val finalUrl = http.followRedirect(parsed.url, HttpHeaders.NETEASE) { resolved ->
                if (ExternalPlaylistParser.parse(resolved) is ExternalInput.Invalid) {
                    throw AppException("短链解析失败，请检查链接是否有效", retryable = false)
                }
            }
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
        const val DEFAULT_INTERVAL_MS = 1_200L

        /** 歌单导入默认每批匹配数量：批次小、落盘频繁，风控时可安全停下。 */
        const val DEFAULT_IMPORT_BATCH_SIZE = 50

        /** Pause after a risk-control hit before continuing the import. */
        private const val SECURITY_BACKOFF_MS = 30_000L

        /** 连续搜索失败达到该次数后中止导入（避免无网络时空跑一遍歌单）。 */
        private const val MAX_CONSECUTIVE_FAILURES = 3

        /** 识别风控时最多回溯的 cause 层数（防止异常链成环）。 */
        private const val MAX_CAUSE_DEPTH = 8

        /** 网易云批量歌曲详情接口与每批数量（补齐全量歌单用）。 */
        private const val NETEASE_DETAIL_URL = "https://music.163.com/api/v3/song/detail"
        private const val NETEASE_DETAIL_BATCH = 100
    }
}
