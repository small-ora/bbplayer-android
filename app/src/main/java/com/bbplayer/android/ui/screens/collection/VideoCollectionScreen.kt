package com.bbplayer.android.ui.screens.collection

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.model.VideoCollection
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 搜索结果里点开的「合集歌单页」（匿名解析，无需登录）。
 *
 * 数据来自 [com.bbplayer.android.core.contracts.MusicRepository.getVideoCollection]：
 * - 多 P 视频 / B 站 season 合集 → 曲目列表，可「播放全部」或「添加到歌单」；
 * - 单视频 → `success(null)`，显示「这个视频不是合集」并兜底按单曲播放；
 * - 请求失败 → 错误态 + 重试。
 *
 * 状态机：loading → failed / notCollection / loaded。
 */
@Composable
fun VideoCollectionScreen(
    bvid: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())

    var loadState by remember { mutableStateOf<CollectionLoadState>(CollectionLoadState.Loading) }
    // 重试计数：ErrorState 的「重试」只改它，由 LaunchedEffect 重新拉取。
    var retryNonce by remember { mutableStateOf(0) }

    LaunchedEffect(bvid, retryNonce) {
        loadState = CollectionLoadState.Loading
        if (bvid.isBlank()) {
            loadState = CollectionLoadState.NotCollection
            return@LaunchedEffect
        }
        graph.musicRepository.getVideoCollection(bvid).fold(
            onSuccess = { collection ->
                loadState = if (collection != null && collection.isCollection) {
                    CollectionLoadState.Loaded(collection)
                } else {
                    // 单视频 / 只有 1 首：不崩溃，按单曲兜底。
                    CollectionLoadState.NotCollection
                }
            },
            onFailure = { loadState = CollectionLoadState.Failed(it.toUserMessage()) },
        )
    }

    // 「添加到歌单」进度：保存中禁用按钮并显示 N/M。
    var saving by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf(0) }

    fun saveToPlaylist(collection: VideoCollection) {
        if (saving) return
        saving = true
        savedCount = 0
        scope.safeLaunch(
            onError = { error ->
                saving = false
                Toast.makeText(context, "保存失败：${error.toUserMessage()}", Toast.LENGTH_SHORT).show()
            },
        ) {
            val name = collection.title.trim().ifBlank { "合集" }
            val playlist = graph.libraryRepository.createPlaylist(name)
            // 开始落库后不再响应取消：即使立刻返回上一页也把整张歌单写完，避免半截歌单。
            withContext(NonCancellable) {
                collection.tracks.forEachIndexed { index, track ->
                    graph.libraryRepository.addTrack(playlist.id, track)
                    savedCount = index + 1
                }
            }
            saving = false
            Toast.makeText(
                context,
                "已创建歌单「${playlist.name}」（${collection.size} 首）",
                Toast.LENGTH_SHORT,
            ).show()
            onOpenPlaylist(playlist.id)
        }
    }

    // 「不是合集」兜底：按单曲解析该视频并播放。
    var playingSingle by remember { mutableStateOf(false) }
    fun playAsSingle() {
        if (playingSingle) return
        playingSingle = true
        scope.safeLaunch(
            onError = {
                playingSingle = false
                Toast.makeText(context, "视频解析失败，请稍后重试", Toast.LENGTH_SHORT).show()
            },
        ) {
            graph.musicRepository.getVideoDetail(bvid)
                .onSuccess { track -> onPlayTrack(listOf(track), 0) }
                .onFailure {
                    Toast.makeText(context, "视频解析失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            playingSingle = false
        }
    }

    when (val state = loadState) {
        CollectionLoadState.Loading -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SmallTopBar(title = "合集", onBack = onBack)
            LoadingState()
        }

        is CollectionLoadState.Failed -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SmallTopBar(title = "合集", onBack = onBack)
            ErrorState(message = state.message, onRetry = { retryNonce++ })
        }

        CollectionLoadState.NotCollection -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SmallTopBar(title = "合集", onBack = onBack)
            EmptyState(
                title = "这个视频不是合集",
                subtitle = "可以按普通单曲播放",
                action = if (playingSingle) "正在解析…" else "播放该视频",
                onAction = if (playingSingle) null else ::playAsSingle,
            )
        }

        is CollectionLoadState.Loaded -> CollectionContent(
            collection = state.collection,
            playback = playback,
            saving = saving,
            savedCount = savedCount,
            onBack = onBack,
            onPlayTrack = onPlayTrack,
            onSave = { saveToPlaylist(state.collection) },
            onRetry = { retryNonce++ },
        )
    }
}

@Composable
private fun CollectionContent(
    collection: VideoCollection,
    playback: PlaybackState,
    saving: Boolean,
    savedCount: Int,
    onBack: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    val tracks = collection.tracks
    val subtitle = buildString {
        append("${collection.size} 首歌曲")
        if (collection.uploader.isNotBlank()) append(" · ${collection.uploader}")
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BbTokens.SecondaryContentBottomPadding),
    ) {
        item { SmallTopBar(title = "合集", onBack = onBack) }
        item {
            Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Sm)) {
                Text(
                    text = collection.title,
                    style = BbType.Display.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(BbSpacing.Sm))
                Text(
                    text = subtitle,
                    color = SaltTheme.colors.subText,
                    style = BbType.Label,
                )
                // season 兜底分页失败时曲目可能不完整：弱提示 + 重试入口（SPEC §6 mitigation）。
                if (collection.partial) {
                    Spacer(Modifier.height(BbSpacing.Sm))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BbSpacing.Sm),
                    ) {
                        Text(
                            text = "部分集数加载失败，可能不完整",
                            color = SaltTheme.colors.subText,
                            style = BbType.Label,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        SaltPill(text = "重试", onClick = onRetry)
                    }
                }
                Spacer(Modifier.height(BbSpacing.Md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BbSpacing.Sm),
                ) {
                    SaltPill(text = "播放全部", selected = true, onClick = { onPlayTrack(tracks, 0) })
                    SaltPill(
                        text = if (saving) "正在保存 $savedCount/${tracks.size}" else "添加到歌单",
                        selected = false,
                        onClick = if (saving) null else onSave,
                    )
                }
            }
        }
        items(count = tracks.size, key = { idx -> tracks[idx].key }) { idx ->
            val track = tracks[idx]
            TrackRow(
                track = track,
                modifier = Modifier.animateItem(),
                isCurrent = playback.track?.key == track.key,
                onClick = { onPlayTrack(tracks, idx) },
            )
        }
    }
}

/** 合集页加载状态。 */
private sealed interface CollectionLoadState {
    data object Loading : CollectionLoadState
    data class Failed(val message: String) : CollectionLoadState
    data object NotCollection : CollectionLoadState
    data class Loaded(val collection: VideoCollection) : CollectionLoadState
}
