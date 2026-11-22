package com.bbplayer.android.ui.screens.player

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.DownloadStatus
import com.bbplayer.android.core.model.FavoriteFolder
import com.bbplayer.android.core.model.LyricVisualStyle
import com.bbplayer.android.core.model.Lyrics
import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.PlayerBackgroundStyle
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.AppGraph
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.PlayPauseIcon
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SeekLine
import com.bbplayer.android.ui.lyrics.DesktopLyricsService
import com.bbplayer.android.ui.lyrics.NowPlayingLyrics
import com.bbplayer.android.ui.screens.comments.CommentsScreen
import com.bbplayer.android.ui.share.ShareCard
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbRadius
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbType
import com.bbplayer.android.ui.theme.PlayerBackdrop
import com.bbplayer.android.ui.theme.PlayerSheetTokens
import com.bbplayer.android.ui.theme.bbCoverShadow
import com.bbplayer.android.ui.theme.rememberAlbumWash
import com.bbplayer.android.util.BiliIds
import com.moriafly.salt.ui.SaltColors
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import java.util.Locale
import kotlinx.coroutines.launch

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
            Row(Modifier.fillMaxWidth().padding(BbSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
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
    // 播放页次级文字用不透明色：半透明白在最坏背景（纯白封面）下只有 3.1:1。
    val onPlayerSecondary = if (pureWhite) Color(0xFF5F6368) else Color(PlayerBackdrop.ON_PLAYER_SECONDARY_ARGB)
    // v9：封面取色先过亮度守卫，保证任意封面（含纯白/亮黄）下白字对比度 ≥ 4.5:1。
    val rawWash = wash.firstOrNull() ?: SaltTheme.colors.highlight
    val backdropBase = Color(PlayerBackdrop.washForBackground(rawWash.toArgb()))

    // ---- P0-2：状态栏图标跟随播放页背景亮度（深底白图标 / 亮底深图标），离开时还原 ----
    val activity = LocalActivity.current
    val view = LocalView.current
    val playerBackgroundIsBright = pureWhite ||
        PlayerBackdrop.isBright(backdropBase.toArgb(), PlayerBackdrop.SCRIM_TOP)
    DisposableEffect(activity, view, playerBackgroundIsBright) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = playerBackgroundIsBright
        onDispose {
            val disposeWindow = activity?.window
            if (previous != null && disposeWindow != null) {
                WindowCompat.getInsetsController(disposeWindow, view).isAppearanceLightStatusBars =
                    previous
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ---- background: 洗色底 + 模糊封面（40%）+ scrim 渐变 ----
        when (backgroundStyle) {
            PlayerBackgroundStyle.PURE_WHITE -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
            PlayerBackgroundStyle.SOLID -> {
                // 洗色底已经是「白字可用」的暗色；再压一层固定 scrim，和 GRADIENT 观感一致。
                Box(Modifier.fillMaxSize().background(backdropBase))
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = PlayerBackdrop.SCRIM_MID))
                )
            }
            PlayerBackgroundStyle.GRADIENT -> {
                // 第 1 层：封面取色（低饱和低明度，保持不透明，避免透出窗口底色）
                Box(Modifier.fillMaxSize().background(backdropBase))
                // 第 2 层：模糊封面，40% 不透明度保留封面气质，同时不破坏白字可读性
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(90.dp)
                        .graphicsLayer(alpha = PlayerBackdrop.COVER_IMAGE_ALPHA),
                )
                // 第 3 层（必须在图片之上）：固定 scrim 渐变，保证顶部/中部/底部白字都 ≥4.5:1
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = PlayerBackdrop.SCRIM_TOP),
                                0.55f to Color.Black.copy(alpha = PlayerBackdrop.SCRIM_MID),
                                1f to Color.Black.copy(alpha = PlayerBackdrop.SCRIM_BOTTOM),
                            )
                        )
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = BbSpacing.Xxl),
        ) {
            state.errorMessage?.let { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = BbSpacing.Xs)
                        .clip(RoundedCornerShape(BbRadius.Media))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = BbSpacing.Md, vertical = BbSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        color = Color.White.copy(alpha = 0.9f),
                        style = BbType.Caption,
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    Icons.Rounded.KeyboardArrowDown, "收起", onClick = onBack,
                    tint = onPlayerColor,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("正在播放", color = onPlayerSecondary, style = BbType.Caption)
                        // UP 主只在下方副标题出现一次，避免重复。
                        // 页码指示：封面 / 歌词 / 评论
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(BbSpacing.Xs)) {
                            repeat(3) { index ->
                                val active = index == pagerState.currentPage
                                Box(
                                    Modifier
                                        .size(if (active) 6.dp else 5.dp)
                                        .clip(BbRadius.FullShape)
                                        .background(onPlayerColor.copy(alpha = if (active) 0.95f else 0.32f))
                                )
                            }
                        }
                    }
                }
                IconAction(
                    Icons.Rounded.MoreHoriz, "更多", onClick = { sheet.value = "more" },
                    tint = onPlayerColor,
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
                    0 -> CoverPage(track = track, state = state, wash = wash)
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
                        contentColor = onPlayerColor,
                        secondaryContentColor = onPlayerSecondary,
                    )
                    else -> CommentsPage(
                        bvid = track.bvid,
                        active = pagerState.currentPage == 2,
                        pureWhite = pureWhite,
                    )
                }
            }

            // ---- 固定区域：歌名 / 进度 / 播放控件 / 底部操作（不随横滑切换）----
            // 底部安全区：只在这一层加 navigationBarsPadding()（根 Column 已移除，避免 inset 双计），
            // 另留 12dp 底部留白，确保功能按钮行不被系统手势条遮挡；播放器背景仍延伸到系统栏下方。
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = BbSpacing.Xs),
            ) {
                // 标题 + 副标题合并在一行块内，收藏红心常驻右侧并垂直居中；
                // 这样底部固定区更紧凑，封面能接近「屏宽 84%」（AC10）。
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.displayTitle,
                            color = onPlayerColor,
                            style = BbType.Title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            // 长标题单行跑马灯，不折行。
                            modifier = Modifier.fillMaxWidth().basicMarquee(),
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.uploader,
                                color = onPlayerSecondary,
                                style = BbType.Label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            QualityBadge(quality = quality, pureWhite = pureWhite)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconAction(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "收藏",
                        onClick = {
                            scope.safeLaunch { isFavorite = graph.libraryRepository.toggleFavorite(track) }
                        },
                        tint = if (isFavorite) Color(0xFFFF6B81) else onPlayerColor.copy(alpha = 0.85f),
                        // 热区固定 48dp，与标题块垂直居中。
                    )
                }
                Spacer(Modifier.height(6.dp))
                SeekLine(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs.coerceAtLeast(1),
                    onSeek = { graph.playbackController.seekTo(it) },
                    activeColor = onPlayerColor,
                    trackColor = onPlayerColor.copy(alpha = 0.24f),
                    timeColor = onPlayerSecondary,
                    touchHeight = 44.dp,
                )
                Spacer(Modifier.height(4.dp))
                TransportRow(
                    state = state,
                    onPrev = { },
                    onToggle = { },
                    onNext = { },
                    pureWhite = pureWhite,
                )
                Spacer(Modifier.height(2.dp))
                BottomActions(
                    state = state,
                    quality = quality,
                    onModeClick = { graph.playbackController.setPlayMode(state.playMode.next()) },
                    onShowSheet = { sheet.value = it },
                    onOpenQueue = { showQueue = true },
                )
            }
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
            ink = pureWhite,
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
            ink = pureWhite,
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
            ink = pureWhite,
        )
        "playlist" -> AddToPlaylistDialog(graph = graph, track = track, ink = pureWhite, onDismiss = { sheet.value = null })
        "favoritePicker" -> favoriteAid?.let { aid ->
            FavoritePickerDialog(graph = graph, aid = aid, ink = pureWhite, onDismiss = { sheet.value = null })
        }
        "searchLyrics" -> LyricsSearchDialog(
            graph = graph,
            track = track,
            ink = pureWhite,
            onDismiss = { sheet.value = null },
            onPicked = { picked ->
                lyrics = picked
                sheet.value = null
            },
        )
        "pasteLyrics" -> PasteLyricsDialog(
            ink = pureWhite,
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
    contentColor: Color,
    secondaryContentColor: Color = contentColor,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在获取歌词…", color = secondaryContentColor, style = BbType.Body)
            }
            lyricsError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = lyricsError,
                        color = secondaryContentColor,
                        style = BbType.Body,
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
                contentColor = contentColor,
                secondaryContentColor = secondaryContentColor,
            )
        }
    }
}


