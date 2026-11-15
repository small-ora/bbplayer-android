package com.bbplayer.android.playback

import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.Track

/**
 * Internal, never-expiring media URI used as the ExoPlayer `MediaItem` URI.
 *
 * The real CDN URL is resolved lazily when the data source is opened (see
 * [BbDataSourceFactory]) so a queue can survive URL expiry and is JSON friendly.
 *
 * Format: `bbplayer://track/<bvid>/<cid>[?quality=<id>]`
 *
 * Pure Kotlin on purpose: parsing/building is covered by plain-JVM unit tests.
 */
object TrackUri {
    const val SCHEME = "bbplayer"
    const val HOST = "track"
    const val QUALITY_PARAM = "quality"
    private const val PREFIX = "$SCHEME://$HOST/"

    /** Artifact of parsing an internal URI. */
    data class Ref(
        val bvid: String,
        val cid: Long,
        val qualityId: Int = 0,
    ) {
        val trackKey: String get() = "$bvid:$cid"

        fun toTrack(): Track = Track(bvid = bvid, cid = cid, title = "", qualityId = qualityId)
    }

    fun build(track: Track, quality: AudioQuality?): String {
        val effectiveQualityId = quality?.id ?: track.qualityId
        val base = PREFIX + track.bvid + "/" + track.cid
        return if (effectiveQualityId > 0) {
            "$base?$QUALITY_PARAM=$effectiveQualityId"
        } else {
            base
        }
    }

    fun build(ref: Ref): String {
        val base = PREFIX + ref.bvid + "/" + ref.cid
        return if (ref.qualityId > 0) "$base?$QUALITY_PARAM=${ref.qualityId}" else base
    }

    /** Returns null when [raw] is not one of our internal URIs. */
    fun parse(raw: String): Ref? {
        if (!raw.startsWith(PREFIX)) return null
        val rest = raw.substring(PREFIX.length)
        val withoutQuery = rest.substringBefore('?')
        val query = rest.substringAfter('?', missingDelimiterValue = "")
        val parts = withoutQuery.split('/').filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val bvid = parts[0]
        if (bvid.isEmpty() || bvid.any { it.isWhitespace() }) return null
        val cid = parts[1].toLongOrNull() ?: return null
        if (cid <= 0L) return null
        val qualityId = query
            .split('&')
            .asSequence()
            .mapNotNull { segment ->
                val key = segment.substringBefore('=')
                if (key == QUALITY_PARAM) segment.substringAfter('=', missingDelimiterValue = "").toIntOrNull() else null
            }
            .firstOrNull()
            ?: 0
        return Ref(bvid = bvid, cid = cid, qualityId = qualityId)
    }

    /** `null` means "let the repository pick the highest available quality". */
    fun qualityOf(ref: Ref): AudioQuality? =
        if (ref.qualityId > 0) AudioQuality.fromId(ref.qualityId) else null
}
