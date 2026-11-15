package com.bbplayer.android.playback

import com.bbplayer.android.core.contracts.AppException
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorMapperTest {
    @Test
    fun `network failures map to retry friendly messages`() {
        assertEquals(
            PlaybackErrorKind.NETWORK,
            PlaybackErrorMapper.kindOf(PlaybackErrorMapper.IO_NETWORK_CONNECTION_FAILED),
        )
        assertEquals(
            PlaybackErrorMapper.MSG_NETWORK,
            PlaybackErrorMapper.message(PlaybackErrorMapper.IO_NETWORK_CONNECTION_FAILED),
        )
        assertEquals(
            PlaybackErrorMapper.MSG_TIMEOUT,
            PlaybackErrorMapper.message(PlaybackErrorMapper.IO_NETWORK_CONNECTION_TIMEOUT),
        )
        assertEquals(
            PlaybackErrorMapper.MSG_HTTP,
            PlaybackErrorMapper.message(PlaybackErrorMapper.IO_BAD_HTTP_STATUS),
        )
    }

    @Test
    fun `unavailable and decode errors get distinct messages`() {
        assertEquals(
            PlaybackErrorKind.AUDIO_UNAVAILABLE,
            PlaybackErrorMapper.kindOf(PlaybackErrorMapper.PARSING_CONTAINER_UNSUPPORTED),
        )
        assertEquals(
            PlaybackErrorMapper.MSG_UNAVAILABLE,
            PlaybackErrorMapper.message(PlaybackErrorMapper.PARSING_CONTAINER_UNSUPPORTED),
        )
        assertEquals(
            PlaybackErrorKind.DECODE,
            PlaybackErrorMapper.kindOf(PlaybackErrorMapper.DECODING_FAILED),
        )
        assertEquals(
            PlaybackErrorMapper.MSG_DECODE,
            PlaybackErrorMapper.message(PlaybackErrorMapper.DECODING_FAILED),
        )
        assertEquals(
            PlaybackErrorKind.AUDIO_OUTPUT,
            PlaybackErrorMapper.kindOf(PlaybackErrorMapper.AUDIO_TRACK_INIT_FAILED),
        )
        assertEquals(
            PlaybackErrorMapper.MSG_AUDIO_OUTPUT,
            PlaybackErrorMapper.message(PlaybackErrorMapper.AUDIO_TRACK_INIT_FAILED),
        )
    }

    @Test
    fun `AppException in the cause chain wins over generic code`() {
        val resolveError = AppException("音频解析失败：网络不可用，请检查网络连接")
        val wrapped = IOException("datasource open failed", IOException("inner", resolveError))
        assertEquals(
            "音频解析失败：网络不可用，请检查网络连接",
            PlaybackErrorMapper.message(PlaybackErrorMapper.IO_UNSPECIFIED, wrapped),
        )
        assertEquals(
            "音频解析失败：网络不可用，请检查网络连接",
            PlaybackErrorMapper.resolveFailureMessage(wrapped),
        )
    }

    @Test
    fun `unknown error falls back to cause message or generic copy`() {
        assertEquals(
            PlaybackErrorMapper.MSG_UNKNOWN,
            PlaybackErrorMapper.message(12345),
        )
        assertEquals(
            "网络不可用，请检查网络连接",
            PlaybackErrorMapper.message(PlaybackErrorMapper.IO_UNSPECIFIED, UnknownHostException()),
        )
        assertEquals(
            PlaybackErrorMapper.MSG_RESOLVE,
            PlaybackErrorMapper.resolveFailureMessage(null),
        )
    }

    @Test
    fun `drm errors are classified`() {
        assertEquals(PlaybackErrorKind.DRM, PlaybackErrorMapper.kindOf(PlaybackErrorMapper.DRM_UNSPECIFIED))
        assertTrue(PlaybackErrorMapper.message(PlaybackErrorMapper.DRM_UNSPECIFIED).isNotBlank())
    }
}