/** 播放页评论：透明背景 + 白色文字，与播放器封面渐变融合（不再是一块纯白卡片）。 */
private val PlayerCommentColors = SaltColors(
    highlight = Color(0xE6FFFFFF),
    text = Color(0xEBFFFFFF),
    subText = Color(0xCCFFFFFF),
    background = Color.Transparent,
    subBackground = Color(0x14FFFFFF),
    popup = Color(0x14FFFFFF),
    stroke = Color(0x38FFFFFF),
    onHighlight = Color(0xFF111111),
)
@Composable
private fun CoverPage(track: Track, state: PlaybackState, wash: List<Color>) {
    // AC10：播放态封面宽 = 屏宽 85%，暂停态 0.97 缩放后仍 ≥82%，二者都落在 82%–85%；
    // 再与可用高度取 min，小窗/横屏也不溢出；圆角 16dp，无白边/描边。
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val coverScale = animateFloatAsState(
            targetValue = if (state.isPlaying) 1f else 0.97f,
            animationSpec = tween(400),
            label = "coverScale",
        )
        val side = minOf(screenWidth * 0.85f, maxHeight)
        // wash 由 PlayerScreen 顶部统一取色，避免同一 coverUrl 二次请求（review P2）。
        val halo = wash.firstOrNull() ?: SaltTheme.colors.highlight
        Box(
            Modifier.size(side * 1.12f).androidxScale(coverScale.value),
            contentAlignment = Alignment.Center,
        ) {
            // P1-4：同图光晕（用封面取色的低透明度径向渐变，不引入 Blur，避免性能回归）。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(halo.copy(alpha = 0.35f), Color.Transparent),
                        )
                    ),
            )
            CoverImage(
                url = track.coverUrl,
                modifier = Modifier
                    .size(side)
                    .bbCoverShadow(BbRadius.Card),
                corner = BbRadius.Card,
                // 缩放只保留在父容器一层（避免 0.97² 跌出 82%–85% 的冻结口径）。
                hairline = false,
            )
        }
    }
}

