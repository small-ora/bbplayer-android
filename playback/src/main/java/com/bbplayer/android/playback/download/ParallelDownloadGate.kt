package com.bbplayer.android.playback.download

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay

/**
 * Dynamic download concurrency gate.
 *
 * SPEC C2 lets the user change the parallel-download count in settings (1–3),
 * so the limit is re-read from [maxParallelProvider] on every acquisition
 * instead of being frozen into a `Semaphore` at construction time. Waiting
 * coroutines poll cheaply (25 ms) and are cancelled with the download job.
 *
 * Pure coroutine logic (no Android), JVM tested for the upper bound.
 */
class ParallelDownloadGate(
    private val maxParallelProvider: () -> Int,
    private val pollIntervalMs: Long = 25L,
) {
    private val active = AtomicInteger(0)

    val activeCount: Int get() = active.get()

    /** Suspends until a slot is free; pair with [release] (or use [withPermit]). */
    suspend fun acquire() {
        while (true) {
            val limit = limit()
            val current = active.get()
            if (current < limit && active.compareAndSet(current, current + 1)) return
            delay(pollIntervalMs)
        }
    }

    fun release() {
        if (active.get() > 0) active.decrementAndGet()
    }

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        return try {
            block()
        } finally {
            release()
        }
    }

    fun limit(): Int = maxParallelProvider().coerceIn(MIN_PARALLEL, MAX_PARALLEL)

    companion object {
        const val MIN_PARALLEL = 1
        const val MAX_PARALLEL = 3
    }
}
