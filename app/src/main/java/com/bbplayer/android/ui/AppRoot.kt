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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import com.bbplayer.android.ui.components.BottomDock
import com.bbplayer.android.ui.components.DockTab
import com.bbplayer.android.ui.components.MiniPlayerBar
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
    DockTab("首页", Icons.Rounded.Home),
    DockTab("搜索", Icons.Rounded.Search),
    DockTab("音乐库", Icons.Rounded.LibraryMusic),
    DockTab("我的", Icons.Rounded.Person),
)
private val tabRoutes = listOf(Routes.HOME, Routes.SEARCH, Routes.LIBRARY, Routes.PROFILE)

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

    BoxWithConstraints(Modifier.fillMaxSize().background(SaltTheme.colors.background)) {
        val twoPane = wideScreen || maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (twoPane) {
                NavRail(tabs = tabs, selectedIndex = tabRoutes.indexOf(route).coerceAtLeast(0), onSelect = { index ->
                    navController.switchTab(tabRoutes[index])
                })
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AppNavHost(
                        navController = navController,
                        playbackState = playback,
                        onOpenPlayer = ::openPlayer,
                        onPlayTrack = onPlay,
                        onOpenComments = { navController.navigate(Routes.comments(it)) },
                        onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                        onOpenImport = { navController.navigate(Routes.IMPORT_PLAYLIST) },
                        onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }
            } else {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AppNavHost(
                        navController = navController,
                        playbackState = playback,
                        onOpenPlayer = ::openPlayer,
                        onPlayTrack = onPlay,
                        onOpenComments = { navController.navigate(Routes.comments(it)) },
                        onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                        onOpenImport = { navController.navigate(Routes.IMPORT_PLAYLIST) },
                        onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }
            }
        }

        // Mini player: either inside the bottom dock (phone) or as a floating capsule.
        if (isTab && playback.track != null && (miniStyle == MiniPlayerStyle.FLOAT || twoPane)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = if (twoPane) 24.dp else 12.dp, vertical = 10.dp)
                    .fillMaxWidth(if (twoPane) 0.72f else 1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SaltTheme.colors.popup)
                    .height(if (twoPane) 64.dp else 62.dp),
            ) {
                MiniPlayerBar(
                    state = playback,
                    onOpenPlayer = ::openPlayer,
                    onTogglePlay = { graph.playbackController.playPause() },
                    onOpenQueue = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                )
            }
        }
        if (isTab && !twoPane) {
            BottomDock(
                tabs = tabs,
                selectedIndex = tabRoutes.indexOf(route).coerceAtLeast(0),
                onSelect = { index -> navController.switchTab(tabRoutes[index]) },
                playbackState = playback,
                onOpenPlayer = ::openPlayer,
                onTogglePlay = { graph.playbackController.playPause() },
                onOpenQueue = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                showMiniPlayer = miniStyle == MiniPlayerStyle.DOCK,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    playbackState: PlaybackState,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenComments: (String) -> Unit,
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
                onOpenComments = onOpenComments,
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
private fun NavRail(tabs: List<DockTab>, selectedIndex: Int, onSelect: (Int) -> Unit) {
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
