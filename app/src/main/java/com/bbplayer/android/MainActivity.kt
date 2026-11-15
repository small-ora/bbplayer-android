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
        enableEdgeToEdge()
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
                    pureWhite = backgroundStyle == PlayerBackgroundStyle.PURE_WHITE,
                ) {
                    AppRoot()
                }
            }
        }
    }
}