@Composable
private fun CommentsPage(bvid: String, active: Boolean, pureWhite: Boolean) {
    // 与播放器背景融合：透明底 + 白色文字（深色渐变时加轻微压暗，保证可读）；
    // 纯白墨水屏播放页则用正常深色文字。
    Box(Modifier.fillMaxSize().padding(top = BbSpacing.Xs)) {
        if (!pureWhite) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.60f)),
                        )
                    )
            )
            SaltTheme(
                configs = SaltConfigs.default(isDarkTheme = false),
                dynamicColors = SaltDynamicColors(PlayerCommentColors, PlayerCommentColors),
            ) {
                CommentsScreen(bvid = bvid, onBack = {}, embedded = true, active = active)
            }
        } else {
            CommentsScreen(bvid = bvid, onBack = {}, embedded = true, active = active)
        }
    }
}

@Composable
private fun QualityBadge(quality: AudioQuality, pureWhite: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(BbRadius.Small))
            .background(if (pureWhite) Color.Black.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = BbSpacing.Xs, vertical = 1.dp),
    ) {
        Text(
            text = when (quality) {
                AudioQuality.HI_RES -> "HR"
                AudioQuality.DOLBY -> "DB"
                AudioQuality.Q192 -> "HQ"
                AudioQuality.Q128 -> "SQ"
                AudioQuality.Q64 -> "LQ"
            },
            color = if (pureWhite) Color.Black.copy(alpha = 0.7f) else Color(PlayerBackdrop.ON_PLAYER_PRIMARY_ARGB),
            style = BbType.Micro,
        )
    }
}

@Composable
private fun TransportRow(
    state: PlaybackState,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    pureWhite: Boolean,
) {
    val graph = LocalAppGraph.current
    val controlTint = if (pureWhite) SaltTheme.colors.text else Color.White
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 上一首 / 下一首保持无底板，视觉层级低于主播放键。
        IconAction(
            Icons.Rounded.SkipPrevious, "上一首", onClick = { graph.playbackController.previous() },
            tint = controlTint.copy(alpha = 0.9f),
        )
        // 主播放键：72dp + 半透明圆形浅色底板（纯白墨水屏用黑色低透明度）。
        Box(
            Modifier
                .size(72.dp)
                .clip(BbRadius.FullShape)
                .background(
                    if (pureWhite) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f)
                )
                .clickable { graph.playbackController.playPause() },
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseIcon(
                isPlaying = state.isPlaying,
                tint = controlTint,
                size = 56.dp,
                modifier = Modifier.semantics {
                    contentDescription = if (state.isPlaying) "暂停" else "播放"
                },
            )
        }
        IconAction(
            Icons.Rounded.SkipNext, "下一首", onClick = { graph.playbackController.next() },
            tint = controlTint.copy(alpha = 0.9f),
        )
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
        Modifier.fillMaxWidth().padding(horizontal = BbSpacing.Xs),
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
        )
        IconAction(Icons.Rounded.Timer, "定时关闭", onClick = { onShowSheet("timer") }, tint = Color.White.copy(alpha = 0.65f))
        IconAction(Icons.Rounded.Speed, "倍速", onClick = { onShowSheet("speed") }, tint = Color.White.copy(alpha = 0.65f))
        IconAction(Icons.Rounded.QueueMusic, "队列", onClick = onOpenQueue, tint = Color.White.copy(alpha = 0.65f))
        IconAction(Icons.Rounded.MoreHoriz, "更多", onClick = { onShowSheet("more") }, tint = Color.White.copy(alpha = 0.65f))
    }
}

