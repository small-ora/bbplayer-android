package com.bbplayer.android.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.model.TrackPage

/**
 * Small paging state holder shared by the Bilibili detail pages (favourite folder / collection /
 * uploader videos). It keeps the UI composables free of repeated paging bookkeeping.
 */
@Stable
class PagedTracksState internal constructor(
    private val loader: suspend (page: Int) -> Result<TrackPage>,
) {
    val items = mutableStateListOf<Track>()
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var page by mutableStateOf(0)
        private set

    private var started = false

    suspend fun start(force: Boolean = false) {
        if (started && !force) return
        started = true
        loading = true
        error = null
        items.clear()
        page = 0
        hasMore = true
        loader(1)
            .onSuccess { result ->
                page = 1
                hasMore = result.hasMore
                items.addAll(result.items)
            }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    suspend fun loadMore() {
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        error = null
        val next = page + 1
        loader(next)
            .onSuccess { result ->
                page = next
                hasMore = result.hasMore
                items.addAll(result.items)
            }
            .onFailure { error = it.toUserMessage() }
        loadingMore = false
    }

    suspend fun retry() {
        if (items.isEmpty()) start(force = true) else loadMore()
    }
}

/**
 * Creates a paging state keyed by [key] and starts loading its first page once.
 * The [loader] receives a 1-based page number.
 */
@Composable
fun rememberPagedTracks(
    key: Any?,
    loader: suspend (page: Int) -> Result<TrackPage>,
): PagedTracksState {
    val state = remember(key) { PagedTracksState(loader) }
    LaunchedEffect(state) { state.start() }
    return state
}
