package com.bbplayer.android.data.cache

import android.content.Context
import com.bbplayer.android.core.contracts.NetworkCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [NetworkCacheRepository] 的 data 实现：包一层 [HttpCacheStore]。
 *
 * 与 [com.bbplayer.android.data.BiliApi] 默认使用**同一个**进程级 store
 * （[BiliCacheRegistry.shared]），所以设置页清除的就是接口缓存本身，
 * 不存在「清了一个、另一个还在」的问题。
 *
 * ```kotlin
 * // AppGraph 接线（零额外参数时已是同一 store）：
 * override val networkCacheRepository: NetworkCacheRepository = BiliNetworkCache(appContext)
 * ```
 */
class BiliNetworkCache(private val store: HttpCacheStore) : NetworkCacheRepository {

    /** 生产构造：复用进程级共享缓存（与 `BiliApi(context)` 共用）。 */
    constructor(context: Context) : this(BiliCacheRegistry.shared(context))

    override fun sizeBytes(): Long = store.sizeBytes()

    override fun entryCount(): Int = store.entryCount()

    override suspend fun clear(): Int = withContext(Dispatchers.IO) { store.clear() }
}
