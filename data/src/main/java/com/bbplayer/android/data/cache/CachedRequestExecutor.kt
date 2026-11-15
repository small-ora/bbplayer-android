package com.bbplayer.android.data.cache

/**
 * 读穿缓存 + 并发合并 + 节流的统一执行器。
 *
 * 执行顺序（决定了「缓存命中不重复请求」与「错误不缓存」）：
 * 1. 命中未过期缓存 → 直接返回（不节流、不发请求）；
 * 2. 未命中 → [InFlightDeduplicator] 合并相同 key 的并发请求（只发一次）；
 * 3. 真正发请求前先 [RequestThrottle.acquire] 拉开间隔；
 * 4. [fetch] **成功**返回后才写缓存；抛异常（网络错误 / 非 2xx / code != 0）
 *    直接向上传播且不落缓存（下次重试）；
 * 5. 返回 null / ttl 为 null 时不缓存。
 *
 * @param cache 为 null 时等同禁用缓存（仅保留去重/节流）。
 * @param throttle 为 null 时不节流（外站接口用）。
 */
internal class CachedRequestExecutor(
    private val cache: HttpCacheStore? = null,
    private val throttle: RequestThrottle? = null,
    private val deduplicator: InFlightDeduplicator = InFlightDeduplicator(),
) {

    suspend fun <T> run(
        key: String,
        ttlMs: Long?,
        decode: (String) -> T?,
        encode: (T) -> String,
        throttled: Boolean = true,
        fetch: suspend () -> T,
    ): T {
        val store = cache
        val ttl = ttlMs?.takeIf { it > 0L }
        if (store != null && ttl != null) {
            val raw = store.get(key)
            if (raw != null) {
                val decoded = runCatching { decode(raw) }.getOrNull()
                if (decoded != null) return decoded
            }
        }
        return deduplicator.run(key) {
            if (throttled) throttle?.acquire()
            val value = fetch()
            if (store != null && ttl != null && value != null) {
                runCatching { store.put(key, encode(value), ttl) }
            }
            value
        }
    }
}
