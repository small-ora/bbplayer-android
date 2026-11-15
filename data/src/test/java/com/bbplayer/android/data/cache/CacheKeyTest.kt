package com.bbplayer.android.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 缓存 key 规范化：参数顺序 / 编码 / 身份参数 / endpoint 隔离。 */
class CacheKeyTest {

    @Test
    fun `参数顺序不影响 key`() {
        val first = CacheKey.of(
            "/x/web-interface/search/type",
            linkedMapOf("keyword" to "夜曲", "page" to "1", "page_size" to "20"),
        )
        val second = CacheKey.of(
            "/x/web-interface/search/type",
            linkedMapOf("page_size" to "20", "keyword" to "夜曲", "page" to "1"),
        )
        assertEquals(first, second)
    }

    @Test
    fun `中文与空格被稳定编码`() {
        val key = CacheKey.of("/x/search", mapOf("keyword" to "周杰伦 夜曲"))
        assertTrue("应做 URL 编码: $key", key.contains("%E5%91%A8%E6%9D%B0%E4%BC%A6"))
        assertTrue("空格应编码", key.contains("%20") || key.contains("+"))
    }

    @Test
    fun `buvid3 等身份参数不进入 key`() {
        val without = CacheKey.of("/x/web-interface/view", mapOf("bvid" to "BV1xx"))
        val withBuvid = CacheKey.of(
            "/x/web-interface/view",
            mapOf("bvid" to "BV1xx", "buvid3" to "random-per-launch", "COOKIE" to "c=1"),
        )
        assertEquals(without, withBuvid)
        assertTrue(!withBuvid.contains("random-per-launch"))
    }

    @Test
    fun `不同 endpoint 或不同参数得到不同 key`() {
        val view = CacheKey.of("/x/web-interface/view", mapOf("bvid" to "BV1"))
        val pagelist = CacheKey.of("/x/player/pagelist", mapOf("bvid" to "BV1"))
        val otherBvid = CacheKey.of("/x/web-interface/view", mapOf("bvid" to "BV2"))
        assertNotEquals(view, pagelist)
        assertNotEquals(view, otherBvid)
    }

    @Test
    fun `无参数时 key 就是 endpoint`() {
        assertEquals("/x/web-interface/search/square", CacheKey.of("/x/web-interface/search/square", emptyMap()))
    }

    @Test
    fun `sha256 摘要正确且文件名为 64 位十六进制`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
        val fileName = CacheKey.fileName("any-key")
        assertTrue(fileName.matches(Regex("[0-9a-f]{64}\\.json")))
    }
}
