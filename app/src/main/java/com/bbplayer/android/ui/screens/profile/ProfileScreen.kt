package com.bbplayer.android.ui.screens.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.model.DailyStat
import com.bbplayer.android.core.model.ListeningSummary
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.LargeTitle
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    val graph = LocalAppGraph.current
    val summary by graph.historyRepository.summary().collectAsStateWithLifecycle(initialValue = ListeningSummary())
    val daily by graph.historyRepository.dailyStats(84).collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item { LargeTitle("我的") }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding)) {
                RoundedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    paddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(SaltTheme.colors.highlight.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Rounded.BarChart, null,
                                tint = SaltTheme.colors.highlight,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text("累计收听", color = SaltTheme.colors.subText, fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                Format.totalListening(summary.totalListenedMs),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("播放次数", color = SaltTheme.colors.subText, fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("${summary.totalPlayCount} 次", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Heatmap(daily)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "最近 12 周听歌记录",
                        color = SaltTheme.colors.subText.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = 12.dp)) {
                RoundedColumn(modifier = Modifier.fillMaxWidth()) {
                    Item(
                        onClick = onOpenSettings,
                        text = "设置",
                        iconPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Settings),
                    )
                    Item(
                        onClick = onOpenSettings,
                        text = "主题与外观",
                        sub = "深浅色 · 动态取色",
                        iconPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Settings),
                    )
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BBPlayer · 安卓原生版", color = SaltTheme.colors.subText, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("数据来自 Bilibili · 界面风格致敬椒盐音乐", color = SaltTheme.colors.subText.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}

/** 12-week x 7-day listening heatmap, Salt Player style: small rounded squares. */
@Composable
private fun Heatmap(daily: List<DailyStat>) {
    val byDate = daily.associateBy { it.date }
    val today = LocalDate.now()
    // Align the grid so the last column ends today (columns = weeks, rows = weekdays).
    val daysBack = 83
    val start = today.minusDays(daysBack.toLong())
    val startOffset = (start.dayOfWeek.value - 1) // Monday = 0
    val cells = mutableListOf<DailyStat?>()
    repeat(startOffset) { cells.add(null) }
    for (i in 0..daysBack) {
        val date = start.plusDays(i.toLong())
        cells.add(byDate[date.toString()] ?: DailyStat(date.toString(), 0, 0))
    }
    while (cells.size % 7 != 0) cells.add(null)
    val maxMs = (cells.filterNotNull().maxOfOrNull { it.listenedMs } ?: 1L).coerceAtLeast(1L)
    val weeks = cells.chunked(7) // one column per week
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (weekday in 0..6) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                weeks.forEach { week ->
                    val day = week.getOrNull(weekday)
                    val alpha = when {
                        day == null -> 0f
                        day.listenedMs <= 0 -> 0.06f
                        else -> 0.25f + 0.75f * (day.listenedMs.toFloat() / maxMs)
                    }
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (day == null) Color.Transparent
                                else SaltTheme.colors.highlight.copy(alpha = alpha)
                            )
                    )
                }
            }
        }
    }
}
