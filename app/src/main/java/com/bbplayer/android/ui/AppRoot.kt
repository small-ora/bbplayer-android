package com.bbplayer.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import com.bbplayer.android.core.safeLaunch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.BottomDock
import com.bbplayer.android.ui.components.DockTab
import com.bbplayer.android.ui.screens.home.HomeScreen
import com.bbplayer.android.ui.screens.library.LibraryScreen
import com.bbplayer.android.ui.screens.player.PlayerScreen
import com.bbplayer.android.ui.screens.playlist.PlaylistDetailScreen
import com.bbplayer.android.ui.screens.profile.ProfileScreen
import com.bbplayer.android.ui.screens.search.SearchScreen
import com.bbplayer.android.ui.screens.settings.SettingsScreen
import com.moriafly.salt.ui.SaltTheme

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{id}"
    fun playlist(id: String) = "playlist/$id"
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
    val showDock = route in tabRoutes

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

    fun openPlayer() {
        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
    }

    Box(Modifier.fillMaxSize().background(SaltTheme.colors.background)) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSearch = { navController.switchTab(Routes.SEARCH) },
                    onOpenPlayer = ::openPlayer,
                    onPlayTrack = { tracks, index ->
                        scope.safeLaunch { graph.playbackController.playQueue(tracks, index) }
                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenPlayer = ::openPlayer,
                    onPlayTrack = { tracks, index ->
                        scope.safeLaunch { graph.playbackController.playQueue(tracks, index) }
                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenPlayer = ::openPlayer,
                    onPlayTrack = { tracks, index ->
                        scope.safeLaunch { graph.playbackController.playQueue(tracks, index) }
                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onPlayTrack = { tracks, index ->
                        scope.safeLaunch { graph.playbackController.playQueue(tracks, index) }
                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.PLAYER) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PLAYLIST) { entry ->
                PlaylistDetailScreen(
                    playlistId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = ::openPlayer,
                    onPlayTrack = { tracks, index ->
                        scope.safeLaunch { graph.playbackController.playQueue(tracks, index) }
                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                    },
                )
            }
        }

        if (showDock) {
            BottomDock(
                tabs = tabs,
                selectedIndex = tabRoutes.indexOf(route).coerceAtLeast(0),
                onSelect = { index -> navController.switchTab(tabRoutes[index]) },
                playbackState = playback,
                onOpenPlayer = ::openPlayer,
                onTogglePlay = { graph.playbackController.playPause() },
                onOpenQueue = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp),
            )
        }
    }
}

private fun androidx.navigation.NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
