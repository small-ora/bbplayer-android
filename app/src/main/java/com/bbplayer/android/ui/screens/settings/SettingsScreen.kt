package com.bbplayer.android.ui.screens.settings

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.AccentPreset
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.LyricSource
import com.bbplayer.android.core.model.LyricVisualStyle
import com.bbplayer.android.core.model.PlayerBackgroundStyle
import com.bbplayer.android.core.model.ReleaseInfo
import com.bbplayer.android.core.model.ThemeMode
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.lyrics.DesktopLyricsService
import com.bbplayer.android.ui.theme.BbRadius
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

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
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item { SmallTopBar(title = "设置", onBack = onBack) }

        item { SettingsGroup("账号") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                val user = session
                SettingRow(
                    title = "Bilibili 账号",
                    value = if (user == null) "未登录" else "${user.name} · Lv.${user.level}",
                    icon = Icons.Rounded.Person,
                ) {}
                if (user == null) {
                    SettingRow("登录 Bilibili", "扫码登录 / Cookie 粘贴", icon = Icons.Rounded.Person) { onOpenLogin() }
                } else {
                    SettingRow("退出登录", "清除本机加密凭据", icon = Icons.Rounded.Person, danger = true) {
                        scope.safeLaunch(onError = { toast = it.toUserMessage() }) {
                            graph.authRepository.logout()
                            toast = "已退出登录"
                        }
                    }
                }
                SettingNoteRow("凭据安全", "仅本机 Keystore 加密保存，界面不显示 Cookie")
            }
        }

        item { SettingsGroup("外观") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("主题模式", themeMode.label, icon = Icons.Rounded.Palette) { dialog = "theme" }
                if (themeMode != ThemeMode.PURE_WHITE) {
                    SettingSwitchRow("动态取色", "跟随系统壁纸取色（Android 12+）", dynamicColor, icon = Icons.Rounded.Palette) {
                        scope.safeLaunch { prefs.setDynamicColor(it) }
                    }
                    SettingRow("强调色", accent.label, icon = Icons.Rounded.Palette) { dialog = "accent" }
                }
                SettingRow("播放器背景", backgroundStyle.label, icon = Icons.Rounded.Palette) { dialog = "bgStyle" }
            }
        }

        item { SettingsGroup("播放") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                // D1: 音质与「高音质」开关合并为单一「音质」下拉（杜比/无损选项需要登录）。
                SettingRow("音质", qualityLabel, icon = Icons.Rounded.PlayCircle) { dialog = "quality" }
                // D4: 启动行为合并为一项三选一。
                SettingRow("启动时", startupLabel, icon = Icons.Rounded.PlayCircle) { dialog = "startup" }
            }
        }

        item { SettingsGroup("隐私") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingSwitchRow(
                    title = "上报播放进度到 B 站历史",
                    subtitle = "默认关闭。开启且已登录时，播放进度会同步到 B 站播放历史；关闭后不发任何请求",
                    checked = reportProgress,
                    icon = Icons.Rounded.Lock,
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
                SettingRow("同时下载数", "${downloadParallel} 个", icon = Icons.Rounded.Download) { dialog = "downloadParallel" }
                SettingRow("已下载管理", "查看/删除/导出", icon = Icons.Rounded.Download) { onOpenDownloads() }
            }
        }

        item { SettingsGroup("歌词") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                StepRow(
                    title = "歌词字号",
                    icon = Icons.Rounded.Subtitles,
                    value = String.format(java.util.Locale.US, "%.1fx", fontScale),
                    onMinus = { scope.safeLaunch { prefs.setLyricsFontScale((fontScale - 0.1f).coerceIn(0.8f, 1.6f)) } },
                    onPlus = { scope.safeLaunch { prefs.setLyricsFontScale((fontScale + 0.1f).coerceIn(0.8f, 1.6f)) } },
                )
                StepRow(
                    title = "歌词偏移",
                    icon = Icons.Rounded.Subtitles,
                    value = if (lyricOffset == 0) "0 ms" else "${if (lyricOffset > 0) "+" else ""}$lyricOffset ms",
                    onMinus = { scope.safeLaunch { prefs.setLyricsOffsetMs((lyricOffset - 100).coerceIn(-2000, 2000)) } },
                    onPlus = { scope.safeLaunch { prefs.setLyricsOffsetMs((lyricOffset + 100).coerceIn(-2000, 2000)) } },
                )
            }
        }

        item { SettingsGroup("数据") }
        item {
            RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                SettingRow("导出备份", "歌单/收藏/历史/设置/歌词 → zip", icon = Icons.Rounded.Storage) { dialog = "exportBackup" }
                SettingRow("从备份恢复", "选择 .zip 文件恢复", icon = Icons.Rounded.Storage) { importLauncher.launch("application/zip") }
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
                    icon = Icons.Rounded.DeleteSweep,
                ) {}
                // D5: 接口缓存与歌词缓存合并为一个清理入口。
                SettingRow("清除缓存", "接口缓存 + 歌词缓存", icon = Icons.Rounded.DeleteSweep) {
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
                SettingRow("版本", version, icon = Icons.Rounded.Info) {}
                SettingRow(
                    title = when {
                        checkingUpdate -> "检查更新中…"
                        updateInfo != null -> "发现新版本 ${updateInfo!!.version}，点此下载"
                        else -> "检查更新"
                    },
                    value = "",
                    icon = Icons.Rounded.Info,
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
                SettingRow("BBPlayer", "数据来自 Bilibili，仅作学习用途", icon = Icons.Rounded.Info) {}
                SettingRow("界面风格", "SaltUI / 椒盐音乐（Apache-2.0）", icon = Icons.Rounded.Info) {}
                SettingRow("上游项目", "bbplayer-app/bbplayer（MIT）", icon = Icons.Rounded.Info) {}
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (toast != null) {
                    Text(toast!!, color = SaltTheme.colors.highlight, style = BbType.Label)
                    Spacer(Modifier.height(BbSpacing.Sm))
                }
                // v9.2：用一张版本卡收尾，代替页脚裸文字
                RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(BbSpacing.Lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("BBPlayer Android Native", color = SaltTheme.colors.text, style = BbType.Label)
                        Spacer(Modifier.height(2.dp))
                        Text("v$version", color = SaltTheme.colors.subText, style = BbType.Caption)
                    }
                }
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
                        // 分享用系统对话框；结果用系统 Toast 提示（设置页底部提示在屏幕外看不到）。
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
                                android.content.Intent.createChooser(intent, "导出备份")
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            android.widget.Toast.makeText(
                                context,
                                "备份已生成：${file.name}",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }.onFailure {
                            android.widget.Toast.makeText(
                                context,
                                "备份已生成：${file.name}（分享失败，可在文件管理中从 Android/data 取出）",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    .onFailure {
                        android.widget.Toast.makeText(
                            context,
                            it.toUserMessage(),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
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
    // v9.2：分组标签改用 accent（Salt/主流设置页做法），层级一眼可读
    Text(
        text = title,
        color = SaltTheme.colors.highlight,
        style = BbType.Label,
        modifier = Modifier.padding(
            start = SaltTheme.dimens.padding + BbSpacing.Xs,
            top = BbSpacing.Xl,
            bottom = BbSpacing.Sm,
        ),
    )
}


/** 标题 + 说明文字的行（长说明不会被挤成竖排）。 */
@Composable
private fun SettingNoteRow(title: String, note: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Spacer(Modifier.height(2.dp))
            Text(note, color = SaltTheme.colors.subText, style = BbType.Caption)
        }
    }
}

/** 危险操作文字色（退出登录 / 清空等；SaltUI 无 error 角色，这里单独给 token）。 */
@Composable
private fun dangerColor(): Color =
    if (SaltTheme.configs.isDarkTheme) Color(0xFFFF99A4) else Color(0xFFC42B1C)

/** 行首图标：36dp 浅色圆底 + 20dp accent 图标（v9.2 P2-4）。 */
@Composable
private fun RowLeadingIcon(icon: ImageVector, danger: Boolean = false) {
    val tint = if (danger) dangerColor() else SaltTheme.colors.highlight
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(BbIconSize.Md),
        )
    }
    Spacer(Modifier.width(BbSpacing.Md))
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    icon: ImageVector? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { RowLeadingIcon(it, danger) }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (danger) dangerColor() else SaltTheme.colors.text,
        )
        if (value.isNotBlank()) {
            Text(
                text = value,
                color = SaltTheme.colors.subText,
                style = BbType.Label,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = BbSpacing.Md),
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    icon: ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { RowLeadingIcon(it) }
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let {
                Text(it, color = SaltTheme.colors.subText, style = BbType.Caption)
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
private fun StepRow(
    title: String,
    value: String,
    icon: ImageVector? = null,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { RowLeadingIcon(it) }
        Text(title, modifier = Modifier.weight(1f))
        SaltPill(text = "−", onClick = onMinus)
        Text(value, color = SaltTheme.colors.subText, style = BbType.Label, modifier = Modifier.padding(horizontal = BbSpacing.Md).width(70.dp))
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
                .clip(RoundedCornerShape(BbRadius.Card))
                .background(SaltTheme.colors.popup)
                .border(0.5.dp, SaltTheme.colors.stroke, RoundedCornerShape(BbRadius.Card))
                .padding(BbSpacing.Xl),
        ) {
            Text(title, style = BbType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            options.forEachIndexed { index, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BbRadius.Media))
                        .clickable { onSelect(index) }
                        .padding(horizontal = BbSpacing.Md, vertical = BbSpacing.Md),
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
