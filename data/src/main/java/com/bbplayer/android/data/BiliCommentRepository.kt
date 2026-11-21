package com.bbplayer.android.data

import com.bbplayer.android.core.input.InputResolver
import com.bbplayer.android.core.input.VideoInput

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.CommentRepository
import com.bbplayer.android.core.model.CommentPage
import java.util.concurrent.ConcurrentHashMap

/**
 * [CommentRepository] 的匿名 B 站实现。
 *
 * - 一级评论：`/x/v2/reply?type=1&oid=<aid>&pn=&ps=20&sort=2`（sort=2 热门 / 0 时间）；
 * - 楼中楼：`/x/v2/reply/reply?type=1&oid=<aid>&root=<rpid>&pn=&ps=20`；
 * - bvid → aid 通过 `/x/web-interface/view` 解析，并在内存中缓存（有上限）。
 *
 * 评论区关闭 / 风控 / 视频不存在都会转成中文 [AppException]。
 */
class BiliCommentRepository(
    private val api: BiliApi,
) : CommentRepository {

    /** 便捷构造：由 AppContainer 直接调用。 */
    constructor(context: Context) : this(BiliApi(context))

    private val aidCache = ConcurrentHashMap<String, Long>()

    override suspend fun fetchComments(bvid: String, page: Int, sort: Int): Result<CommentPage> =
        repoResult {
            val safePage = page.coerceAtLeast(1)
            val aid = resolveAid(bvid)
            val data = api.getObject(
                path = "/x/v2/reply",
                params = mapOf(
                    "type" to "1",
                    "oid" to aid.toString(),
                    "pn" to safePage.toString(),
                    "ps" to CommentMapper.PAGE_SIZE.toString(),
                    "sort" to if (sort == SORT_BY_TIME) "0" else "2",
                ),
                messageFor = CommentMapper::commentErrorMessage,
            )
            val result = CommentMapper.commentsPage(data, safePage)
            // 首页没有评论通常意味着评论区被关闭 / 视频被删除，给出明确提示；
            // 翻页到末尾的空页属于正常结束，不报错。
            if (result.items.isEmpty() && safePage == 1) {
                throw AppException("评论不存在或已关闭", retryable = false)
            }
            result
        }

    override suspend fun fetchReplies(bvid: String, rootId: Long, page: Int): Result<CommentPage> =
        repoResult {
            if (rootId <= 0L) throw AppException("评论ID不正确", retryable = false)
            val safePage = page.coerceAtLeast(1)
            val aid = resolveAid(bvid)
            val data = api.getObject(
                path = "/x/v2/reply/reply",
                params = mapOf(
                    "type" to "1",
                    "oid" to aid.toString(),
                    "root" to rootId.toString(),
                    "pn" to safePage.toString(),
                    "ps" to CommentMapper.PAGE_SIZE.toString(),
                ),
                messageFor = CommentMapper::replyErrorMessage,
            )
            val result = CommentMapper.commentsPage(data, safePage)
            if (result.items.isEmpty() && safePage == 1) {
                throw AppException("回复不存在或已删除", retryable = false)
            }
            result
        }

    /** bvid / av 号 → aid；结果缓存，避免每次翻页都请求 view 接口。 */
    private suspend fun resolveAid(raw: String): Long {
        val bvid = raw.trim()
        if (bvid.isEmpty()) throw AppException("视频ID不能为空", retryable = false)
        InputResolver.extractVideoRef(bvid)?.let { ref ->
            when (ref) {
                is VideoInput.Aid -> return ref.aid
                is VideoInput.Bvid -> Unit
                else -> Unit
            }
        }
        val normalized = if (bvid.startsWith("BV", ignoreCase = true)) {
            InputResolver.normalizeBvid(bvid)
        } else {
            bvid
        }
        aidCache[normalized]?.let { return it }
        val view = api.getObject("/x/web-interface/view", mapOf("bvid" to normalized))
        val aid = CommentMapper.aidFromView(view)
            ?: throw AppException("视频不存在或已被删除", retryable = false)
        if (aidCache.size >= MAX_AID_CACHE) aidCache.clear()
        aidCache[normalized] = aid
        return aid
    }

    companion object {
        /** 与 `sort` 参数对应：0 = 按时间，其余（默认 2）= 按热度。 */
        const val SORT_BY_TIME = 0

        private const val MAX_AID_CACHE = 128
    }
}
