package com.bbplayer.android.playback

import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-disk resume snapshot: queue (physical order), current index, position,
 * play mode and speed. Everything else is re-derivable.
 */
@Serializable
data class PersistedPlayback(
    val version: Int = CURRENT_VERSION,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val speed: Float = PlaybackMapping.DEFAULT_SPEED,
) {
    /** Repairs values that may have gone stale/corrupt on disk. */
    fun sanitized(): PersistedPlayback {
        val safeQueue = queue.filter { it.bvid.isNotBlank() && it.cid > 0L }
        val safeIndex = when {
            safeQueue.isEmpty() -> -1
            currentIndex !in safeQueue.indices -> 0
            else -> currentIndex
        }
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeSpeed = PlaybackMapping.sanitizeSpeed(speed)
        if (safeQueue.size == queue.size && safeIndex == currentIndex &&
            safePosition == positionMs && safeSpeed == speed && version == CURRENT_VERSION
        ) {
            return this
        }
        return copy(
            version = CURRENT_VERSION,
            queue = safeQueue,
            currentIndex = safeIndex,
            positionMs = safePosition,
            speed = safeSpeed,
        )
    }

    companion object {
        const val CURRENT_VERSION: Int = 1
        val EMPTY: PersistedPlayback = PersistedPlayback()
    }
}

/**
 * Tiny JSON store for [PersistedPlayback]. Written atomically (tmp + rename) so
 * a process kill mid-write cannot corrupt the resume point.
 *
 * Plain `java.io.File` + kotlinx.serialization, hence JVM-unit-testable.
 */
class PlaybackStateStore(
    private val file: File,
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val filePath: String get() = file.path

    suspend fun save(state: PersistedPlayback) = withContext(ioDispatcher) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val payload = json.encodeToString(PersistedPlayback.serializer(), state.sanitized())
        val tmp = File(file.path + ".tmp")
        tmp.writeText(payload)
        if (!tmp.renameTo(file)) {
            // Some filesystems refuse to rename over an existing file.
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                file.writeText(payload)
                tmp.delete()
            }
        }
    }

    /** Returns null when the file is missing, empty or corrupted. */
    suspend fun load(): PersistedPlayback? = withContext(ioDispatcher) {
        if (!file.exists() || file.length() == 0L) return@withContext null
        val text = runCatching { file.readText() }.getOrNull() ?: return@withContext null
        runCatching { json.decodeFromString(PersistedPlayback.serializer(), text) }
            .getOrNull()
            ?.sanitized()
    }

    suspend fun clear() = withContext(ioDispatcher) {
        runCatching { file.delete() }
        Unit
    }

    companion object {
        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

        fun defaultFile(filesDir: File): File = File(filesDir, PlaybackConfig.STATE_FILE_NAME)
    }
}
