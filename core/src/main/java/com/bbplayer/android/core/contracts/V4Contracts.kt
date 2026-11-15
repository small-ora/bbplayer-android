package com.bbplayer.android.core.contracts

import com.bbplayer.android.core.model.ImportBatchResult
import com.bbplayer.android.core.model.ImportProgress
import com.bbplayer.android.core.model.ImportTask

/** Persistence for a resumable external-playlist import task. */
interface ImportTaskRepository {
    suspend fun load(): ImportTask?
    suspend fun save(task: ImportTask)
    suspend fun clear()
}

/**
 * Batch matching on top of [ExternalPlaylistRepository]: processes at most [batchSize] tracks
 * starting from [task]'s `nextIndex`, saves progress through [ImportTaskRepository] and returns
 * a [ImportBatchResult] the UI can render (including "blocked by risk control" + retry delay).
 */
interface ImportBatchMatcher {
    suspend fun matchBatch(
        task: ImportTask,
        batchSize: Int,
        onProgress: (ImportProgress) -> Unit,
    ): ImportBatchResult
}
