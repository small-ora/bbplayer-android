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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.ListeningSummary
import com.bbplayer.android.core.model.Playlist
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.LargeTitle
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SectionHeader
import com.bbplayer.android.ui.components.TrackRow
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenLeaderboard: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenPlaylist: (String) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val history by graph.historyRepository.recent(20).collectAsStateWithLifecycle(initialValue = emptyList())
    val summary by graph.historyRepository.summary().collectAsStateWithLifecycle(initialValue = ListeningSummary())
    val playlists by graph.libraryRepository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by graph.libraryRepository.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
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

    // 同一首歌重复播放时只保留最近一次，避免「最近播放」里出现多张相同的卡
    val recentTracks = history.distinctBy { it.track.key }.take(10)
    val recentPlayedPlaylists = playlists
        .filter { it.lastPlayedAt > 0 }
        .sortedByDescending { it.lastPlayedAt }
        .take(8)
    val myPlaylists = playlists.sortedByDescending { it.updatedAt }.take(8)
    val favoriteTracks = favorites.map { it.track }.take(8)
    val hotTracks = trending.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
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

        // ---- 最近播放（单曲）----
        if (recentTracks.isNotEmpty()) {
            item { SectionHeader(title = "最近播放", action = "全部", onAction = onOpenHistory) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = recentTracks.size, key = { idx -> recentTracks[idx].track.key }) { idx ->
                        RecentCard(
                            track = recentTracks[idx].track,
                            onClick = { play(recentTracks.map { it.track }, idx) },
                        )
                    }
                }
            }
        }

        // ---- 最近播放的歌单 ----
        if (recentPlayedPlaylists.isNotEmpty()) {
            item {
                SectionHeader(title = "最近播放的歌单", action = "音乐库", onAction = onOpenLibrary)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = recentPlayedPlaylists.size, key = { idx -> "recent-" + recentPlayedPlaylists[idx].id }) { idx ->
                        PlaylistCard(
                            playlist = recentPlayedPlaylists[idx],
                            metaSuffix = "最近播放",
                            onClick = { onOpenPlaylist(recentPlayedPlaylists[idx].id) },
                        )
                    }
                }
            }
        } else if (myPlaylists.isNotEmpty()) {
            // 还没有播放过歌单时，先展示本地歌单，避免新用户看不到这个模块
            item {
                SectionHeader(title = "我的歌单", action = "音乐库", onAction = onOpenLibrary)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = myPlaylists.size, key = { idx -> "mine-" + myPlaylists[idx].id }) { idx ->
                        PlaylistCard(
                            playlist = myPlaylists[idx],
                            metaSuffix = if (myPlaylists[idx].lastPlayedAt > 0) "最近播放" else null,
                            onClick = { onOpenPlaylist(myPlaylists[idx].id) },
                        )
                    }
                }
            }
        }

        // ---- 我的收藏 ----
        if (favoriteTracks.isNotEmpty()) {
            item { SectionHeader(title = "我的收藏", action = "音乐库", onAction = onOpenLibrary) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = favoriteTracks.size, key = { idx -> "fav-" + favoriteTracks[idx].key }) { idx ->
                        RecentCard(
                            track = favoriteTracks[idx],
                            onClick = { play(favoriteTracks, idx) },
                        )
                    }
                }
            }
        }

        // ---- 收听统计 ----
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = 6.dp)) {
                RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenHistory).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SaltTheme.colors.highlight.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.BarChart,
                                contentDescription = null,
                                tint = SaltTheme.colors.highlight,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("累计收听", color = SaltTheme.colors.subText, fontSize = 12.sp)
                            Text(
                                Format.totalListening(summary.totalListenedMs),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("播放次数", color = SaltTheme.colors.subText, fontSize = 12.sp)
                            Text("${summary.totalPlayCount} 次", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ---- B 站音乐区热门（只取前 6 首，不再占满整页）----
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
            else -> items(count = hotTracks.size, key = { idx -> hotTracks[idx].key }) { idx ->
                val track = hotTracks[idx]
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

@Composable
private fun PlaylistCard(playlist: Playlist, metaSuffix: String?, onClick: () -> Unit) {
    val covers = playlist.coverUrls
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SaltTheme.colors.subBackground),
        ) {
            when {
                covers.size >= 4 -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CoverImage(url = covers[0], modifier = Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
                        CoverImage(url = covers[1], modifier = Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CoverImage(url = covers[2], modifier = Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
                        CoverImage(url = covers[3], modifier = Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
                    }
                }
                covers.isNotEmpty() -> CoverImage(url = covers[0], modifier = Modifier.fillMaxSize(), corner = 12.dp)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("歌单", color = SaltTheme.colors.subText, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
        )
        val meta = buildString {
            append("${playlist.tracks.size} 首")
            if (metaSuffix != null) append(" · ").append(metaSuffix)
        }
        Text(
            text = meta,
            color = SaltTheme.colors.subText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = SaltTheme.textStyles.sub.fontSize,
        )
    }
}
