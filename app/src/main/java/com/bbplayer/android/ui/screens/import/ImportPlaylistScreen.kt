package com.bbplayer.android.ui.screens.playlistimport

import com.bbplayer.android.ui.theme.BbType
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.contracts.ImportBatchMatcher
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.ExternalPlaylist
import com.bbplayer.android.core.model.ImportBatchResult
import com.bbplayer.android.core.model.ImportMatch
import com.bbplayer.android.core.model.ImportProgress
import com.bbplayer.android.core.model.ImportStatus
import com.bbplayer.android.core.model.ImportTask
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.theme.BbTokens
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlin.math.abs

/**
 * 外部歌单导入（SPEC H1/H2/H3）：
 * 粘贴网易云 / QQ 音乐歌单链接或 ID → 解析歌单信息 → 限速匹配 B 站视频（展示进度与结果）
 * → 未匹配项可手动搜索替换 → 保存为本地歌单。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportPlaylistScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var playlist by remember { mutableStateOf<ExternalPlaylist?>(null) }

    var matching by remember { mutableStateOf(false) }
    var matchError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    var matches by remember { mutableStateOf<List<ImportMatch>>(emptyList()) }
    var matchDone by remember { mutableStateOf(false) }

    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var manualTarget by remember { mutableStateOf<ImportMatch?>(null) }

    // ---- v4: resumable / batched import ----
    var task by remember { mutableStateOf<ImportTask?>(null) }
    var paused by remember { mutableStateOf(false) }
    var blockedUntil by remember { mutableStateOf(0L) }
    var blockedNow by remember { mutableStateOf(false) }
    var batchSize by remember { mutableStateOf(50) }

    LaunchedEffect(Unit) {
        graph.preferencesRepository.importBatchSize.collect { batchSize = it }
    }
    LaunchedEffect(Unit) {
        graph.importTaskRepository.load()?.let { saved ->
            task = saved
            input = saved.input
            playlist = saved.playlist
            matches = saved.matches
            progress = ImportProgress(
                done = saved.matches.size,
                total = saved.total,
                matches = saved.matches,
                finished = saved.isFinished,
            )
            matchDone = saved.status == ImportStatus.DONE
            if (saved.errorMessage != null) notice = saved.errorMessage
        }
    }

    fun persistTask(update: ImportTask) {
        task = update
        matches = update.matches
        progress = ImportProgress(
            done = update.matches.size,
            total = update.total,
            matches = update.matches,
            finished = update.isFinished,
        )
        matchDone = update.status == ImportStatus.DONE
        scope.safeLaunch { graph.importTaskRepository.save(update) }
    }

    fun restartImport() {
        task = null
        matches = emptyList()
        progress = null
        matchDone = false
        matchError = null
        paused = false
        blockedNow = false
        scope.safeLaunch { graph.importTaskRepository.clear() }
    }

    fun resetForNewPlaylist() {
        playlist = null
        matches = emptyList()
        progress = null
        matchDone = false
        matchError = null
        notice = null
    }

    fun parse() {
        val query = input.trim()
        if (query.isEmpty() || parsing) return
        parsing = true
        parseError = null
        resetForNewPlaylist()
        scope.safeLaunch(
            onError = {
                parsing = false
                parseError = it.toUserMessage()
            },
        ) {
            graph.externalPlaylistRepository.fetchPlaylist(query)
                .onSuccess {
                    playlist = it
                    parsing = false
                }
                .onFailure {
                    parseError = it.toUserMessage()
                    parsing = false
                }
        }
    }

    fun startMatch(resume: Boolean = false) {
        val current = playlist ?: return
        if (matching || current.tracks.isEmpty()) return
        val base = if (resume && task != null) task!! else ImportTask(
            input = input.trim(),
            playlist = current,
            matches = emptyList(),
            nextIndex = 0,
            status = ImportStatus.RUNNING,
            updatedAt = System.currentTimeMillis(),
        )
        matching = true
        paused = false
        blockedNow = false
        matchError = null
        matchDone = false
        if (!resume) {
            matches = emptyList()
            progress = ImportProgress(done = 0, total = current.tracks.size)
        }
        task = base
        persistTask(base.copy(status = ImportStatus.RUNNING, errorMessage = null))

        scope.safeLaunch(
            onError = {
                matching = false
                matchError = it.toUserMessage()
            },
        ) {
            val matcher = graph.externalPlaylistRepository as? ImportBatchMatcher
            if (matcher == null) {
                // Fallback: legacy all-at-once matching.
                graph.externalPlaylistRepository.match(current) { p ->
                    progress = p
                    if (p.matches.isNotEmpty()) matches = p.matches
                }.onSuccess { result ->
                    matches = result
                    matchDone = true
                    matching = false
                    persistTask(
                        base.copy(
                            matches = result,
                            nextIndex = current.tracks.size,
                            status = ImportStatus.DONE,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }.onFailure {
                    matching = false
                    matchError = it.toUserMessage()
                }
                return@safeLaunch
            }

            var loopTask = base
            val batch = batchSize.coerceIn(10, 200)
            while (true) {
                if (paused) {
                    persistTask(loopTask.copy(status = ImportStatus.PAUSED, updatedAt = System.currentTimeMillis()))
                    matching = false
                    notice = "已暂停：下次进入会从 ${loopTask.done}/${loopTask.total} 继续"
                    return@safeLaunch
                }
                val result: ImportBatchResult = matcher.matchBatch(loopTask, batch) { p ->
                    progress = p
                    if (p.matches.isNotEmpty()) matches = p.matches
                }
                loopTask = result.task
                persistTask(loopTask)
                if (result.blocked) {
                    blockedNow = true
                    blockedUntil = System.currentTimeMillis() + result.retryAfterMs.coerceAtLeast(30_000L)
                    matching = false
                    notice = result.task.errorMessage
                        ?: "B站触发安全风控，已暂停。请稍后点「继续匹配」，任务进度已保存。"
                    return@safeLaunch
                }
                if (loopTask.status == ImportStatus.DONE || loopTask.isFinished) {
                    matching = false
                    matchDone = true
                    notice = "匹配完成：成功 ${loopTask.matches.count { it.matched != null }} 首"
                    return@safeLaunch
                }
                if (loopTask.status == ImportStatus.FAILED) {
                    matching = false
                    matchError = loopTask.errorMessage ?: "匹配失败，请稍后重试"
                    return@safeLaunch
                }
            }
        }
    }

    fun replaceMatch(index: Int, track: Track) {
        matches = matches.map { if (it.index == index) it.copy(matched = track, score = 1f) else it }
        notice = "已替换为《${track.displayTitle}》"
    }

    fun savePlaylist() {
        val current = playlist ?: return
        val tracks = matches.mapNotNull { it.matched }
        if (tracks.isEmpty()) {
            notice = "没有匹配成功的歌曲，暂时无法保存"
            return
        }
        if (saving) return
        saving = true
        notice = null
        scope.safeLaunch(
            onError = {
                saving = false
                notice = it.toUserMessage()
            },
        ) {
            val name = current.name.ifBlank { "导入歌单" }
            val created = graph.libraryRepository.createPlaylist(name)
            var added = 0
            tracks.forEach { track ->
                if (graph.libraryRepository.addTrack(created.id, track)) added++
            }
            saving = false
            notice = "已保存为本地歌单「${created.name}」，共 $added 首"
            onSaved(created.id)
        }
    }

    val matchedCount = matches.count { it.matched != null }
    val unmatchedCount = matches.count { it.matched == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item { SmallTopBar(title = "导入外部歌单", onBack = onBack) }

        // Sticky action bar: the "save" action must stay reachable when the match list is long.
        val stickyPlaylist = playlist
        if (stickyPlaylist != null && matches.isNotEmpty()) {
            stickyHeader(key = "import-save-bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaltTheme.colors.background)
                        .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stickyPlaylist.name.ifBlank { "导入歌单" },
                            style = BbType.Body,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "已匹配 $matchedCount / ${stickyPlaylist.tracks.size}"
                                + if (matching) " · 匹配中" else "",
                            color = SaltTheme.colors.subText,
                            style = BbType.Micro,
                        )
                    }
                    SaltPill(
                        text = when {
                            saving -> "保存中…"
                            matchedCount == 0 -> "暂无匹配结果"
                            else -> "保存为本地歌单（$matchedCount 首）"
                        },
                        selected = !saving && matchedCount > 0,
                        onClick = { if (!saving && matchedCount > 0) savePlaylist() },
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding)) {
                Text(
                    text = "支持网易云音乐 / QQ 音乐歌单长链接或歌单 ID",
                    color = SaltTheme.colors.subText,
                    style = BbType.Caption,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SaltTheme.colors.subBackground)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = "粘贴歌单链接或 ID，例如 https://music.163.com/playlist?id=…",
                            color = SaltTheme.colors.subText,
                            style = BbType.Label,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = SaltTheme.colors.text, fontSize = BbType.Body.fontSize),
                        cursorBrush = SolidColor(SaltTheme.colors.highlight),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { parse() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SaltPill(
                        text = if (parsing) "解析中…" else "解析",
                        selected = input.isNotBlank() && !parsing,
                        onClick = { parse() },
                    )
                    if (playlist != null) {
                        Spacer(Modifier.width(10.dp))
                        SaltPill(text = "重新解析", onClick = { parse() })
                    }
                }
            }
        }

        if (parseError != null) {
            item {
                InlineError(message = parseError!!, onRetry = { parse() })
            }
        }

        if (parsing) {
            item { LoadingState(text = "正在解析歌单…") }
        }

        val parsed = playlist
        if (parsed != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SaltTheme.dimens.padding, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverImage(
                        url = parsed.coverUrl,
                        modifier = Modifier.size(72.dp),
                        corner = 12.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = parsed.name.ifBlank { "未命名歌单" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = BbType.Section,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "${parsed.source.label} · ${parsed.tracks.size} 首歌曲",
                            color = SaltTheme.colors.subText,
                            style = BbType.Label,
                        )
                        if (parsed.description.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = parsed.description,
                                color = SaltTheme.colors.subText,
                                style = BbType.Micro,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            val savedTask = task
            if (savedTask != null && !matching && (savedTask.done > 0 || savedTask.status == ImportStatus.PAUSED)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SaltTheme.colors.highlight.copy(alpha = 0.09f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已保存进度：${savedTask.done} / ${savedTask.total}"
                                + if (savedTask.status == ImportStatus.PAUSED) "（已暂停）" else "",
                            color = SaltTheme.colors.text,
                            style = BbType.Caption,
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f),
                        )
                        SaltPill(
                            text = if (savedTask.isFinished) "已完成" else "继续匹配",
                            selected = !savedTask.isFinished,
                            onClick = {
                                if (savedTask.isFinished) {
                                    notice = "该任务已匹配完成，可直接保存或「重新匹配」"
                                } else {
                                    startMatch(resume = true)
                                }
                            },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = SaltTheme.dimens.padding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaltPill(
                        text = when {
                            matching -> "暂停匹配"
                            matchDone -> "重新匹配"
                            savedTask != null && savedTask.matches.isNotEmpty() -> "继续匹配"
                            else -> "开始匹配"
                        },
                        selected = !matching,
                        onClick = {
                            when {
                                matching -> paused = true
                                savedTask != null && savedTask.matches.isNotEmpty() && !matchDone ->
                                    startMatch(resume = true)
                                else -> {
                                    restartImport()
                                    startMatch()
                                }
                            }
                        },
                    )
                    if (savedTask != null && !matching) {
                        Spacer(Modifier.width(10.dp))
                        SaltPill(text = "重新开始", onClick = { restartImport(); startMatch() })
                    }
                    Spacer(Modifier.width(10.dp))
                    SaltPill(
                        text = "每批 $batchSize 首",
                        onClick = {
                            scope.safeLaunch {
                                val next = when (batchSize) {
                                    20 -> 50
                                    50 -> 100
                                    100 -> 200
                                    else -> 20
                                }
                                graph.preferencesRepository.setImportBatchSize(next)
                                batchSize = next
                            }
                        },
                    )
                    if (matchDone) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "成功 $matchedCount · 未匹配 $unmatchedCount",
                            color = SaltTheme.colors.subText,
                            style = BbType.Caption,
                        )
                    }
                }
            }

            if (matching) {
                item {
                    val p = progress
                    Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = 12.dp)) {
                        Text(
                            text = if (p != null && p.total > 0) {
                                "正在分批匹配：${p.done} / ${p.total}（每批 $batchSize 首，可随时暂停）"
                            } else {
                                "正在分批匹配…"
                            },
                            color = SaltTheme.colors.subText,
                            style = BbType.Caption,
                        )
                        Spacer(Modifier.height(6.dp))
                        MatchProgressBar(
                            fraction = if (p != null && p.total > 0) p.done.toFloat() / p.total else 0f,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "为降低风控概率，匹配按约 1.2 秒/首限速；暂停/退出后进度会保存，可在下次继续",
                            color = SaltTheme.colors.subText,
                            style = BbType.Micro,
                        )
                    }
                }
            }

            if (matchError != null) {
                item {
                    InlineError(message = matchError!!, onRetry = { startMatch() })
                }
            }

            if (matches.isNotEmpty() && !matching) {
                item {
                    Text(
                        text = "匹配结果",
                        style = BbType.Section,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = SaltTheme.dimens.padding,
                            top = 16.dp,
                            bottom = 4.dp,
                        ),
                    )
                }
                itemsIndexed(matches, key = { _, match -> "match-${match.index}" }) { _, match ->
                    MatchRow(
                        match = match,
                        onManualMatch = { manualTarget = match },
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = 16.dp)) {
                        if (matchDone && matchedCount > 0) {
                            Text(
                                text = "可直接点顶部「保存为本地歌单」保存匹配成功的歌曲。",
                                color = SaltTheme.colors.subText,
                                style = BbType.Micro,
                                lineHeight = 16.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (matchDone && unmatchedCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "还有 $unmatchedCount 首未匹配，可直接保存（仅保存匹配成功的歌曲），或点击「手动匹配」逐首替换。",
                                color = SaltTheme.colors.subText,
                                style = BbType.Micro,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        } else if (!parsing && parseError == null) {
            item {
                EmptyState(
                    title = "导入一个外部歌单",
                    subtitle = "解析后会把每首歌逐个匹配到 B 站视频，并保存为本地歌单",
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                )
            }
        }

        if (notice != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SaltTheme.dimens.padding, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SaltTheme.colors.highlight.copy(alpha = 0.1f))
                        .clickable { notice = null }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notice!!,
                        color = SaltTheme.colors.highlight,
                        style = BbType.Caption,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("知道了", color = SaltTheme.colors.subText, style = BbType.Micro)
                }
            }
        }
    }

    val target = manualTarget
    if (target != null) {
        ManualMatchDialog(
            match = target,
            onDismiss = { manualTarget = null },
            onPicked = { track ->
                replaceMatch(target.index, track)
                manualTarget = null
            },
        )
    }
}

@Composable
private fun MatchRow(match: ImportMatch, onManualMatch: () -> Unit) {
    val matched = match.matched
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (matched != null) {
            CoverImage(url = matched.coverUrl, modifier = Modifier.size(44.dp), corner = 8.dp)
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SaltTheme.colors.subBackground),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.CloudOff,
                    null,
                    tint = SaltTheme.colors.subText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = match.external.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = BbType.Body,
            )
            Spacer(Modifier.height(2.dp))
            if (matched != null) {
                Text(
                    text = "→ ${matched.displayTitle}",
                    color = SaltTheme.colors.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = BbType.Caption,
                )
                val diffMs = matched.durationMs - match.external.durationMs
                if (matched.durationMs > 0 && match.external.durationMs > 0 && abs(diffMs) >= 5_000) {
                    Text(
                        text = "时长差 ${if (diffMs > 0) "+" else "−"}${Format.duration(abs(diffMs))}",
                        color = SaltTheme.colors.subText,
                        style = BbType.MicroTnum,
                    )
                }
            } else {
                Text(
                    text = "未匹配 · ${match.external.artist}",
                    color = SaltTheme.colors.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = BbType.Caption,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (matched == null) {
            SaltPill(text = "手动匹配", onClick = onManualMatch)
        } else {
            Text(
                text = "${(match.score * 100).toInt()}%",
                color = SaltTheme.colors.subText,
                style = BbType.Micro,
            )
        }
    }
}

@Composable
private fun MatchProgressBar(fraction: Float) {
    val value = fraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(SaltTheme.colors.subBackground),
    ) {
        if (value > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(SaltTheme.colors.highlight),
            )
        }
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = SaltTheme.colors.subText,
            style = BbType.Caption,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        SaltPill(text = "重试", onClick = onRetry)
    }
}

/**
 * 手动匹配对话框：输入关键词 → musicRepository.search → 默认使用第一条结果。
 */
