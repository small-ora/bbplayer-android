package com.bbplayer.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.BiliCollection
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.FavoriteFolder
import com.bbplayer.android.core.model.Playlist
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.LargeTitle
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.bbRootContentBottomPadding
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

private enum class LibraryTab(val label: String) {
    PLAYLISTS("歌单"),
    FAVORITES("收藏"),
    RECENT("最近"),
    FOLDERS("收藏夹"),
    COLLECTIONS("合集"),
}

@Composable
fun LibraryScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenImport: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onOpenFavoriteFolder: (Long, String) -> Unit = { _, _ -> },
    onOpenCollection: (Long, String) -> Unit = { _, _ -> },
    onOpenWatchLater: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val playlists by graph.libraryRepository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by graph.libraryRepository.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recent by graph.historyRepository.recent(50).collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = com.bbplayer.android.core.model.PlaybackState())
    val session by graph.authRepository.session().collectAsStateWithLifecycle(initialValue = null as BiliUser?)
    val loggedIn = session != null
    var tab by remember { mutableStateOf(LibraryTab.PLAYLISTS) }
    var showCreate by remember { mutableStateOf(false) }

    // ---- 歌单操作（AC8）：MoreVert → ModalBottomSheet，重命名复用既有 Dialog ----
    var sheetPlaylistId by remember { mutableStateOf<String?>(null) }
    var renamePlaylistId by remember { mutableStateOf<String?>(null) }
    val sheetPlaylist = sheetPlaylistId?.let { id -> playlists.firstOrNull { it.id == id } }
    val renamePlaylist = renamePlaylistId?.let { id -> playlists.firstOrNull { it.id == id } }
    // 目标歌单已被删除（例如另一端刷新）时自动收起，避免空引用。
    if (sheetPlaylistId != null && sheetPlaylist == null) {
        LaunchedEffect(sheetPlaylistId) { sheetPlaylistId = null }
    }

    // ---- 批量管理（AC8）：仅曲库页「歌单」Tab 的多选 + 批量删除 ----
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 批量删除前二次确认（独立审查 P2：全选+删除不应两击即永久丢失）
    var pendingBatchDelete by remember { mutableStateOf<List<String>>(emptyList()) }
    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }
    // 切换到其它 Tab 时退出多选，避免选择态泄漏到收藏/最近等列表。
    LaunchedEffect(tab) {
        if (tab != LibraryTab.PLAYLISTS) exitSelection()
    }

    // 收藏夹（B 站）只在需要且已登录时加载，退出登录后清空。
    var folders by remember { mutableStateOf<List<FavoriteFolder>?>(null) }
    var foldersError by remember { mutableStateOf<String?>(null) }
    var foldersNonce by remember { mutableStateOf(0) }
    LaunchedEffect(tab, loggedIn, foldersNonce) {
        if (tab != LibraryTab.FOLDERS || !loggedIn) return@LaunchedEffect
        folders = null
        foldersError = null
        graph.favoritesRepository.folders()
            .onSuccess { folders = it }
            .onFailure { foldersError = it.toUserMessage() }
    }

    // 我的合集（B 站）同样懒加载。
    var collections by remember { mutableStateOf<List<BiliCollection>?>(null) }
    var collectionsError by remember { mutableStateOf<String?>(null) }
    var collectionsNonce by remember { mutableStateOf(0) }
    LaunchedEffect(tab, loggedIn, collectionsNonce) {
        if (tab != LibraryTab.COLLECTIONS || !loggedIn) return@LaunchedEffect
        collections = null
        collectionsError = null
        graph.collectionRepository.myCollections()
            .onSuccess { collections = it }
            .onFailure { collectionsError = it.toUserMessage() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().clipToBounds(),
        contentPadding = PaddingValues(bottom = bbRootContentBottomPadding()),
    ) {
        item {
            if (selectionMode && tab == LibraryTab.PLAYLISTS) {
                LargeTitle(
                    title = "已选 ${selectedIds.size}",
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectionHeaderAction(
                                text = "全选",
                                onClick = {
                                    selectedIds = if (selectedIds.size == playlists.size) {
                                        emptySet()
                                    } else {
                                        playlists.map { it.id }.toSet()
                                    }
                                },
                            )
                            SelectionHeaderAction(
                                text = "删除",
                                enabled = selectedIds.isNotEmpty(),
                                onClick = {
                                    if (selectedIds.isNotEmpty()) pendingBatchDelete = selectedIds.toList()
                                },
                            )
                            SelectionHeaderAction(text = "取消", onClick = { exitSelection() })
                        }
                    },
                )
            } else {
                LargeTitle(
                    title = "曲库",
                    actions = {
                        IconAction(Icons.Rounded.Download, "已下载", onClick = onOpenDownloads)
                        IconAction(Icons.Rounded.PlaylistAdd, "导入歌单", onClick = onOpenImport)
                        IconAction(Icons.Rounded.Add, "新建歌单", onClick = { showCreate = true })
                    },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = SaltTheme.dimens.padding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryTab.entries.forEach { t ->
                    SaltPill(text = t.label, selected = tab == t, onClick = { tab = t })
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = 10.dp)) {
                RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                    Item(
                        onClick = { if (loggedIn) onOpenWatchLater() else onOpenLogin() },
                        text = "稍后再看",
                        sub = if (loggedIn) "B 站稍后再看列表" else "登录后查看",
                        iconPainter = rememberVectorPainter(Icons.Rounded.WatchLater),
                    )
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
                            selectionMode = selectionMode,
                            selected = playlist.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = selectedIds.toggle(playlist.id)
                                } else {
                                    onOpenPlaylist(playlist.id)
                                }
                            },
                            onToggleSelected = { selectedIds = selectedIds.toggle(playlist.id) },
                            onMore = { sheetPlaylistId = playlist.id },
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
            LibraryTab.FOLDERS -> {
                val list = folders
                when {
                    !loggedIn -> item {
                        LoginRequired(
                            title = "收藏夹",
                            icon = Icons.Rounded.Folder,
                            onOpenLogin = onOpenLogin,
                        )
                    }
                    foldersError != null -> item {
                        ErrorState(message = foldersError!!, onRetry = { foldersNonce++ })
                    }
                    list == null -> item { LoadingState(text = "正在加载收藏夹…") }
                    list.isEmpty() -> item {
                        EmptyState(
                            title = "还没有收藏夹",
                            subtitle = "在 B 站网页端创建收藏夹后会显示在这里",
                            icon = Icons.Rounded.Folder,
                        )
                    }
                    else -> {
                        item { Spacer(Modifier.height(6.dp)) }
                        items(items = list, key = { it.id }) { folder ->
                            FavoriteFolderRow(
                                folder = folder,
                                onClick = { onOpenFavoriteFolder(folder.id, folder.title) },
                            )
                        }
                    }
                }
            }
            LibraryTab.COLLECTIONS -> {
                val list = collections
                when {
                    !loggedIn -> item {
                        LoginRequired(
                            title = "合集",
                            icon = Icons.Rounded.VideoLibrary,
                            onOpenLogin = onOpenLogin,
                        )
                    }
                    collectionsError != null -> item {
                        ErrorState(message = collectionsError!!, onRetry = { collectionsNonce++ })
                    }
                    list == null -> item { LoadingState(text = "正在加载合集…") }
                    list.isEmpty() -> item {
                        EmptyState(
                            title = "还没有合集",
                            subtitle = "B 站「合集 / 系列」会显示在这里",
                            icon = Icons.Rounded.VideoLibrary,
                        )
                    }
                    else -> {
                        item { Spacer(Modifier.height(6.dp)) }
                        items(items = list, key = { it.id }) { collection ->
                            CollectionRow(
                                collection = collection,
                                onClick = { onOpenCollection(collection.id, collection.title) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 歌单操作弹层（立即播放 / 重命名 / 批量管理 / 删除） ----
    val sheet = sheetPlaylist
    if (sheet != null) {
        PlaylistActionsSheet(
            playlistName = sheet.name,
            canPlay = sheet.tracks.isNotEmpty(),
            onDismiss = { sheetPlaylistId = null },
            onPlayNow = {
                // 与旧「播放」胶囊完全一致：记录播放时间 + 从头播放。
                scope.safeLaunch { graph.libraryRepository.markPlaylistPlayed(sheet.id) }
                onPlayTrack(sheet.tracks, 0)
            },
            onRename = { renamePlaylistId = sheet.id },
            onBatchManage = {
                selectionMode = true
                selectedIds = emptySet()
            },
            onDelete = {
                scope.safeLaunch { graph.libraryRepository.deletePlaylist(sheet.id) }
            },
        )
    }

    if (renamePlaylist != null) {
        RenamePlaylistDialog(
            current = renamePlaylist.name,
            onDismiss = { renamePlaylistId = null },
            onConfirm = { name ->
                renamePlaylistId = null
                scope.safeLaunch { graph.libraryRepository.renamePlaylist(renamePlaylist.id, name) }
            },
        )
    }

    if (pendingBatchDelete.isNotEmpty()) {
        BatchDeleteConfirmDialog(
            count = pendingBatchDelete.size,
            onDismiss = { pendingBatchDelete = emptyList() },
            onConfirm = {
                val ids = pendingBatchDelete
                pendingBatchDelete = emptyList()
                if (ids.isNotEmpty()) {
                    // 先退出多选再逐个删除，UI 立即恢复普通态。
                    exitSelection()
                    scope.safeLaunch {
                        ids.forEach { id -> graph.libraryRepository.deletePlaylist(id) }
                    }
                }
            },
        )
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

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

@Composable
private fun SelectionHeaderAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) SaltTheme.colors.highlight else SaltTheme.colors.subText.copy(alpha = 0.4f),
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistActionsSheet(
    playlistName: String,
    canPlay: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onRename: () -> Unit,
    onBatchManage: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 防连点：一次弹层只接受一个操作。
    var acting by remember { mutableStateOf(false) }
    // 独立审查 P2：不要依赖 hide() 的完成回调（弹层中途被移除时协程会被取消，动作静默丢失）。
    // 先关状态再执行动作；BottomSheet 随状态移除，操作一定落地。
    fun dismissThen(action: () -> Unit) {
        if (acting) return
        acting = true
        onDismiss()
        action()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SaltTheme.colors.background,
        contentColor = SaltTheme.colors.text,
        scrimColor = Color.Black.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = playlistName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SheetActionRow(Icons.Rounded.PlayArrow, "立即播放", enabled = canPlay, onClick = { dismissThen(onPlayNow) })
            SheetActionRow(Icons.Rounded.Edit, "重命名", onClick = { dismissThen(onRename) })
            SheetActionRow(Icons.Rounded.Checklist, "批量管理", onClick = { dismissThen(onBatchManage) })
            SheetActionRow(Icons.Rounded.Delete, "删除", onClick = { dismissThen(onDelete) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) SaltTheme.colors.text else SaltTheme.colors.subText.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = text, color = contentColor, fontSize = 15.sp)
    }
}

@Composable
private fun LoginRequired(title: String, icon: ImageVector, onOpenLogin: () -> Unit) {
    EmptyState(
        title = "登录后查看$title",
        subtitle = "登录 Bilibili 账号后即可同步",
        icon = icon,
        action = "登录 Bilibili",
        onAction = onOpenLogin,
    )
}

@Composable
private fun FavoriteFolderRow(folder: FavoriteFolder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = folder.coverUrl, modifier = Modifier.size(54.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folder.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (folder.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    SaltPill(text = "默认")
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${folder.mediaCount} 个视频",
                color = SaltTheme.colors.subText,
                fontSize = SaltTheme.textStyles.sub.fontSize,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SaltTheme.colors.subText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CollectionRow(collection: BiliCollection, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = collection.coverUrl, modifier = Modifier.size(54.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = collection.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (collection.description.isBlank()) {
                    "${collection.count} 个视频"
                } else {
                    collection.description
                },
                color = SaltTheme.colors.subText,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${collection.count}",
            color = SaltTheme.colors.subText,
            fontSize = SaltTheme.textStyles.sub.fontSize,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SaltTheme.colors.subText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected() },
                colors = CheckboxDefaults.colors(
                    checkedColor = SaltTheme.colors.highlight,
                    checkmarkColor = SaltTheme.colors.onHighlight,
                    uncheckedColor = SaltTheme.colors.subText,
                ),
            )
            Spacer(Modifier.width(4.dp))
        }
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
        if (!selectionMode) {
            // 48dp 热区（AC8）：单个 MoreVert 取代旧的 [播放][重命名][删除] 三胶囊。
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "歌单更多操作",
                    tint = SaltTheme.colors.subText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
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

@Composable
private fun BatchDeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SaltTheme.colors.popup)
                .padding(20.dp),
        ) {
            Text("删除 $count 个歌单？", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                "只会删除本机歌单，不会影响 B 站内容；删除后无法撤销。",
                color = SaltTheme.colors.subText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                SaltPill(text = "删除", selected = true, onClick = onConfirm)
            }
        }
    }
}
