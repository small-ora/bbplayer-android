package com.bbplayer.android.ui.screens.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.ListeningRank
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SkeletonTrackList
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

private enum class LeaderboardTab(val label: String) {
    BILI("B 站热门"),
    MINE("我的排行"),
}

/**
 * 排行榜（SPEC D3）：「B 站音乐区热门」与「我的收听排行（按播放次数/时长）」两个 tab，
 * 点击任一条目从该列表对应位置开始播放。
 */
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    val graph = LocalAppGraph.current
    var tab by remember { mutableStateOf(LeaderboardTab.BILI) }
    var trending by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val ranks by graph.historyRepository.ranking(50)
        .collectAsStateWithLifecycle(initialValue = emptyList<ListeningRank>())
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())

    LaunchedEffect(reloadKey) {
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

    val rankTracks = ranks.map { it.track }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item {
            SmallTopBar(
                title = "排行榜",
                onBack = onBack,
                actions = {
                    IconAction(Icons.Rounded.Refresh, "刷新", onClick = { reloadKey++ })
                },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(BbSpacing.Sm),
            ) {
                LeaderboardTab.entries.forEach { item ->
                    SaltPill(
                        text = item.label,
                        selected = tab == item,
                        onClick = { tab = item },
                    )
                }
            }
        }

        when (tab) {
            LeaderboardTab.BILI -> {
                when {
                    loading -> item { SkeletonTrackList(rows = 6) }
                    error != null -> item {
                        ErrorState(message = error!!, onRetry = { reloadKey++ })
                    }
                    trending.isEmpty() -> item {
                        EmptyState(
                            title = "暂时没有热门榜单",
                            subtitle = "稍后再试，或到搜索页找找喜欢的歌",
                            icon = Icons.Rounded.EmojiEvents,
                        )
                    }
                    else -> itemsIndexed(trending, key = { _, track -> track.key }) { index, track ->
                        RankRow(
                            rank = index + 1,
                            track = track,
                            isCurrent = playback.track?.key == track.key,
                            subtitle = track.uploader.ifBlank { "未知 UP 主" },
                            trailing = if (track.playCount > 0) "${Format.count(track.playCount)} 播放" else null,
                            onClick = { onPlayTrack(trending, index) },
                        )
                    }
                }
            }

            LeaderboardTab.MINE -> {
                if (ranks.isEmpty()) {
                    item {
                        EmptyState(
                            title = "还没有收听排行",
                            subtitle = "多听几首歌后，这里会按播放次数排序",
                            icon = Icons.Rounded.EmojiEvents,
                        )
                    }
                } else {
                    itemsIndexed(ranks, key = { _, rank -> rank.track.key }) { index, rank ->
                        RankRow(
                            rank = index + 1,
                            track = rank.track,
                            isCurrent = playback.track?.key == rank.track.key,
                            subtitle = "${rank.playCount} 次 · ${Format.totalListening(rank.listenedMs)}",
                            trailing = null,
                            onClick = { onPlayTrack(rankTracks, index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(
    rank: Int,
    track: Track,
    isCurrent: Boolean,
    subtitle: String,
    trailing: String?,
    onClick: () -> Unit,
) {
    val textColor = if (isCurrent) SaltTheme.colors.highlight else SaltTheme.colors.text
    val subColor = if (isCurrent) SaltTheme.colors.highlight else SaltTheme.colors.subText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            color = if (rank <= 3) SaltTheme.colors.highlight else SaltTheme.colors.subText,
            fontSize = if (rank <= 3) 17.sp else 14.sp,
            fontWeight = if (rank <= 3) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
        )
        Spacer(Modifier.width(6.dp))
        CoverImage(url = track.coverUrl, modifier = Modifier.size(48.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.displayTitle,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    color = subColor,
                    style = BbType.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.durationMs > 0) {
                    Text(
                        text = " · ${Format.duration(track.durationMs)}",
                        color = subColor,
                        style = BbType.CaptionTnum,
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                color = subColor,
                style = BbType.Micro,
            )
        }
    }
}
