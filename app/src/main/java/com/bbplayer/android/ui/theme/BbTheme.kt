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
import com.bbplayer.android.core.model.AccentPreset
import com.bbplayer.android.core.model.ThemeMode
import com.moriafly.salt.ui.SaltColors
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.darkSaltColors
import com.moriafly.salt.ui.lightSaltColors

/**
 * BBPlayer theme: SaltUI (the component library extracted from Salt Player) with
 * optional Material You (Monet) dynamic colours or a preset accent colour, plus a
 * pure-white (e-ink) mode.
 */
@Composable
fun BbTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentPreset: AccentPreset = AccentPreset.SALT,
    pureWhite: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = if (pureWhite) false else when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        pureWhite -> SaltDynamicColors(light = PureWhiteColors, dark = PureWhiteColors)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> SaltDynamicColors(
            light = dynamicLightColorScheme(context).toSaltColors(),
            dark = dynamicDarkColorScheme(context).toSaltColors(),
        )
        else -> SaltDynamicColors(
            light = lightSaltColors().withAccent(accentPreset.light),
            dark = darkSaltColors().withAccent(accentPreset.dark),
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

private fun SaltColors.withAccent(accent: Long): SaltColors = copy(highlight = Color(accent))

/** E-ink friendly palette: pure white surfaces, black text, no tinting. */
private val PureWhiteColors: SaltColors = SaltColors(
    highlight = Color(0xFF1A1A1A),
    text = Color(0xFF000000),
    subText = Color(0xFF6B6B6B),
    background = Color(0xFFFFFFFF),
    subBackground = Color(0xFFFFFFFF),
    popup = Color(0xFFFFFFFF),
    stroke = Color(0x33000000),
    onHighlight = Color(0xFFFFFFFF),
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
