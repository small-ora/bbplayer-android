package com.bbplayer.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.bbplayer.android.core.model.MiniPlayerStyle
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.AppDrawerSheet
import com.bbplayer.android.ui.components.BottomPlayerBar
import com.bbplayer.android.ui.components.LocalOpenMenu
import com.bbplayer.android.ui.components.NavEntry
import com.bbplayer.android.ui.components.NavTab
import com.bbplayer.android.ui.lyrics.NowPlayingLyrics
import android.net.Uri
import com.bbplayer.android.ui.screens.comments.CommentsScreen
import com.bbplayer.android.ui.screens.library.CollectionDetailScreen
import com.bbplayer.android.ui.screens.library.FavoriteFolderScreen
import com.bbplayer.android.ui.screens.library.WatchLaterScreen
import com.bbplayer.android.ui.screens.login.LoginScreen
import com.bbplayer.android.ui.screens.uploader.UploaderScreen
import com.bbplayer.android.ui.screens.discover.LeaderboardScreen
import com.bbplayer.android.ui.screens.downloads.DownloadsScreen
import com.bbplayer.android.ui.screens.history.HistoryScreen
import com.bbplayer.android.ui.screens.home.HomeScreen
import com.bbplayer.android.ui.screens.playlistimport.ImportPlaylistScreen
import com.bbplayer.android.ui.screens.library.LibraryScreen
import com.bbplayer.android.ui.screens.player.PlayerScreen
import com.bbplayer.android.ui.screens.playlist.PlaylistDetailScreen
import com.bbplayer.android.ui.screens.profile.ProfileScreen
import com.bbplayer.android.ui.screens.search.SearchScreen
import com.bbplayer.android.ui.screens.settings.SettingsScreen
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
    const val COMMENTS = "comments/{bvid}"
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
    fun comments(bvid: String) = "comments/$bvid"
    fun favoriteFolder(id: Long, title: String) = "favoriteFolder/$id?title=${Uri.encode(title)}"
    fun collection(id: Long, title: String) = "collection/$id?title=${Uri.encode(title)}"
    fun uploader(mid: Long) = "uploader/$mid"
}

private val tabs = listOf(
    NavTab("首页", Icons.Rounded.Home),
    NavTab("搜索", Icons.Rounded.Search),
    NavTab("音乐库", Icons.Rounded.LibraryMusic),
    NavTab("我的", Icons.Rounded.Person),
)
private val tabRoutes = listOf(Routes.HOME, Routes.SEARCH, Routes.LIBRARY, Routes.PROFILE)

/** Secondary destinations in the side drawer's "更多" section (pushed pages, not tabs). */
private val moreEntries = listOf(
    NavEntry("排行榜", Icons.Rounded.BarChart, Routes.LEADERBOARD),
    NavEntry("收听历史", Icons.Rounded.History, Routes.HISTORY),
    NavEntry("已下载", Icons.Rounded.Download, Routes.DOWNLOADS),
    NavEntry("导入歌单", Icons.Rounded.PlaylistAdd, Routes.IMPORT_PLAYLIST),
    NavEntry("设置", Icons.Rounded.Settings, Routes.SETTINGS),
)

@Composable
fun AppRoot() {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val isTab = route in tabRoutes
    val miniStyle by graph.preferencesRepository.miniPlayerStyle.collectAsStateWithLifecycle(initialValue = MiniPlayerStyle.DOCK)
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val openMenu: () -> Unit = remember(drawerState) {
        { scope.launch { drawerState.open() } }
    }

    val navHost: @Composable () -> Unit = {
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
    }

    // Bottom bar = music transport controls only (Salt Player style). Navigation lives in the
    // drawer (phone) / rail (wide screen). Hidden on the player screen and pushed pages.
    val playerBar: @Composable (Modifier, MiniPlayerStyle) -> Unit = { barModifier, barStyle ->
        if (isTab && playback.track != null) {
            BottomPlayerBar(
                state = playback,
                style = barStyle,
                onOpenPlayer = ::openPlayer,
                onTogglePlay = { graph.playbackController.playPause() },
                onPrevious = { graph.playbackController.previous() },
                onNext = { graph.playbackController.next() },
                onOpenQueue = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                modifier = barModifier,
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(SaltTheme.colors.background)) {
        val twoPane = wideScreen || maxWidth >= 600.dp
        val selectedTab = tabRoutes.indexOf(route).coerceAtLeast(0)
        Row(Modifier.fillMaxSize()) {
            if (twoPane) {
                NavRail(tabs = tabs, selectedIndex = selectedTab, onSelect = { index ->
                    navController.switchTab(tabRoutes[index])
                })
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (twoPane) {
                    navHost()
                    playerBar(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp)
                            .fillMaxWidth(0.72f),
                        MiniPlayerStyle.FLOAT,
                    )
                } else {
                    // Navigation lives in a side drawer (opened by the top-left hamburger), so the
                    // bottom bar can be dedicated to music transport controls.
                    CompositionLocalProvider(
                        LocalOpenMenu provides openMenu,
                    ) {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            // M3 gates scrim-tap-to-close on gesturesEnabled: enable gestures only
                            // while open (also enables edge-swipe dismiss with predictive back),
                            // keep them off while closed so horizontal lists/seek bars work.
                            gesturesEnabled = drawerState.isOpen,
                            scrimColor = Color.Black.copy(alpha = 0.32f),
                            drawerContent = {
                                AppDrawerSheet(
                                    drawerState = drawerState,
                                    tabs = tabs,
                                    selectedIndex = selectedTab,
                                    onSelectTab = { index ->
                                        navController.switchTab(tabRoutes[index])
                                        scope.launch { drawerState.close() }
                                    },
                                    secondary = moreEntries,
                                    onOpenSecondary = { target ->
                                        navController.navigate(target) { launchSingleTop = true }
                                        scope.launch { drawerState.close() }
                                    },
                                )
                            },
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                navHost()
                                playerBar(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding(),
                                    miniStyle,
                                )
                            }
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
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSearch = { navController.switchTab(Routes.SEARCH) },
                onOpenPlayer = onOpenPlayer,
                onPlayTrack = onPlayTrack,
                onOpenLeaderboard = onOpenLeaderboard,
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
        composable(Routes.PLAYER) {
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
        composable(Routes.COMMENTS) { entry ->
            CommentsScreen(
                bvid = entry.arguments?.getString("bvid").orEmpty(),
                onBack = { navController.popBackStack() },
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

@Composable
private fun NavRail(tabs: List<NavTab>, selectedIndex: Int, onSelect: (Int) -> Unit) {
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
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) SaltTheme.colors.highlight.copy(alpha = 0.14f) else SaltTheme.colors.subBackground
                    )
                    .clickable { onSelect(index) },
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
