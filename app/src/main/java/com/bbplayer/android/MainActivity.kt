package com.bbplayer.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.model.AccentPreset
import com.bbplayer.android.core.model.PlayerBackgroundStyle
import com.bbplayer.android.core.model.ThemeMode
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.AppRoot
import com.bbplayer.android.ui.theme.BbTheme

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边：状态栏/导航栏透明，由 Compose 内容延伸到系统栏下方；
        // 关闭系统自动加的对比遮罩，避免和界面背景出现「色带/断层」。
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        val app = application as BbPlayerApp
        val graph = app.graph
        setContent {
            val themeMode by graph.preferencesRepository.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by graph.preferencesRepository.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            val accentPreset by graph.preferencesRepository.accentPreset.collectAsStateWithLifecycle(initialValue = AccentPreset.SALT)
            val backgroundStyle by graph.preferencesRepository.playerBackgroundStyle.collectAsStateWithLifecycle(initialValue = PlayerBackgroundStyle.GRADIENT)
            CompositionLocalProvider(LocalAppGraph provides graph) {
                BbTheme(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    accentPreset = accentPreset,
                    pureWhite = themeMode == ThemeMode.PURE_WHITE ||
                        backgroundStyle == PlayerBackgroundStyle.PURE_WHITE,
                ) {
                    AppRoot()
                }
            }
        }
    }
}
