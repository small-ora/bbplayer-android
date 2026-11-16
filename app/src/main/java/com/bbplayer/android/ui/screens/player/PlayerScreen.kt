package com.bbplayer.android.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.bbplayer.android.util.BiliIds
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.FavoriteFolder
import android.widget.Toast
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.DownloadStatus
import com.bbplayer.android.core.model.LyricVisualStyle
import com.bbplayer.android.core.model.PlayerBackgroundStyle
import com.bbplayer.android.core.model.Lyrics
import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.AppGraph
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.lyrics.DesktopLyricsService
import com.bbplayer.android.ui.lyrics.NowPlayingLyrics
import com.bbplayer.android.ui.share.ShareCard
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.SeekLine
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.screens.comments.CommentsScreen
import com.bbplayer.android.ui.theme.rememberAlbumWash
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit = {},
    onOpenUploader: (Long) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val state by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())
    val track = state.track

    if (track == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
                .statusBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Rounded.KeyboardArrowDown, "返回", onClick = onBack)
            }
            EmptyState(title = "还没有正在播放的歌曲", subtitle = "去首页挑一首吧")
        }
        return
    }

    val wash = rememberAlbumWash(track.coverUrl)
    var showQueue by remember { mutableStateOf(false) }
    // 三页横滑：0 = 封面（默认）、1 = 歌词（纯展示）、2 = 评论
    val pagerState = rememberPagerState(pageCount = { 3 })
    val sheet = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by graph.authRepository.session().collectAsStateWithLifecycle(initialValue = null as BiliUser?)
    val loggedIn = session != null
    var favoriteAid by remember(track.key) { mutableStateOf<Long?>(null) }
    var watchLaterContains by remember(track.key) { mutableStateOf(false) }

    val prefs = graph.preferencesRepository
    val quality by prefs.audioQuality.collectAsStateWithLifecycle(initialValue = AudioQuality.Q192)
    val fontScale by prefs.lyricsFontScale.collectAsStateWithLifecycle(initialValue = 1f)
    val prefsOffset by prefs.lyricsOffsetMs.collectAsStateWithLifecycle(initialValue = 0)
    // v2 遗留的「逐曲偏移」仍会叠加生效（播放页不再提供调整按钮；全局偏移在设置里调）。
    var legacyTrackOffset by remember(track.key) { mutableStateOf(0) }
    LaunchedEffect(track.key) {
        legacyTrackOffset = runCatching { graph.lyricsRepository.getTrackOffset(track.key) }.getOrDefault(0)
    }
    val backgroundStyle by prefs.playerBackgroundStyle.collectAsStateWithLifecycle(initialValue = PlayerBackgroundStyle.GRADIENT)
    val lyricStyle by prefs.lyricVisualStyle.collectAsStateWithLifecycle(initialValue = LyricVisualStyle.MODERN)
    val verbatim by prefs.verbatimLyrics.collectAsStateWithLifecycle(initialValue = true)
    val showTranslation by prefs.showTranslation.collectAsStateWithLifecycle(initialValue = true)
    val desktopLyricsOn by prefs.desktopLyricsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val downloads by graph.downloadRepository.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadEntry = downloads.firstOrNull { it.track.key == track.key }

    var lyrics by remember(track.key) { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading by remember(track.key) { mutableStateOf(true) }
    var lyricsError by remember(track.key) { mutableStateOf<String?>(null) }
    var lyricsNonce by remember(track.key) { mutableStateOf(0) }
    LaunchedEffect(track.key, lyricsNonce) {
        lyricsLoading = true
        lyricsError = null
        runCatching {
            graph.lyricsRepository.getCached(track.key)
                ?: graph.lyricsRepository.fetch(track).getOrElse { throw it }
        }.onSuccess {
            lyrics = it
        }.onFailure {
            lyricsError = it.toUserMessage()
        }
        lyricsLoading = false
    }

    var isFavorite by remember(track.key) { mutableStateOf(false) }
    LaunchedEffect(track.key) { isFavorite = graph.libraryRepository.isFavorite(track.key) }

    // Keep the 稍后再看 toggle in sync with the remote list when the account is logged in.
    LaunchedEffect(track.key, loggedIn) {
        watchLaterContains = false
        if (!loggedIn) return@LaunchedEffect
        val aid = BiliIds.aidOf(track.bvid) ?: return@LaunchedEffect
        graph.watchLaterRepository.list().onSuccess { list ->
            watchLaterContains = list.any { BiliIds.aidOf(it.bvid) == aid }
        }
    }

    // 偏移只在「设置 → 歌词 → 歌词偏移」调整；播放页歌词页为纯展示，不再提供逐曲偏移按钮。
    val effectiveLyrics = remember(lyrics, prefsOffset, legacyTrackOffset) {
        lyrics?.copy(offsetMs = (lyrics?.offsetMs ?: 0) + prefsOffset + legacyTrackOffset)
    }

    // Desktop overlay hides while the in-app lyrics page (pager page 1) is visible.
    androidx.compose.runtime.DisposableEffect(pagerState.currentPage) {
        NowPlayingLyrics.inAppLyricsVisible.value = pagerState.currentPage == 1
        onDispose { }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { NowPlayingLyrics.inAppLyricsVisible.value = false }
    }

    // Publish the current line for the desktop lyrics overlay and the media notification.
    LaunchedEffect(effectiveLyrics) { NowPlayingLyrics.setLyrics(track.key, effectiveLyrics) }
    LaunchedEffect(state.positionMs, effectiveLyrics) {
        NowPlayingLyrics.update(state.positionMs)
    }
    val currentLine = NowPlayingLyrics.snapshot.collectAsStateWithLifecycle().value

    val pureWhite = backgroundStyle == PlayerBackgroundStyle.PURE_WHITE
    val onPlayerColor = if (pureWhite) Color(0xFF111111) else Color.White
    val onPlayerSecondary = if (pureWhite) Color(0x99000000) else Color.White.copy(alpha = 0.65f)
    Box(Modifier.fillMaxSize()) {
        // ---- background: gradient / solid / pure white ----
        val base = wash.firstOrNull() ?: SaltTheme.colors.highlight
        when (backgroundStyle) {
            PlayerBackgroundStyle.PURE_WHITE -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
            PlayerBackgroundStyle.SOLID -> Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(base.copy(alpha = 0.92f), base.copy(alpha = 0.75f))
                        )
                    )
            )
            PlayerBackgroundStyle.GRADIENT -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    base.copy(alpha = 0.95f),
                                    base.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.9f),
                                )
                            )
                        )
                )
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(90.dp)
                        .background(Color.Black.copy(alpha = 0.25f)),
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            state.errorMessage?.let { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    SaltPill(text = "重试", onClick = {
                        scope.safeLaunch {
                            graph.playbackController.playQueue(state.queue, state.currentIndex)
                        }
                    })
                }
            }

            // ---- top bar ----
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    Icons.Rounded.KeyboardArrowDown, "收起", onClick = onBack,
                    tint = onPlayerColor, size = 40,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("正在播放", color = onPlayerColor.copy(alpha = 0.85f), fontSize = 12.sp)
                        Text(
                            track.uploader,
                            color = onPlayerColor.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 页码指示：封面 / 歌词 / 评论
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(3) { index ->
                                val active = index == pagerState.currentPage
                                Box(
                                    Modifier
                                        .size(if (active) 6.dp else 5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(onPlayerColor.copy(alpha = if (active) 0.95f else 0.32f))
                                )
                            }
                        }
                    }
                }
                IconAction(
                    Icons.Rounded.MoreHoriz, "更多", onClick = { sheet.value = "more" },
                    tint = onPlayerColor, size = 40,
                )
            }

            // ---- 可横滑区域（仅此区域切换：封面 / 歌词 / 评论）----
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // 三页保持组合：歌词滚动/评论分页/楼中楼展开在左右滑动时不会丢状态。
                beyondViewportPageCount = 2,
            ) { page ->
                when (page) {
                    0 -> CoverPage(track = track, state = state)
                    1 -> LyricsPage(
                        lyrics = effectiveLyrics,
                        positionMs = state.positionMs,
                        fontScale = fontScale,
                        lyricStyle = lyricStyle,
                        verbatim = verbatim,
                        showTranslation = showTranslation,
                        lyricsLoading = lyricsLoading,
                        lyricsError = lyricsError,
                        onRetry = { lyricsNonce++ },
                        onManualSearch = { sheet.value = "searchLyrics" },
                        onSeekTo = { graph.playbackController.seekTo(it.coerceAtLeast(0)) },
                    )
                    else -> CommentsPage(
                        bvid = track.bvid,
                        active = pagerState.currentPage == 2,
                    )
                }
            }

            // ---- 固定区域：歌名 / 进度 / 播放控件 / 底部操作（不随横滑切换）----
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.displayTitle,
                        color = onPlayerColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.uploader,
                            color = onPlayerSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        QualityBadge(quality)
                    }
                }
                IconAction(
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "收藏",
                    onClick = {
                        scope.safeLaunch { isFavorite = graph.libraryRepository.toggleFavorite(track) }
                    },
                    tint = if (isFavorite) Color(0xFFFF6B81) else onPlayerColor.copy(alpha = 0.85f),
                    size = 40,
                )
            }
            Spacer(Modifier.height(14.dp))
            SeekLine(
                positionMs = state.positionMs,
                durationMs = state.durationMs.coerceAtLeast(1),
                onSeek = { graph.playbackController.seekTo(it) },
                activeColor = onPlayerColor,
                trackColor = onPlayerColor.copy(alpha = 0.24f),
                timeColor = onPlayerSecondary,
            )
            Spacer(Modifier.height(10.dp))
            TransportRow(
                state = state,
                onPrev = { },
                onToggle = { },
                onNext = { },
                white = !pureWhite,
            )
            Spacer(Modifier.height(6.dp))
            BottomActions(
                state = state,
                quality = quality,
                onModeClick = { graph.playbackController.setPlayMode(state.playMode.next()) },
                onShowSheet = { sheet.value = it },
                onOpenQueue = { showQueue = true },
            )
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.currentIndex,
            onDismiss = { showQueue = false },
            onJumpTo = { graph.playbackController.jumpTo(it); showQueue = false },
            onRemove = { graph.playbackController.removeFromQueue(it) },
            onClear = { graph.playbackController.clearQueue(); showQueue = false },
        )
    }

    when (sheet.value) {
        "speed" -> OptionDialog(
            title = "播放速度",
            options = listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x"),
            selectedIndex = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).indexOfFirst { it == state.speed }.coerceAtLeast(2),
            onSelect = {
                graph.playbackController.setSpeed(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)[it])
                sheet.value = null
            },
            onDismiss = { sheet.value = null },
            dark = true,
        )
        "timer" -> OptionDialog(
            title = "定时关闭",
            options = listOf("关闭", "15 分钟", "30 分钟", "45 分钟", "60 分钟"),
            selectedIndex = 0,
            onSelect = {
                graph.playbackController.setSleepTimer(if (it == 0) null else listOf(0, 15, 30, 45, 60)[it])
                sheet.value = null
            },
            onDismiss = { sheet.value = null },
            dark = true,
        )
        "more" -> OptionDialog(
            title = "更多",
            options = listOf(
                "添加到歌单",
                "收藏到 B 站收藏夹",
                if (watchLaterContains) "移出稍后再看" else "加入稍后再看",
                "UP 主主页",
                if (downloadEntry?.status == DownloadStatus.COMPLETED) "删除下载" else "下载到本地",
                "查看评论",
                "分享歌词卡片",
                if (desktopLyricsOn) "关闭桌面歌词" else "开启桌面歌词",
                "搜索歌词",
                "粘贴 LRC 歌词",
                "重新加载音频",
            ),
            selectedIndex = -1,
            onSelect = { idx ->
                sheet.value = null
                when (idx) {
                    0 -> sheet.value = "playlist"
                    1 -> {
                        val aid = BiliIds.aidOf(track.bvid)
                        when {
                            !loggedIn -> {
                                Toast.makeText(context, "请先登录 Bilibili 账号", Toast.LENGTH_SHORT).show()
                                onOpenLogin()
                            }
                            aid == null -> Toast.makeText(
                                context,
                                "无法解析该视频的 aid，暂时不能收藏",
                                Toast.LENGTH_SHORT,
                            ).show()
                            else -> {
                                favoriteAid = aid
                                sheet.value = "favoritePicker"
                            }
                        }
                    }
                    2 -> {
                        val aid = BiliIds.aidOf(track.bvid)
                        when {
                            !loggedIn -> {
                                Toast.makeText(context, "请先登录 Bilibili 账号", Toast.LENGTH_SHORT).show()
                                onOpenLogin()
                            }
                            aid == null -> Toast.makeText(
                                context,
                                "无法解析该视频的 aid，暂时不能加入稍后再看",
                                Toast.LENGTH_SHORT,
                            ).show()
                            else -> scope.safeLaunch(onError = {
                                Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show()
                            }) {
                                if (watchLaterContains) {
                                    graph.watchLaterRepository.remove(aid)
                                        .onSuccess {
                                            watchLaterContains = false
                                            Toast.makeText(context, "已移出稍后再看", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    graph.watchLaterRepository.add(aid, track.cid.takeIf { it > 0 })
                                        .onSuccess {
                                            watchLaterContains = true
                                            Toast.makeText(context, "已加入稍后再看", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        }
                    }
                    3 -> {
                        if (track.uploaderMid > 0) {
                            onOpenUploader(track.uploaderMid)
                        } else {
                            Toast.makeText(context, "该视频没有 UP 主信息", Toast.LENGTH_SHORT).show()
                        }
                    }
                    4 -> scope.safeLaunch {
                        val result: Result<Unit> = when (downloadEntry?.status) {
                            DownloadStatus.COMPLETED -> graph.downloadRepository.remove(track.key)
                            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                                graph.downloadRepository.cancel(track.key)
                                Result.success(Unit)
                            }
                            else -> graph.downloadRepository.enqueue(track)
                        }
                        result.onFailure {
                            Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show()
                        }
                    }
                    5 -> scope.launch { pagerState.animateScrollToPage(2) }
                    6 -> scope.safeLaunch {
                        val line = currentLine.text.ifBlank { track.displayTitle }
                        val ok = ShareCard.share(context, track, line, currentLine.translation)
                        Toast.makeText(context, if (ok) "已打开分享" else "生成分享卡片失败", Toast.LENGTH_SHORT).show()
                    }
                    7 -> scope.safeLaunch {
                        val enable = !desktopLyricsOn
                        prefs.setDesktopLyricsEnabled(enable)
                        if (enable) {
                            val hasOverlay = android.provider.Settings.canDrawOverlays(context)
                            if (!hasOverlay) {
                                Toast.makeText(context, "请在设置中授予「显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                )
                                context.startActivity(intent)
                            } else {
                                DesktopLyricsService.start(context)
                            }
                        } else {
                            DesktopLyricsService.stop(context)
                        }
                    }
                    8 -> sheet.value = "searchLyrics"
                    9 -> sheet.value = "pasteLyrics"
                    10 -> scope.safeLaunch { graph.playbackController.playQueue(state.queue, state.currentIndex) }
                }
            },
            onDismiss = { sheet.value = null },
            dark = true,
        )
        "playlist" -> AddToPlaylistDialog(graph = graph, track = track, onDismiss = { sheet.value = null })
        "favoritePicker" -> favoriteAid?.let { aid ->
            FavoritePickerDialog(graph = graph, aid = aid, onDismiss = { sheet.value = null })
        }
        "searchLyrics" -> LyricsSearchDialog(
            graph = graph,
            track = track,
            onDismiss = { sheet.value = null },
            onPicked = { picked ->
                lyrics = picked
                sheet.value = null
            },
        )
        "pasteLyrics" -> PasteLyricsDialog(
            onDismiss = { sheet.value = null },
            onSave = { text ->
                scope.safeLaunch {
                    graph.lyricsRepository.saveManual(track.key, text).getOrNull()?.let { lyrics = it }
                    sheet.value = null
                }
            },
        )
    }
}

@Composable
private fun LyricsPage(
    lyrics: Lyrics?,
    positionMs: Long,
    fontScale: Float,
    lyricStyle: LyricVisualStyle,
    verbatim: Boolean,
    showTranslation: Boolean,
    lyricsLoading: Boolean,
    lyricsError: String?,
    onRetry: () -> Unit,
    onManualSearch: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingState(text = "正在获取歌词…")
            }
            lyricsError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = lyricsError,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    SaltPill(text = "重试", onClick = onRetry)
                    Spacer(Modifier.height(8.dp))
                    SaltPill(text = "手动搜索", onClick = onManualSearch)
                }
            }
            else -> LyricsPanel(
                lyrics = lyrics,
                positionMs = positionMs,
                fontScale = fontScale,
                modifier = Modifier.fillMaxSize(),
                style = lyricStyle,
                verbatim = verbatim,
                showTranslation = showTranslation,
                onSeekTo = onSeekTo,
            )
        }
    }
}

