package com.bbplayer.android.ui.screens.playlist

import com.bbplayer.android.ui.theme.BbType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.model.Playlist
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.theme.BbTokens
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.safeLaunch

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    val graph = LocalAppGraph.current
    val playlists by graph.libraryRepository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = com.bbplayer.android.core.model.PlaybackState())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item { SmallTopBar(title = playlist?.name ?: "歌单", onBack = onBack) }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp)) {
                Text(
                    text = playlist?.name ?: "歌单",
                    style = BbType.Display,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "${playlist?.tracks?.size ?: 0} 首歌曲",
                        color = SaltTheme.colors.subText,
                        style = BbType.Label,
                    )
                    Spacer(Modifier.size(12.dp))
                    if (!playlist?.tracks.isNullOrEmpty()) {
                        SaltPill(text = "播放全部", selected = true, onClick = {
                            scope.safeLaunch { graph.libraryRepository.markPlaylistPlayed(playlistId) }
                            onPlayTrack(playlist!!.tracks, 0)
                        })
                    }
                }
            }
        }
        val tracks = playlist?.tracks.orEmpty()
        if (tracks.isEmpty()) {
            item { EmptyState(title = "歌单还是空的", subtitle = "在搜索结果或播放页把歌曲加入歌单") }
        } else {
            items(count = tracks.size, key = { idx -> tracks[idx].key }) { idx ->
                val track = tracks[idx]
                TrackRow(
                    track = track,
                    isCurrent = playback.track?.key == track.key,
                    onClick = {
                        scope.safeLaunch { graph.libraryRepository.markPlaylistPlayed(playlistId) }
                        onPlayTrack(tracks, idx)
                    },
                    onMoreClick = {
                        scope.safeLaunch { graph.libraryRepository.removeTrack(playlistId, track.key) }
                    },
                )
            }
        }
    }
}
