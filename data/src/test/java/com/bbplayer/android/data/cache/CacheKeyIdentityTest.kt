package com.bbplayer.android.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 缓存 key 的登录身份隔离：登录/未登录/不同账号互不复用。 */
class CacheKeyIdentityTest {

    private val path = "/x/web-interface/nav"
    private val params = mapOf("bvid" to "BV1xx")

    @Test
    fun `不同登录身份得到不同 key`() {
        val anon = CacheKey.of(path, params, identity = "anon")
        val userA = CacheKey.of(path, params, identity = "u:1")
        val userB = CacheKey.of(path, params, identity = "u:2")
        assertNotEquals(anon, userA)
        assertNotEquals(userA, userB)
        assertNotEquals(anon, userB)
    }

    @Test
    fun `未传身份时与匿名身份不同`() {
        val plain = CacheKey.of(path, params)
        val anon = CacheKey.of(path, params, identity = "anon")
        assertNotEquals(plain, anon)
    }

    @Test
    fun `身份只作为前缀且不含 Cookie 明文`() {
        val key = CacheKey.of(path, params, identity = "u:10086")
        assertEquals("[u:10086] $path?bvid=BV1xx", key)
        assertFalse(key.contains("SESSDATA"))
        assertFalse(key.contains("bili_jct"))
    }

    @Test
    fun `同一身份下参数顺序仍稳定`() {
        assertEquals(
            CacheKey.of("/x/a", linkedMapOf("b" to "2", "a" to "1"), identity = "u:1"),
            CacheKey.of("/x/a", linkedMapOf("a" to "1", "b" to "2"), identity = "u:1"),
        )
    }

    @Test
    fun `空白身份被忽略`() {
        assertEquals(CacheKey.of(path, params), CacheKey.of(path, params, identity = "   "))
    }
}
