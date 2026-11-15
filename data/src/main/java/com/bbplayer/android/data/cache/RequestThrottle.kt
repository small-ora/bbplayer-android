package com.bbplayer.android.data.cache

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * 请求节流器：保证相邻两次真实网络请求之间至少间隔
 * [minIntervalMs] ± [jitterMs]（默认 350ms ± 100ms，即 250–450ms）。
 *
 * - **并发请求串行化**：多个协程同时 [acquire] 时按 Mutex 排队，各自补足间隔，
 *   避免同一时刻多个请求一起打出去触发 B 站安全策略；
 * - 抖动让请求间隔不呈固定节奏，降低被识别为脚本的概率；
 * - [clock] / [sleep] / [jitterProvider] 均可注入，测试用 fake clock 断言节流时长，
 *   不需要真的等待。
 *
 * 只对真实网络请求调用 [acquire]（缓存命中不节流）。
 */
class RequestThrottle(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val jitterMs: Long = DEFAULT_JITTER_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val jitterProvider: () -> Long = {
        if (jitterMs <= 0L) 0L else Random.nextLong(-jitterMs, jitterMs + 1)
    },
) {

    private val mutex = Mutex()
    private var lastStartAt: Long? = null

    /** 获取一次请求许可：必要时挂起补足间隔。 */
    suspend fun acquire() {
        mutex.withLock {
            val last = lastStartAt
            if (last != null) {
                val interval = (minIntervalMs + jitterProvider()).coerceAtLeast(0L)
                val waitMs = last + interval - clock()
                if (waitMs > 0L) sleep(waitMs)
            }
            lastStartAt = clock()
        }
    }

    /** 节流后执行 [block]。 */
    suspend fun <T> run(block: suspend () -> T): T {
        acquire()
        return block()
    }

    companion object {
        /** 相邻请求最小间隔。 */
        const val DEFAULT_MIN_INTERVAL_MS = 350L

        /** 抖动幅度（±）。 */
        const val DEFAULT_JITTER_MS = 100L
    }
}
