package com.bbplayer.android.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WatchLater
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
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SkeletonTrackList
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.theme.BbRadius
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.bbplayer.android.util.BiliIds
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** B 站「稍后再看」：列表、播放、逐条移除与清空。 */
@Composable
fun WatchLaterScreen(
    onBack: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenPlayer: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by graph.authRepository.session().collectAsStateWithLifecycle(initialValue = null as BiliUser?)
    val loggedIn = session != null
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())
    var items by remember { mutableStateOf<List<Track>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var nonce by remember { mutableStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn, nonce) {
        if (!loggedIn) {
            items = null
            error = null
            return@LaunchedEffect
        }
        items = null
        error = null
        graph.watchLaterRepository.list()
            .onSuccess { items = it }
            .onFailure { error = it.toUserMessage() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item {
            SmallTopBar(
                title = "稍后再看",
                onBack = onBack,
                actions = {
                    val list = items
                    if (loggedIn && !list.isNullOrEmpty()) {
                        SaltPill(text = "清空", onClick = { showClearConfirm = true })
                        Spacer(Modifier.width(8.dp))
                    }
                },
            )
        }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Sm)) {
                Text("稍后再看", style = BbType.Title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (loggedIn) "${items?.size ?: 0} 个视频" else "登录后查看",
                        color = SaltTheme.colors.subText,
                        style = BbType.Label,
                    )
                    Spacer(Modifier.width(12.dp))
                    val list = items
                    if (loggedIn && !list.isNullOrEmpty()) {
                        SaltPill(text = "播放全部", selected = true, onClick = {
                            onPlayTrack(list, 0)
                        })
                    }
                }
            }
        }
        when {
            !loggedIn -> item {
                EmptyState(
                    title = "登录后查看稍后再看",
                    subtitle = "登录 Bilibili 账号后即可同步稍后再看列表",
                    icon = Icons.Rounded.WatchLater,
                    action = "登录 Bilibili",
                    onAction = onOpenLogin,
                )
            }
            error != null -> item {
                ErrorState(message = error!!, onRetry = { nonce++ })
            }
            items == null -> item { SkeletonTrackList(rows = 6) }
            items.isNullOrEmpty() -> item {
                EmptyState(
                    title = "稍后再看是空的",
                    subtitle = "在播放页「更多」菜单中把视频加入稍后再看",
                    icon = Icons.Rounded.WatchLater,
                )
            }
            else -> {
                val list = items!!
                items(count = list.size, key = { idx -> list[idx].key }) { idx ->
                    val track = list[idx]
                    TrackRow(
                        track = track,
                        isCurrent = playback.track?.key == track.key,
                        onClick = { onPlayTrack(list, idx) },
                        trailing = {
                            SaltPill(text = "移除", onClick = {
                                val aid = BiliIds.aidOf(track.bvid)
                                if (aid == null) {
                                    Toast.makeText(context, "无法解析该视频的 aid", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.safeLaunch(onError = {
                                        Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show()
                                    }) {
                                        graph.watchLaterRepository.remove(aid)
                                        items = items?.filterNot { it.key == track.key }
                                        Toast.makeText(context, "已从稍后再看移除", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            })
                        },
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
                    .border(0.5.dp, SaltTheme.colors.stroke, RoundedCornerShape(BbRadius.Card))
                    .padding(BbSpacing.Xl),
            ) {
                Text("清空稍后再看？", style = BbType.Section, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "将删除 B 站账号下的全部稍后再看条目，此操作不可撤销。",
                    color = SaltTheme.colors.subText,
                    style = BbType.Label,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SaltPill(text = "取消", onClick = { showClearConfirm = false })
                    Spacer(Modifier.width(10.dp))
                    SaltPill(text = "清空", selected = true, onClick = {
                        showClearConfirm = false
                        scope.safeLaunch(onError = {
                            Toast.makeText(context, it.toUserMessage(), Toast.LENGTH_SHORT).show()
                        }) {
                            graph.watchLaterRepository.clear()
                            items = emptyList()
                            Toast.makeText(context, "已清空稍后再看", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }
}
