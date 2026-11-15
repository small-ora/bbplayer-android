package com.bbplayer.android.ui.screens.search

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.LargeTitle
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SearchField
import com.bbplayer.android.ui.components.TrackRow
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History

@Composable
fun SearchScreen(
    onOpenPlayer: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val historyStore = remember { SearchHistoryStore(context) }
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(historyStore.load()) }
    var searchNonce by remember { mutableStateOf(0) }
    val playback by graph.playbackController.state.collectAsStateWithLifecycle(initialValue = com.bbplayer.android.core.model.PlaybackState())

    fun submit(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        submittedQuery = q
        history = historyStore.add(q)
        loading = true
        error = null
        searchNonce++
    }

    LaunchedEffect(submittedQuery, searchNonce) {
        if (submittedQuery.isEmpty()) return@LaunchedEffect
        // BV / AV / b23.tv / full URL are resolved as direct links first;
        // anything else falls back to keyword search.
        val direct = graph.musicRepository.resolveInput(submittedQuery)
        if (direct.isSuccess && direct.getOrNull()?.isNotEmpty() == true) {
            results = direct.getOrThrow()
            loading = false
        } else {
            graph.musicRepository.search(submittedQuery)
                .onSuccess { results = it.items; loading = false }
                .onFailure { error = it.toUserMessage(); loading = false }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item { LargeTitle("搜索") }
        item {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { submit(it) },
            )
        }
        if (submittedQuery.isEmpty()) {
            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            start = SaltTheme.dimens.padding,
                            end = SaltTheme.dimens.padding,
                            top = 20.dp,
                            bottom = 8.dp,
                        ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("最近搜索", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(
                            "清空",
                            color = SaltTheme.colors.subText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(4.dp).clickable {
                                historyStore.clear()
                                history = emptyList()
                            },
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SaltTheme.dimens.padding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        history.take(6).forEach { h ->
                            SaltPill(text = h, onClick = { query = h; submit(h) })
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
                    EmptyState(
                        title = "搜索你喜欢的音乐",
                        subtitle = "支持关键词、BV 号、av 号、b23.tv 短链与视频链接",
                        icon = Icons.Rounded.History,
                    )
                }
            }
        } else {
            when {
                loading -> item { LoadingState() }
                error != null -> item { ErrorState(message = error!!, onRetry = { submit(submittedQuery) }) }
                results.isEmpty() -> item { EmptyState(title = "没有找到相关结果", subtitle = "换个关键词试试") }
                else -> items(count = results.size, key = { idx -> results[idx].key }) { idx ->
                    val track = results[idx]
                    TrackRow(
                        track = track,
                        isCurrent = playback.track?.key == track.key,
                        onClick = { onPlayTrack(results, idx) },
                    )
                }
            }
        }
    }
}

/** Tiny persisted recent-search list (kept local to the search screen). */
class SearchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    fun load(): List<String> = prefs.getString(KEY, "")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun add(query: String): List<String> {
        val updated = (listOf(query) + load().filterNot { it == query }).take(12)
        prefs.edit().putString(KEY, updated.joinToString("\n")).apply()
        return updated
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "items"
    }
}