private fun Modifier.androidxScale(scale: Float): Modifier =
    this.then(Modifier.scale(scale))

/** 播放页弹层的调色板：沉浸页深灰玻璃 / 纯白墨水屏白面（SPEC AC7）。 */
private class PlayerSheetPalette(
    val container: Color,
    val on: Color,
    val onSecondary: Color,
    val field: Color,
    val accent: Color,
    val onAccent: Color,
)

private fun playerSheetPalette(ink: Boolean): PlayerSheetPalette = if (ink) PlayerSheetPalette(
    container = Color(PlayerSheetTokens.InkContainer),
    on = Color(PlayerSheetTokens.InkOn),
    onSecondary = Color(PlayerSheetTokens.InkOnSecondary),
    field = Color(PlayerSheetTokens.InkField),
    accent = Color(PlayerSheetTokens.InkAccent),
    onAccent = Color(PlayerSheetTokens.InkOnAccent),
) else PlayerSheetPalette(
    container = Color(PlayerSheetTokens.DarkContainer),
    on = Color(PlayerSheetTokens.DarkOn),
    onSecondary = Color(PlayerSheetTokens.DarkOnSecondary),
    field = Color(PlayerSheetTokens.DarkField),
    accent = Color(PlayerSheetTokens.DarkAccent),
    onAccent = Color(PlayerSheetTokens.DarkOnAccent),
)

/** 弹层里的文字按钮（胶囊视觉 + 48dp 热区）。 */
@Composable
private fun SheetTextButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(BbRadius.FullShape)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = BbSpacing.Xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) color else color.copy(alpha = 0.45f),
            style = BbType.Body,
        )
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    ink: Boolean = false,
) {
    val sheet = playerSheetPalette(ink)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(BbRadius.Sheet))
                .background(sheet.container)
                .verticalScroll(rememberScrollState())
                .padding(BbSpacing.Xl),
        ) {
            Text(title, color = sheet.on, style = BbType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            options.forEachIndexed { index, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BbRadius.Media))
                        .clickable { onSelect(index) }
                        .padding(horizontal = BbSpacing.Md, vertical = BbSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option,
                        color = if (index == selectedIndex) sheet.accent else sheet.onSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (index == selectedIndex) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = sheet.accent,
                            modifier = Modifier.size(BbIconSize.Sm),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SheetTextButton(text = "取消", color = sheet.on, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(graph: AppGraph, track: Track, ink: Boolean, onDismiss: () -> Unit) {
    val sheet = playerSheetPalette(ink)
    val playlists by graph.libraryRepository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BbRadius.Sheet))
                .background(sheet.container)
                .padding(BbSpacing.Xl),
        ) {
            Text("添加到歌单", color = sheet.on, style = BbType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (playlists.isEmpty()) {
                Text("还没有歌单，去音乐库新建一个吧", color = sheet.onSecondary, style = BbType.Label)
            } else {
                playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(BbRadius.Media))
                            .clickable {
                                scope.safeLaunch {
                                    val added = graph.libraryRepository.addTrack(playlist.id, track)
                                    message = if (added) "已添加到「${playlist.name}」" else "已在「${playlist.name}」中"
                                }
                            }
                            .padding(vertical = BbSpacing.Md, horizontal = BbSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(playlist.name, color = sheet.on, modifier = Modifier.weight(1f))
                        Text("${playlist.tracks.size} 首", color = sheet.onSecondary, style = BbType.Caption)
                    }
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = sheet.accent, style = BbType.Caption)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SheetTextButton(text = "关闭", color = sheet.on, onClick = onDismiss)
            }
        }
    }
}


