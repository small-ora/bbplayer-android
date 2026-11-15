package com.bbplayer.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.Lyrics
import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.AppGraph
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.CoverImage
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.IconAction
import com.bbplayer.android.ui.components.SeekLine
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.theme.rememberAlbumWash
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val state by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = PlaybackState())
    val track = state.track

    if (track == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
                .statusBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Rounded.KeyboardArrowDown, "返回", onClick = onBack)
            }
            EmptyState(title = "还没有正在播放的歌曲", subtitle = "去首页挑一首吧")
        }
        return
    }

    val wash = rememberAlbumWash(track.coverUrl)
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val sheet = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val prefs = graph.preferencesRepository
    val quality by prefs.audioQuality.collectAsStateWithLifecycle(initialValue = AudioQuality.Q192)
    val fontScale by prefs.lyricsFontScale.collectAsStateWithLifecycle(initialValue = 1f)
    val prefsOffset by prefs.lyricsOffsetMs.collectAsStateWithLifecycle(initialValue = 0)

    var lyrics by remember(track.key) { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading by remember(track.key) { mutableStateOf(true) }
    var lyricsError by remember(track.key) { mutableStateOf<String?>(null) }
    var lyricsNonce by remember(track.key) { mutableStateOf(0) }
    LaunchedEffect(track.key, lyricsNonce) {
        lyricsLoading = true
        lyricsError = null
        runCatching {
            graph.lyricsRepository.getCached(track.key)
                ?: graph.lyricsRepository.fetch(track).getOrElse { throw it }
        }.onSuccess {
            lyrics = it
        }.onFailure {
            lyricsError = it.toUserMessage()
        }
        lyricsLoading = false
    }

    var isFavorite by remember(track.key) { mutableStateOf(false) }
    LaunchedEffect(track.key) { isFavorite = graph.libraryRepository.isFavorite(track.key) }

    val effectiveLyrics = remember(lyrics, prefsOffset) {
        lyrics?.copy(offsetMs = (lyrics?.offsetMs ?: 0) + prefsOffset)
    }

    Box(Modifier.fillMaxSize()) {
        // ---- background: blurred artwork wash + scrim (Salt Player treatment) ----
        val base = wash.firstOrNull() ?: SaltTheme.colors.highlight
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            base.copy(alpha = 0.95f),
                            base.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.9f),
                        )
                    )
                )
        )
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(90.dp)
                .background(Color.Black.copy(alpha = 0.25f)),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            // ---- top bar ----
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    Icons.Rounded.KeyboardArrowDown, "收起", onClick = onBack,
                    tint = Color.White, size = 40,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("正在播放", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        Text(
                            track.uploader,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconAction(
                    Icons.Rounded.MoreHoriz, "更多", onClick = { sheet.value = "more" },
                    tint = Color.White, size = 40,
                )
            }

            AnimatedContent(targetState = showLyrics, label = "playerMode") { lyricsMode ->
                if (lyricsMode) {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(8.dp))
                        when {
                            lyricsLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LoadingState(text = "正在获取歌词…")
                            }
                            lyricsError != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = lyricsError!!,
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 14.sp,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    SaltPill(text = "重试", onClick = { lyricsNonce++ })
                                    Spacer(Modifier.height(8.dp))
                                    SaltPill(text = "手动搜索", onClick = { sheet.value = "searchLyrics" })
                                }
                            }
                            else -> LyricsPanel(
                                lyrics = effectiveLyrics,
                                positionMs = state.positionMs,
                                fontScale = fontScale,
                                modifier = Modifier.weight(1f),
                                onSeekTo = { graph.playbackController.seekTo(it.coerceAtLeast(0)) },
                            )
                        }
                        // offset controls + compact controls
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SaltPill(text = "−0.1s", onClick = {
                                scope.safeLaunch { prefs.setLyricsOffsetMs((prefsOffset - 100).coerceIn(-3000, 3000)) }
                            })
                            Text(
                                text = "偏移 $prefsOffset ms",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                            SaltPill(text = "+0.1s", onClick = {
                                scope.safeLaunch { prefs.setLyricsOffsetMs((prefsOffset + 100).coerceIn(-3000, 3000)) }
                            })
                        }
                        MiniTransport(state = state, onPrev = { graph.playbackController.previous() }, onToggle = { graph.playbackController.playPause() }, onNext = { graph.playbackController.next() }, white = true)
                        Spacer(Modifier.height(6.dp))
                    }
                } else {
                    MainPlayerContent(
                        state = state,
                        track = track,
                        quality = quality,
                        isFavorite = isFavorite,
                        onToggleFavorite = {
                            scope.safeLaunch { isFavorite = graph.libraryRepository.toggleFavorite(track) }
                        },
                        onSeek = { graph.playbackController.seekTo(it) },
                        onToggleLyrics = { showLyrics = true },
                        onOpenQueue = { showQueue = true },
                        onShowSheet = { sheet.value = it },
                        onModeClick = {
                            graph.playbackController.setPlayMode(state.playMode.next())
                        },
                    )
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.currentIndex,
            onDismiss = { showQueue = false },
            onJumpTo = { graph.playbackController.jumpTo(it); showQueue = false },
            onRemove = { graph.playbackController.removeFromQueue(it) },
            onClear = { graph.playbackController.clearQueue(); showQueue = false },
        )
    }

    when (sheet.value) {
        "speed" -> OptionDialog(
            title = "播放速度",
            options = listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x"),
            selectedIndex = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).indexOfFirst { it == state.speed }.coerceAtLeast(2),
            onSelect = {
                graph.playbackController.setSpeed(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)[it])
                sheet.value = null
            },
            onDismiss = { sheet.value = null },
            dark = true,
        )
        "timer" -> OptionDialog(
            title = "定时关闭",
            options = listOf("关闭", "15 分钟", "30 分钟", "45 分钟", "60 分钟"),
            selectedIndex = 0,
            onSelect = {
                graph.playbackController.setSleepTimer(if (it == 0) null else listOf(0, 15, 30, 45, 60)[it])
                sheet.value = null
            },
            onDismiss = { sheet.value = null },
            dark = true,
        )
        "more" -> OptionDialog(
            title = "更多",
            options = listOf("添加到歌单", "搜索歌词", "粘贴 LRC 歌词", "重新加载音频"),
            selectedIndex = -1,
            onSelect = { idx ->
                sheet.value = null
                when (idx) {
                    0 -> sheet.value = "playlist"
                    1 -> sheet.value = "searchLyrics"
                    2 -> sheet.value = "pasteLyrics"
                    3 -> scope.launch { graph.playbackController.playQueue(state.queue, state.currentIndex) }
                }
            },
            onDismiss = { sheet.value = null },
            dark = true,
        )
        "playlist" -> AddToPlaylistDialog(graph = graph, track = track, onDismiss = { sheet.value = null })
        "searchLyrics" -> LyricsSearchDialog(
            graph = graph,
            track = track,
            onDismiss = { sheet.value = null },
            onPicked = { picked ->
                lyrics = picked
                sheet.value = null
            },
        )
        "pasteLyrics" -> PasteLyricsDialog(
            onDismiss = { sheet.value = null },
            onSave = { text ->
                scope.safeLaunch {
                    graph.lyricsRepository.saveManual(track.key, text).getOrNull()?.let { lyrics = it }
                    sheet.value = null
                }
            },
        )
    }
}

