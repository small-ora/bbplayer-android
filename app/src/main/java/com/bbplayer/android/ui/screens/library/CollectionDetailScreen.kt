package com.bbplayer.android.ui.screens.library

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** Bilibili 合集 / 系列详情（登录后分页加载）。 */
@Composable
fun CollectionDetailScreen(
    seasonId: Long,
    title: String,
    onBack: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val state = rememberPagedTracks(seasonId) { page ->
        graph.collectionRepository.collectionTracks(seasonId, page)
    }
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())
    val listState = rememberLazyListState()
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(nearBottom, state.page, state.hasMore, state.loading, state.loadingMore) {
        if (nearBottom && state.error == null) state.loadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item { SmallTopBar(title = title, onBack = onBack) }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Sm)) {
                Text(title, style = BbType.Title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.items.size} 个视频",
                        color = SaltTheme.colors.subText,
                        style = BbType.Label,
                    )
                    Spacer(Modifier.padding(horizontal = BbSpacing.Sm))
                    SaltPill(
                        text = "播放全部",
                        selected = state.items.isNotEmpty(),
                        onClick = {
                            if (state.items.isNotEmpty()) onPlayTrack(state.items.toList(), 0)
                        },
                    )
                }
            }
        }
        when {
            state.loading && state.items.isEmpty() -> item {
                LoadingState(text = "正在加载合集…")
            }
            state.error != null && state.items.isEmpty() -> item {
                ErrorState(message = state.error!!, onRetry = { scope.safeLaunch { state.retry() } })
            }
            state.items.isEmpty() -> item {
                EmptyState(title = "合集是空的", subtitle = "该合集暂时没有可播放的视频")
            }
            else -> {
                items(count = state.items.size, key = { idx -> state.items[idx].key }) { idx ->
                    val track = state.items[idx]
                    TrackRow(
                        track = track,
                        isCurrent = playback.track?.key == track.key,
                        onClick = { onPlayTrack(state.items.toList(), idx) },
                    )
                }
                if (state.error != null) {
                    item {
                        ErrorState(message = state.error!!, onRetry = { scope.safeLaunch { state.retry() } })
                    }
                } else if (state.loadingMore) {
                    item { LoadingState(text = "加载更多…") }
                } else if (state.hasMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = BbSpacing.Lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            SaltPill(text = "加载更多", onClick = {
                                scope.safeLaunch { state.loadMore() }
                            })
                        }
                    }
                } else {
                    item {
                        Text(
                            "已经到底啦",
                            color = SaltTheme.colors.subText,
                            style = BbType.Micro,
                            modifier = Modifier.fillMaxWidth().padding(vertical = BbSpacing.Xl),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