@Composable
private fun CoverPage(track: Track, state: PlaybackState) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val coverScale = animateFloatAsState(
            targetValue = if (state.isPlaying) 1f else 0.94f,
            animationSpec = tween(400),
            label = "coverScale",
        )
        val side = minOf(maxWidth * 0.86f, maxHeight)
        CoverImage(
            url = track.coverUrl,
            modifier = Modifier.size(side).androidxScale(coverScale.value),
            corner = 20.dp,
        )
    }
}

@Composable
private fun CommentsPage(bvid: String, active: Boolean) {
    // 评论页在播放页里以不透明圆角卡片呈现，保证浅/深色主题下都易读，也能看清左右滑动边界。
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SaltTheme.colors.background),
    ) {
        CommentsScreen(bvid = bvid, onBack = {}, embedded = true, active = active)
    }
}

@Composable
private fun QualityBadge(quality: AudioQuality) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = when (quality) {
                AudioQuality.HI_RES -> "HR"
                AudioQuality.DOLBY -> "DB"
                AudioQuality.Q192 -> "HQ"
                AudioQuality.Q128 -> "SQ"
                AudioQuality.Q64 -> "LQ"
            },
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun TransportRow(state: PlaybackState, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, white: Boolean) {
    val graph = LocalAppGraph.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(Icons.Rounded.SkipPrevious, "上一首", onClick = { graph.playbackController.previous() }, tint = if (white) Color.White.copy(alpha = 0.9f) else SaltTheme.colors.text, size = 52)
        Box(
            Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(50))
                .clickable { graph.playbackController.playPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "暂停" else "播放",
                tint = if (white) Color.White else SaltTheme.colors.text,
                modifier = Modifier.size(52.dp),
            )
        }
        IconAction(Icons.Rounded.SkipNext, "下一首", onClick = { graph.playbackController.next() }, tint = if (white) Color.White.copy(alpha = 0.9f) else SaltTheme.colors.text, size = 52)
    }
}

