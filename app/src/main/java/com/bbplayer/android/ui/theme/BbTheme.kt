package com.bbplayer.android.ui.theme

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bbplayer.android.core.model.ThemeMode
import com.moriafly.salt.ui.SaltColors
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTheme

/**
 * BBPlayer theme: SaltUI (the component library extracted from Salt Player) with
 * optional Material You (Monet) dynamic colours mapped onto Salt's colour roles.
 */
@Composable
fun BbTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SaltDynamicColors(
            light = dynamicLightColorScheme(context).toSaltColors(),
            dark = dynamicDarkColorScheme(context).toSaltColors(),
        )
    } else {
        SaltDynamicColors(
            light = com.moriafly.salt.ui.lightSaltColors(),
            dark = com.moriafly.salt.ui.darkSaltColors(),
        )
    }
    CompositionLocalProvider(LocalBbColors provides BbExtraColors(dark)) {
        SaltTheme(
            configs = SaltConfigs.default(isDarkTheme = dark),
            dynamicColors = colors,
            content = content,
        )
    }
}

private fun androidx.compose.material3.ColorScheme.toSaltColors(): SaltColors = SaltColors(
    highlight = primary,
    text = onSurface,
    subText = onSurfaceVariant,
    background = surface,
    subBackground = surfaceContainer,
    popup = surfaceContainerHigh,
    stroke = outlineVariant,
    onHighlight = onPrimary,
)

/** Player-screen album-art wash colours (independent from Salt's design tokens). */
@Immutable
data class BbExtraColors(
    val isDark: Boolean,
    val playerScrim: Color = if (isDark) Color(0xB3000000) else Color(0x99000000),
    val onPlayer: Color = Color.White,
    val onPlayerSecondary: Color = Color(0xCCFFFFFF),
)

val LocalBbColors = staticCompositionLocalOf { BbExtraColors(isDark = false) }
