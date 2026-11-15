package com.bbplayer.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.ThemeMode
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = graph.preferencesRepository

    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dynamicColor by prefs.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val quality by prefs.audioQuality.collectAsStateWithLifecycle(initialValue = AudioQuality.Q192)
    val autoplay by prefs.autoplayOnStart.collectAsStateWithLifecycle(initialValue = false)
    val resume by prefs.resumeOnStart.collectAsStateWithLifecycle(initialValue = true)
    val fontScale by prefs.lyricsFontScale.collectAsStateWithLifecycle(initialValue = 1f)
    val lyricOffset by prefs.lyricsOffsetMs.collectAsStateWithLifecycle(initialValue = 0)

    var dialog by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item { SmallTopBar(title = "设置", onBack = onBack) }

        item { SettingsGroup("外观") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("主题模式", themeMode.label) { dialog = "theme" }
                SettingSwitchRow("动态取色", "跟随系统壁纸取色（Android 12+）", dynamicColor) {
                    scope.launch { prefs.setDynamicColor(it) }
                }
            }
        }

        item { SettingsGroup("播放") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("音质", quality.label) { dialog = "quality" }
                SettingSwitchRow("启动时自动播放", "打开 App 后自动继续上次播放", autoplay) {
                    scope.launch { prefs.setAutoplayOnStart(it) }
                }
                SettingSwitchRow("断点续播", "记住上次播放的位置", resume) {
                    scope.launch { prefs.setResumeOnStart(it) }
                }
            }
        }

        item { SettingsGroup("歌词") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                StepRow(
                    title = "歌词字号",
                    value = String.format(java.util.Locale.US, "%.1fx", fontScale),
                    onMinus = { scope.launch { prefs.setLyricsFontScale((fontScale - 0.1f).coerceIn(0.8f, 1.6f)) } },
                    onPlus = { scope.launch { prefs.setLyricsFontScale((fontScale + 0.1f).coerceIn(0.8f, 1.6f)) } },
                )
                StepRow(
                    title = "歌词偏移",
                    value = if (lyricOffset == 0) "0 ms" else "${if (lyricOffset > 0) "+" else ""}$lyricOffset ms",
                    onMinus = { scope.launch { prefs.setLyricsOffsetMs((lyricOffset - 100).coerceIn(-2000, 2000)) } },
                    onPlus = { scope.launch { prefs.setLyricsOffsetMs((lyricOffset + 100).coerceIn(-2000, 2000)) } },
                )
                SettingRow("清除歌词缓存", "") {
                    scope.launch {
                        graph.lyricsRepository.clearCache()
                        toast = "歌词缓存已清除"
                    }
                }
            }
        }

        item { SettingsGroup("关于") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("版本", version) {}
                SettingRow("BBPlayer", "数据来自 Bilibili，仅作学习用途") {}
                SettingRow("界面风格", "SaltUI / 椒盐音乐（LGPL-2.1）") {}
                SettingRow("上游项目", "bbplayer-app/bbplayer（MIT）") {}
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (toast != null) {
                    Text(toast!!, color = SaltTheme.colors.highlight, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("BBPlayer Android Native · v$version", color = SaltTheme.colors.subText, fontSize = 11.sp)
            }
        }
    }

    when (dialog) {
        "theme" -> OptionDialog(
            title = "主题模式",
            options = ThemeMode.entries.map { it.label },
            selectedIndex = ThemeMode.entries.indexOf(themeMode),
            onSelect = { idx ->
                scope.launch { prefs.setThemeMode(ThemeMode.entries[idx]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "quality" -> OptionDialog(
            title = "默认音质",
            options = AudioQuality.entries.sortedByDescending { it.id }.map { it.label },
            selectedIndex = AudioQuality.entries.sortedByDescending { it.id }.indexOf(quality),
            onSelect = { idx ->
                scope.launch { prefs.setAudioQuality(AudioQuality.entries.sortedByDescending { it.id }[idx]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(
        text = title,
        color = SaltTheme.colors.subText,
        fontSize = 13.sp,
        modifier = Modifier.padding(
            start = SaltTheme.dimens.padding + 4.dp,
            top = 18.dp,
            bottom = 6.dp,
        ),
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, color = SaltTheme.colors.subText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let {
                Text(it, color = SaltTheme.colors.subText, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SaltTheme.colors.onHighlight,
                checkedTrackColor = SaltTheme.colors.highlight,
                uncheckedThumbColor = SaltTheme.colors.subText,
                uncheckedTrackColor = SaltTheme.colors.subText.copy(alpha = 0.2f),
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

@Composable
private fun StepRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        SaltPill(text = "−", onClick = onMinus)
        Text(value, color = SaltTheme.colors.subText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp).width(70.dp))
        SaltPill(text = "+", onClick = onPlus)
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SaltTheme.colors.popup)
                .padding(20.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            options.forEachIndexed { index, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option,
                        color = if (index == selectedIndex) SaltTheme.colors.highlight else SaltTheme.colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (index == selectedIndex) {
                        Text("✓", color = SaltTheme.colors.highlight)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
            }
        }
    }
}
