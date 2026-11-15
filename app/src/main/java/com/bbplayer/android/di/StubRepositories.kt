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

    val auth: com.bbplayer.android.core.contracts.AuthRepository =
        object : com.bbplayer.android.core.contracts.AuthRepository {
            override fun session() = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun generateQrCode() =
                Result.failure<com.bbplayer.android.core.model.QrCodeInfo>(notReady("登录"))
            override suspend fun pollQrCode(qrKey: String) =
                Result.failure<com.bbplayer.android.core.model.QrPollResult>(notReady("登录"))
            override suspend fun loginWithCookie(rawCookie: String) =
                Result.failure<com.bbplayer.android.core.model.BiliUser>(notReady("登录"))
            override suspend fun refreshUser() = Result.success(null)
            override suspend fun logout() {}
            override fun cookieHeader(): String? = null
            override fun cookieValue(name: String): String? = null
            override fun csrfToken(): String? = null
            override fun isLoggedIn(): Boolean = false
        }

    val favorites: com.bbplayer.android.core.contracts.FavoritesRepository =
        object : com.bbplayer.android.core.contracts.FavoritesRepository {
            override suspend fun folders() = Result.failure<List<com.bbplayer.android.core.model.FavoriteFolder>>(notReady("收藏夹"))
            override suspend fun folderTracks(mediaId: Long, page: Int, pageSize: Int) =
                Result.failure<com.bbplayer.android.core.model.TrackPage>(notReady("收藏夹"))
            override suspend fun updateFavorite(aid: Long, addTo: List<Long>, removeFrom: List<Long>) =
                Result.failure<Unit>(notReady("收藏"))
            override suspend fun isFavorited(aid: Long) = Result.failure<List<Long>>(notReady("收藏"))
        }

    val collections: com.bbplayer.android.core.contracts.CollectionRepository =
        object : com.bbplayer.android.core.contracts.CollectionRepository {
            override suspend fun myCollections() = Result.failure<List<com.bbplayer.android.core.model.BiliCollection>>(notReady("合集"))
            override suspend fun collectionTracks(seasonId: Long, page: Int) =
                Result.failure<com.bbplayer.android.core.model.TrackPage>(notReady("合集"))
        }

    val watchLater: com.bbplayer.android.core.contracts.WatchLaterRepository =
        object : com.bbplayer.android.core.contracts.WatchLaterRepository {
            override suspend fun list() = Result.failure<List<com.bbplayer.android.core.model.Track>>(notReady("稍后再看"))
            override suspend fun add(aid: Long, cid: Long?) = Result.failure<Unit>(notReady("稍后再看"))
            override suspend fun remove(aid: Long) = Result.failure<Unit>(notReady("稍后再看"))
            override suspend fun clear() = Result.failure<Unit>(notReady("稍后再看"))
        }

    val uploader: com.bbplayer.android.core.contracts.UploaderRepository =
        object : com.bbplayer.android.core.contracts.UploaderRepository {
            override suspend fun info(mid: Long) = Result.failure<com.bbplayer.android.core.model.UploaderInfo>(notReady("UP 主"))
            override suspend fun videos(mid: Long, page: Int, keyword: String) =
                Result.failure<com.bbplayer.android.core.model.TrackPage>(notReady("UP 主"))
            override suspend fun setFollow(mid: Long, follow: Boolean) = Result.failure<Unit>(notReady("关注"))
        }

    val playbackReporter: com.bbplayer.android.core.contracts.PlaybackReporter =
        object : com.bbplayer.android.core.contracts.PlaybackReporter {
            override val enabled = kotlinx.coroutines.flow.flowOf(false)
            override suspend fun report(track: com.bbplayer.android.core.model.Track, progressMs: Long) = Result.success(Unit)
        }

    val backup: BackupRepository = object : BackupRepository {
        override suspend fun exportZip() = Result.failure<File>(notReady("备份"))
        override suspend fun importZip(file: File) = Result.failure<Int>(notReady("恢复"))
    }
}
