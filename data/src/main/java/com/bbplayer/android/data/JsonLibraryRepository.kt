package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.LibraryRepository
import com.bbplayer.android.core.model.FavoriteEntry
import com.bbplayer.android.core.model.Playlist
import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/** 本地音乐库的持久化结构（`filesDir/library.json`）。 */
@Serializable
data class LibraryData(
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<FavoriteEntry> = emptyList(),
)

/**
 * 基于 JSON 文件的本地歌单 + 收藏仓库。
 *
 * - 内存用 [MutableStateFlow] 暴露给 UI，变更后立即原子落盘；
 * - 首次读取失败/文件缺失时从空库开始；
 * - 歌单 id 使用 UUID，重复添加同一 Track（按 [Track.key]）返回 false。
 */
class JsonLibraryRepository(
    private val store: JsonFileStore<LibraryData>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : LibraryRepository {

    /** 便捷构造：由 AppContainer 直接调用。 */
    constructor(context: Context) : this(
        JsonFileStore(File(context.filesDir, FILE_NAME), LibraryData.serializer()),
    )

    private val mutex = Mutex()
    private val data = MutableStateFlow(LibraryData())

    @Volatile
    private var loaded = false

    override val playlists: Flow<List<Playlist>> = data
        .map { it.playlists }
        .onStart { ensureLoaded() }
        .distinctUntilChanged()

    override val favorites: Flow<List<FavoriteEntry>> = data
        .map { it.favorites }
        .onStart { ensureLoaded() }
        .distinctUntilChanged()

    override suspend fun createPlaylist(name: String): Playlist {
        val now = clock()
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { DEFAULT_PLAYLIST_NAME },
            createdAt = now,
            updatedAt = now,
        )
        mutate { current -> current.copy(playlists = current.playlists + playlist) to playlist }
        return playlist
    }

    override suspend fun renamePlaylist(id: String, name: String) {
        val newName = name.trim()
        if (newName.isEmpty()) return
        val now = clock()
        mutate { current ->
            current.copy(
                playlists = current.playlists.map { playlist ->
                    if (playlist.id == id) playlist.copy(name = newName, updatedAt = now) else playlist
                },
            ) to Unit
        }
    }

    override suspend fun deletePlaylist(id: String) {
        mutate { current -> current.copy(playlists = current.playlists.filterNot { it.id == id }) to Unit }
    }

    override suspend fun getPlaylist(id: String): Playlist? {
        ensureLoaded()
        return data.value.playlists.firstOrNull { it.id == id }
    }

    override suspend fun addTrack(playlistId: String, track: Track): Boolean = mutate { current ->
        val playlist = current.playlists.firstOrNull { it.id == playlistId }
        if (playlist == null || playlist.tracks.any { it.key == track.key }) {
            current to false
        } else {
            val now = clock()
            val updated = playlist.copy(tracks = playlist.tracks + track, updatedAt = now)
            current.copy(
                playlists = current.playlists.map { if (it.id == playlistId) updated else it },
            ) to true
        }
    }

    override suspend fun removeTrack(playlistId: String, trackKey: String) {
        val now = clock()
        mutate { current ->
            current.copy(
                playlists = current.playlists.map { playlist ->
                    if (playlist.id != playlistId) {
                        playlist
                    } else {
                        playlist.copy(
                            tracks = playlist.tracks.filterNot { it.key == trackKey },
                            updatedAt = now,
                        )
                    }
                },
            ) to Unit
        }
    }

    override suspend fun isFavorite(trackKey: String): Boolean {
        ensureLoaded()
        return data.value.favorites.any { it.track.key == trackKey }
    }

    override suspend fun toggleFavorite(track: Track): Boolean = mutate { current ->
        val exists = current.favorites.any { it.track.key == track.key }
        val next = if (exists) {
            current.copy(favorites = current.favorites.filterNot { it.track.key == track.key })
        } else {
            current.copy(favorites = listOf(FavoriteEntry(track, clock())) + current.favorites)
        }
        next to !exists
    }

    // ------------------------------------------------------------ 内部实现

    private suspend fun ensureLoaded() {
        if (loaded) return
        withContext(io) {
            mutex.withLock {
                if (!loaded) {
                    data.value = store.read() ?: LibraryData()
                    loaded = true
                }
            }
        }
    }

    private suspend fun <R> mutate(block: (LibraryData) -> Pair<LibraryData, R>): R = withContext(io) {
        mutex.withLock {
            if (!loaded) {
                data.value = store.read() ?: LibraryData()
                loaded = true
            }
            val (next, value) = block(data.value)
            if (next != data.value) {
                data.value = next
                // 落盘失败不阻断 UI 操作（下次变更会重试写全量快照）。
                runCatching { store.write(next) }
            }
            value
        }
    }

    companion object {
        private const val FILE_NAME = "library.json"
        private const val DEFAULT_PLAYLIST_NAME = "未命名歌单"
    }
}
