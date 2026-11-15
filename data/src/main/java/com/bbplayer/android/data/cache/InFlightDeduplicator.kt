package com.bbplayer.android.data.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 并发请求合并（single-flight）。
 *
 * 同一个 key 同时有多个调用方时，只有**第一个**真正执行 [run]，
 * 其余调用方挂起等待同一次结果：成功时共享返回值，失败时同样收到原异常；
 * 无论成功失败，执行结束后立即移除 in-flight 记录（失败后可重试）。
 *
 * 缓存命中不经过这里——[CachedRequestExecutor] 先查缓存，未命中才做去重。
 */
class InFlightDeduplicator {

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> run(key: String, block: suspend () -> T): T {
        val isOwner: Boolean
        val deferred: CompletableDeferred<Any?>
        mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                deferred = existing
                isOwner = false
            } else {
                deferred = CompletableDeferred()
                inFlight[key] = deferred
                isOwner = true
            }
        }
        if (!isOwner) return deferred.await() as T

        try {
            val value = block()
            deferred.complete(value)
            return value
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }
}