@Composable
private fun BottomActions(
    state: PlaybackState,
    quality: AudioQuality,
    onModeClick: () -> Unit,
    onShowSheet: (String) -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = when (state.playMode) {
                PlayMode.SEQUENCE -> Icons.Rounded.Repeat
                PlayMode.REPEAT_ALL -> Icons.Rounded.Repeat
                PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
                PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
            },
            contentDescription = state.playMode.label,
            onClick = onModeClick,
            tint = if (state.playMode in listOf(PlayMode.REPEAT_ONE, PlayMode.SHUFFLE)) Color.White else Color.White.copy(alpha = 0.65f),
            size = 38,
        )
        IconAction(Icons.Rounded.Timer, "定时关闭", onClick = { onShowSheet("timer") }, tint = Color.White.copy(alpha = 0.65f), size = 38)
        IconAction(Icons.Rounded.Speed, "倍速", onClick = { onShowSheet("speed") }, tint = Color.White.copy(alpha = 0.65f), size = 38)
        IconAction(Icons.Rounded.QueueMusic, "队列", onClick = onOpenQueue, tint = Color.White.copy(alpha = 0.65f), size = 38)
        IconAction(Icons.Rounded.MoreHoriz, "更多", onClick = { onShowSheet("more") }, tint = Color.White.copy(alpha = 0.65f), size = 38)
    }
}

