package com.bbplayer.android.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 加密 Cookie 存储：save / load / clear / 损坏 / 密钥失效。 */
class SecureCookieStoreTest {

    private val cookies = mapOf(
        "SESSDATA" to "sess-secret",
        "bili_jct" to "jct-secret",
        "DedeUserID" to "42",
    )

    @Test
    fun `保存后能读回`() {
        val storage = MemorySecureStorage()
        val store = memoryCookieStore(storage)
        assertTrue(store.save(cookies))
        assertEquals(cookies, store.load())
        assertTrue("保存后应有密文", storage.value != null)
    }

    @Test
    fun `落盘的是密文而不是 Cookie 明文`() {
        val storage = MemorySecureStorage()
        val store = memoryCookieStore(storage)
        store.save(cookies)
        val encoded = storage.value ?: error("expected ciphertext")
        assertFalse("SESSDATA 明文不能落盘", encoded.contains("SESSDATA"))
        assertFalse("值明文不能落盘", encoded.contains("sess-secret"))
        assertFalse("JSON 不能落盘", encoded.contains("{"))
    }

    @Test
    fun `新实例可读取同一存储`() {
        val storage = MemorySecureStorage()
        memoryCookieStore(storage).save(cookies)
        assertEquals(cookies, memoryCookieStore(storage).load())
    }

    @Test
    fun `clear 删除密文并丢弃密钥`() {
        val storage = MemorySecureStorage()
        val cipher = FakeCookieCipher()
        val store = memoryCookieStore(storage, cipher)
        store.save(cookies)
        store.clear()
        assertNull(storage.value)
        assertTrue(store.load().isEmpty())
        assertEquals(1, cipher.resets)
    }

    @Test
    fun `空 map 等价于清空`() {
        val storage = MemorySecureStorage()
        val store = memoryCookieStore(storage)
        store.save(cookies)
        assertTrue(store.save(emptyMap()))
        assertNull(storage.value)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `Base64 损坏时安全清空并返回空`() {
        val storage = MemorySecureStorage(value = "!!!not-base64!!!")
        val store = memoryCookieStore(storage)
        assertTrue(store.load().isEmpty())
        assertNull("损坏数据应被清除", storage.value)
    }

    @Test
    fun `密钥失效时安全清空并返回空`() {
        val storage = MemorySecureStorage()
        memoryCookieStore(storage).save(cookies)
        val brokenCipher = FakeCookieCipher(failDecrypt = true)
        val brokenStore = memoryCookieStore(storage, brokenCipher)
        assertTrue(brokenStore.load().isEmpty())
        assertNull(storage.value)
        assertEquals(1, brokenCipher.resets)
    }

    @Test
    fun `密文解密成功但不是合法 JSON 时清空`() {
        val storage = MemorySecureStorage()
        val cipher = FakeCookieCipher()
        // 用合法 cipher 加密一段非 JSON 内容
        val notJson = cipher.encrypt("not a json".toByteArray())
        storage.value = java.util.Base64.getEncoder().encodeToString(notJson)
        val store = memoryCookieStore(storage, cipher)
        assertTrue(store.load().isEmpty())
        assertNull(storage.value)
    }

    @Test
    fun `加密失败返回 false 且清空旧数据`() {
        val storage = MemorySecureStorage()
        storage.value = "old"
        val store = memoryCookieStore(storage, FakeCookieCipher(failEncrypt = true))
        assertFalse(store.save(cookies))
        assertNull(storage.value)
    }

    @Test
    fun `写入失败返回 false 不崩溃`() {
        val storage = MemorySecureStorage(failWrite = true)
        val store = memoryCookieStore(storage)
        assertFalse(store.save(cookies))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `读取存储异常时返回空不崩溃`() {
        val storage = MemorySecureStorage(failRead = true)
        assertTrue(memoryCookieStore(storage).load().isEmpty())
    }
}
