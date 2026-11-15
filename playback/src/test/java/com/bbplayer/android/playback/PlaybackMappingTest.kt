package com.bbplayer.android.playback

import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMappingTest {
    @Test
    fun `play modes map to ExoPlayer repeat and shuffle`() {
        assertEquals(0, PlaybackMapping.repeatMode(PlayMode.SEQUENCE))
        assertEquals(2, PlaybackMapping.repeatMode(PlayMode.REPEAT_ALL))
        assertEquals(1, PlaybackMapping.repeatMode(PlayMode.REPEAT_ONE))
        assertEquals(2, PlaybackMapping.repeatMode(PlayMode.SHUFFLE))

        assertFalse(PlaybackMapping.shuffleEnabled(PlayMode.SEQUENCE))
        assertFalse(PlaybackMapping.shuffleEnabled(PlayMode.REPEAT_ALL))
        assertFalse(PlaybackMapping.shuffleEnabled(PlayMode.REPEAT_ONE))
        assertTrue(PlaybackMapping.shuffleEnabled(PlayMode.SHUFFLE))
    }

    @Test
    fun `speed is clamped to the spec range`() {
        assertEquals(0.5f, PlaybackMapping.sanitizeSpeed(0.1f), 0.0001f)
        assertEquals(2.0f, PlaybackMapping.sanitizeSpeed(3.0f), 0.0001f)
        assertEquals(1.0f, PlaybackMapping.sanitizeSpeed(1.0f), 0.0001f)
        assertEquals(1.0f, PlaybackMapping.sanitizeSpeed(Float.NaN), 0.0001f)
        assertEquals(1.0f, PlaybackMapping.sanitizeSpeed(0f), 0.0001f)
        assertEquals(1.5f, PlaybackMapping.sanitizeSpeed(1.5f), 0.0001f)
    }

    @Test
    fun `track quality id maps to AudioQuality with highest as default`() {
        assertNull(PlaybackMapping.qualityForTrack(Track("BV1", 1L, "t", qualityId = 0)))
        assertEquals(
            AudioQuality.Q64,
            PlaybackMapping.qualityForTrack(Track("BV1", 1L, "t", qualityId = 30216)),
        )
        assertEquals(
            AudioQuality.Q128,
            PlaybackMapping.qualityForTrack(Track("BV1", 1L, "t", qualityId = 30232)),
        )
        assertEquals(
            AudioQuality.Q192,
            PlaybackMapping.qualityForTrack(Track("BV1", 1L, "t", qualityId = 30280)),
        )
        // Unknown / future ids fall back to the highest anonymous-safe tier.
        assertEquals(
            AudioQuality.Q192,
            PlaybackMapping.qualityForTrack(Track("BV1", 1L, "t", qualityId = 99999)),
        )
        assertEquals(AudioQuality.DEFAULT, AudioQuality.fromId(-1))
        assertEquals(AudioQuality.DEFAULT, AudioQuality.fromId(0))
    }

    @Test
    fun `effective quality id never returns zero`() {
        assertEquals(AudioQuality.DEFAULT.id, PlaybackMapping.effectiveQualityId(Track("BV1", 1L, "t")))
        assertEquals(
            AudioQuality.Q64.id,
            PlaybackMapping.effectiveQualityId(Track("BV1", 1L, "t", qualityId = 30216)),
        )
    }
}
