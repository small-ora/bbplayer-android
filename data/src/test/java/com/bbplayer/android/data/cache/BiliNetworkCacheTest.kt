package com.bbplayer.android.data.cache

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [BiliNetworkCache] 包装 store：统计与 clear 一致性。 */
class BiliNetworkCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `size 与 count 反映 store 状态`() {
        val store = HttpCacheStore(temp.newFolder()) { 1_000_000L }
        val cache = BiliNetworkCache(store)
        assertEquals(0L, cache.sizeBytes())
        assertEquals(0, cache.entryCount())

        store.put("a", "v1", ttlMs = 60_000L)
        store.put("b", "v2", ttlMs = 60_000L)
        assertEquals(2, cache.entryCount())
        assertEquals(store.sizeBytes(), cache.sizeBytes())
    }

    @Test
    fun `clear 返回条数并让 store 归零`() = runTest {
        val store = HttpCacheStore(temp.newFolder()) { 1_000_000L }
        val cache = BiliNetworkCache(store)
        store.put("a", "v1", ttlMs = 60_000L)
        store.put("b", "v2", ttlMs = 60_000L)
        store.put("c", "v3", ttlMs = 60_000L)

        assertEquals(3, cache.clear())
        assertEquals(0, cache.entryCount())
        assertEquals(0L, cache.sizeBytes())
        assertNull(store.get("a"))
    }

    @Test
    fun `TTL 表与需求一致`() {
        assertEquals(10 * BiliCacheTtl.MINUTE, BiliCacheTtl.forBiliPath("/x/web-interface/search/type"))
        assertEquals(24 * BiliCacheTtl.HOUR, BiliCacheTtl.forBiliPath("/x/web-interface/view"))
        assertEquals(24 * BiliCacheTtl.HOUR, BiliCacheTtl.forBiliPath("/x/player/pagelist"))
        assertEquals(30 * BiliCacheTtl.MINUTE, BiliCacheTtl.forBiliPath("/x/player/playurl"))
        assertEquals(10 * BiliCacheTtl.MINUTE, BiliCacheTtl.forBiliPath("/x/web-interface/ranking/v2"))
        assertEquals(30 * BiliCacheTtl.MINUTE, BiliCacheTtl.forBiliPath("/x/web-interface/archive/related"))
        assertEquals(30 * BiliCacheTtl.MINUTE, BiliCacheTtl.forBiliPath("/x/web-interface/search/square"))
        assertEquals(5 * BiliCacheTtl.MINUTE, BiliCacheTtl.forBiliPath("/x/v2/reply"))
        assertEquals(5 * BiliCacheTtl.MINUTE, BiliCacheTtl.forBiliPath("/x/v2/reply/reply"))
        assertEquals(24 * BiliCacheTtl.HOUR, BiliCacheTtl.forBiliPath("/x/player/v2"))
        assertNull("未知/写接口不缓存", BiliCacheTtl.forBiliPath("/x/v2/reply/add"))
    }

    @Test
    fun `外站 URL 的 TTL 与需求一致`() {
        assertEquals(5 * BiliCacheTtl.MINUTE, BiliCacheTtl.forUrl("https://s.search.bilibili.com/main/suggest?term=x"))
        assertEquals(10 * BiliCacheTtl.MINUTE, BiliCacheTtl.forUrl("https://api.github.com/repos/a/b/releases/latest"))
        assertEquals(30 * BiliCacheTtl.MINUTE, BiliCacheTtl.forUrl("https://music.163.com/api/v6/playlist/detail?id=1"))
        assertEquals(30 * BiliCacheTtl.MINUTE, BiliCacheTtl.forUrl("https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg?id=1"))
        assertEquals(24 * BiliCacheTtl.HOUR, BiliCacheTtl.forUrl("https://aisubtitle.hdslb.com/bfs/ai_subtitle/x.json"))
        assertNull(BiliCacheTtl.forUrl("https://example.com/unknown"))
    }
}
