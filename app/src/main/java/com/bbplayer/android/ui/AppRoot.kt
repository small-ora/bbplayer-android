package com.bbplayer.android.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.BbBottomNavBar
import com.bbplayer.android.ui.components.MainTab
import com.bbplayer.android.ui.components.MiniPlayer
import com.bbplayer.android.ui.lyrics.NowPlayingLyrics
import com.bbplayer.android.ui.screens.discover.LeaderboardScreen
import com.bbplayer.android.ui.screens.downloads.DownloadsScreen
import com.bbplayer.android.ui.screens.history.HistoryScreen
import com.bbplayer.android.ui.screens.home.HomeScreen
import com.bbplayer.android.ui.screens.playlistimport.ImportPlaylistScreen
import com.bbplayer.android.ui.screens.library.CollectionDetailScreen
import com.bbplayer.android.ui.screens.library.FavoriteFolderScreen
import com.bbplayer.android.ui.screens.library.LibraryScreen
import com.bbplayer.android.ui.screens.library.WatchLaterScreen
import com.bbplayer.android.ui.screens.login.LoginScreen
import com.bbplayer.android.ui.screens.player.PlayerScreen
import com.bbplayer.android.ui.screens.playlist.PlaylistDetailScreen
import com.bbplayer.android.ui.screens.profile.ProfileScreen
import com.bbplayer.android.ui.screens.search.SearchScreen
import com.bbplayer.android.ui.screens.settings.SettingsScreen
import com.bbplayer.android.ui.screens.uploader.UploaderScreen
import com.bbplayer.android.ui.theme.BbTokens
import com.moriafly.salt.ui.SaltTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{id}"
    const val DOWNLOADS = "downloads"
    const val IMPORT_PLAYLIST = "import"
    const val LEADERBOARD = "leaderboard"
    const val HISTORY = "history"
    const val LOGIN = "login"
    const val WATCH_LATER = "watchlater"
    const val FAVORITE_FOLDER = "favoriteFolder/{id}?title={title}"
    const val COLLECTION_DETAIL = "collection/{id}?title={title}"
    const val UPLOADER = "uploader/{mid}"
    fun playlist(id: String) = "playlist/$id"
    fun favoriteFolder(id: Long, title: String) = "favoriteFolder/$id?title=${Uri.encode(title)}"
    fun collection(id: Long, title: String) = "collection/$id?title=${Uri.encode(title)}"
    fun uploader(mid: Long) = "uploader/$mid"
}

/** 底部导航 3 个核心 Tab（需求 §1）：发现（原首页）/ 曲库（原音乐库）/ 我的（原个人中心）。 */
private val mainTabs = listOf(
    MainTab("发现", Routes.HOME, Icons.Rounded.Home),
    MainTab("曲库", Routes.LIBRARY, Icons.Rounded.LibraryMusic),
    MainTab("我的", Routes.PROFILE, Icons.Rounded.Person),
)
private val rootRoutes = listOf(Routes.HOME, Routes.LIBRARY, Routes.PROFILE)

