package com.bbplayer.android.playback.download

/**
 * Byte-progress throttling for the download loop: report at most every
 * [minIntervalMs] **or** whenever another [minFractionDelta] (2%) of the file
 * has arrived, whichever comes first. The final 100% progress tick is always
 * emitted so the UI never gets stuck at 99%.
 *
 * Pure arithmetic, JVM tested.
 */
class DownloadProgressThrottle(
    private val minIntervalMs: Long = 250L,
    private val minFractionDelta: Float = 0.02f,
) {
    private var lastEmitAtMs: Long = Long.MIN_VALUE
    private var lastFraction: Float = -1f
    private var lastDownloadedBytes: Long = -1L

    /** @return true when the caller should publish a new progress value. */
    fun shouldEmit(nowMs: Long, downloadedBytes: Long, totalBytes: Long): Boolean {
        val downloaded = downloadedBytes.coerceAtLeast(0L)
        if (downloaded == lastDownloadedBytes) return false
        val fraction = fractionOf(downloaded, totalBytes)
        val finished = fraction >= 1f && lastFraction < 1f
        val enoughBytes = lastFraction < 0f || (fraction - lastFraction) >= minFractionDelta
        val enoughTime = lastEmitAtMs == Long.MIN_VALUE || (nowMs - lastEmitAtMs) >= minIntervalMs
        if (!finished && !enoughBytes && !enoughTime) return false
        lastEmitAtMs = nowMs
        lastFraction = fraction
        lastDownloadedBytes = downloaded
        return true
    }

    /** Forget the previous sample (e.g. after a Range resume restart). */
    fun reset() {
        lastEmitAtMs = Long.MIN_VALUE
        lastFraction = -1f
        lastDownloadedBytes = -1L
    }

    companion object {
        /** 0 when the server did not send a Content-Length (chunked transfer). */
        fun fractionOf(downloadedBytes: Long, totalBytes: Long): Float {
            if (totalBytes <= 0L) return 0f
            val fraction = downloadedBytes.toDouble() / totalBytes.toDouble()
            return fraction.toFloat().coerceIn(0f, 1f)
        }
    }
}
