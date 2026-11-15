package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.PlaybackReporter
import com.bbplayer.android.core.contracts.PreferencesRepository
import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** 上报所需的视频标识（`view` 接口解析）。 */
internal data class ReportTarget(val aid: Long, val cid: Long)

/**
 * 播放历史上报实现（默认关闭，需用户主动开启且已登录）。
 *
 * - [enabled] 直接来自 `PreferencesRepository.reportPlaybackProgress`（默认 false）；
 * - 关闭 / 未登录时**不发任何请求**，直接成功返回；
 * - 开启且登录：`POST https://api.bilibili.com/x/v2/history/report`
 *   表单 `aid`、`cid`、`progress`（秒）、`csrf`（`bili_jct`）；
 * - 任何失败都收敛成 [Result.failure]（[AppException]），调用方忽略即可，不会崩溃。
 *
 * 单测通过 internal 构造注入开关 / 登录态 / 解析 / 提交的假实现（不依赖 Android）。
 */
class PlaybackReporterImpl internal constructor(
    private val enabledFlow: Flow<Boolean>,
    private val loggedIn: () -> Boolean,
    private val csrfToken: () -> String?,
    private val resolveVideo: suspend (bvid: String) -> ReportTarget?,
    private val submit: suspend (form: Map<String, String>) -> Unit,
) : PlaybackReporter {

    /** 生产构造：与 [BiliApi] 共用进程级缓存 / Cookie 来源。 */
    constructor(api: BiliApi, preferences: PreferencesRepository) : this(
        enabledFlow = preferences.reportPlaybackProgress,
        loggedIn = { api.isLoggedIn() },
        csrfToken = { api.csrfToken() },
        resolveVideo = { bvid -> resolveVideo(api, bvid) },
        submit = { form -> api.postForm(PATH_REPORT, form) },
    )

    /** 便捷构造：AppContainer 直接调用（共享 [AuthRegistry] 登录态）。 */
    constructor(context: Context, preferences: PreferencesRepository) : this(
        BiliApi(context),
        preferences,
    )

    override val enabled: Flow<Boolean> = enabledFlow

    /** `bvid -> (aid, cid)` 内存缓存：上报每 20s 一次，避免反复请求 view 接口。 */
    private val resolvedTargets = java.util.concurrent.ConcurrentHashMap<String, ReportTarget>()

    override suspend fun report(track: Track, progressMs: Long): Result<Unit> = repoResult {
        val isEnabled = runCatching { enabledFlow.first() }.getOrDefault(false)
        if (!isEnabled) return@repoResult
        if (!loggedIn()) return@repoResult
        val csrf = csrfToken()
        if (csrf.isNullOrBlank()) {
            throw AppException("登录状态不完整，请重新登录后再开启上报", retryable = false)
        }
        val target = resolveTarget(track.bvid)
            ?: throw AppException("无法解析视频信息，播放进度未上报", retryable = true)
        val cid = track.cid.takeIf { it > 0L } ?: target.cid
        if (cid <= 0L) throw AppException("无法解析视频分P，播放进度未上报", retryable = false)
        val progressSeconds = (progressMs / 1000L).coerceAtLeast(0L)
        submit(buildReportForm(target.aid, cid, progressSeconds, csrf))
    }

    private suspend fun resolveTarget(bvid: String): ReportTarget? {
        if (bvid.isBlank()) return null
        resolvedTargets[bvid]?.let { return it }
        val resolved = resolveVideo(bvid) ?: return null
        if (resolvedTargets.size >= MAX_CACHED_TARGETS) resolvedTargets.clear()
        resolvedTargets[bvid] = resolved
        return resolved
    }

    private companion object {
        const val MAX_CACHED_TARGETS = 256
        const val PATH_REPORT = "/x/v2/history/report"
    }
}

/** 历史上报表单（`progress` 为秒）；csrf 缺失时不带该字段（服务端会拒绝，由调用方保证登录态）。 */
internal fun buildReportForm(
    aid: Long,
    cid: Long,
    progressSeconds: Long,
    csrf: String?,
): Map<String, String> = buildMap {
    put("aid", aid.toString())
    put("cid", cid.toString())
    put("progress", progressSeconds.toString())
    csrf?.takeIf { it.isNotBlank() }?.let { put("csrf", it) }
}

/** `view` 接口 → (aid, 首P cid)；失败返回 null（上报静默跳过）。 */
private suspend fun resolveVideo(api: BiliApi, bvid: String): ReportTarget? {
    if (bvid.isBlank()) return null
    return runCatching {
        val view = api.getObject("/x/web-interface/view", mapOf("bvid" to bvid))
        val aid = view.longOrNull("aid")?.takeIf { it > 0L } ?: return@runCatching null
        val cid = view.arrOrNull("pages").toObjectList().firstOrNull()?.longOrNull("cid")
            ?: view.longOrNull("cid")
            ?: 0L
        ReportTarget(aid = aid, cid = cid)
    }.getOrNull()
}
