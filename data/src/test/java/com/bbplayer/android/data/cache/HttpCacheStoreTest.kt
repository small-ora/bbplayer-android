package com.bbplayer.android.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [HttpCacheStore]：TTL / LRU / 磁盘上限 / 损坏文件 / 持久化 / 统计。 */
class HttpCacheStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var now = 1_000_000L

    private fun store(
        dir: File = temp.newFolder(),
        maxEntries: Int = 512,
        maxBytes: Long = 8L * 1024L * 1024L,
    ) = HttpCacheStore(dir, maxEntries, maxBytes) { now }

    @Test
    fun `未过期命中并统计`() {
        val cache = store()
        cache.put("k1", "v1", ttlMs = 10_000L)
        assertEquals("v1", cache.get("k1"))
        assertEquals(1, cache.entryCount())
        assertTrue(cache.sizeBytes() > 0L)
        assertNull(cache.get("missing"))
    }

    @Test
    fun `TTL 到期后不命中且条目被移除`() {
        val cache = store()
        cache.put("k1", "v1", ttlMs = 1_000L)
        now += 999L
        assertEquals("v1", cache.get("k1"))
        now += 1L // 恰好到期
        assertNull(cache.get("k1"))
        assertEquals(0, cache.entryCount())
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun `LRU 按条数淘汰最久未访问`() {
        val cache = store(maxEntries = 2)
        cache.put("a", "1", ttlMs = 60_000L)
        cache.put("b", "2", ttlMs = 60_000L)
        // 访问 a，使 b 成为最久未访问
        assertEquals("1", cache.get("a"))
        cache.put("c", "3", ttlMs = 60_000L)
        assertEquals(2, cache.entryCount())
        assertNotNull(cache.get("a"))
        assertNull("b 应被 LRU 淘汰", cache.get("b"))
        assertNotNull(cache.get("c"))
    }

    @Test
    fun `磁盘总大小上限淘汰`() {
        val dir = temp.newFolder()
        val cache = store(dir = dir, maxBytes = 1_200L)
        val payload = "x".repeat(400)
        repeat(5) { index -> cache.put("key-$index", payload, ttlMs = 60_000L) }
        assertTrue("sizeBytes 超上限", cache.sizeBytes() <= 1_200L)
        assertTrue("应触发淘汰", cache.entryCount() < 5)
        assertTrue("至少保留一条", cache.entryCount() >= 1)
        val diskBytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
        assertTrue("磁盘实际占用超上限: $diskBytes", diskBytes <= 1_200L)
    }

    @Test
    fun `损坏磁盘文件被忽略且不影响新写入`() {
        val dir = temp.newFolder()
        File(dir, "garbage.json").writeText("not-a-json{{{")
        File(dir, "missing-field.json").writeText("""{"key":"k","expiresAt":123}""")
        File(dir, "half.json.tmp").writeText("{\"key\":")
        val cache = store(dir = dir)
        assertEquals(0, cache.entryCount())
        assertNull(cache.get("k"))
        // 损坏文件不应影响后续正常读写
        cache.put("ok", "value", ttlMs = 60_000L)
        assertEquals("value", cache.get("ok"))
        assertEquals(1, cache.entryCount())
    }

    @Test
    fun `clear 返回清除条数且目录被清空`() {
        val dir = temp.newFolder()
        val cache = store(dir = dir)
        cache.put("a", "1", ttlMs = 60_000L)
        cache.put("b", "2", ttlMs = 60_000L)
        cache.put("c", "3", ttlMs = 60_000L)
        // 额外塞一个损坏文件：不计入条数，但会被一并清理
        File(dir, "junk.json").writeText("junk")

        assertEquals(3, cache.clear())
        assertEquals(0, cache.entryCount())
        assertEquals(0L, cache.sizeBytes())
        val remaining = dir.listFiles()?.size ?: 0
        assertEquals("目录应被清空", 0, remaining)
        assertEquals(0, cache.clear())
    }

    @Test
    fun `磁盘持久化跨实例读取`() {
        val dir = temp.newFolder()
        val first = store(dir = dir)
        first.put("k1", "persisted", ttlMs = 60_000L)

        val second = store(dir = dir)
        assertEquals("persisted", second.get("k1"))
        assertEquals(1, second.entryCount())
        assertTrue(second.sizeBytes() > 0L)
    }

    @Test
    fun `跨实例加载时丢弃已过期条目`() {
        val dir = temp.newFolder()
        store(dir = dir).put("k1", "v1", ttlMs = 1_000L)
        now += 5_000L
        val reloaded = store(dir = dir)
        assertEquals(0, reloaded.entryCount())
        assertNull(reloaded.get("k1"))
    }

    @Test
    fun `同 key 覆盖更新值且条数不增长`() {
        val cache = store()
        cache.put("k", "v1", ttlMs = 60_000L)
        cache.put("k", "v2", ttlMs = 60_000L)
        assertEquals("v2", cache.get("k"))
        assertEquals(1, cache.entryCount())
    }

    @Test
    fun `文件名使用 sha256 且无特殊字符`() {
        val dir = temp.newFolder()
        val cache = store(dir = dir)
        cache.put("/x/web-interface/view?bvid=BV1xx/中文", "v", ttlMs = 60_000L)
        val files = dir.listFiles().orEmpty()
        assertEquals(1, files.size)
        assertTrue(files[0].name.matches(Regex("[0-9a-f]{64}\\.json")))
    }
}
