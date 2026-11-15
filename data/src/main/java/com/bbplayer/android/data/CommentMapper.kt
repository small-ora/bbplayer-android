package com.bbplayer.android.data

import com.bbplayer.android.core.model.CommentItem
import com.bbplayer.android.core.model.CommentPage
import kotlinx.serialization.json.JsonObject

/**
 * B 站评论接口响应 → [CommentPage] 的纯函数映射（可单测，不依赖网络）。
 *
 * 一级评论（`/x/v2/reply`）与楼中楼（`/x/v2/reply/reply`）的 `data.replies[]`
 * 结构一致，因此共用 [commentItem]。
 */
internal object CommentMapper {

    /** 评论页大小（与请求参数 `ps` 保持一致）。 */
    const val PAGE_SIZE = 20

    fun commentsPage(data: JsonObject, page: Int): CommentPage {
        val items = data.arrOrNull("replies").toObjectList().mapNotNull(::commentItem)
        val pageNode = data.objOrNull("page")
        val total = pageNode?.intOrNull("count", "acount") ?: 0
        val size = pageNode?.intOrNull("size")?.takeIf { it > 0 } ?: PAGE_SIZE
        val currentPage = pageNode?.intOrNull("num", "pn")?.takeIf { it > 0 } ?: page
        val hasMore = items.isNotEmpty() && total > 0 && currentPage * size < total
        return CommentPage(
            items = items,
            page = currentPage,
            hasMore = hasMore,
            total = total.coerceAtLeast(0),
        )
    }

    /** `data.replies[]` 单条 → [CommentItem]；缺少 rpid 的脏数据丢弃。 */
    fun commentItem(raw: JsonObject): CommentItem? {
        val id = raw.longOrNull("rpid", "rpid_str") ?: return null
        val member = raw.objOrNull("member")
        val content = raw.objOrNull("content")?.strOrNull("message")
            ?: raw.strOrNull("message")
            ?: return null
        val userName = HtmlText.clean(member?.strOrNull("uname", "name")).ifBlank { "未知用户" }
        return CommentItem(
            id = id,
            userName = userName,
            avatarUrl = BiliMapper.normalizeImage(member?.strOrNull("avatar")),
            content = HtmlText.decodeEntities(content).trim(),
            likeCount = raw.longOrNull("like") ?: 0L,
            createdAtSec = raw.longOrNull("ctime") ?: 0L,
            replyCount = (raw.longOrNull("rcount") ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            replies = raw.arrOrNull("replies").toObjectList().mapNotNull(::commentItem),
        )
    }

    /** `x/web-interface/view` 响应 → aid（评论接口按 aid 查询）。 */
    fun aidFromView(view: JsonObject): Long? = view.longOrNull("aid")

    /**
     * 评论接口业务错误码 → 中文提示。
     * 复用 [apiMessage] 的通用映射，但对「视频正常但评论区关闭」更精确。
     */
    fun commentErrorMessage(code: Long, serverMessage: String?): String = when (code) {
        -404L -> "评论不存在或已关闭"
        -403L -> "评论不可见，可能已被UP主关闭"
        -412L -> "请求被B站安全策略拦截，请稍后重试"
        -509L, -799L -> "请求过于频繁，请稍后重试"
        else -> apiMessage(code, serverMessage)
    }

    /** 楼中楼接口错误码 → 中文提示。 */
    fun replyErrorMessage(code: Long, serverMessage: String?): String = when (code) {
        -404L -> "回复不存在或已删除"
        else -> commentErrorMessage(code, serverMessage)
    }
}
