package com.bbplayer.android.playback

import androidx.media3.common.Player
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.Track

/**
 * Pure mapping between the app's domain enums and Media3/ExoPlayer constants.
 *
 * Kept free of `android.*` so it can be unit tested on the JVM: the referenced
 * `Player.REPEAT_MODE_*` values are Java compile-time constants and get inlined.
 */
object PlaybackMapping {
    /** SPEC C5: playback speed range is 0.5x – 2.0x. */
    const val MIN_SPEED: Float = 0.5f
    const val MAX_SPEED: Float = 2.0f
    const val DEFAULT_SPEED: Float = 1.0f

    fun repeatMode(playMode: PlayMode): Int = when (playMode) {
        PlayMode.SEQUENCE -> Player.REPEAT_MODE_OFF
        PlayMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
        PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        // Random playback loops the shuffled traversal, like "list loop + shuffle".
        PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL
    }

    fun shuffleEnabled(playMode: PlayMode): Boolean = playMode == PlayMode.SHUFFLE

    /** Clamps / repairs a speed coming from UI or persisted state. */
    fun sanitizeSpeed(speed: Float): Float {
        if (speed.isNaN() || speed.isInfinite() || speed <= 0f) return DEFAULT_SPEED
        return speed.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /**
     * [Track.qualityId] is `0` for "not resolved yet" (repository picks the
     * highest anonymous-safe quality, i.e. 30280); unknown ids fall back to it.
     */
    fun qualityForTrack(track: Track): AudioQuality? =
        if (track.qualityId > 0) AudioQuality.fromId(track.qualityId) else null

    fun effectiveQualityId(track: Track): Int = qualityForTrack(track)?.id ?: AudioQuality.DEFAULT.id
}
