package com.bbplayer.android.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.model.AccentPreset
import com.bbplayer.android.core.model.ThemeMode
import com.moriafly.salt.ui.SaltColors
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltShapes
import com.moriafly.salt.ui.SaltTextStyles
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.default
import com.moriafly.salt.ui.darkSaltColors
import com.moriafly.salt.ui.lightSaltColors
import com.moriafly.salt.ui.saltTextStyles

/**
 * BBPlayer theme: SaltUI (the component library extracted from Salt Player) with
 * optional Material You (Monet) dynamic colours or a preset accent colour, plus a
 * pure-white (e-ink) mode.
 *
 * 需求 §6 的浅色 neutral token（背景 #F8F9FA / 卡片 #EDF2F7 / 卡片 16dp 圆角 /
 * 20sp Bold 标题）在这里统一覆盖；主色仍来自动态取色或强调色预设。
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
        ThemeMode.PURE_WHITE -> false
    }
    val context = LocalContext.current
    val colors = when {
        pureWhite -> SaltDynamicColors(light = PureWhiteColors, dark = PureWhiteColors)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> SaltDynamicColors(
            light = dynamicLightColorScheme(context).toSaltColors().withLightNeutrals(),
            dark = dynamicDarkColorScheme(context).toSaltColors(),
        )
        else -> SaltDynamicColors(
            light = lightSaltColors().withAccent(accentPreset.light).withLightNeutrals(),
            dark = darkSaltColors().withAccent(accentPreset.dark),
        )
    }
    CompositionLocalProvider(LocalBbColors provides BbExtraColors(dark)) {
        SaltTheme(
            configs = SaltConfigs.default(isDarkTheme = dark),
            dynamicColors = colors,
            textStyles = BbTextStyles,
            shapes = BbShapes,
            content = content,
        )
    }
}

/** 需求 §6：一级标题 20sp Bold / 正文 14sp Regular / 辅助 12sp。 */
private val BbTextStyles: SaltTextStyles = saltTextStyles(
    main = TextStyle(fontSize = 14.sp),
    sub = TextStyle(fontSize = 12.sp),
    paragraph = TextStyle(fontSize = 14.sp, lineHeight = 1.5f.em),
    largeTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
)

/** 需求 §6：全应用卡片圆角统一 16dp（SaltUI 默认 medium 为 12dp）。 */
private val BbShapes: SaltShapes = SaltShapes.default(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(BbTokens.CardCorner),
    large = RoundedCornerShape(24.dp),
)

/** 需求 §6：浅色背景 #F8F9FA，卡片表面 #EDF2F7，弹层用白色。 */
private val LightBackground = Color(0xFFF8F9FA)
private val LightCardSurface = Color(0xFFEDF2F7)
private val LightPopup = Color(0xFFFFFFFF)

private fun SaltColors.withLightNeutrals(): SaltColors = copy(
    background = LightBackground,
    subBackground = LightCardSurface,
    popup = LightPopup,
)

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