@Composable
private fun MainPlayerContent(
    state: PlaybackState,
    track: Track,
    quality: AudioQuality,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onShowSheet: (String) -> Unit,
    onModeClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val coverSize = animateFloatAsState(if (state.isPlaying) 1f else 0.94f, tween(400), label = "coverScale")
            CoverImage(
                url = track.coverUrl,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1f)
                    .androidxScale(coverSize.value),
                corner = 20.dp,
            )
        }
        // title + uploader + favourite
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.displayTitle,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.uploader,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    QualityBadge(quality)
                }
            }
            IconAction(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "收藏",
                onClick = onToggleFavorite,
                tint = if (isFavorite) Color(0xFFFF6B81) else Color.White.copy(alpha = 0.85f),
                size = 40,
            )
        }
        Spacer(Modifier.height(14.dp))
        SeekLine(
            positionMs = state.positionMs,
            durationMs = state.durationMs.coerceAtLeast(1),
            onSeek = onSeek,
            activeColor = Color.White,
            trackColor = Color.White.copy(alpha = 0.24f),
            timeColor = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(10.dp))
        TransportRow(
            state = state,
            onPrev = { /* handled by caller via lambda */ },
            onToggle = { },
            onNext = { },
            white = true,
        )
        Spacer(Modifier.height(6.dp))
        BottomActions(
            state = state,
            quality = quality,
            onModeClick = onModeClick,
            onShowSheet = onShowSheet,
            onOpenQueue = onOpenQueue,
            onToggleLyrics = onToggleLyrics,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun QualityBadge(quality: AudioQuality) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = when (quality) {
                AudioQuality.Q192 -> "HQ"
                AudioQuality.Q128 -> "SQ"
                AudioQuality.Q64 -> "LQ"
            },
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun TransportRow(state: PlaybackState, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, white: Boolean) {
    val graph = LocalAppGraph.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(Icons.Rounded.SkipPrevious, "上一首", onClick = { graph.playbackController.previous() }, tint = if (white) Color.White.copy(alpha = 0.9f) else SaltTheme.colors.text, size = 52)
        Box(
            Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(50))
                .clickable { graph.playbackController.playPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "暂停" else "播放",
                tint = if (white) Color.White else SaltTheme.colors.text,
                modifier = Modifier.size(52.dp),
            )
        }
        IconAction(Icons.Rounded.SkipNext, "下一首", onClick = { graph.playbackController.next() }, tint = if (white) Color.White.copy(alpha = 0.9f) else SaltTheme.colors.text, size = 52)
    }
}

