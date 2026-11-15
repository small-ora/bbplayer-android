package com.bbplayer.android.core.contracts

import com.bbplayer.android.core.model.CommentPage
import com.bbplayer.android.core.model.DailyHistory
import com.bbplayer.android.core.model.DownloadEntry
import com.bbplayer.android.core.model.ExternalPlaylist
import com.bbplayer.android.core.model.ImportProgress
import com.bbplayer.android.core.model.ListeningRank
import com.bbplayer.android.core.model.ReleaseInfo
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.model.VideoSubtitle
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Search suggestions + trending keywords (Bilibili public endpoints). */
interface DiscoverRepository {
    suspend fun suggestions(query: String): Result<List<String>>
    suspend fun hotKeywords(): Result<List<String>>
}

/** Public read-only comment browsing (no login required). */
interface CommentRepository {
    suspend fun fetchComments(bvid: String, page: Int = 1, sort: Int = 2): Result<CommentPage>
    suspend fun fetchReplies(bvid: String, rootId: Long, page: Int = 1): Result<CommentPage>
}

/** Import a Netease / QQ Music playlist and match it against Bilibili videos. */
interface ExternalPlaylistRepository {
    suspend fun fetchPlaylist(input: String): Result<ExternalPlaylist>

    /** Rate-limited matching; [onProgress] is called on the calling coroutine context. */
    suspend fun match(playlist: ExternalPlaylist, onProgress: (ImportProgress) -> Unit): Result<List<com.bbplayer.android.core.model.ImportMatch>>
}

/** Bilibili CC subtitles (used by the podcast/subtitle reader). */
interface SubtitleRepository {
    suspend fun fetchSubtitle(bvid: String, cid: Long): Result<VideoSubtitle?>
}

/** GitHub Releases based update check. */
interface UpdateRepository {
    suspend fun checkForUpdate(currentVersion: String): Result<ReleaseInfo?>
}

/** Offline downloads: queue, progress, removal and export to the public Music folder. */
interface DownloadRepository {
    val entries: Flow<List<DownloadEntry>>
    suspend fun enqueue(track: Track): Result<Unit>
    suspend fun cancel(trackKey: String)
    suspend fun remove(trackKey: String): Result<Unit>
    fun localFileFor(trackKey: String): File?
    /** Export a downloaded track (optional sidecar .lrc) into Music/BBPlayer. */
    suspend fun export(trackKey: String, lrc: String? = null): Result<String>
    suspend fun clearCompleted()
}

/** Local backup / restore of user data (playlists, favorites, history, settings, lyrics). */
interface BackupRepository {
    suspend fun exportZip(): Result<File>
    suspend fun importZip(file: File): Result<Int>
}
