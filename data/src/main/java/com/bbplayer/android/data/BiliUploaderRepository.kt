package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import com.bbplayer.android.core.contracts.UploaderRepository
import com.bbplayer.android.core.model.TrackPage
import com.bbplayer.android.core.model.UploaderInfo
import com.bbplayer.android.data.wbi.WbiSigner
import kotlinx.coroutines.CancellationException

/**
 * [UploaderRepository] 的登录态 B 站实现（UP 主主页 / 投稿 / 关注）。
 *
 * - 信息：WBI `GET /x/space/wbi/acc/info?mid&w_rid&wts`；关注状态 `GET /x/relation?fid`
 *   （`attribute` 2 / 6 = 已关注）；粉丝数在 `acc/info` 缺失时用
 *   `GET /x/relation/stat?vmid` 兜底。
 * - 投稿：WBI `GET /x/space/wbi/arc/search?mid&ps=30&pn&keyword&order=pubdate&w_rid&wts`，
 *   取 `data.list.vlist[]` 与 `data.page.total`。
 * - 关注 / 取关：`POST /x/relation/modify`（`fid` `act=1|2` `re_src=11` `csrf`）。
 *
 * 空间接口对匿名请求会返回 -352 风控，因此全部要求登录；未登录返回不可重试的
 * `AppException("请先登录 Bilibili 账号")`。`wts`/`w_rid` 由 [WbiSigner] 注入。
 */
class BiliUploaderRepository internal constructor(
    private val api: BiliContentApi,
    private val auth: AuthCookieProvider,
    private val wbi: WbiSigner,
) : UploaderRepository {

    constructor(api: BiliApi, auth: AuthCookieProvider, wbi: WbiSigner = WbiSigner()) :
        this(BiliApiContentApi(api), auth, wbi)

    /** 便捷构造：每个仓储自建共享 OkHttp/缓存的 [BiliApi]，Cookie 由 [auth] 提供。 */
    constructor(context: Context, auth: AuthCookieProvider, wbi: WbiSigner = WbiSigner()) :
        this(BiliApi(context, authProvider = auth), auth, wbi)

    override suspend fun info(mid: Long): Result<UploaderInfo> = repoResult {
        auth.requireLogin()
        requireMid(mid)
        val signed = wbi.sign(mapOf("mid" to mid.toString()))
        val data = api.getObject(PATH_ACC_INFO, signed)
        val followed = attempt {
            api.getObject(PATH_RELATION, mapOf("fid" to mid.toString()))
        }?.let(BiliContentMapper::relationFollowed) ?: false
        // acc/info 通常不含粉丝数，缺失时用 relation/stat 兜底（失败就保持 0）。
        val follower = if (data.longOrNull("follower", "fans") == null) {
            attempt {
                api.getObject(PATH_RELATION_STAT, mapOf("vmid" to mid.toString()))
            }?.longOrNull("follower")
        } else {
            null
        }
        BiliContentMapper.uploaderInfo(data, followed, follower)
            ?: throw AppException("UP主不存在或已被封禁", retryable = false)
    }

    override suspend fun videos(
        mid: Long,
        page: Int,
        keyword: String,
    ): Result<TrackPage> = repoResult {
        auth.requireLogin()
        requireMid(mid)
        val safePage = page.coerceAtLeast(1)
        val params = linkedMapOf(
            "mid" to mid.toString(),
            "ps" to PAGE_SIZE.toString(),
            "pn" to safePage.toString(),
            "order" to "pubdate",
        )
        keyword.trim().takeIf { it.isNotEmpty() }?.let { params["keyword"] = it }
        val signed = wbi.sign(params)
        val data = api.getObject(PATH_ARC_SEARCH, signed)
        BiliContentMapper.uploaderVideos(data, safePage, PAGE_SIZE, mid)
    }

    override suspend fun setFollow(mid: Long, follow: Boolean): Result<Unit> = repoResult {
        auth.requireLogin()
        requireMid(mid)
        api.postForm(
            path = PATH_RELATION_MODIFY,
            form = linkedMapOf(
                "fid" to mid.toString(),
                "act" to if (follow) "1" else "2",
                "re_src" to "11",
                "csrf" to auth.requireCsrf(),
            ),
        )
    }

    private fun requireMid(mid: Long) {
        if (mid <= 0L) throw AppException("UP主ID不能为空", retryable = false)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    companion object {
        internal const val PATH_ACC_INFO = "/x/space/wbi/acc/info"
        internal const val PATH_ARC_SEARCH = "/x/space/wbi/arc/search"
        internal const val PATH_RELATION = "/x/relation"
        internal const val PATH_RELATION_STAT = "/x/relation/stat"
        internal const val PATH_RELATION_MODIFY = "/x/relation/modify"
        internal const val PAGE_SIZE = 30
    }
}
