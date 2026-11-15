package com.bbplayer.android.di

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.BackupRepository
import com.bbplayer.android.core.contracts.CommentRepository
import com.bbplayer.android.core.contracts.DiscoverRepository
import com.bbplayer.android.core.contracts.DownloadRepository
import com.bbplayer.android.core.contracts.ExternalPlaylistRepository
import com.bbplayer.android.core.contracts.SubtitleRepository
import com.bbplayer.android.core.contracts.UpdateRepository
import com.bbplayer.android.core.model.CommentPage
import com.bbplayer.android.core.model.DownloadEntry
import com.bbplayer.android.core.model.ExternalPlaylist
import com.bbplayer.android.core.model.ImportMatch
import com.bbplayer.android.core.model.ImportProgress
import com.bbplayer.android.core.model.ReleaseInfo
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.model.VideoSubtitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

/**
 * Temporary placeholders for the v2 repositories so the UI can be developed and compiled
 * before the data/playback implementations land. [RealAppGraph] wires the real ones.
 */
object StubRepositories {
    private fun notReady(feature: String) = AppException("$feature 尚在接入中", retryable = false)

    val discover: DiscoverRepository = object : DiscoverRepository {
        override suspend fun suggestions(query: String) = Result.failure<List<String>>(notReady("搜索建议"))
        override suspend fun hotKeywords() = Result.failure<List<String>>(notReady("热门搜索"))
    }

    val comments: CommentRepository = object : CommentRepository {
        override suspend fun fetchComments(bvid: String, page: Int, sort: Int) = Result.failure<CommentPage>(notReady("评论"))
        override suspend fun fetchReplies(bvid: String, rootId: Long, page: Int) = Result.failure<CommentPage>(notReady("评论回复"))
    }

    val externalPlaylist: ExternalPlaylistRepository = object : ExternalPlaylistRepository {
        override suspend fun fetchPlaylist(input: String) = Result.failure<ExternalPlaylist>(notReady("歌单导入"))
        override suspend fun match(playlist: ExternalPlaylist, onProgress: (ImportProgress) -> Unit) =
            Result.failure<List<ImportMatch>>(notReady("歌单匹配"))
    }

    val subtitle: SubtitleRepository = object : SubtitleRepository {
        override suspend fun fetchSubtitle(bvid: String, cid: Long) = Result.failure<VideoSubtitle?>(notReady("字幕"))
    }

    val update: UpdateRepository = object : UpdateRepository {
        override suspend fun checkForUpdate(currentVersion: String) = Result.failure<ReleaseInfo?>(notReady("更新检查"))
    }

    val downloads: DownloadRepository = object : DownloadRepository {
        override val entries: Flow<List<DownloadEntry>> = flowOf(emptyList())
        override suspend fun enqueue(track: Track) = Result.failure<Unit>(notReady("下载"))
        override suspend fun cancel(trackKey: String) {}
        override suspend fun remove(trackKey: String) = Result.failure<Unit>(notReady("下载"))
        override fun localFileFor(trackKey: String): File? = null
        override suspend fun export(trackKey: String, lrc: String?) = Result.failure<String>(notReady("导出"))
        override suspend fun clearCompleted() {}
    }

    val importTasks: com.bbplayer.android.core.contracts.ImportTaskRepository =
        object : com.bbplayer.android.core.contracts.ImportTaskRepository {
            private var task: com.bbplayer.android.core.model.ImportTask? = null
            override suspend fun load() = task
            override suspend fun save(task: com.bbplayer.android.core.model.ImportTask) { this.task = task }
            override suspend fun clear() { task = null }
        }

    val backup: BackupRepository = object : BackupRepository {
        override suspend fun exportZip() = Result.failure<File>(notReady("备份"))
        override suspend fun importZip(file: File) = Result.failure<Int>(notReady("恢复"))
    }
}
