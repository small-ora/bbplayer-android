package com.bbplayer.android.data.cache

import android.content.Context
import java.io.File

/**
 * 进程内共享的缓存 / 节流 / 去重实例。
 *
 * 为什么需要共享：各仓储（音乐、评论、字幕、发现…）历史上各自 `BiliApi(context)`，
 * 如果每个实例持有自己的 store/throttle，缓存与清除不统一、请求间隔也无法全局生效。
 * 这里按进程单例，让所有 `BiliApi(context)` 默认复用同一套：
 * - [shared]：磁盘缓存（`filesDir/api_cache`），[BiliNetworkCache] 与 [BiliApi] 共用，
 *   因此设置页「清除缓存」清的就是接口缓存本身；
 * - [sharedThrottle]：全局请求节流（不同仓储的请求也互相排队，真正避免突发）；
 * - [sharedDeduplicator]：全局并发合并（不同仓储请求同一 key 也只发一次）。
 *
 * 显式注入优先：`BiliApi(cache = ...)` / `HttpFetcher(cache = ...)` 可绕开单例（测试用）。
 */
internal object BiliCacheRegistry {

    /** 缓存目录名（位于 `context.filesDir` 下）。 */
    const val DIRECTORY_NAME = "api_cache"

    private val lock = Any()
    private var store: HttpCacheStore? = null
    private var throttle: RequestThrottle? = null
    private var deduplicator: InFlightDeduplicator? = null

    /** 取（首次创建）共享磁盘缓存。 */
    fun shared(context: Context): HttpCacheStore = synchronized(lock) {
        store ?: HttpCacheStore(File(context.applicationContext.filesDir, DIRECTORY_NAME))
            .also { store = it }
    }

    /** 已创建的共享缓存；尚未初始化时返回 null（外站 HttpFetcher 默认使用它）。 */
    fun sharedOrNull(): HttpCacheStore? = synchronized(lock) { store }

    /** 全局请求节流器。 */
    fun sharedThrottle(): RequestThrottle = synchronized(lock) {
        throttle ?: RequestThrottle().also { throttle = it }
    }

    /** 全局并发合并器。 */
    fun sharedDeduplicator(): InFlightDeduplicator = synchronized(lock) {
        deduplicator ?: InFlightDeduplicator().also { deduplicator = it }
    }
}
