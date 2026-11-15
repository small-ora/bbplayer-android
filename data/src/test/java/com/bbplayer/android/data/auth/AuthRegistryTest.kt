package com.bbplayer.android.data.auth

import com.bbplayer.android.core.contracts.AuthCookieProvider
import com.bbplayer.android.data.cacheIdentityFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 登录态注册表 + 接口缓存身份标签。 */
class AuthRegistryTest {

    private val registry = AuthRegistry()

    @Test
    fun `未登录时无 cookie 且身份为 anon`() {
        assertNull(registry.cookieHeader())
        assertNull(registry.csrfToken())
        assertNull(registry.cookieValue("DedeUserID"))
        assertFalse(registry.isLoggedIn())
        assertEquals(AuthRegistry.ANONYMOUS, registry.identityTag())
    }

    @Test
    fun `更新后暴露 cookie、csrf 与登录态`() {
        registry.update(
            mapOf(
                "SESSDATA" to "sess",
                "bili_jct" to "jct",
                "DedeUserID" to "42",
                "buvid3" to "must-be-dropped",
            ),
        )
        assertTrue(registry.isLoggedIn())
        assertEquals("jct", registry.csrfToken())
        assertEquals("42", registry.cookieValue("DedeUserID"))
        assertNull("非登录 cookie 不进入注册表", registry.cookieValue("buvid3"))
        val header = registry.cookieHeader().orEmpty()
        assertTrue(header.contains("SESSDATA=sess"))
        assertTrue(header.contains("bili_jct=jct"))
        assertFalse(header.contains("buvid3"))
    }

    @Test
    fun `身份标签优先使用 mid`() {
        registry.update(mapOf("SESSDATA" to "sess", "DedeUserID" to "10086"))
        assertEquals("u:10086", registry.identityTag())
    }

    @Test
    fun `缺少 mid 时使用 SESSDATA 短 hash 且不泄露明文`() {
        registry.update(mapOf("SESSDATA" to "very-secret-session"))
        val tag = registry.identityTag()
        assertTrue("应为 hash 前缀: $tag", tag.startsWith("h:"))
        assertEquals(14, tag.length)
        assertFalse(tag.contains("very-secret-session"))
    }

    @Test
    fun `清空后回到未登录`() {
        registry.update(mapOf("SESSDATA" to "sess", "DedeUserID" to "42"))
        registry.clear()
        assertFalse(registry.isLoggedIn())
        assertNull(registry.cookieHeader())
        assertEquals(AuthRegistry.ANONYMOUS, registry.identityTag())
    }

    @Test
    fun `缓存身份区分登录与未登录`() {
        val anon = object : AuthCookieProvider {
            override fun cookieHeader() = null
            override fun cookieValue(name: String) = null
            override fun csrfToken() = null
            override fun isLoggedIn() = false
        }
        assertEquals("anon", cacheIdentityFor(anon))

        val byMid = object : AuthCookieProvider {
            override fun cookieHeader() = "SESSDATA=s; bili_jct=j; DedeUserID=7"
            override fun cookieValue(name: String) = if (name == "DedeUserID") "7" else null
            override fun csrfToken() = "j"
            override fun isLoggedIn() = true
        }
        assertEquals("u:7", cacheIdentityFor(byMid))

        val noMid = object : AuthCookieProvider {
            override fun cookieHeader() = "SESSDATA=another-secret"
            override fun cookieValue(name: String) = null
            override fun csrfToken() = null
            override fun isLoggedIn() = true
        }
        val tag = cacheIdentityFor(noMid)
        assertTrue(tag.startsWith("h:"))
        assertFalse(tag.contains("another-secret"))

        val registryIdentity = AuthRegistry().apply {
            update(mapOf("SESSDATA" to "s", "DedeUserID" to "7"))
        }
        assertEquals("u:7", cacheIdentityFor(registryIdentity))
    }
}
