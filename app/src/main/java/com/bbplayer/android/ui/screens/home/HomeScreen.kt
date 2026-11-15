package com.bbplayer.android.ui.screens.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.LargeTitle
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SectionHeader
import com.bbplayer.android.ui.components.TrackRow
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenLeaderboard: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val history by graph.historyRepository.recent(20).collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = com.bbplayer.android.core.model.PlaybackState())
    var trending by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        graph.musicRepository.getTrending()
            .onSuccess {
                trending = it
                loading = false
            }
            .onFailure {
                error = it.toUserMessage()
                loading = false
            }
    }

    fun play(tracks: List<Track>, index: Int) {
        onPlayTrack(tracks, index)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            LargeTitle(
                title = "发现音乐",
                subtitle = "从 B 站听见喜欢的歌",
                actions = {
                    IconAction(Icons.Rounded.Refresh, "刷新", onClick = { refreshKey++ })
                },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SaltTheme.colors.subBackground)
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = SaltTheme.colors.subText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "搜索音乐 / BV / 链接",
                    color = SaltTheme.colors.subText.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                )
            }
        }

        if (history.isNotEmpty()) {
            item { SectionHeader(title = "最近播放", action = "全部", onAction = onOpenSearch) }
            item {
                val recent = history.take(10)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = recent.size, key = { idx -> recent[idx].track.key + "@" + recent[idx].playedAt }) { idx ->
                        RecentCard(
                            track = recent[idx].track,
                            onClick = { play(recent.map { it.track }, idx) },
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "B 站音乐区热门",
                subtitle = "排行榜",
                action = "完整榜单",
                onAction = onOpenLeaderboard,
            )
        }
        when {
            loading -> item { LoadingState() }
            error != null -> item {
                ErrorState(message = error!!, onRetry = { refreshKey++ })
            }
            else -> items(count = trending.size, key = { idx -> trending[idx].key }) { idx ->
                val track = trending[idx]
                TrackRow(
                    track = track,
                    isCurrent = playback.track?.key == track.key,
                    onClick = { play(trending, idx) },
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun RecentCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
    ) {
        CoverImage(url = track.coverUrl, modifier = Modifier.size(112.dp), corner = 12.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.displayTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
        )
        Text(
            text = track.uploader,
            color = SaltTheme.colors.subText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = SaltTheme.textStyles.sub.fontSize,
        )
    }
}