@Composable
private fun MiniTransport(state: PlaybackState, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, white: Boolean) {
    val graph = LocalAppGraph.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconAction(Icons.Rounded.SkipPrevious, "上一首", onClick = { graph.playbackController.previous() }, tint = Color.White.copy(alpha = 0.9f), size = 44)
        IconAction(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放", onClick = { graph.playbackController.playPause() }, tint = Color.White, size = 52)
        IconAction(Icons.Rounded.SkipNext, "下一首", onClick = { graph.playbackController.next() }, tint = Color.White.copy(alpha = 0.9f), size = 44)
    }
}

@Composable
private fun BottomActions(
    state: PlaybackState,
    quality: AudioQuality,
    onModeClick: () -> Unit,
    onShowSheet: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = when (state.playMode) {
                PlayMode.SEQUENCE -> Icons.Rounded.Repeat
                PlayMode.REPEAT_ALL -> Icons.Rounded.Repeat
                PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
                PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
            },
            contentDescription = state.playMode.label,
            onClick = onModeClick,
            tint = if (state.playMode in listOf(PlayMode.REPEAT_ONE, PlayMode.SHUFFLE)) Color.White else Color.White.copy(alpha = 0.65f),
            size = 38,
        )
        IconAction(Icons.Rounded.Timer, "定时关闭", onClick = { onShowSheet("timer") }, tint = Color.White.copy(alpha = 0.65f), size = 38)
        IconAction(Icons.Rounded.Speed, "倍速", onClick = { onShowSheet("speed") }, tint = Color.White.copy(alpha = 0.65f), size = 38)
        IconAction(Icons.Rounded.Subtitles, "歌词", onClick = onToggleLyrics, tint = Color.White.copy(alpha = 0.65f), size = 38)
        IconAction(Icons.Rounded.QueueMusic, "队列", onClick = onOpenQueue, tint = Color.White.copy(alpha = 0.65f), size = 38)
        IconAction(Icons.Rounded.MoreHoriz, "更多", onClick = { onShowSheet("more") }, tint = Color.White.copy(alpha = 0.65f), size = 38)
    }
}

private fun Modifier.androidxScale(scale: Float): Modifier =
    this.then(Modifier.scale(scale))

@Composable
private fun OptionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (dark) Color(0xFF262626) else SaltTheme.colors.popup)
                .padding(20.dp),
        ) {
            Text(title, color = if (dark) Color.White else SaltTheme.colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            options.forEachIndexed { index, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option,
                        color = when {
                            dark && index == selectedIndex -> Color.White
                            dark -> Color.White.copy(alpha = 0.75f)
                            index == selectedIndex -> SaltTheme.colors.highlight
                            else -> SaltTheme.colors.text
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (index == selectedIndex) Text("✓", color = if (dark) Color.White else SaltTheme.colors.highlight)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(graph: AppGraph, track: Track, onDismiss: () -> Unit) {
    val playlists by graph.libraryRepository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF262626))
                .padding(20.dp),
        ) {
            Text("添加到歌单", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (playlists.isEmpty()) {
                Text("还没有歌单，去音乐库新建一个吧", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            } else {
                playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.safeLaunch {
                                    val added = graph.libraryRepository.addTrack(playlist.id, track)
                                    message = if (added) "已添加到「${playlist.name}」" else "已在「${playlist.name}」中"
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(playlist.name, color = Color.White, modifier = Modifier.weight(1f))
                        Text("${playlist.tracks.size} 首", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFF7Fb8FF), fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "关闭", onClick = onDismiss)
            }
        }
    }
}


@Composable
private fun PasteLyricsDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF262626))
                .padding(20.dp),
        ) {
            Text("粘贴 LRC 歌词", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(12.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        "[00:01.00]第一句歌词\n[00:05.00]第二句歌词",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF7FB8FF)),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                SaltPill(text = "保存", selected = text.isNotBlank(), onClick = {
                    if (text.isNotBlank()) onSave(text)
                })
            }
        }
    }
}


@Composable
private fun LyricsSearchDialog(
    graph: AppGraph,
    track: Track,
    onDismiss: () -> Unit,
    onPicked: (Lyrics) -> Unit,
) {
    var candidates by remember { mutableStateOf<List<com.bbplayer.android.core.model.LyricCandidate>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingIndex by remember { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(track.key) {
        graph.lyricsRepository.searchCandidates(track)
            .onSuccess { candidates = it }
            .onFailure { error = it.toUserMessage() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF262626))
                .padding(20.dp),
        ) {
            Text("搜索歌词", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            when {
                error != null -> Text(error!!, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                candidates == null -> Text("正在搜索…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                candidates!!.isEmpty() -> Text("没有找到匹配歌词", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(count = candidates!!.size, key = { idx -> candidates!![idx].source + candidates!![idx].sourceId }) { idx ->
                        val candidate = candidates!![idx]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    loadingIndex = idx
                                    scope.safeLaunch(onError = { error = it.toUserMessage() }) {
                                        graph.lyricsRepository.fetchCandidate(track, candidate)
                                            .onSuccess { onPicked(it) }
                                            .onFailure { error = it.toUserMessage() }
                                        loadingIndex = -1
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${candidate.title} · ${candidate.artist}",
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (loadingIndex == idx) "加载中…" else candidate.source.uppercase(),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaltPill(text = "关闭", onClick = onDismiss)
            }
        }
    }
}
