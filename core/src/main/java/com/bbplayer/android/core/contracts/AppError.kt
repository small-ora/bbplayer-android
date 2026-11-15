package com.bbplayer.android.core.contracts

/** Domain error surfaced to the UI with a human-readable Chinese message. */
class AppException(
    val userMessage: String,
    val cause2: Throwable? = null,
    val retryable: Boolean = true,
) : Exception(userMessage, cause2)

fun Throwable.toUserMessage(): String = when (this) {
    is AppException -> userMessage
    is java.net.UnknownHostException -> "网络不可用，请检查网络连接"
    is java.net.SocketTimeoutException -> "网络超时，请重试"
    is java.io.IOException -> "网络请求失败，请重试"
    else -> message?.takeIf { it.isNotBlank() } ?: "出错了，请重试"
}
