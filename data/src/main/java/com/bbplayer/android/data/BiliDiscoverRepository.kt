package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.DiscoverRepository
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * [DiscoverRepository] 的匿名 B 站实现。
 *
 * - 联想词：`s.search.bilibili.com/main/suggest`（不同域，走 [HttpFetcher]）；
 * - 热搜：`x/web-interface/search/square?limit=10`（走 [BiliApi]，带 buvid3）。
 *
 * 两个接口都只读、无需登录；失败统一转成 [AppException]。
 */
class BiliDiscoverRepository internal constructor(
    private val api: BiliApi,
    private val http: HttpFetcher,
) : DiscoverRepository {

    constructor(api: BiliApi) : this(api, HttpFetcher())

    /** 便捷构造：由 AppContainer 直接调用。 */
    constructor(context: Context) : this(BiliApi(context))

    override suspend fun suggestions(query: String): Result<List<String>> = repoResult {
        val keyword = query.trim()
        if (keyword.isEmpty()) return@repoResult emptyList()
        val url = SUGGEST_URL.toHttpUrl().newBuilder()
            .addQueryParameter("main_ver", "v1")
            .addQueryParameter("term", keyword)
            .build()
            .toString()
        // 校验放在缓存写入前：code != 0 的结果不缓存，避免错误结果被缓存 5 分钟。
        val root = http.getJsonObject(url, HttpHeaders.BILIBILI) { payload ->
            payload.requireSuccess("搜索建议加载失败，请稍后重试")
        }
        DiscoverMapper.suggestions(root)
    }

    override suspend fun hotKeywords(): Result<List<String>> = repoResult {
        val data = api.getObject(
            path = "/x/web-interface/search/square",
            params = mapOf("limit" to "10"),
        )
        DiscoverMapper.hotKeywords(data)
    }

    companion object {
        private const val SUGGEST_URL = "https://s.search.bilibili.com/main/suggest"
    }
}
