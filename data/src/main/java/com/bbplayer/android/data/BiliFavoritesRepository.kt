package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.AuthCookieProvider
import com.bbplayer.android.core.contracts.FavoritesRepository
import com.bbplayer.android.core.model.FavoriteFolder
import com.bbplayer.android.core.model.TrackPage
import kotlinx.coroutines.CancellationException

/**
 * [FavoritesRepository] 的登录态 B 站实现。
 *
 * - 收藏夹列表：`GET /x/v3/fav/folder/created/list-all?up_mid=<mid>`
 * - 收藏夹内容：`GET /x/v3/fav/resource/list?media_id=&pn=&ps=&platform=web`
 * - 收藏 / 取消：`POST /x/v3/fav/resource/deal`（表单 `rid` `type=2` `add_media_ids`
 *   `del_media_ids` `csrf`）
 * - 是否已收藏：`GET /x/v3/fav/folder/created/list-all?up_mid=&rid=&type=2`
 *
 * 所有方法先检查 [AuthCookieProvider.isLoggedIn]；未登录返回
 * `AppException("请先登录 Bilibili 账号", retryable = false)`，不会发请求。
 * 写操作（deal）不缓存；重复 id 会去重，空列表表示该侧不改动。
 */
class BiliFavoritesRepository internal constructor(
    private val api: BiliContentApi,
    private val auth: AuthCookieProvider,
) : FavoritesRepository {

    /** 生产构造：共享 [BiliApi]；登录 Cookie 由 BiliApi 自身的 AuthCookieProvider / 进程级 AuthRegistry 注入。 */
    constructor(api: BiliApi, auth: AuthCookieProvider) : this(BiliApiContentApi(api), auth)

    /** 便捷构造：每个仓储自建共享 OkHttp/缓存的 [BiliApi]，Cookie 由 [auth] 提供。 */
    constructor(context: Context, auth: AuthCookieProvider) :
        this(BiliApi(context, authProvider = auth), auth)

    override suspend fun folders(): Result<List<FavoriteFolder>> = repoResult {
        auth.requireLogin()
        val mid = auth.requireMid()
        val data = api.getObject(PATH_FOLDERS, mapOf("up_mid" to mid.toString()))
        BiliContentMapper.favoriteFolders(data)
    }

    override suspend fun folderTracks(
        mediaId: Long,
        page: Int,
        pageSize: Int,
    ): Result<TrackPage> = repoResult {
        auth.requireLogin()
        if (mediaId <= 0L) throw AppException("收藏夹ID不能为空", retryable = false)
        val safePage = page.coerceAtLeast(1)
        // 收藏夹接口单页上限 40；越界传参会直接被服务端拒绝。
        val safeSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val data = api.getObject(
            path = PATH_FOLDER_TRACKS,
            params = mapOf(
                "media_id" to mediaId.toString(),
                "pn" to safePage.toString(),
                "ps" to safeSize.toString(),
                "platform" to "web",
            ),
        )
        BiliContentMapper.favoriteTracks(data, safePage, safeSize)
    }

    override suspend fun updateFavorite(
        aid: Long,
        addTo: List<Long>,
        removeFrom: List<Long>,
    ): Result<Unit> = repoResult {
        auth.requireLogin()
        if (aid <= 0L) throw AppException("视频ID不能为空", retryable = false)
        val add = addTo.filter { it > 0L }.distinct()
        val remove = removeFrom.filter { it > 0L }.distinct()
        // 两侧都为空 = 不改动，直接成功（不要求 csrf，避免无意义请求）。
        if (add.isEmpty() && remove.isEmpty()) return@repoResult
        val csrf = auth.requireCsrf()
        val form = linkedMapOf(
            "rid" to aid.toString(),
            "type" to "2",
        )
        if (add.isNotEmpty()) form["add_media_ids"] = add.joinToString(",")
        if (remove.isNotEmpty()) form["del_media_ids"] = remove.joinToString(",")
        form["csrf"] = csrf
        api.postForm(PATH_DEAL, form)
    }

    override suspend fun isFavorited(aid: Long): Result<List<Long>> = repoResult {
        auth.requireLogin()
        if (aid <= 0L) return@repoResult emptyList()
        val mid = auth.requireMid()
        // 该接口失败（如 -404 视频不存在）按“不在任何收藏夹”处理，不打断详情页。
        val data = attempt {
            api.getObject(
                path = PATH_FOLDERS,
                params = mapOf(
                    "up_mid" to mid.toString(),
                    "rid" to aid.toString(),
                    "type" to "2",
                ),
            )
        } ?: return@repoResult emptyList()
        BiliContentMapper.favoriteStateIds(data)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    companion object {
        internal const val PATH_FOLDERS = "/x/v3/fav/folder/created/list-all"
        internal const val PATH_FOLDER_TRACKS = "/x/v3/fav/resource/list"
        internal const val PATH_DEAL = "/x/v3/fav/resource/deal"
        internal const val MAX_PAGE_SIZE = 40
    }
}
