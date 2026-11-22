package com.bbplayer.android.ui.screens.profile

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.Format
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.DailyStat
import com.bbplayer.android.core.model.ListeningSummary
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.LargeTitle
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbRadius
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.bbplayer.android.ui.theme.bbRootContentBottomPadding
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import java.time.LocalDate

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenLeaderboard: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val summary by graph.historyRepository.summary().collectAsStateWithLifecycle(initialValue = ListeningSummary())
    val daily by graph.historyRepository.dailyStats(84).collectAsStateWithLifecycle(initialValue = emptyList())
    val session by graph.authRepository.session().collectAsStateWithLifecycle(initialValue = null as BiliUser?)
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().clipToBounds(),
        contentPadding = PaddingValues(bottom = bbRootContentBottomPadding()),
    ) {
        item {
            LargeTitle(
                title = "我的",
                actions = {
                    IconAction(Icons.Rounded.Settings, "设置", onOpenSettings)
                },
            )
        }
        item {
            AccountCard(
                user = session,
                onOpenLogin = onOpenLogin,
                onLogout = { showLogoutConfirm = true },
            )
        }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Xs)) {
                DashboardCard(summary = summary, daily = daily)
            }
        }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Md)) {
                RoundedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    paddingValues = PaddingValues(0.dp),
                ) {
                    ProfileEntryRow(
                        icon = Icons.Rounded.BarChart,
                        title = "收听排行",
                        subtitle = "B 站音乐榜 · 我的收听排行",
                        onClick = onOpenLeaderboard,
                    )
                    ProfileEntryRow(
                        icon = Icons.Rounded.History,
                        title = "播放历史",
                        subtitle = "按日查看听歌记录",
                        onClick = onOpenHistory,
                    )
                    ProfileEntryRow(
                        icon = Icons.Rounded.Download,
                        title = "已下载",
                        subtitle = "离线歌曲与导出",
                        onClick = onOpenDownloads,
                    )
                    ProfileEntryRow(
                        icon = Icons.Rounded.PlaylistAdd,
                        title = "导入歌单",
                        subtitle = "网易云 / QQ 音乐 → B 站",
                        onClick = onOpenImport,
                    )
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(BbSpacing.Xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BBPlayer · 安卓原生版", color = SaltTheme.colors.subText, style = BbType.Caption)
                Spacer(Modifier.height(4.dp))
                Text("数据来自 Bilibili · 界面风格致敬椒盐音乐", color = SaltTheme.colors.subText, style = BbType.Micro)
            }
        }
    }

    if (showLogoutConfirm) {
        Dialog(onDismissRequest = { showLogoutConfirm = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BbRadius.Card))
                    .background(SaltTheme.colors.popup)
                    .border(0.5.dp, SaltTheme.colors.stroke, RoundedCornerShape(BbRadius.Card))
                    .padding(BbSpacing.Xl),
            ) {
                Text("退出登录？", style = BbType.Section, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "退出后将清除本机保存的加密凭据，收藏夹、合集与稍后再看需要重新登录才能查看。",
                    color = SaltTheme.colors.subText,
                    style = BbType.Label,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SaltPill(text = "取消", onClick = { showLogoutConfirm = false })
                    Spacer(Modifier.size(10.dp))
                    SaltPill(text = "退出登录", selected = true, onClick = {
                        showLogoutConfirm = false
                        scope.safeLaunch(onError = { Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show() }) {
                            graph.authRepository.logout()
                            Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }
}

/**
 * 仪表板卡片：左侧累计指标，右侧 12 周紧凑点阵热力图（AC9）。
 *
 * 12 周 × 7 天、cell 9dp、gap 2dp → 宽 130dp，右对齐垂直居中，避免右侧大片留白。
 */
@Composable
private fun DashboardCard(summary: ListeningSummary, daily: List<DailyStat>) {
    RoundedColumn(
        modifier = Modifier.fillMaxWidth(),
        paddingValues = PaddingValues(0.dp),
    ) {
        Column(Modifier.padding(BbSpacing.Lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("累计收听", color = SaltTheme.colors.subText, style = BbType.Caption)
                Spacer(Modifier.height(4.dp))
                Text(
                    Format.totalListening(summary.totalListenedMs),
                    style = BbType.TitleTnum,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "播放次数 ${summary.totalPlayCount} 次",
                    color = SaltTheme.colors.subText,
                    style = BbType.CaptionTnum,
                )
            }
            Spacer(Modifier.width(16.dp))
            Heatmap(daily)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "最近 12 周听歌记录",
            color = SaltTheme.colors.subText,
            style = BbType.Caption,
        )
        }
    }
}

/** 12 周 × 7 天的紧凑点阵热力图，点阵整体 130dp 宽，右对齐。 */
@Composable
private fun Heatmap(daily: List<DailyStat>) {
    val byDate = daily.associateBy { it.date }
    val today = LocalDate.now()
    // 以「本周一」为最后一列，向前取满 12 列；每列 7 天（周一 → 周日）。
    val currentMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val startMonday = currentMonday.minusWeeks(11)
    val weeks = (0 until 12).map { week ->
        (0 until 7).map { weekday ->
            val date = startMonday.plusDays((week * 7 + weekday).toLong())
            when {
                date.isAfter(today) -> null
                else -> byDate[date.toString()] ?: DailyStat(date.toString(), 0, 0)
            }
        }
    }
    val maxMs = (weeks.flatten().filterNotNull().maxOfOrNull { it.listenedMs } ?: 1L).coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (weekday in 0..6) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                weeks.forEach { week ->
                    val day = week.getOrNull(weekday)
                    val alpha = when {
                        day == null -> 0f
                        day.listenedMs <= 0 -> 0.06f
                        else -> 0.25f + 0.75f * (day.listenedMs.toFloat() / maxMs)
                    }
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.dp))
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

/** 统一的功能入口行：40dp 圆形浅色底托 + 22dp 图标 + 标题/副标题 + ChevronRight（AC9）。 */
@Composable
private fun ProfileEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SaltTheme.colors.highlight.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = SaltTheme.colors.highlight,
                modifier = Modifier.size(BbIconSize.Md),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = BbType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = SaltTheme.colors.subText,
                style = BbType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SaltTheme.colors.subText,
            modifier = Modifier.size(BbIconSize.Md),
        )
    }
}

