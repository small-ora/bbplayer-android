package com.bbplayer.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRetryPolicyTest {
    @Test
    fun `expired or missing stream status codes are retryable`() {
        assertTrue(StreamRetryPolicy.isRetryableStatus(403))
        assertTrue(StreamRetryPolicy.isRetryableStatus(410))
        assertTrue(StreamRetryPolicy.isRetryableStatus(404))
        assertTrue(StreamRetryPolicy.isRetryableStatus(408))
        assertTrue(StreamRetryPolicy.isRetryableStatus(429))
    }

    @Test
    fun `server errors retry and client errors do not`() {
        assertTrue(StreamRetryPolicy.isRetryableStatus(500))
        assertTrue(StreamRetryPolicy.isRetryableStatus(503))
        assertTrue(StreamRetryPolicy.isRetryableStatus(599))
        assertFalse(StreamRetryPolicy.isRetryableStatus(400))
        assertFalse(StreamRetryPolicy.isRetryableStatus(401))
        assertFalse(StreamRetryPolicy.isRetryableStatus(200))
        assertFalse(StreamRetryPolicy.isRetryableStatus(304))
    }

    @Test
    fun `retry budget is exactly one`() {
        assertEquals(1, StreamRetryPolicy.MAX_RETRIES)
    }

    @Test
    fun `stream headers carry referer and desktop ua`() {
        assertEquals(
            "https://www.bilibili.com/",
            PlaybackConfig.STREAM_HEADERS["Referer"],
        )
        assertTrue(PlaybackConfig.STREAM_HEADERS["User-Agent"]!!.contains("Chrome"))
        assertTrue(PlaybackConfig.STREAM_HEADERS["User-Agent"]!!.contains("Windows"))
        assertTrue(PlaybackConfig.STREAM_CACHE_TTL_MS in 60L * 60_000L..120L * 60_000L)
    }
}
