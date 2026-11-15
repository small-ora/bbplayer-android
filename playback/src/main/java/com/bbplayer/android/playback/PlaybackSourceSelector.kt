package com.bbplayer.android.playback

import java.io.File

/** Where the bytes for a queue item come from. */
sealed interface PlaybackSource {
    /** Fully downloaded file on internal storage — playback must not touch the network. */
    data class Local(val file: File) : PlaybackSource

    /** Resolve a fresh Bilibili stream URL lazily through [BbDataSourceFactory]. */
    data object Remote : PlaybackSource
}

/**
 * SPEC C3: a completed download always wins over the network stream.
 *
 * The provider is injected into [PlaybackControllerImpl] so the playback module
 * has no dependency on the download manager and can be unit tested with a plain
 * `File?` lambda. A file is only used when it exists and is non-empty, otherwise
 * a partially downloaded file would make playback fail instead of falling back.
 */
object PlaybackSourceSelector {
    fun select(trackKey: String, localFileProvider: (String) -> File?): PlaybackSource {
        val file = runCatching { localFileProvider(trackKey) }.getOrNull()
            ?: return PlaybackSource.Remote
        return if (file.isFile && file.length() > 0L) {
            PlaybackSource.Local(file)
        } else {
            PlaybackSource.Remote
        }
    }
}
