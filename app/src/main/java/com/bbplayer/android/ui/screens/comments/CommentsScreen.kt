package com.bbplayer.android.ui.screens.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.CommentItem
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** 楼中楼分页状态（按一级评论 id 保存）。 */
private data class ReplyState(
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val moreError: String? = null,
    val items: List<CommentItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
)

/**
 * 评论页（SPEC E1/E2/E3）：一级评论分页（每页 20 条），可展开楼中楼回复并分页，
 * 网络失败显示错误与重试。
 */
@Composable
fun CommentsScreen(
    bvid: String,
    onBack: () -> Unit,
    /** 嵌入播放页（横向 pager 第 3 页）：不显示返回栏，由外层提供卡片背景。 */
    embedded: Boolean = false,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()

    var items by remember(bvid) { mutableStateOf<List<CommentItem>>(emptyList()) }
    var pageIndex by remember(bvid) { mutableStateOf(0) }
    var total by remember(bvid) { mutableStateOf(0) }
    var hasMore by remember(bvid) { mutableStateOf(false) }
    var loading by remember(bvid) { mutableStateOf(true) }
    var loadingMore by remember(bvid) { mutableStateOf(false) }
    var error by remember(bvid) { mutableStateOf<String?>(null) }
    var moreError by remember(bvid) { mutableStateOf<String?>(null) }
    var reloadKey by remember(bvid) { mutableStateOf(0) }
    var replyStates by remember(bvid) { mutableStateOf<Map<Long, ReplyState>>(emptyMap()) }

    suspend fun fetchPage(target: Int, replace: Boolean) {
        if (replace) {
            loading = true
            error = null
            moreError = null
        } else {
            loadingMore = true
            moreError = null
        }
        graph.commentRepository.fetchComments(bvid, target)
            .onSuccess { page ->
                items = if (replace) page.items else items + page.items
                pageIndex = target
                hasMore = page.hasMore
                total = if (page.total > 0) page.total else items.size
            }
            .onFailure {
                if (replace) error = it.toUserMessage() else moreError = it.toUserMessage()
            }
        loading = false
        loadingMore = false
    }

    suspend fun fetchReplies(root: CommentItem, target: Int, replace: Boolean) {
        val current = replyStates[root.id] ?: ReplyState(expanded = true)
        replyStates = replyStates + (
            root.id to current.copy(loading = true, error = null, moreError = null)
            )
        graph.commentRepository.fetchReplies(bvid, root.id, target)
            .onSuccess { page ->
                val cur = replyStates[root.id] ?: current
                replyStates = replyStates + (
                    root.id to cur.copy(
                        expanded = true,
                        loading = false,
                        error = null,
                        moreError = null,
                        items = if (replace) page.items else cur.items + page.items,
                        page = target,
                        hasMore = page.hasMore,
                    )
                    )
            }
            .onFailure {
                val cur = replyStates[root.id] ?: current
                replyStates = replyStates + (
                    root.id to cur.copy(
                        loading = false,
                        error = if (replace) it.toUserMessage() else cur.error,
                        moreError = if (replace) null else it.toUserMessage(),
                    )
                    )
            }
    }

    fun retryReplies(root: CommentItem) {
        replyStates = replyStates + (root.id to ReplyState(expanded = true, loading = true))
        scope.safeLaunch(onError = { throwable ->
            val cur = replyStates[root.id] ?: ReplyState(expanded = true)
            replyStates = replyStates + (
                root.id to cur.copy(loading = false, error = throwable.toUserMessage())
                )
        }) {
            fetchReplies(root, 1, replace = true)
        }
    }

    fun toggleReplies(root: CommentItem) {
        val state = replyStates[root.id]
        if (state?.expanded == true) {
            replyStates = replyStates + (root.id to state.copy(expanded = false))
            return
        }
        val inline = root.replies
        if (inline.isNotEmpty()) {
            replyStates = replyStates + (
                root.id to ReplyState(
                    expanded = true,
                    items = inline,
                    page = 1,
                    hasMore = inline.size < root.replyCount,
                )
                )
            return
        }
        replyStates = replyStates + (root.id to ReplyState(expanded = true, loading = true))
        scope.safeLaunch(onError = { throwable ->
            val cur = replyStates[root.id] ?: ReplyState(expanded = true)
            replyStates = replyStates + (
                root.id to cur.copy(loading = false, error = throwable.toUserMessage())
                )
        }) {
            fetchReplies(root, 1, replace = true)
        }
    }

    LaunchedEffect(bvid, reloadKey) {
        scope.safeLaunch(onError = { throwable ->
            loading = false
            error = throwable.toUserMessage()
        }) {
            fetchPage(1, replace = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (embedded) 16.dp else 40.dp),
    ) {
        if (embedded) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("评论", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    if (total > 0) {
                        Text(
                            text = "共 $total 条",
                            color = SaltTheme.colors.subText,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            item {
                SmallTopBar(
                    title = "评论",
                    onBack = onBack,
                    actions = {
                        if (total > 0) {
                            Text(
                                text = "共 $total 条",
                                color = SaltTheme.colors.subText,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    },
                )
            }
        }

        when {
            loading && items.isEmpty() -> item { LoadingState(text = "正在加载评论…") }
            error != null && items.isEmpty() -> item {
                ErrorState(message = error!!, onRetry = { reloadKey++ })
            }
            items.isEmpty() -> item {
                EmptyState(
                    title = "还没有评论",
                    subtitle = "来发第一条评论吧（当前版本仅支持浏览）",
                    icon = Icons.AutoMirrored.Rounded.Comment,
                )
            }
            else -> {
                itemsIndexed(items, key = { _, comment -> comment.id }) { _, comment ->
                    CommentRow(
                        comment = comment,
                        replyState = replyStates[comment.id],
                        onToggleReplies = { toggleReplies(comment) },
                        onRetryReplies = { retryReplies(comment) },
                        onLoadMoreReplies = { state ->
                            val next = state.page + 1
                            scope.safeLaunch(onError = { throwable ->
                                val cur = replyStates[comment.id] ?: state
                                replyStates = replyStates + (
                                    comment.id to cur.copy(loading = false, moreError = throwable.toUserMessage())
                                    )
                            }) {
                                fetchReplies(comment, next, replace = false)
                            }
                        },
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            loadingMore -> Text(
                                text = "加载中…",
                                color = SaltTheme.colors.subText,
                                fontSize = 12.sp,
                            )
                            moreError != null -> SaltPill(
                                text = "加载失败，点击重试",
                                onClick = {
                                    scope.safeLaunch(onError = { throwable ->
                                        moreError = throwable.toUserMessage()
                                    }) { fetchPage(pageIndex + 1, replace = false) }
                                },
                            )
                            hasMore -> SaltPill(
                                text = "加载更多评论",
                                onClick = {
                                    scope.safeLaunch(onError = { throwable ->
                                        loadingMore = false
                                        moreError = throwable.toUserMessage()
                                    }) { fetchPage(pageIndex + 1, replace = false) }
                                },
                            )
                            else -> Text(
                                text = "没有更多评论了",
                                color = SaltTheme.colors.subText,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentItem,
    replyState: ReplyState?,
    onToggleReplies: () -> Unit,
    onRetryReplies: () -> Unit,
    onLoadMoreReplies: (ReplyState) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 9.dp),
    ) {
        Row {
            CoverImage(
                url = comment.avatarUrl,
                modifier = Modifier.size(36.dp),
                corner = 18.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = comment.userName.ifBlank { "匿名用户" },
                    color = SaltTheme.colors.subText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = comment.content,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatRelativeTime(comment.createdAtSec),
                        color = SaltTheme.colors.subText.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Icon(
                        imageVector = Icons.Rounded.ThumbUp,
                        contentDescription = null,
                        tint = SaltTheme.colors.subText.copy(alpha = 0.75f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = Format.count(comment.likeCount),
                        color = SaltTheme.colors.subText.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                    )
                }
            }
        }

        if (comment.replyCount > 0 || replyState?.expanded == true) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(start = 46.dp)) {
                SaltPill(
                    text = when {
                        replyState?.expanded == true -> "收起回复"
                        comment.replyCount > 0 -> "查看回复 (${comment.replyCount})"
                        else -> "查看回复"
                    },
                    selected = replyState?.expanded == true,
                    onClick = onToggleReplies,
                )
            }
        }

        val state = replyState
        if (state != null && state.expanded) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .padding(start = 46.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SaltTheme.colors.subBackground.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                when {
                    state.loading && state.items.isEmpty() -> Text(
                        text = "正在加载回复…",
                        color = SaltTheme.colors.subText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    state.error != null && state.items.isEmpty() -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.error,
                            color = SaltTheme.colors.subText,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        SaltPill(text = "重试", onClick = onRetryReplies)
                    }
                    state.items.isEmpty() -> Text(
                        text = "暂无回复",
                        color = SaltTheme.colors.subText,
                        fontSize = 12.sp,
                    )
                    else -> {
                        state.items.forEach { reply ->
                            ReplyRow(reply)
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                state.loading -> Text(
                                    text = "加载中…",
                                    color = SaltTheme.colors.subText,
                                    fontSize = 11.sp,
                                )
                                state.moreError != null -> SaltPill(
                                    text = "加载失败，点击重试",
                                    onClick = { onLoadMoreReplies(state) },
                                )
                                state.hasMore -> SaltPill(
                                    text = "加载更多回复",
                                    onClick = { onLoadMoreReplies(state) },
                                )
                                else -> Text(
                                    text = "没有更多回复了",
                                    color = SaltTheme.colors.subText,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(reply: CommentItem) {
    Row(Modifier.fillMaxWidth()) {
        CoverImage(
            url = reply.avatarUrl,
            modifier = Modifier.size(26.dp),
            corner = 13.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.userName.ifBlank { "匿名用户" },
                    color = SaltTheme.colors.subText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(reply.createdAtSec),
                    color = SaltTheme.colors.subText.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(text = reply.content, fontSize = 13.sp, lineHeight = 18.sp)
            if (reply.likeCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "赞 ${Format.count(reply.likeCount)}",
                    color = SaltTheme.colors.subText.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun formatRelativeTime(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    val nowSec = System.currentTimeMillis() / 1000
    val diff = (nowSec - epochSec).coerceAtLeast(0L)
    return when {
        diff < 60L -> "刚刚"
        diff < 3600L -> "${diff / 60} 分钟前"
        diff < 86_400L -> "${diff / 3600} 小时前"
        diff < 7 * 86_400L -> "${diff / 86_400} 天前"
        else -> runCatching {
            java.time.Instant.ofEpochSecond(epochSec)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }.getOrDefault("")
    }
}
