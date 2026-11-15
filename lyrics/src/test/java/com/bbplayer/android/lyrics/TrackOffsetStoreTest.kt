package com.bbplayer.android.lyrics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Unit tests for per-track lyric offset persistence (AC G2): round trip, persistence, clamping, corrupt files. */
class TrackOffsetStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(): FileTrackOffsetStore =
        FileTrackOffsetStore(File(temporaryFolder.newFolder(), "lyric_offsets.json"))

    @Test
    fun `file store round trips offsets`() {
        val store = store()

        store.set("BV1xx411c7mD:123456", 250)
        store.set("BV1xx411c7mD:654321", -300)

        assertEquals(250, store.get("BV1xx411c7mD:123456"))
        assertEquals(-300, store.get("BV1xx411c7mD:654321"))
        assertEquals(0, store.get("missing:track"))
        assertEquals(2, store.snapshot().size)
    }

    @Test
    fun `epoch persists across store instances and uses the json file`() {
        val dir = temporaryFolder.newFolder("persist")
        val file = File(dir, "lyric_offsets.json")
        FileTrackOffsetStore(file).set("key:1", 400)

        assertTrue(file.isFile)
        assertTrue(file.readText(Charsets.UTF_8).contains("key:1"))

        val reopened = FileTrackOffsetStore(file)
        assertEquals(400, reopened.get("key:1"))
    }

    @Test
    fun `offsets are clamped to plus minus five seconds`() {
        val store = store()

        store.set("a:1", 9_999)
        store.set("b:2", -9_999)

        assertEquals(MAX_TRACK_OFFSET_MS, store.get("a:1"))
        assertEquals(-MAX_TRACK_OFFSET_MS, store.get("b:2"))
        assertEquals(5_000, clampTrackOffset(12_345))
        assertEquals(0, clampTrackOffset(0))
    }

    @Test
    fun `zero offset removes the entry`() {
        val store = store()
        store.set("a:1", 300)

        store.set("a:1", 0)

        assertEquals(0, store.get("a:1"))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `corrupt file is treated as empty and can be rewritten`() {
        val dir = temporaryFolder.newFolder("corrupt")
        val file = File(dir, "lyric_offsets.json")
        file.writeText("{not valid json", Charsets.UTF_8)
        val store = FileTrackOffsetStore(file)

        assertEquals(0, store.get("a:1"))
        assertTrue(store.snapshot().isEmpty())

        store.set("a:1", 150)
        assertEquals(150, store.get("a:1"))
        assertEquals(150, FileTrackOffsetStore(file).get("a:1"))
    }

    @Test
    fun `clear removes every persisted offset`() {
        val dir = temporaryFolder.newFolder("clear")
        val file = File(dir, "lyric_offsets.json")
        val store = FileTrackOffsetStore(file)
        store.set("a:1", 100)
        store.set("b:2", 200)

        store.clear()

        assertEquals(0, store.get("a:1"))
        assertTrue(store.snapshot().isEmpty())
        assertTrue(!file.exists())
    }

    @Test
    fun `codec ignores invalid entries and clamps out of range values`() {
        val decoded = TrackOffsetCodec.decode(
            """{"ok:1":123,"bad:2":"oops","high:3":99999,"low:4":-99999,"null:5":null}""",
        )

        assertEquals(123, decoded["ok:1"])
        assertEquals(MAX_TRACK_OFFSET_MS, decoded["high:3"])
        assertEquals(-MAX_TRACK_OFFSET_MS, decoded["low:4"])
        assertTrue(decoded["bad:2"] == null)
        assertTrue(decoded["null:5"] == null)

        assertEquals(emptyMap<String, Int>(), TrackOffsetCodec.decode("not json"))
        assertEquals(123, TrackOffsetCodec.decode(TrackOffsetCodec.encode(mapOf("ok:1" to 123)))["ok:1"])
    }

    @Test
    fun `in memory store round trips and clamps`() {
        val store = InMemoryTrackOffsetStore(mapOf("a:1" to 9_000))
        assertEquals(MAX_TRACK_OFFSET_MS, store.get("a:1"))

        store.set("b:2", -700)
        assertEquals(-700, store.get("b:2"))
        assertEquals(2, store.snapshot().size)

        store.clear()
        assertTrue(store.snapshot().isEmpty())
    }
}
