package com.bbplayer.android.ui.screens.uploader

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.model.UploaderInfo
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.screens.library.rememberPagedTracks
import com.bbplayer.android.ui.theme.BbTokens
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** UP 主主页：资料、关注 / 取关与视频分页列表。 */
@Composable
fun UploaderScreen(
    mid: Long,
    onBack: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenLogin: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by graph.authRepository.session().collectAsStateWithLifecycle(initialValue = null as BiliUser?)
    val loggedIn = session != null
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())

    var info by remember(mid) { mutableStateOf<UploaderInfo?>(null) }
    var infoError by remember(mid) { mutableStateOf<String?>(null) }
    var infoLoading by remember(mid) { mutableStateOf(true) }
    var infoAttempt by remember(mid) { mutableStateOf(0) }
    var followed by remember(mid) { mutableStateOf(false) }
    var followBusy by remember(mid) { mutableStateOf(false) }

    LaunchedEffect(mid, infoAttempt) {
        infoLoading = true
        infoError = null
        graph.uploaderRepository.info(mid)
            .onSuccess {
                info = it
                followed = it.followed
            }
            .onFailure { infoError = it.toUserMessage() }
        infoLoading = false
    }

    val videos = rememberPagedTracks(mid) { page -> graph.uploaderRepository.videos(mid, page) }
    val listState = rememberLazyListState()
    val nearBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 2
        }
    }
    LaunchedEffect(nearBottom, videos.page, videos.hasMore, videos.loading, videos.loadingMore) {
        if (nearBottom && videos.error == null) videos.loadMore()
    }

    fun toggleFollow() {
        if (followBusy) return
        if (!loggedIn) {
            Toast.makeText(context, "请先登录 Bilibili 账号", Toast.LENGTH_SHORT).show()
            onOpenLogin()
            return
        }
        followBusy = true
        val target = !followed
        scope.safeLaunch(onError = {
            followBusy = false
            Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show()
        }) {
            graph.uploaderRepository.setFollow(mid, target)
                .onSuccess {
                    followed = target
                    Toast.makeText(
                        context,
                        if (target) "已关注 ${info?.name.orEmpty()}" else "已取消关注",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure { Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show() }
            followBusy = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item { SmallTopBar(title = info?.name ?: "UP 主", onBack = onBack) }
        item {
            when {
                infoLoading && info == null -> LoadingState(text = "正在加载 UP 主资料…")
                infoError != null && info == null -> ErrorState(
                    message = infoError!!,
                    onRetry = { infoAttempt++ },
                )
                else -> {
                    val user = info
                    Column(Modifier.fillMaxWidth().padding(horizontal = SaltTheme.dimens.padding, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverImage(
                                url = user?.avatarUrl.orEmpty(),
                                modifier = Modifier.size(72.dp),
                                corner = 36.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = user?.name ?: "UP 主",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "粉丝 ${Format.count(user?.follower ?: 0L)}",
                                    color = SaltTheme.colors.subText,
                                    fontSize = 12.sp,
                                )
                                if (!user?.sign.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = user.sign,
                                        color = SaltTheme.colors.subText,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            SaltPill(
                                text = when {
                                    followBusy -> "处理中…"
                                    followed -> "已关注"
                                    else -> "+ 关注"
                                },
                                selected = followed,
                                onClick = { toggleFollow() },
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("视频", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${videos.items.size} 个",
                    color = SaltTheme.colors.subText,
                    fontSize = 12.sp,
                )
            }
        }
        when {
            videos.loading && videos.items.isEmpty() -> item {
                LoadingState(text = "正在加载视频…")
            }
            videos.error != null && videos.items.isEmpty() -> item {
                ErrorState(message = videos.error!!, onRetry = { scope.safeLaunch { videos.retry() } })
            }
            videos.items.isEmpty() -> item {
                EmptyState(title = "还没有视频", subtitle = "该 UP 主暂时没有可播放的视频")
            }
            else -> {
                items(count = videos.items.size, key = { idx -> videos.items[idx].key }) { idx ->
                    val track = videos.items[idx]
                    TrackRow(
                        track = track,
                        isCurrent = playback.track?.key == track.key,
                        onClick = { onPlayTrack(videos.items.toList(), idx) },
                    )
                }
                if (videos.error != null) {
                    item {
                        ErrorState(message = videos.error!!, onRetry = { scope.safeLaunch { videos.retry() } })
                    }
                } else if (videos.loadingMore) {
                    item { LoadingState(text = "加载更多…") }
                } else if (videos.hasMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SaltPill(text = "加载更多", onClick = {
                                scope.safeLaunch { videos.loadMore() }
                            })
                        }
                    }
                } else {
                    item {
                        Text(
                            "已经到底啦",
                            color = SaltTheme.colors.subText,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
