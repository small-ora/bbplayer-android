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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.model.AccentPreset
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.LyricSource
import com.bbplayer.android.core.model.LyricVisualStyle
import com.bbplayer.android.core.model.MiniPlayerStyle
import com.bbplayer.android.core.model.PlayerBackgroundStyle
import com.bbplayer.android.core.model.ReleaseInfo
import com.bbplayer.android.core.model.ThemeMode
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.ui.lyrics.DesktopLyricsService
import com.bbplayer.android.ui.components.SmallTopBar
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.bbplayer.android.core.safeLaunch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = graph.preferencesRepository

    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dynamicColor by prefs.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val quality by prefs.audioQuality.collectAsStateWithLifecycle(initialValue = AudioQuality.Q192)
    val highQuality by prefs.highQualityAudio.collectAsStateWithLifecycle(initialValue = false)
    val autoplay by prefs.autoplayOnStart.collectAsStateWithLifecycle(initialValue = false)
    val resume by prefs.resumeOnStart.collectAsStateWithLifecycle(initialValue = true)
    val fontScale by prefs.lyricsFontScale.collectAsStateWithLifecycle(initialValue = 1f)
    val lyricOffset by prefs.lyricsOffsetMs.collectAsStateWithLifecycle(initialValue = 0)
    val backgroundStyle by prefs.playerBackgroundStyle.collectAsStateWithLifecycle(initialValue = PlayerBackgroundStyle.GRADIENT)
    val miniStyle by prefs.miniPlayerStyle.collectAsStateWithLifecycle(initialValue = MiniPlayerStyle.DOCK)
    val accent by prefs.accentPreset.collectAsStateWithLifecycle(initialValue = AccentPreset.SALT)
    val lyricStyle by prefs.lyricVisualStyle.collectAsStateWithLifecycle(initialValue = LyricVisualStyle.MODERN)
    val verbatim by prefs.verbatimLyrics.collectAsStateWithLifecycle(initialValue = true)
    val showTranslation by prefs.showTranslation.collectAsStateWithLifecycle(initialValue = true)
    val desktopLyrics by prefs.desktopLyricsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val statusBarLyric by prefs.statusBarLyricLine.collectAsStateWithLifecycle(initialValue = true)
    val downloadParallel by prefs.downloadParallel.collectAsStateWithLifecycle(initialValue = 1)
    val autoUpdate by prefs.autoCheckUpdate.collectAsStateWithLifecycle(initialValue = true)
    val lyricSource by prefs.lyricSource.collectAsStateWithLifecycle(initialValue = LyricSource.AUTO)
    val reportProgress by prefs.reportPlaybackProgress.collectAsStateWithLifecycle(initialValue = false)
    val session by graph.authRepository.session().collectAsStateWithLifecycle(initialValue = null as BiliUser?)

    var dialog by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.safeLaunch {
            val target = java.io.File(context.cacheDir, "restore-backup.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            graph.backupRepository.importZip(target)
                .onSuccess { toast = "已恢复 $it 个文件，请重启应用生效" }
                .onFailure { toast = it.toUserMessage() }
        }
    }
    // v5 的两个控件互相独立，升级后可能出现「显示无损但实际不优先」或反过来；这里按
    // 真实行为显示：无损/杜比档在未登录时标注「需登录」，高音质开着但档位是 AAC 时显示
    // 「无损优先（回退 xx）」。AppRoot 启动时会把旧组合归一化，正常不会再出现错位。
    val qualityLabel = when (quality) {
        AudioQuality.HI_RES, AudioQuality.DOLBY ->
            if (session == null) "${quality.label}（需登录）" else quality.label
        else -> when {
            highQuality && session != null -> "无损优先（FLAC / 杜比，无则最高 AAC）"
            highQuality -> "无损优先（登录后生效）"
            else -> quality.label
        }
    }
    // D4: 「启动时自动播放」+「断点续播」两个开关合并成一个三选一（仍写两个旧偏好）。
    val startupLabel = when {
        !resume -> "关闭"
        autoplay -> "恢复并播放"
        else -> "恢复上次播放"
    }
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

        item { SettingsGroup("账号") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                val user = session
                SettingRow(
                    title = "Bilibili 账号",
                    value = if (user == null) "未登录" else "${user.name} · Lv.${user.level}",
                ) {}
                if (user == null) {
                    SettingRow("登录 Bilibili", "扫码登录 / Cookie 粘贴") { onOpenLogin() }
                } else {
                    SettingRow("退出登录", "清除本机加密凭据") {
                        scope.safeLaunch(onError = { toast = it.toUserMessage() }) {
                            graph.authRepository.logout()
                            toast = "已退出登录"
                        }
                    }
                }
                SettingRow("凭据安全", "仅本机 Keystore 加密保存，界面不显示 Cookie") {}
            }
        }

        item { SettingsGroup("外观") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("主题模式", themeMode.label) { dialog = "theme" }
                SettingSwitchRow("动态取色", "跟随系统壁纸取色（Android 12+）", dynamicColor) {
                    scope.safeLaunch { prefs.setDynamicColor(it) }
                }
                SettingRow("强调色", accent.label) { dialog = "accent" }
                SettingRow("播放器背景", backgroundStyle.label) { dialog = "bgStyle" }
                SettingRow("迷你播放器", miniStyle.label) { dialog = "miniStyle" }
            }
        }

        item { SettingsGroup("播放") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                // D1: 音质与「高音质」开关合并为单一「音质」下拉（杜比/无损选项需要登录）。
                SettingRow("音质", qualityLabel) { dialog = "quality" }
                // D4: 启动行为合并为一项三选一。
                SettingRow("启动时", startupLabel) { dialog = "startup" }
            }
        }

        item { SettingsGroup("隐私") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingSwitchRow(
                    title = "上报播放进度到 B 站历史",
                    subtitle = "默认关闭。开启且已登录时，播放进度会同步到 B 站播放历史；关闭后不发任何请求",
                    checked = reportProgress,
                ) { enabled ->
                    if (enabled && session == null) {
                        android.widget.Toast.makeText(
                            context,
                            "上报播放进度需要先登录 Bilibili 账号",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        onOpenLogin()
                    } else {
                        scope.safeLaunch { prefs.setReportPlaybackProgress(enabled) }
                    }
                }
            }
        }

        item { SettingsGroup("下载") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("同时下载数", "${downloadParallel} 个") { dialog = "downloadParallel" }
                SettingRow("已下载管理", "查看/删除/导出") { onOpenDownloads() }
            }
        }

        item { SettingsGroup("歌词") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                StepRow(
                    title = "歌词字号",
                    value = String.format(java.util.Locale.US, "%.1fx", fontScale),
                    onMinus = { scope.safeLaunch { prefs.setLyricsFontScale((fontScale - 0.1f).coerceIn(0.8f, 1.6f)) } },
                    onPlus = { scope.safeLaunch { prefs.setLyricsFontScale((fontScale + 0.1f).coerceIn(0.8f, 1.6f)) } },
                )
                StepRow(
                    title = "歌词偏移",
                    value = if (lyricOffset == 0) "0 ms" else "${if (lyricOffset > 0) "+" else ""}$lyricOffset ms",
                    onMinus = { scope.safeLaunch { prefs.setLyricsOffsetMs((lyricOffset - 100).coerceIn(-2000, 2000)) } },
                    onPlus = { scope.safeLaunch { prefs.setLyricsOffsetMs((lyricOffset + 100).coerceIn(-2000, 2000)) } },
                )
            }
        }

        item { SettingsGroup("数据") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("导出备份", "歌单/收藏/历史/设置/歌词 → zip") { dialog = "exportBackup" }
                SettingRow("从备份恢复", "选择 .zip 文件恢复") { importLauncher.launch("application/zip") }
            }
        }

        item { SettingsGroup("缓存") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                var cacheSize by remember { mutableStateOf(0L) }
                var cacheCount by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    cacheSize = graph.networkCacheRepository.sizeBytes()
                    cacheCount = graph.networkCacheRepository.entryCount()
                }
                SettingRow(
                    title = "接口缓存",
                    value = "${cacheCount} 条 · ${formatBytes(cacheSize)}",
                ) {}
                // D5: 接口缓存与歌词缓存合并为一个清理入口。
                SettingRow("清除缓存", "接口缓存 + 歌词缓存") {
                    scope.safeLaunch {
                        val cleared = graph.networkCacheRepository.clear()
                        graph.lyricsRepository.clearCache()
                        cacheSize = graph.networkCacheRepository.sizeBytes()
                        cacheCount = graph.networkCacheRepository.entryCount()
                        toast = "已清除 $cleared 条接口缓存与歌词缓存"
                    }
                }
            }
        }

        item { SettingsGroup("关于") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("版本", version) {}
                SettingRow(
                    title = when {
                        checkingUpdate -> "检查更新中…"
                        updateInfo != null -> "发现新版本 ${updateInfo!!.version}，点此下载"
                        else -> "检查更新"
                    },
                    value = "",
                ) {
                    if (updateInfo != null) {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updateInfo!!.url)))
                        }
                        return@SettingRow
                    }
                    scope.safeLaunch {
                        checkingUpdate = true
                        graph.updateRepository.checkForUpdate(version)
                            .onSuccess { info ->
                                updateInfo = info
                                toast = if (info == null) "已是最新版本" else "发现新版本 ${info.version}"
                            }
                            .onFailure { toast = it.toUserMessage() }
                        checkingUpdate = false
                    }
                }
                SettingRow("BBPlayer", "数据来自 Bilibili，仅作学习用途") {}
                SettingRow("界面风格", "SaltUI / 椒盐音乐（Apache-2.0）") {}
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
                scope.safeLaunch { prefs.setThemeMode(ThemeMode.entries[idx]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "accent" -> OptionDialog(
            title = "强调色",
            options = AccentPreset.entries.map { it.label },
            selectedIndex = AccentPreset.entries.indexOf(accent),
            onSelect = { idx ->
                scope.safeLaunch { prefs.setAccentPreset(AccentPreset.entries[idx]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "bgStyle" -> OptionDialog(
            title = "播放器背景",
            options = PlayerBackgroundStyle.entries.map { it.label },
            selectedIndex = PlayerBackgroundStyle.entries.indexOf(backgroundStyle),
            onSelect = { idx ->
                scope.safeLaunch { prefs.setPlayerBackgroundStyle(PlayerBackgroundStyle.entries[idx]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "miniStyle" -> OptionDialog(
            title = "迷你播放器样式",
            options = MiniPlayerStyle.entries.map { it.label },
            selectedIndex = MiniPlayerStyle.entries.indexOf(miniStyle),
            onSelect = { idx ->
                scope.safeLaunch { prefs.setMiniPlayerStyle(MiniPlayerStyle.entries[idx]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "lyricSource" -> OptionDialog(
            title = "歌词来源偏好",
            options = LyricSource.entries.map { it.label },
            selectedIndex = LyricSource.entries.indexOf(lyricSource),
            onSelect = { idx ->
                scope.safeLaunch { prefs.setLyricSource(LyricSource.entries[idx]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "lyricStyle" -> OptionDialog(
            title = "歌词样式",
            options = LyricVisualStyle.entries.map { it.label },
            selectedIndex = LyricVisualStyle.entries.indexOf(lyricStyle),
            onSelect = { idx ->
                scope.safeLaunch { prefs.setLyricVisualStyle(LyricVisualStyle.entries[idx]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "downloadParallel" -> OptionDialog(
            title = "同时下载数",
            options = listOf("1 个", "2 个", "3 个"),
            selectedIndex = downloadParallel - 1,
            onSelect = { idx ->
                scope.safeLaunch { prefs.setDownloadParallel(idx + 1) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "exportBackup" -> {
            scope.safeLaunch {
                graph.backupRepository.exportZip()
                    .onSuccess { file ->
                        runCatching {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file,
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(intent, "导出备份").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure { toast = "导出文件已生成：${file.name}" }
                        toast = "备份已导出：${file.name}"
                    }
                    .onFailure { toast = it.toUserMessage() }
            }
            dialog = null
        }
        "quality" -> {
            // 无损优先，其次杜比，再按常用 AAC 档位从高到低；只保留这一个音质入口。
            val ordered = listOf(
                AudioQuality.HI_RES,
                AudioQuality.DOLBY,
                AudioQuality.Q192,
                AudioQuality.Q128,
                AudioQuality.Q64,
            )
            OptionDialog(
                title = "音质（杜比 / 无损需登录）",
                options = ordered.map { it.label },
                selectedIndex = when {
                    // 旧「高音质」开 + 音质停在 AAC：等价于「无损优先」，高亮无损项。
                    highQuality && quality != AudioQuality.HI_RES && quality != AudioQuality.DOLBY -> 0
                    else -> ordered.indexOf(quality).coerceAtLeast(0)
                },
                onSelect = { idx ->
                    val picked = ordered[idx]
                    val needsLogin = picked == AudioQuality.DOLBY || picked == AudioQuality.HI_RES
                    if (needsLogin && session == null) {
                        dialog = null
                        // 设置页马上要跳登录页，页面内 footer toast 看不到，用系统 Toast 提示。
                        android.widget.Toast.makeText(
                            context,
                            "杜比 / 无损需要先登录 Bilibili 账号",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        onOpenLogin()
                    } else {
                        dialog = null
                        scope.safeLaunch {
                            // 两次写不是原子的：先写「会降级为显示无损优先」的方向，保证半途失败时
                            // 显示与实际一致（选无损：先开 highQuality 再改档位；选 AAC：先改档位再关）。
                            if (needsLogin) {
                                prefs.setHighQualityAudio(true)
                                prefs.setAudioQuality(picked)
                            } else {
                                prefs.setAudioQuality(picked)
                                prefs.setHighQualityAudio(false)
                            }
                        }
                    }
                },
                onDismiss = { dialog = null },
            )
        }
        "startup" -> OptionDialog(
            title = "启动时",
            options = listOf("关闭", "恢复上次播放", "恢复并播放"),
            selectedIndex = when {
                !resume -> 0
                autoplay -> 2
                else -> 1
            },
            onSelect = { idx ->
                scope.safeLaunch {
                    when (idx) {
                        0 -> {
                            prefs.setResumeOnStart(false)
                            prefs.setAutoplayOnStart(false)
                        }
                        1 -> {
                            prefs.setResumeOnStart(true)
                            prefs.setAutoplayOnStart(false)
                        }
                        else -> {
                            prefs.setResumeOnStart(true)
                            prefs.setAutoplayOnStart(true)
                        }
                    }
                }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
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
