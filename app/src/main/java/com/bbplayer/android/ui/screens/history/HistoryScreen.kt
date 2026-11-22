package com.bbplayer.android.ui.screens.history

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.DailyHistory
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.theme.BbRadius
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 历史页（SPEC D4）：按日分组展示收听记录，每日显示日期、总时长与条目列表；
 * 顶部「清空历史」需二次确认。
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val days by graph.historyRepository.historyByDay(30)
        .collectAsStateWithLifecycle(initialValue = emptyList<DailyHistory>())
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())
    var showClearConfirm by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item {
            SmallTopBar(
                title = "收听历史",
                onBack = onBack,
                actions = {
                    if (days.isNotEmpty()) {
                        SaltPill(text = "清空历史", onClick = { showClearConfirm = true })
                        Spacer(Modifier.width(8.dp))
                    }
                },
            )
        }

        if (notice != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Sm)
                        .clip(RoundedCornerShape(BbRadius.Media))
                        .background(SaltTheme.colors.highlight.copy(alpha = 0.1f))
                        .clickable { notice = null }
                        .padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notice!!,
                        color = SaltTheme.colors.highlight,
                        style = BbType.Caption,
                        modifier = Modifier.weight(1f),
                    )
                    Text("知道了", color = SaltTheme.colors.subText, style = BbType.Micro)
                }
            }
        }

        if (days.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有收听记录",
                    subtitle = "播放过的歌曲会按天记录在这里",
                    icon = Icons.Rounded.History,
                )
            }
        } else {
            days.forEach { day ->
                item(key = "day-${day.date}") {
                    DayHeader(day = day)
                }
                itemsIndexed(
                    items = day.entries,
                    key = { index, entry -> "${day.date}-${entry.track.key}-${entry.playedAt}-$index" },
                ) { index, entry ->
                    TrackRow(
                        track = entry.track,
                        isCurrent = playback.track?.key == entry.track.key,
                        onClick = { onPlayTrack(day.entries.map { it.track }, index) },
                    )
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Lg),
                ) {
                    Text(
                        text = "只展示最近 30 天的记录；清空历史不会删除歌单与收藏。",
                        color = SaltTheme.colors.subText,
                        style = BbType.Micro,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        Dialog(onDismissRequest = { showClearConfirm = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BbRadius.Card))
                    .background(SaltTheme.colors.popup)
                    .padding(BbSpacing.Xl),
            ) {
                Text("清空收听历史？", style = BbType.Section, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "将删除全部收听记录与每日统计，此操作不可撤销。",
                    color = SaltTheme.colors.subText,
                    style = BbType.Label,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SaltPill(text = "取消", onClick = { showClearConfirm = false })
                    Spacer(Modifier.width(10.dp))
                    SaltPill(
                        text = "清空",
                        selected = true,
                        onClick = {
                            showClearConfirm = false
                            scope.safeLaunch(onError = { notice = it.toUserMessage() }) {
                                graph.historyRepository.clearHistory()
                                notice = "收听历史已清空"
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: DailyHistory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SaltTheme.dimens.padding,
                end = SaltTheme.dimens.padding,
                top = BbSpacing.Xl,
                bottom = BbSpacing.Sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayLabel(day.date),
            style = BbType.Section,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${Format.totalListening(day.listenedMs)} · ${day.entries.size} 首",
            color = SaltTheme.colors.subText,
            style = BbType.CaptionTnum,
        )
    }
}

private fun dayLabel(date: String): String {
    if (date.isBlank()) return "未知日期"
    return runCatching {
        val parsed = LocalDate.parse(date)
        val today = LocalDate.now()
        when {
            parsed == today -> "今天"
            parsed == today.minusDays(1) -> "昨天"
            parsed.year == today.year -> parsed.format(
                DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA),
            )
            else -> parsed.format(
                DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA),
            )
        }
    }.getOrElse { date }
}
