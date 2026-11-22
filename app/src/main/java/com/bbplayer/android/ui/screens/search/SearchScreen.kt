package com.bbplayer.android.ui.screens.search

import android.content.Context
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.input.SearchInput
import com.bbplayer.android.core.input.parseSearchInput
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.EmptyState
import com.bbplayer.android.ui.components.ErrorState
import com.bbplayer.android.ui.components.LargeTitle
import com.bbplayer.android.ui.components.LoadingState
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SearchField
import com.bbplayer.android.ui.components.TrackRow
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbSpacing
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

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
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var hotKeywords by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        graph.discoverRepository.hotKeywords().onSuccess { hotKeywords = it }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(320)
        graph.discoverRepository.suggestions(query).onSuccess { suggestions = it }
    }
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
        loading = true
        error = null
        // 关键词 → 列表搜索（返回全部结果）；BV / AV / b23.tv / 完整链接 → 解析为视频（含分 P）。
        // 之前所有输入都先走 resolveInput，而它对关键词只取首条视频，导致「搜什么都只有一条」。
        when (val input = parseSearchInput(submittedQuery)) {
            is SearchInput.KeywordQuery -> {
                // 关键词：走列表搜索，返回全部结果。
                graph.musicRepository.search(input.query)
                    .onSuccess { results = it.items; loading = false }
                    .onFailure { error = it.toUserMessage(); loading = false }
            }
            is SearchInput.DirectVideo -> {
                // 直接视频引用：解析该视频（含分 P）；解析失败再回退到关键词搜索。
                val direct = graph.musicRepository.resolveInput(input.raw)
                if (direct.isSuccess && direct.getOrNull()?.isNotEmpty() == true) {
                    results = direct.getOrThrow()
                    loading = false
                } else {
                    graph.musicRepository.search(input.raw)
                        .onSuccess { results = it.items; loading = false }
                        .onFailure { error = it.toUserMessage(); loading = false }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
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
        if (query.isNotBlank() && suggestions.isNotEmpty() && query != submittedQuery) {
            item {
                Column(Modifier.padding(horizontal = SaltTheme.dimens.padding, vertical = BbSpacing.Sm)) {
                    suggestions.take(8).forEach { suggestion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = suggestion
                                    submit(suggestion)
                                }
                                .padding(vertical = BbSpacing.Md),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Rounded.Search,
                                null,
                                tint = SaltTheme.colors.subText,
                                modifier = Modifier.size(BbIconSize.Sm),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = suggestion,
                                color = SaltTheme.colors.text,
                                style = BbType.Body,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (submittedQuery.isEmpty()) {
            if (hotKeywords.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            start = SaltTheme.dimens.padding,
                            end = SaltTheme.dimens.padding,
                            top = BbSpacing.Xl,
                            bottom = BbSpacing.Sm,
                        ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("B 站热搜", style = BbType.Section, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                }
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding),
                        horizontalArrangement = Arrangement.spacedBy(BbSpacing.Sm),
                    ) {
                        items(count = hotKeywords.size, key = { "hot_$it" }) { index ->
                            SaltPill(text = hotKeywords[index], onClick = {
                                query = hotKeywords[index]
                                submit(hotKeywords[index])
                            })
                        }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            start = SaltTheme.dimens.padding,
                            end = SaltTheme.dimens.padding,
                            top = BbSpacing.Xl,
                            bottom = BbSpacing.Sm,
                        ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("最近搜索", style = BbType.Section, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(
                            "清空",
                            color = SaltTheme.colors.subText,
                            style = BbType.Label,
                            modifier = Modifier.padding(BbSpacing.Xs).clickable {
                                historyStore.clear()
                                history = emptyList()
                            },
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SaltTheme.dimens.padding),
                        horizontalArrangement = Arrangement.spacedBy(BbSpacing.Sm),
                    ) {
                        history.take(6).forEach { h ->
                            SaltPill(text = h, onClick = { query = h; submit(h) })
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxSize().padding(top = BbSpacing.Section)) {
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
