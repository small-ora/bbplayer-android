package com.bbplayer.android.playback

import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackUriTest {
    private val track = Track(
        bvid = "BV1xx411c7mD",
        cid = 123456789L,
        title = "demo",
    )

    @Test
    fun `builds and parses a quality qualified uri`() {
        val raw = TrackUri.build(track, AudioQuality.Q128)
        assertEquals("bbplayer://track/BV1xx411c7mD/123456789?quality=30232", raw)
        val ref = TrackUri.parse(raw)
        assertEquals(TrackUri.Ref("BV1xx411c7mD", 123456789L, AudioQuality.Q128.id), ref)
        assertEquals("BV1xx411c7mD:123456789", ref!!.trackKey)
    }

    @Test
    fun `omits quality when unknown`() {
        val raw = TrackUri.build(track.copy(qualityId = 0), null)
        assertEquals("bbplayer://track/BV1xx411c7mD/123456789", raw)
        assertEquals(0, TrackUri.parse(raw)!!.qualityId)
        assertNull(TrackUri.qualityOf(TrackUri.parse(raw)!!))
    }

    @Test
    fun `track qualityId is used when caller passes null`() {
        val raw = TrackUri.build(track.copy(qualityId = AudioQuality.Q64.id), null)
        assertEquals("bbplayer://track/BV1xx411c7mD/123456789?quality=30216", raw)
        assertEquals(AudioQuality.Q64, TrackUri.qualityOf(TrackUri.parse(raw)!!))
    }

    @Test
    fun `rejects foreign and malformed uris`() {
        assertNull(TrackUri.parse("https://example.com/track/BV1/1"))
        assertNull(TrackUri.parse("bbplayer://track/BV1"))
        assertNull(TrackUri.parse("bbplayer://track//123"))
        assertNull(TrackUri.parse("bbplayer://track/BV1/not-a-number"))
        assertNull(TrackUri.parse("bbplayer://track/BV1/0"))
        assertNull(TrackUri.parse(""))
    }

    @Test
    fun `ref round trips back to a track`() {
        val ref = TrackUri.Ref("BV1yy411c7mE", 42L, AudioQuality.Q192.id)
        val track = TrackUri.parse(TrackUri.build(ref))!!.toTrack()
        assertEquals("BV1yy411c7mE", track.bvid)
        assertEquals(42L, track.cid)
        assertEquals(AudioQuality.Q192.id, track.qualityId)
    }
}
