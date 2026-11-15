package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import com.bbplayer.android.core.contracts.CollectionRepository
import com.bbplayer.android.core.model.BiliCollection
import com.bbplayer.android.core.model.TrackPage
import kotlinx.coroutines.CancellationException

/**
 * [CollectionRepository] 的登录态 B 站实现（合集 / 系列）。
 *
 * ## 接口选择（与任务书的差异，见交付报告）
 *
 * 任务书给的 `GET /x/v3/fav/folder/collected/list` 实际返回的是「**收藏的他人收藏夹**」，
 * 其 `id` 是 media_id 而非 season_id，拿它去取合集内容会失败；真正对应「我的合集/系列」的是
 * `GET /x/polymer/web-space/seasons_series_list?mid&page_num&page_size`
 * （`items_lists.seasons_list` + `series_list`，`season_id`/`series_id`）。
 * 因此这里**首选空间合集接口**，仍保留 collected/list 作为空结果/失败时的兜底，
 * 保证接口形状变化时不会直接空白。
 *
 * 合集内容：`GET /x/space/fav/season/list?season_id&ps=30&pn`（任务书指定）；
 * 该接口若失败则回退到当前在用的
 * `GET /x/polymer/web-space/seasons_archives_list?mid&season_id&sort_reverse=false&page_num&page_size`
 * （`data.archives[]`）。
 *
 * 未登录统一返回不可重试的 `AppException("请先登录 Bilibili 账号")`。
 */
class BiliCollectionRepository internal constructor(
    private val api: BiliContentApi,
    private val auth: AuthCookieProvider,
) : CollectionRepository {

    constructor(api: BiliApi, auth: AuthCookieProvider) : this(BiliApiContentApi(api), auth)

    /** 便捷构造：每个仓储自建共享 OkHttp/缓存的 [BiliApi]，Cookie 由 [auth] 提供。 */
    constructor(context: Context, auth: AuthCookieProvider) :
        this(BiliApi(context, authProvider = auth), auth)

    override suspend fun myCollections(): Result<List<BiliCollection>> = repoResult {
        auth.requireLogin()
        val mid = auth.requireMid()
        val space = attempt {
            api.getObject(
                path = PATH_SPACE_SEASONS,
                params = mapOf(
                    "mid" to mid.toString(),
                    "page_num" to "1",
                    "page_size" to PAGE_SIZE.toString(),
                ),
            )
        }
        val collections = space?.let(BiliContentMapper::spaceCollections).orEmpty()
        if (collections.isNotEmpty()) return@repoResult collections
        // 兜底：collected/list（收藏的收藏夹；id 可能是 media_id）。
        val collected = attempt {
            api.getObject(
                path = PATH_COLLECTED,
                params = mapOf(
                    "up_mid" to mid.toString(),
                    "pn" to "1",
                    "ps" to PAGE_SIZE.toString(),
                    "platform" to "web",
                ),
            )
        } ?: return@repoResult collections
        BiliContentMapper.collections(collected).ifEmpty { collections }
    }

    override suspend fun collectionTracks(
        seasonId: Long,
        page: Int,
    ): Result<TrackPage> = repoResult {
        auth.requireLogin()
        if (seasonId <= 0L) throw AppException("合集ID不能为空", retryable = false)
        val safePage = page.coerceAtLeast(1)
        var primaryError: Throwable? = null
        val primary = try {
            api.getObject(
                path = PATH_SEASON_LIST,
                params = mapOf(
                    "season_id" to seasonId.toString(),
                    "ps" to SEASON_PAGE_SIZE.toString(),
                    "pn" to safePage.toString(),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            primaryError = e
            null
        }
        val mapped = primary?.let {
            BiliContentMapper.collectionTracks(it, safePage, SEASON_PAGE_SIZE)
        }
        if (mapped != null && (mapped.items.isNotEmpty() || safePage > 1)) return@repoResult mapped

        // 任务书接口失败或首页形状不符时回退到空间合集视频接口（真实在用）。
        val mid = auth.requireMid()
        val fallback = attempt {
            api.getObject(
                path = PATH_SEASON_ARCHIVES,
                params = mapOf(
                    "mid" to mid.toString(),
                    "season_id" to seasonId.toString(),
                    "sort_reverse" to "false",
                    "page_num" to safePage.toString(),
                    "page_size" to SEASON_PAGE_SIZE.toString(),
                ),
            )
        }
        if (fallback != null) {
            return@repoResult BiliContentMapper.collectionTracks(fallback, safePage, SEASON_PAGE_SIZE)
        }
        if (mapped != null) return@repoResult mapped
        throw primaryError ?: AppException("合集内容加载失败，请稍后重试", retryable = true)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    companion object {
        internal const val PATH_SPACE_SEASONS = "/x/polymer/web-space/seasons_series_list"
        internal const val PATH_COLLECTED = "/x/v3/fav/folder/collected/list"
        internal const val PATH_SEASON_LIST = "/x/space/fav/season/list"
        internal const val PATH_SEASON_ARCHIVES = "/x/polymer/web-space/seasons_archives_list"
        internal const val PAGE_SIZE = 50
        internal const val SEASON_PAGE_SIZE = 30
    }
}