private fun Modifier.androidxScale(scale: Float): Modifier =
    this.then(Modifier.scale(scale))

@Composable
private fun OptionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (dark) Color(0xFF262626) else SaltTheme.colors.popup)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(title, color = if (dark) Color.White else SaltTheme.colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            options.forEachIndexed { index, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option,
                        color = when {
                            dark && index == selectedIndex -> Color.White
                            dark -> Color.White.copy(alpha = 0.75f)
                            index == selectedIndex -> SaltTheme.colors.highlight
                            else -> SaltTheme.colors.text
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (index == selectedIndex) Text("✓", color = if (dark) Color.White else SaltTheme.colors.highlight)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(graph: AppGraph, track: Track, onDismiss: () -> Unit) {
    val playlists by graph.libraryRepository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF262626))
                .padding(20.dp),
        ) {
            Text("添加到歌单", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (playlists.isEmpty()) {
                Text("还没有歌单，去音乐库新建一个吧", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            } else {
                playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.safeLaunch {
                                    val added = graph.libraryRepository.addTrack(playlist.id, track)
                                    message = if (added) "已添加到「${playlist.name}」" else "已在「${playlist.name}」中"
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(playlist.name, color = Color.White, modifier = Modifier.weight(1f))
                        Text("${playlist.tracks.size} 首", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFF7Fb8FF), fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "关闭", onClick = onDismiss)
            }
        }
    }
}


@Composable
private fun FavoritePickerDialog(graph: AppGraph, aid: Long, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var folders by remember(aid) { mutableStateOf<List<FavoriteFolder>?>(null) }
    var original by remember(aid) { mutableStateOf<Set<Long>>(emptySet()) }
    var selected by remember(aid) { mutableStateOf<Set<Long>>(emptySet()) }
    var error by remember(aid) { mutableStateOf<String?>(null) }
    var saving by remember(aid) { mutableStateOf(false) }
    var attempt by remember(aid) { mutableStateOf(0) }

    LaunchedEffect(aid, attempt) {
        folders = null
        error = null
        val folderResult = graph.favoritesRepository.folders()
        if (folderResult.isFailure) {
            error = folderResult.exceptionOrNull()?.toUserMessage() ?: "读取收藏夹失败，请重试"
            return@LaunchedEffect
        }
        val favoriteResult = graph.favoritesRepository.isFavorited(aid)
        if (favoriteResult.isFailure) {
            error = favoriteResult.exceptionOrNull()?.toUserMessage() ?: "读取收藏状态失败，请重试"
            return@LaunchedEffect
        }
        val ids = favoriteResult.getOrDefault(emptyList()).toSet()
        folders = folderResult.getOrDefault(emptyList())
        original = ids
        selected = ids
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF262626))
                .padding(20.dp),
        ) {
            Text("收藏到 B 站收藏夹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            when {
                error != null -> {
                    Text(error!!, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    SaltPill(text = "重试", onClick = { attempt++ })
                }
                folders == null -> Text("正在读取收藏夹…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                folders!!.isEmpty() -> Text(
                    "还没有收藏夹，请先在 B 站网页端创建",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
                else -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        folders!!.forEach { folder ->
                            val checked = folder.id in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selected = if (checked) selected - folder.id else selected + folder.id
                                    }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (checked) Color(0xFF7FB8FF) else Color.White.copy(alpha = 0.12f)
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (checked) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF101010),
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        folder.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${folder.mediaCount} 个视频${if (folder.isDefault) " · 默认收藏夹" else ""}",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SaltPill(text = "取消", onClick = onDismiss)
                        Spacer(Modifier.width(10.dp))
                        SaltPill(
                            text = if (saving) "保存中…" else "保存",
                            selected = !saving,
                            onClick = {
                                if (!saving) {
                                    val add = (selected - original).toList()
                                    val remove = (original - selected).toList()
                                    if (add.isEmpty() && remove.isEmpty()) {
                                        Toast.makeText(context, "收藏夹未修改", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        saving = true
                                        scope.safeLaunch(onError = {
                                            saving = false
                                            error = it.toUserMessage()
                                        }) {
                                            graph.favoritesRepository.updateFavorite(aid, add, remove)
                                                .onSuccess {
                                                    Toast.makeText(context, "已更新收藏夹", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                }
                                                .onFailure { error = it.toUserMessage() }
                                            saving = false
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasteLyricsDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF262626))
                .padding(20.dp),
        ) {
            Text("粘贴 LRC 歌词", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(12.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        "[00:01.00]第一句歌词\n[00:05.00]第二句歌词",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF7FB8FF)),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                SaltPill(text = "保存", selected = text.isNotBlank(), onClick = {
                    if (text.isNotBlank()) onSave(text)
                })
            }
        }
    }
}


@Composable
private fun LyricsSearchDialog(
    graph: AppGraph,
    track: Track,
    onDismiss: () -> Unit,
    onPicked: (Lyrics) -> Unit,
) {
    var candidates by remember { mutableStateOf<List<com.bbplayer.android.core.model.LyricCandidate>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingIndex by remember { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(track.key) {
        graph.lyricsRepository.searchCandidates(track)
            .onSuccess { candidates = it }
            .onFailure { error = it.toUserMessage() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF262626))
                .padding(20.dp),
        ) {
            Text("搜索歌词", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            when {
                error != null -> Text(error!!, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                candidates == null -> Text("正在搜索…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                candidates!!.isEmpty() -> Text("没有找到匹配歌词", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(count = candidates!!.size, key = { idx -> candidates!![idx].source + candidates!![idx].sourceId }) { idx ->
                        val candidate = candidates!![idx]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    loadingIndex = idx
                                    scope.safeLaunch(onError = { error = it.toUserMessage() }) {
                                        graph.lyricsRepository.fetchCandidate(track, candidate)
                                            .onSuccess { onPicked(it) }
                                            .onFailure { error = it.toUserMessage() }
                                        loadingIndex = -1
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${candidate.title} · ${candidate.artist}",
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (loadingIndex == idx) "加载中…" else candidate.source.uppercase(),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "关闭", onClick = onDismiss)
            }
        }
    }
}