@Composable
fun AppRoot() {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val statusBarLyric by graph.preferencesRepository.statusBarLyricLine.collectAsStateWithLifecycle(initialValue = true)

    // Keep lyrics + current line available app-wide (desktop lyrics / notification lyric line),
    // even when the player screen is not open.
    LaunchedEffect(playback.track?.key) {
        val track = playback.track
        if (track == null) {
            NowPlayingLyrics.clear()
        } else {
            val lyrics = runCatching { graph.lyricsRepository.getCached(track.key) }.getOrNull()
                ?: runCatching { graph.lyricsRepository.fetch(track).getOrNull() }.getOrNull()
            NowPlayingLyrics.setLyrics(track.key, lyrics)
        }
    }
    LaunchedEffect(playback.positionMs) {
        NowPlayingLyrics.update(playback.positionMs)
    }

    // Feed the current lyric line to the media notification (status-bar lyrics).
    val lyricEnabled by rememberUpdatedState(statusBarLyric)
    DisposableEffect(graph, lyricEnabled) {
        graph.playbackController.setLyricLineProvider {
            if (lyricEnabled) NowPlayingLyrics.snapshot.value.text.takeIf { it.isNotBlank() } else null
        }
        onDispose { graph.playbackController.setLyricLineProvider(null) }
    }

    // Restore the previous queue/position once per process launch (C4 断点续播).
    LaunchedEffect(Unit) {
        val prefs = graph.preferencesRepository
        // v6 migration: v5 的「音质下拉」与「高音质开关」互相独立，升级用户可能出现
        // quality=杜比/无损 但 highQuality=false（实际不优先 FLAC）。v6 已把两者折叠成
        // 单一「音质」入口，这里一次性把它们归一化，让显示与实际一致（偏好键保留）。
        runCatching {
            val q = prefs.audioQuality.first()
            if ((q == com.bbplayer.android.core.model.AudioQuality.HI_RES ||
                    q == com.bbplayer.android.core.model.AudioQuality.DOLBY) &&
                !prefs.highQualityAudio.first()
            ) {
                prefs.setHighQualityAudio(true)
            }
        }
        val resumeEnabled = runCatching { prefs.resumeOnStart.first() }.getOrDefault(true)
        if (resumeEnabled) {
            val controller = graph.playbackController
            if (controller is com.bbplayer.android.playback.PlaybackControllerImpl) {
                val autoplay = runCatching { prefs.autoplayOnStart.first() }.getOrDefault(false)
                runCatching { controller.restore(autoplay = autoplay) }
                    .onFailure { android.util.Log.w("BBPlayer", "restore playback failed", it) }
            }
        }
    }

    /** Play with the user's preferred audio quality (setting -> track -> playback). */
    fun playTracks(tracks: List<Track>, index: Int) {
        scope.safeLaunch {
            val quality = graph.preferencesRepository.audioQuality.first()
            val prepared = tracks.map { track ->
                if (track.qualityId == quality.id) track else track.copy(qualityId = quality.id)
            }
            graph.playbackController.playQueue(prepared, index)
        }
        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
    }

    fun openPlayer() {
        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
    }

    val configuration = LocalConfiguration.current
    val wideScreen = configuration.screenWidthDp >= 600
    val onPlay: (List<Track>, Int) -> Unit = { tracks, index -> playTracks(tracks, index) }

    // 根 Tab 页显示底部导航；二级页（搜索/详情/设置…）底部导航下滑隐藏。
    val isRootTab = route in rootRoutes
    val selectedRoute = if (route == Routes.SEARCH) Routes.HOME else route
    // 二级页迷你播放器仍然常驻；播放页不显示（播放页自身就是全屏播放器）。
    val showMiniPlayer = playback.track != null && route != Routes.PLAYER

    BoxWithConstraints(Modifier.fillMaxSize().background(SaltTheme.colors.background)) {
        val twoPane = wideScreen || maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (twoPane) {
                NavRail(
                    tabs = mainTabs,
                    selectedRoute = selectedRoute,
                    onSelect = { navController.switchTab(it) },
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AppNavHost(
                    navController = navController,
                    playbackState = playback,
                    onOpenPlayer = ::openPlayer,
                    onPlayTrack = onPlay,
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onOpenImport = { navController.navigate(Routes.IMPORT_PLAYLIST) },
                    onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                )

                if (twoPane) {
                    // 宽屏：底部导航收敛为左侧 rail；迷你播放器悬浮在内容底部。
                    if (showMiniPlayer) {
                        // 系统手势条 inset 区域也铺底色：否则页面内容会从小白条下面透出来。
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(SaltTheme.colors.background)
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiniPlayer(
                                state = playback,
                                onOpenPlayer = ::openPlayer,
                                onTogglePlay = { graph.playbackController.playPause() },
                                onNext = { graph.playbackController.next() },
                                onPrevious = { graph.playbackController.previous() },
                                modifier = Modifier
                                    .widthIn(max = 560.dp)
                                    .padding(bottom = BbTokens.MiniPlayerGap),
                            )
                        }
                    }
                } else {
                    // 手机：迷你播放器悬浮在底部导航正上方；进二级页时导航下滑隐藏、播放器下沉贴底。
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            // 先铺满整条底部 chrome（含系统手势条 inset）再避让 inset：
                            // 页面内容不会再从系统手势条（小白条）下方透出来。
                            .background(SaltTheme.colors.background)
                            .navigationBarsPadding(),
                    ) {
                        if (showMiniPlayer) {
                            val gap by animateDpAsState(
                                targetValue = if (isRootTab) BbTokens.MiniPlayerGap else 0.dp,
                                label = "miniGap",
                            )
                            MiniPlayer(
                                state = playback,
                                onOpenPlayer = ::openPlayer,
                                onTogglePlay = { graph.playbackController.playPause() },
                                onNext = { graph.playbackController.next() },
                                onPrevious = { graph.playbackController.previous() },
                                modifier = Modifier.padding(bottom = gap),
                            )
                        }
                        AnimatedVisibility(
                            visible = isRootTab,
                            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                        ) {
                            BbBottomNavBar(
                                tabs = mainTabs,
                                selectedRoute = selectedRoute,
                                onSelect = { navController.switchTab(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    playbackState: PlaybackState,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val openLogin: () -> Unit = { navController.navigate(Routes.LOGIN) { launchSingleTop = true } }
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSearch = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
                onOpenPlayer = onOpenPlayer,
                onPlayTrack = onPlayTrack,
                onOpenLeaderboard = onOpenLeaderboard,
                onOpenHistory = onOpenHistory,
                onOpenLibrary = { navController.switchTab(Routes.LIBRARY) },
                onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(onOpenPlayer = onOpenPlayer, onPlayTrack = onPlayTrack)
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                onOpenPlayer = onOpenPlayer,
                onPlayTrack = onPlayTrack,
                onOpenImport = onOpenImport,
                onOpenDownloads = onOpenDownloads,
                onOpenLogin = openLogin,
                onOpenFavoriteFolder = { id, title -> navController.navigate(Routes.favoriteFolder(id, title)) },
                onOpenCollection = { id, title -> navController.navigate(Routes.collection(id, title)) },
                onOpenWatchLater = { navController.navigate(Routes.WATCH_LATER) { launchSingleTop = true } },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onPlayTrack = onPlayTrack,
                onOpenLeaderboard = onOpenLeaderboard,
                onOpenHistory = onOpenHistory,
                onOpenDownloads = onOpenDownloads,
                onOpenImport = onOpenImport,
                onOpenLogin = openLogin,
            )
        }
        // 全屏播放器：从底部平滑展开 / 收起（迷你播放器点击或向上拖拽触发）。
        composable(
            route = Routes.PLAYER,
            enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
            exitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
            popEnterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
            popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onOpenLogin = openLogin,
                onOpenUploader = { mid -> navController.navigate(Routes.uploader(mid)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDownloads = onOpenDownloads,
                onOpenLogin = openLogin,
            )
        }
        composable(Routes.PLAYLIST) { entry ->
            PlaylistDetailScreen(
                playlistId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenPlayer = onOpenPlayer,
                onPlayTrack = onPlayTrack,
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = onOpenPlayer,
                onPlayTrack = onPlayTrack,
            )
        }
        composable(Routes.IMPORT_PLAYLIST) {
            ImportPlaylistScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = onOpenPlayer,
                onPlayTrack = onPlayTrack,
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = onOpenPlayer,
                onPlayTrack = onPlayTrack,
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoggedIn = { navController.popBackStack() },
            )
        }
        composable(Routes.WATCH_LATER) {
            WatchLaterScreen(
                onBack = { navController.popBackStack() },
                onPlayTrack = onPlayTrack,
                onOpenPlayer = onOpenPlayer,
                onOpenLogin = openLogin,
            )
        }
        composable(
            route = Routes.FAVORITE_FOLDER,
            arguments = listOf(navArgument("title") { defaultValue = "" }),
        ) { entry ->
            FavoriteFolderScreen(
                folderId = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                title = entry.arguments?.getString("title").orEmpty().ifBlank { "收藏夹" },
                onBack = { navController.popBackStack() },
                onPlayTrack = onPlayTrack,
                onOpenPlayer = onOpenPlayer,
            )
        }
        composable(
            route = Routes.COLLECTION_DETAIL,
            arguments = listOf(navArgument("title") { defaultValue = "" }),
        ) { entry ->
            CollectionDetailScreen(
                seasonId = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                title = entry.arguments?.getString("title").orEmpty().ifBlank { "合集" },
                onBack = { navController.popBackStack() },
                onPlayTrack = onPlayTrack,
                onOpenPlayer = onOpenPlayer,
            )
        }
        composable(Routes.UPLOADER) { entry ->
            UploaderScreen(
                mid = entry.arguments?.getString("mid")?.toLongOrNull() ?: 0L,
                onBack = { navController.popBackStack() },
                onPlayTrack = onPlayTrack,
                onOpenLogin = openLogin,
            )
        }
    }
}

/** 宽屏（≥600dp）常驻左侧导航栏（窄屏用底部导航）。 */
@Composable
private fun NavRail(tabs: List<MainTab>, selectedRoute: String?, onSelect: (String) -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(SaltTheme.colors.background)
            .statusBarsPadding()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == selectedRoute
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(BbTokens.CardCorner))
                    .background(
                        if (selected) SaltTheme.colors.highlight.copy(alpha = 0.14f) else SaltTheme.colors.subBackground
                    )
                    .clickable { onSelect(tab.route) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