@Composable
private fun ManualMatchDialog(
    match: ImportMatch,
    onDismiss: () -> Unit,
    onPicked: (Track) -> Unit,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    var keyword by remember {
        mutableStateOf(
            listOf(match.external.title, match.external.artist)
                .filter { it.isNotBlank() }
                .joinToString(" "),
        )
    }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }

    fun search() {
        val query = keyword.trim()
        if (query.isEmpty() || searching) return
        searching = true
        error = null
        results = emptyList()
        scope.safeLaunch(
            onError = {
                searching = false
                error = it.toUserMessage()
            },
        ) {
            graph.musicRepository.search(query)
                .onSuccess {
                    results = it.items.take(10)
                    searching = false
                }
                .onFailure {
                    error = it.toUserMessage()
                    searching = false
                }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SaltTheme.colors.popup)
                .padding(20.dp),
        ) {
            Text("手动匹配", style = BbType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "原曲：${match.external.title} · ${match.external.artist}",
                color = SaltTheme.colors.subText,
                style = BbType.Caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SaltTheme.colors.subBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (keyword.isEmpty()) {
                    Text("输入歌曲关键词", color = SaltTheme.colors.subText, style = BbType.Body)
                }
                BasicTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    textStyle = TextStyle(color = SaltTheme.colors.text, fontSize = BbType.Body.fontSize),
                    cursorBrush = SolidColor(SaltTheme.colors.highlight),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                SaltPill(
                    text = if (searching) "搜索中…" else "搜索",
                    selected = keyword.isNotBlank() && !searching,
                    onClick = { search() },
                )
            }

            when {
                searching -> LoadingState(text = "搜索中…")
                error != null -> Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(error!!, color = SaltTheme.colors.subText, style = BbType.Caption)
                    Spacer(Modifier.height(8.dp))
                    SaltPill(text = "重试", onClick = { search() })
                }
                results.isNotEmpty() -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "搜索结果（默认使用第一条）",
                        color = SaltTheme.colors.subText,
                        style = BbType.Micro,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        results.forEachIndexed { index, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPicked(track) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoverImage(
                                    url = track.coverUrl,
                                    modifier = Modifier.size(36.dp),
                                    corner = 7.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = track.displayTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = BbType.Label,
                                    )
                                    Text(
                                        text = if (index == 0) "默认匹配 · ${track.uploader}" else track.uploader,
                                        color = if (index == 0) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                                        style = BbType.Micro,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SaltPill(
                        text = "使用第一条：${results.first().displayTitle.take(18)}",
                        selected = true,
                        onClick = { onPicked(results.first()) },
                    )
                }
            }
        }
    }
}