@Composable
private fun AccountCard(user: BiliUser?, onOpenLogin: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Xs)) {
        RoundedColumn(
            modifier = Modifier.fillMaxWidth(),
            paddingValues = PaddingValues(0.dp),
        ) {
          Column(Modifier.padding(horizontal = BbSpacing.Lg, vertical = BbSpacing.Lg)) {
            if (user == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(BbRadius.FullShape)
                            .background(SaltTheme.colors.subBackground)
                            .border(0.5.dp, SaltTheme.colors.stroke, BbRadius.FullShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Person, null,
                            tint = SaltTheme.colors.subText,
                            modifier = Modifier.size(BbIconSize.Lg),
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("未登录", style = BbType.Section, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "登录后同步收藏夹、合集、稍后再看与高音质",
                            color = SaltTheme.colors.subText,
                            style = BbType.Caption,
                            lineHeight = 16.sp,
                        )
                    }
                    SaltPill(text = "登录 Bilibili", selected = true, onClick = onOpenLogin)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(
                        url = user.avatarUrl,
                        modifier = Modifier.size(52.dp),
                        corner = 26.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                user.name,
                                style = BbType.Section,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.size(6.dp))
                            SaltPill(text = "Lv.${user.level}")
                            if (user.vip) {
                                Spacer(Modifier.size(6.dp))
                                SaltPill(text = "大会员")
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "UID ${user.mid}",
                            color = SaltTheme.colors.subText,
                            style = BbType.Micro,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    SaltPill(text = "退出登录", onClick = onLogout)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "凭据仅保存在本机 Keystore 加密存储，界面不会显示 Cookie。",
                color = SaltTheme.colors.subText,
                style = BbType.Micro,
                lineHeight = 16.sp,
            )
          }
        }
    }
}
