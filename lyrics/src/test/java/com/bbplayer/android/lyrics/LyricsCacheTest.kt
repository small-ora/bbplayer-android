package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Unit tests for the JSON cache codec / stores (AC D2, no Android dependency). */
class LyricsCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun sampleLyrics(source: String = "qq") = LrcParser.parse(
        """
        [offset:+100]
        [00:01.00]Hello
        [00:01.00]你好
        [00:02.00]World
        """.trimIndent(),
        source = source,
    )

    @Test
    fun `file store round trips a cache entry`() {
        val dir = temporaryFolder.newFolder("lyrics")
        val store = FileLyricsCacheStore(dir)
        val entry = CachedLyrics(
            trackKey = "BV1xx411c7mD:123456",
            lyrics = sampleLyrics(),
            source = "qq",
            fetchedAt = 1_700_000_000_000L,
            query = "夜に駆ける",
        )

        store.write(entry)
        val restored = store.read(entry.trackKey)

        assertNotNull(restored)
        assertEquals(entry.trackKey, restored!!.trackKey)
        assertEquals("qq", restored.source)
        assertEquals(1_700_000_000_000L, restored.fetchedAt)
        assertEquals("夜に駆ける", restored.query)
        assertEquals(entry.lyrics.offsetMs, restored.lyrics.offsetMs)
        assertEquals(2, restored.lyrics.lines.size)
        assertEquals("你好", restored.lyrics.lines[0].translation)
        assertEquals("Hello", restored.lyrics.lines[0].text)
        assertTrue(dir.listFiles()!!.any { it.name.endsWith(".json") })
    }

    @Test
    fun `file store returns null for a missing key`() {
        val store = FileLyricsCacheStore(temporaryFolder.newFolder("empty"))
        assertNull(store.read("does:not-exist"))
    }

    @Test
    fun `file store ignores corrupt json`() {
        val dir = temporaryFolder.newFolder("corrupt")
        val key = "BV1xx411c7mD:1"
        java.io.File(dir, sha1Hex(key) + ".json").writeText("{not json", Charsets.UTF_8)

        val store = FileLyricsCacheStore(dir)
        assertNull(store.read(key))
    }

    @Test
    fun `file store reports size and clears entries`() {
        val dir = temporaryFolder.newFolder("size")
        val store = FileLyricsCacheStore(dir)
        store.write(CachedLyrics("a:1", sampleLyrics()))
        store.write(CachedLyrics("b:2", sampleLyrics()))

        assertTrue(store.sizeBytes() > 0L)

        store.clear()
        assertEquals(0L, store.sizeBytes())
        assertNull(store.read("a:1"))
    }

    @Test
    fun `cache file name is stable sha1 of the track key`() {
        assertEquals(sha1Hex("BV1xx:1"), sha1Hex("BV1xx:1"))
        assertNotEquals(sha1Hex("BV1xx:1"), sha1Hex("BV1xx:2"))
        assertEquals(40, sha1Hex("BV1xx:1").length)
        assertTrue(sha1Hex("BV1xx:1").matches(Regex("[0-9a-f]{40}")))
    }

    @Test
    fun `codec json contains provenance fields`() {
        val encoded = LyricsCacheCodec.encode(
            CachedLyrics("k:1", Lyrics(emptyList(), source = "manual"), source = "manual", fetchedAt = 42L),
        )
        assertTrue(encoded.contains("\"source\""))
        assertTrue(encoded.contains("\"manual\""))
        assertTrue(encoded.contains("\"fetchedAt\""))
        assertNotNull(LyricsCacheCodec.decode(encoded))
    }

    @Test
    fun `in memory store round trips entries`() {
        val store = InMemoryLyricsCacheStore()
        store.write(CachedLyrics("k:1", sampleLyrics("manual"), source = "manual"))

        assertEquals("manual", store.read("k:1")?.source)
        assertTrue(store.sizeBytes() > 0L)

        store.clear()
        assertNull(store.read("k:1"))
    }
}
