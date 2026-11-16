package com.bbplayer.android.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.DownloadEntry
import com.bbplayer.android.core.model.DownloadStatus
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SectionHeader
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.theme.BbTokens
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import java.util.Locale

/**
 * 下载管理（SPEC C1/C2/C4）：
 * 三段分组（进行中 / 已完成 / 失败），每项展示封面、标题、UP 主、大小与进度；
 * 支持取消、删除、清空已完成、导出到公共目录（导出结果以提示条展示路径）。
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val entries by graph.downloadRepository.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())
    var notice by remember { mutableStateOf<String?>(null) }
    var showClearCompleted by remember { mutableStateOf(false) }

    val active = entries.filter {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED
    }
    val completed = entries.filter { it.status == DownloadStatus.COMPLETED }
    val failed = entries.filter { it.status == DownloadStatus.FAILED }
    val completedTracks = completed.map { it.track }
    val usedBytes = completed.sumOf { it.downloadedBytes.coerceAtLeast(0L) }

    fun export(entry: DownloadEntry) {
        scope.safeLaunch(onError = { notice = it.toUserMessage() }) {
            // 歌词已缓存时一并导出同名 .lrc（SPEC C4）。
            val lrc = runCatching { graph.lyricsRepository.getCached(entry.key)?.plainText }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            graph.downloadRepository.export(entry.key, lrc)
                .onSuccess { path -> notice = "已导出到 $path" }
                .onFailure { notice = it.toUserMessage() }
        }
    }

    fun remove(entry: DownloadEntry) {
        scope.safeLaunch(onError = { notice = it.toUserMessage() }) {
            graph.downloadRepository.remove(entry.key)
                .onSuccess { notice = "已删除「${entry.track.displayTitle}」" }
                .onFailure { notice = it.toUserMessage() }
        }
    }

    fun cancel(entry: DownloadEntry) {
        scope.safeLaunch(onError = { notice = it.toUserMessage() }) {
            graph.downloadRepository.cancel(entry.key)
            notice = "已取消「${entry.track.displayTitle}」"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item {
            SmallTopBar(
                title = "下载管理",
                onBack = onBack,
                actions = {
                    if (completed.isNotEmpty()) {
                        SaltPill(text = "清空已完成", onClick = { showClearCompleted = true })
                        Spacer(Modifier.width(8.dp))
                    }
                },
            )
        }
        if (notice != null) {
            item {
                NoticeBanner(text = notice!!, onDismiss = { notice = null })
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有下载任务",
                    subtitle = "在播放页或歌曲菜单中点击「下载」，离线也能听",
                    icon = Icons.Rounded.Download,
                )
            }
        } else {
            item {
                Text(
                    text = "共 ${entries.size} 项 · 已占用 ${formatBytes(usedBytes)}",
                    color = SaltTheme.colors.subText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(
                        start = SaltTheme.dimens.padding + 4.dp,
                        top = 10.dp,
                    ),
                )
            }
        }

        if (active.isNotEmpty()) {
            item { SectionHeader(title = "进行中", subtitle = "${active.size} 项") }
            itemsIndexed(active, key = { _, entry -> "active-${entry.key}" }) { _, entry ->
                DownloadRow(
                    entry = entry,
                    isCurrent = false,
                    onCancel = { cancel(entry) },
                    onRemove = { remove(entry) },
                    onExport = null,
                )
            }
        }

        if (failed.isNotEmpty()) {
            item { SectionHeader(title = "失败", subtitle = "${failed.size} 项") }
            itemsIndexed(failed, key = { _, entry -> "failed-${entry.key}" }) { _, entry ->
                DownloadRow(
                    entry = entry,
                    isCurrent = false,
                    onCancel = null,
                    onRemove = { remove(entry) },
                    onExport = null,
                )
            }
        }

        if (completed.isNotEmpty()) {
            item { SectionHeader(title = "已完成", subtitle = "${completed.size} 首") }
            itemsIndexed(completed, key = { _, entry -> "done-${entry.key}" }) { index, entry ->
                DownloadRow(
                    entry = entry,
                    isCurrent = playback.track?.key == entry.track.key,
                    onClick = { onPlayTrack(completedTracks, index) },
                    onRemove = { remove(entry) },
                    onExport = { export(entry) },
                )
            }
        }

        if (entries.isNotEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SaltTheme.dimens.padding, vertical = 18.dp),
                ) {
                    Text(
                        text = "下载文件为 B 站音频容器（m4s），导出到公共目录时以 .m4a 命名；离线播放不会发起网络流解析。",
                        color = SaltTheme.colors.subText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }

    if (showClearCompleted) {
        ConfirmDialog(
            title = "清空已完成下载？",
            message = "将删除 ${completed.size} 个已下载文件，此操作不可撤销。",
            confirmText = "清空",
            onDismiss = { showClearCompleted = false },
            onConfirm = {
                showClearCompleted = false
                scope.safeLaunch(onError = { notice = it.toUserMessage() }) {
                    graph.downloadRepository.clearCompleted()
                    notice = "已清空已完成下载"
                }
            },
        )
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    isCurrent: Boolean,
    onClick: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    val textColor = if (isCurrent) SaltTheme.colors.highlight else SaltTheme.colors.text
    val subColor = if (isCurrent) SaltTheme.colors.highlight.copy(alpha = 0.75f) else SaltTheme.colors.subText
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = entry.track.coverUrl, modifier = Modifier.size(48.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.track.displayTitle,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.status.label(),
                    color = if (entry.status == DownloadStatus.FAILED) SaltTheme.colors.highlight else subColor,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(entry.track.uploader.ifBlank { "未知 UP 主" })
                    append(" · ")
                    append(formatBytes(entry.totalBytes.takeIf { it > 0 } ?: entry.downloadedBytes))
                    if (entry.track.durationMs > 0) {
                        append(" · ")
                        append(Format.duration(entry.track.durationMs))
                    }
                },
                color = subColor,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.status == DownloadStatus.DOWNLOADING || entry.status == DownloadStatus.QUEUED || entry.status == DownloadStatus.PAUSED) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(fraction = entry.progress, active = entry.status == DownloadStatus.DOWNLOADING)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatBytes(entry.downloadedBytes)} / ${formatBytes(entry.totalBytes)} · ${(entry.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    color = subColor,
                    fontSize = 11.sp,
                )
            }
            if (entry.status == DownloadStatus.FAILED) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.errorMessage?.takeIf { it.isNotBlank() } ?: "下载失败，请重试",
                    color = SaltTheme.colors.subText,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        if (onExport != null) {
            IconAction(Icons.Rounded.Download, "导出到公共目录", onClick = onExport, tint = SaltTheme.colors.subText, size = 36)
        }
        if (onCancel != null) {
            SaltPill(text = "取消", onClick = onCancel)
        }
        if (onRemove != null) {
            IconAction(Icons.Rounded.Delete, "删除", onClick = onRemove, tint = SaltTheme.colors.subText, size = 36)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, active: Boolean) {
    val value = fraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) SaltTheme.colors.highlight.copy(alpha = 0.15f) else SaltTheme.colors.subBackground),
    ) {
        if (value > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (active) SaltTheme.colors.highlight else SaltTheme.colors.subText.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SaltTheme.colors.highlight.copy(alpha = 0.1f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = SaltTheme.colors.highlight,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text("知道了", color = SaltTheme.colors.subText, fontSize = 11.sp)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
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
            Spacer(Modifier.height(10.dp))
            Text(message, color = SaltTheme.colors.subText, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                SaltPill(text = confirmText, selected = true, onClick = onConfirm)
            }
        }
    }
}

private fun DownloadStatus.label(): String = when (this) {
    DownloadStatus.QUEUED -> "排队中"
    DownloadStatus.DOWNLOADING -> "下载中"
    DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.COMPLETED -> "已完成"
    DownloadStatus.FAILED -> "下载失败"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${value.toInt()} ${units[unit]}"
    else String.format(Locale.US, "%.1f %s", value, units[unit])
}
