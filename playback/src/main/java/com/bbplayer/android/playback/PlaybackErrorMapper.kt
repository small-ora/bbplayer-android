package com.bbplayer.android.playback

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.toUserMessage

/** Coarse buckets used to turn ExoPlayer errors into actionable Chinese copy. */
enum class PlaybackErrorKind {
    /** DNS / connect / timeout / HTTP status failures (403 after one retry lands here). */
    NETWORK,

    /** Stream URL could not be resolved from the Bilibili API. */
    RESOLVE_FAILED,

    /** Content is unsupported / malformed (bad m4s container etc.). */
    AUDIO_UNAVAILABLE,

    /** Local decoder failure. */
    DECODE,

    /** Audio output/track failure. */
    AUDIO_OUTPUT,

    /** DRM protected content (not expected for Bilibili audio). */
    DRM,

    UNKNOWN,
}

/**
 * Maps Media3 `PlaybackException` codes (mirrored as ints so the mapper stays
 * JVM-testable and does not depend on Android at all) to user-facing Chinese
 * messages. Values mirror `androidx.media3.common.PlaybackException` 1.11.1.
 */
object PlaybackErrorMapper {
    // IO (2xxx)
    const val IO_UNSPECIFIED = 2000
    const val IO_NETWORK_CONNECTION_FAILED = 2001
    const val IO_NETWORK_CONNECTION_TIMEOUT = 2002
    const val IO_INVALID_HTTP_CONTENT_TYPE = 2003
    const val IO_BAD_HTTP_STATUS = 2004
    const val IO_FILE_NOT_FOUND = 2005
    const val IO_NO_PERMISSION = 2006
    const val IO_CLEARTEXT_NOT_PERMITTED = 2007
    const val IO_READ_POSITION_OUT_OF_RANGE = 2008
    // Parsing (3xxx)
    const val PARSING_CONTAINER_MALFORMED = 3001
    const val PARSING_MANIFEST_MALFORMED = 3002
    const val PARSING_CONTAINER_UNSUPPORTED = 3003
    const val PARSING_MANIFEST_UNSUPPORTED = 3004
    // Decoding (4xxx)
    const val DECODER_INIT_FAILED = 4001
    const val DECODER_QUERY_FAILED = 4002
    const val DECODING_FAILED = 4003
    const val DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4004
    const val DECODING_FORMAT_UNSUPPORTED = 4005
    const val DECODING_RESOURCES_RECLAIMED = 4006
    // Audio output (5xxx)
    const val AUDIO_TRACK_INIT_FAILED = 5001
    const val AUDIO_TRACK_WRITE_FAILED = 5002
    // DRM (6xxx)
    const val DRM_UNSPECIFIED = 6000

    const val MSG_NETWORK = "网络不可用，请检查网络连接"
    const val MSG_TIMEOUT = "网络超时，请重试"
    const val MSG_HTTP = "网络请求失败，请重试"
    const val MSG_RESOLVE = "音频解析失败，请稍后重试"
    const val MSG_UNAVAILABLE = "音频不可用，请尝试其他曲目"
    const val MSG_DECODE = "音频解码失败，请重试"
    const val MSG_AUDIO_OUTPUT = "音频输出初始化失败，请重试"
    const val MSG_DRM = "该内容受版权保护，无法播放"
    const val MSG_UNKNOWN = "播放失败，请重试"

    fun kindOf(errorCode: Int): PlaybackErrorKind = when (errorCode) {
        IO_NETWORK_CONNECTION_FAILED, IO_INVALID_HTTP_CONTENT_TYPE, IO_CLEARTEXT_NOT_PERMITTED -> PlaybackErrorKind.NETWORK
        IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackErrorKind.NETWORK
        IO_UNSPECIFIED, IO_BAD_HTTP_STATUS, IO_FILE_NOT_FOUND, IO_NO_PERMISSION,
        IO_READ_POSITION_OUT_OF_RANGE -> PlaybackErrorKind.NETWORK
        PARSING_CONTAINER_MALFORMED, PARSING_MANIFEST_MALFORMED,
        PARSING_CONTAINER_UNSUPPORTED, PARSING_MANIFEST_UNSUPPORTED -> PlaybackErrorKind.AUDIO_UNAVAILABLE
        DECODER_INIT_FAILED, DECODER_QUERY_FAILED, DECODING_FAILED,
        DECODING_FORMAT_EXCEEDS_CAPABILITIES, DECODING_FORMAT_UNSUPPORTED,
        DECODING_RESOURCES_RECLAIMED -> PlaybackErrorKind.DECODE
        AUDIO_TRACK_INIT_FAILED, AUDIO_TRACK_WRITE_FAILED -> PlaybackErrorKind.AUDIO_OUTPUT
        in DRM_UNSPECIFIED..6999 -> PlaybackErrorKind.DRM
        else -> PlaybackErrorKind.UNKNOWN
    }

    /**
     * Chinese message for an ExoPlayer error.
     *
     * A [AppException] anywhere in the cause chain wins: it already carries the
     * repository's specific message (e.g. "音频地址解析失败").
     */
    fun message(errorCode: Int, cause: Throwable? = null): String {
        appExceptionIn(cause)?.let { return it.userMessage }
        return when (kindOf(errorCode)) {
            PlaybackErrorKind.NETWORK -> {
                val io = generateSequence(cause) { it.cause }
                    .filterIsInstance<java.io.IOException>()
                    .firstOrNull()
                when {
                    errorCode == IO_NETWORK_CONNECTION_TIMEOUT -> MSG_TIMEOUT
                    errorCode == IO_NETWORK_CONNECTION_FAILED -> MSG_NETWORK
                    io is java.net.UnknownHostException -> MSG_NETWORK
                    io is java.net.SocketTimeoutException -> MSG_TIMEOUT
                    else -> MSG_HTTP
                }
            }
            PlaybackErrorKind.RESOLVE_FAILED -> MSG_RESOLVE
            PlaybackErrorKind.AUDIO_UNAVAILABLE -> MSG_UNAVAILABLE
            PlaybackErrorKind.DECODE -> MSG_DECODE
            PlaybackErrorKind.AUDIO_OUTPUT -> MSG_AUDIO_OUTPUT
            PlaybackErrorKind.DRM -> MSG_DRM
            PlaybackErrorKind.UNKNOWN -> {
                val io = generateSequence(cause) { it.cause }.filterIsInstance<java.io.IOException>().firstOrNull()
                io?.toUserMessage() ?: MSG_UNKNOWN
            }
        }
    }

    /** Message for a failure that originated in the stream resolver (no ExoPlayer error code). */
    fun resolveFailureMessage(cause: Throwable?): String =
        appExceptionIn(cause)?.userMessage ?: MSG_RESOLVE

    private fun appExceptionIn(cause: Throwable?): AppException? =
        generateSequence(cause) { it.cause }.filterIsInstance<AppException>().firstOrNull()
}
