package com.bbplayer.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.Playlist
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SectionHeader
import com.bbplayer.android.ui.components.SkeletonTrackList
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbRadius
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.bbplayer.android.ui.theme.bbRootContentBottomPadding
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
        // 保留整体状态栏避让：滚动时列表内容不会绘制到透明状态栏下（与曲库/我的行为一致）。
        modifier = Modifier.fillMaxSize().statusBarsPadding().clipToBounds(),
        contentPadding = PaddingValues(bottom = bbRootContentBottomPadding()),
    ) {
        // ---- 顶部：搜索入口 pill（替代「发现音乐」大标题）----
        item {
            HomeSearchHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = BbSpacing.Lg, end = BbSpacing.Lg, top = BbSpacing.Sm, bottom = BbSpacing.Xs),
                onOpenSearch = onOpenSearch,
                onRefresh = { refreshKey++ },
            )
        }

        // ---- 「继续收听」hero：把最近一次播放放大成编辑位（v9.2 P2-3）----
        if (recentTracks.isNotEmpty()) {
            item {
                ContinueListeningHero(
                    track = recentTracks.first().track,
                    onClick = { play(recentTracks.map { it.track }, 0) },
                    modifier = Modifier.padding(
                        start = BbSpacing.Lg,
                        end = BbSpacing.Lg,
                        top = BbSpacing.Xl,
                        bottom = BbSpacing.Xs,
                    ),
                )
            }
        }

        // ---- 最近播放（单曲）----
        if (recentTracks.isNotEmpty()) {
            item { SectionHeader(title = "最近播放", action = "全部", onAction = onOpenHistory) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(BbSpacing.Md),
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
                SectionHeader(title = "最近播放的歌单", action = "曲库", onAction = onOpenLibrary)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(BbSpacing.Md),
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
                SectionHeader(title = "我的歌单", action = "曲库", onAction = onOpenLibrary)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(BbSpacing.Md),
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
            item { SectionHeader(title = "我的收藏", action = "曲库", onAction = onOpenLibrary) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                    horizontalArrangement = Arrangement.spacedBy(BbSpacing.Md),
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
            loading -> item { SkeletonTrackList(rows = 5) }
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

/**
 * 发现页顶部搜索入口。
 *
 * v9.1 修复（人工验收反馈「搜索框描边和白色底不在同一尺寸、像分开了」）：
 * 不再用 M3 `SearchBar` 当静态入口——`SearchBar` 作为 top bar 内部固定 `.padding(top = 8.dp)`
 * （`SearchBarAsTopBarPadding`），外层 `border` 会画在这个 8dp 之外，而白色 Surface 在里面，
 * 于是描边与白底错位。改为自绘 pill：底色 / 描边 / 圆角都挂在同一个 Row 上，线面天然贴合，
 * 同时去掉不用的 M3 展开态与 `ExperimentalMaterial3Api`。
 */
@Composable
private fun HomeSearchHeader(
    modifier: Modifier = Modifier,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    val searchShape = BbRadius.FullShape
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(searchShape)
            .background(SaltTheme.colors.subBackground)
            .border(0.5.dp, SaltTheme.colors.stroke, searchShape)
            .clickable(onClick = onOpenSearch)
            .padding(start = BbSpacing.Lg, end = BbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = SaltTheme.colors.subText,
            modifier = Modifier.size(BbIconSize.Md),
        )
        Spacer(Modifier.width(BbSpacing.Md))
        Text(
            text = "搜索音乐 / BV号 / 链接",
            color = SaltTheme.colors.subText,
            style = BbType.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconAction(Icons.Rounded.Refresh, "刷新", onClick = onRefresh)
    }
}

/**
 * 「继续收听」hero（v9.2 P2-3）：16:9 大卡 + 底部渐变 + 22sp 标题 + 48dp 播放键。
 * 只消费已有历史数据，不新增任何请求。
 */
@Composable
private fun ContinueListeningHero(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(BbRadius.Card))
            .clickable(onClick = onClick),
    ) {
        CoverImage(
            url = track.coverUrl,
            modifier = Modifier.fillMaxSize(),
            corner = 0.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = BbSpacing.Lg, end = 72.dp, bottom = BbSpacing.Lg),
        ) {
            Text("继续收听", color = Color.White.copy(alpha = 0.85f), style = BbType.Caption)
            Spacer(Modifier.height(BbSpacing.Xs))
            Text(
                text = track.displayTitle,
                color = Color.White,
                style = BbType.Title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.uploader,
                color = Color.White.copy(alpha = 0.75f),
                style = BbType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(BbSpacing.Lg)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "继续收听",
                tint = Color.Black,
                modifier = Modifier.size(BbIconSize.Lg),
            )
        }
    }
}

/** 16:9 视频卡：封面在上，标题最多 2 行，UP 主 1 行（发现页最近播放 / 我的收藏）。 */
@Composable
private fun RecentCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick),
    ) {
        CoverImage(
            url = track.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            corner = BbTokens.CoverCorner,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.displayTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = BbType.Body,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = track.uploader,
            color = SaltTheme.colors.subText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = BbType.Caption,
        )
    }
}

/** 歌单卡：保持方形 2×2 拼图封面，不做 16:9 改造。 */
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
                .clip(RoundedCornerShape(BbRadius.Media))
                .background(SaltTheme.colors.subBackground)
                .border(0.5.dp, SaltTheme.colors.stroke, RoundedCornerShape(BbRadius.Media)),
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
                    Text("歌单", color = SaltTheme.colors.subText, style = BbType.Label)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = BbType.Caption,
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
            style = BbType.CaptionTnum,
        )
    }
}