@Composable
private fun FavoritePickerDialog(graph: AppGraph, aid: Long, ink: Boolean, onDismiss: () -> Unit) {
    val sheet = playerSheetPalette(ink)
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
                .clip(RoundedCornerShape(BbRadius.Sheet))
                .background(sheet.container)
                .padding(BbSpacing.Xl),
        ) {
            Text("收藏到 B 站收藏夹", color = sheet.on, style = BbType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            when {
                error != null -> {
                    Text(error!!, color = sheet.onSecondary, style = BbType.Label)
                    Spacer(Modifier.height(10.dp))
                    SheetTextButton(text = "重试", color = sheet.accent, onClick = { attempt++ })
                }
                folders == null -> Text("正在读取收藏夹…", color = sheet.onSecondary, style = BbType.Label)
                folders!!.isEmpty() -> Text(
                    "还没有收藏夹，请先在 B 站网页端创建",
                    color = sheet.onSecondary,
                    style = BbType.Label,
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
                                    .clip(RoundedCornerShape(BbRadius.Media))
                                    .clickable {
                                        selected = if (checked) selected - folder.id else selected + folder.id
                                    }
                                    .padding(horizontal = BbSpacing.Sm, vertical = BbSpacing.Md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(BbRadius.Small))
                                        .background(
                                            if (checked) sheet.accent else sheet.field
                                        )
                                        .then(
                                            if (!checked) {
                                                Modifier.border(
                                                    width = 1.dp,
                                                    color = sheet.on.copy(alpha = if (ink) 0.55f else 0.45f),
                                                    shape = RoundedCornerShape(BbRadius.Small),
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (checked) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = sheet.onAccent,
                                            modifier = Modifier.size(BbIconSize.Sm),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        folder.title,
                                        color = sheet.on,
                                        style = BbType.Body,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${folder.mediaCount} 个视频${if (folder.isDefault) " · 默认收藏夹" else ""}",
                                        color = sheet.onSecondary,
                                        style = BbType.Micro,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SheetTextButton(text = "取消", color = sheet.on, onClick = onDismiss)
                        Spacer(Modifier.width(10.dp))
                        SheetTextButton(
                            text = if (saving) "保存中…" else "保存",
                            color = sheet.accent,
                            enabled = !saving,
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
private fun PasteLyricsDialog(ink: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val sheet = playerSheetPalette(ink)
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BbRadius.Sheet))
                .background(sheet.container)
                .padding(BbSpacing.Xl),
        ) {
            Text("粘贴 LRC 歌词", color = sheet.on, style = BbType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(BbRadius.Media))
                    .background(sheet.field)
                    .padding(BbSpacing.Md),
            ) {
                if (text.isEmpty()) {
                    Text(
                        "[00:01.00]第一句歌词\n[00:05.00]第二句歌词",
                        color = sheet.onSecondary,
                        style = BbType.Caption,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = sheet.on, fontSize = BbType.Caption.fontSize),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(sheet.accent),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SheetTextButton(text = "取消", color = sheet.on, onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                SheetTextButton(text = "保存", color = sheet.accent, enabled = text.isNotBlank(), onClick = {
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
    ink: Boolean,
    onDismiss: () -> Unit,
    onPicked: (Lyrics) -> Unit,
) {
    val sheet = playerSheetPalette(ink)
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
                .clip(RoundedCornerShape(BbRadius.Sheet))
                .background(sheet.container)
                .padding(BbSpacing.Xl),
        ) {
            Text("搜索歌词", color = sheet.on, style = BbType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            when {
                error != null -> Text(error!!, color = sheet.onSecondary, style = BbType.Label)
                candidates == null -> Text("正在搜索…", color = sheet.onSecondary, style = BbType.Label)
                candidates!!.isEmpty() -> Text("没有找到匹配歌词", color = sheet.onSecondary, style = BbType.Label)
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(count = candidates!!.size, key = { idx -> candidates!![idx].source + candidates!![idx].sourceId }) { idx ->
                        val candidate = candidates!![idx]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(BbRadius.Media))
                                .clickable {
                                    loadingIndex = idx
                                    scope.safeLaunch(onError = { error = it.toUserMessage() }) {
                                        graph.lyricsRepository.fetchCandidate(track, candidate)
                                            .onSuccess { onPicked(it) }
                                            .onFailure { error = it.toUserMessage() }
                                        loadingIndex = -1
                                    }
                                }
                                .padding(vertical = BbSpacing.Md, horizontal = BbSpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${candidate.title} · ${candidate.artist}",
                                color = sheet.on,
                                style = BbType.Label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (loadingIndex == idx) "加载中…" else candidate.source.uppercase(),
                                color = sheet.onSecondary,
                                style = BbType.Micro,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SheetTextButton(text = "关闭", color = sheet.on, onClick = onDismiss)
            }
        }
    }
}
