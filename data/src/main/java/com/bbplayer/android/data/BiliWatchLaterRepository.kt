package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import com.bbplayer.android.core.contracts.WatchLaterRepository
import com.bbplayer.android.core.model.Track

/**
 * [WatchLaterRepository] 的登录态 B 站实现（稍后再看）。
 *
 * - 列表：`GET /x/v2/history/toview`
 * - 添加：`POST /x/v2/history/toview/add`（`aid` `cid?` `csrf`）
 * - 移除：`POST /x/v2/history/toview/del`（`aid` `csrf`）
 * - 清空：`POST /x/v2/history/toview/clear`（`csrf`）
 *
 * 写操作均不缓存；未登录返回不可重试的 `AppException("请先登录 Bilibili 账号")`。
 * 服务端对稍后再看有条数上限，`add` 超限时以 B 站返回的错误文案为准（`code != 0`）。
 */
class BiliWatchLaterRepository internal constructor(
    private val api: BiliContentApi,
    private val auth: AuthCookieProvider,
) : WatchLaterRepository {

    constructor(api: BiliApi, auth: AuthCookieProvider) : this(BiliApiContentApi(api), auth)

    /** 便捷构造：每个仓储自建共享 OkHttp/缓存的 [BiliApi]，Cookie 由 [auth] 提供。 */
    constructor(context: Context, auth: AuthCookieProvider) :
        this(BiliApi(context, authProvider = auth), auth)

    override suspend fun list(): Result<List<Track>> = repoResult {
        auth.requireLogin()
        BiliContentMapper.watchLaterTracks(api.getObject(PATH_LIST))
    }

    override suspend fun add(aid: Long, cid: Long?): Result<Unit> = repoResult {
        auth.requireLogin()
        if (aid <= 0L) throw AppException("视频ID不能为空", retryable = false)
        val form = linkedMapOf(
            "aid" to aid.toString(),
            "csrf" to auth.requireCsrf(),
        )
        cid?.takeIf { it > 0L }?.let { form["cid"] = it.toString() }
        api.postForm(PATH_ADD, form)
    }

    override suspend fun remove(aid: Long): Result<Unit> = repoResult {
        auth.requireLogin()
        if (aid <= 0L) throw AppException("视频ID不能为空", retryable = false)
        api.postForm(
            path = PATH_DEL,
            form = linkedMapOf(
                "aid" to aid.toString(),
                "csrf" to auth.requireCsrf(),
            ),
        )
    }

    override suspend fun clear(): Result<Unit> = repoResult {
        auth.requireLogin()
        api.postForm(PATH_CLEAR, mapOf("csrf" to auth.requireCsrf()))
    }

    companion object {
        internal const val PATH_LIST = "/x/v2/history/toview"
        internal const val PATH_ADD = "/x/v2/history/toview/add"
        internal const val PATH_DEL = "/x/v2/history/toview/del"
        internal const val PATH_CLEAR = "/x/v2/history/toview/clear"
    }
}
