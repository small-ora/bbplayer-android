package com.bbplayer.android.ui.screens.library

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.model.Playlist
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.LargeTitle
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.TrackRow
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.bbplayer.android.core.safeLaunch

private enum class LibraryTab(val label: String) { PLAYLISTS("歌单"), FAVORITES("收藏"), RECENT("最近") }

@Composable
fun LibraryScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenImport: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val playlists by graph.libraryRepository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by graph.libraryRepository.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recent by graph.historyRepository.recent(50).collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = com.bbplayer.android.core.model.PlaybackState())
    var tab by remember { mutableStateOf(LibraryTab.PLAYLISTS) }
    var showCreate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            LargeTitle(
                title = "音乐库",
                actions = {
                    IconAction(Icons.Rounded.Download, "已下载", onClick = onOpenDownloads)
                    IconAction(Icons.Rounded.PlaylistAdd, "导入歌单", onClick = onOpenImport)
                    IconAction(Icons.Rounded.Add, "新建歌单", onClick = { showCreate = true })
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SaltTheme.dimens.padding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryTab.entries.forEach { t ->
                    SaltPill(text = t.label, selected = tab == t, onClick = { tab = t })
                }
            }
        }
        when (tab) {
            LibraryTab.PLAYLISTS -> {
                if (playlists.isEmpty()) {
                    item { EmptyState(title = "还没有歌单", subtitle = "点右上角 + 新建一个歌单", icon = Icons.Rounded.LibraryMusic) }
                } else {
                    item { Spacer(Modifier.height(10.dp)) }
                    items(count = playlists.size, key = { idx -> playlists[idx].id }) { idx ->
                        val playlist = playlists[idx]
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id) },
                            onPlay = {
                                if (playlist.tracks.isNotEmpty()) onPlayTrack(playlist.tracks, 0)
                            },
                            onDelete = { scope.safeLaunch { graph.libraryRepository.deletePlaylist(playlist.id) } },
                            onRename = { newName ->
                                scope.safeLaunch { graph.libraryRepository.renamePlaylist(playlist.id, newName) }
                            },
                        )
                    }
                }
            }
            LibraryTab.FAVORITES -> {
                val tracks = favorites.map { it.track }
                if (tracks.isEmpty()) {
                    item { EmptyState(title = "还没有收藏", subtitle = "在播放页点击红心收藏歌曲") }
                } else {
                    item { Spacer(Modifier.height(6.dp)) }
                    items(count = tracks.size, key = { idx -> tracks[idx].key }) { idx ->
                        val track = tracks[idx]
                        TrackRow(
                            track = track,
                            isCurrent = playback.track?.key == track.key,
                            onClick = { onPlayTrack(tracks, idx) },
                        )
                    }
                }
            }
            LibraryTab.RECENT -> {
                val tracks = recent.map { it.track }
                if (tracks.isEmpty()) {
                    item { EmptyState(title = "还没有听歌记录", subtitle = "去首页找首歌听听吧") }
                } else {
                    item { Spacer(Modifier.height(6.dp)) }
                    items(
                        count = recent.size,
                        key = { idx -> recent[idx].track.key + "@" + recent[idx].playedAt },
                    ) { idx ->
                        val track = recent[idx].track
                        TrackRow(
                            track = track,
                            isCurrent = playback.track?.key == track.key,
                            onClick = { onPlayTrack(tracks, idx) },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                showCreate = false
                scope.safeLaunch { graph.libraryRepository.createPlaylist(name) }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp)) {
            CoverImage(
                url = playlist.coverUrls.firstOrNull().orEmpty(),
                modifier = Modifier.fillMaxSize(),
                corner = 10.dp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "${playlist.tracks.size} 首歌曲",
                color = SaltTheme.colors.subText,
                fontSize = SaltTheme.textStyles.sub.fontSize,
            )
        }
        if (playlist.tracks.isNotEmpty()) {
            SaltPill(text = "播放", onClick = onPlay)
        }
        Spacer(Modifier.width(8.dp))
        SaltPill(text = "重命名", onClick = { showRename = true })
        Spacer(Modifier.width(8.dp))
        SaltPill(text = "删除", onClick = onDelete)
    }
    if (showRename) {
        RenamePlaylistDialog(
            current = playlist.name,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                showRename = false
                onRename(name)
            },
        )
    }
}

@Composable
private fun RenamePlaylistDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SaltTheme.colors.popup)
                .padding(20.dp),
        ) {
            Text("重命名歌单", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SaltTheme.colors.subBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = SaltTheme.colors.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(SaltTheme.colors.highlight),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                SaltPill(text = "保存", selected = name.isNotBlank(), onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim())
                })
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SaltTheme.colors.popup)
                .padding(20.dp),
        ) {
            Text("新建歌单", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SaltTheme.colors.subBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (name.isEmpty()) {
                    Text("歌单名称", color = SaltTheme.colors.subText, fontSize = 15.sp)
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = SaltTheme.colors.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(SaltTheme.colors.highlight),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                SaltPill(text = "创建", selected = name.isNotBlank(), onClick = {
                    if (name.isNotBlank()) onCreate(name.trim())
                })
            }
        }
    }
}

