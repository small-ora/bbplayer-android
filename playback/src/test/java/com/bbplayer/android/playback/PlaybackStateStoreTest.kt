package com.bbplayer.android.playback

import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.Track
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateStoreTest {
    private val tempFiles = mutableListOf<File>()

    private fun newStore(): Pair<PlaybackStateStore, File> {
        val file = File.createTempFile("playback_state", ".json").also {
            it.delete() // start from "no file"
            tempFiles += it
        }
        return PlaybackStateStore(file) to file
    }

    private fun track(key: String, cid: Long = key.hashCode().toLong()) = Track(
        bvid = key,
        cid = cid,
        title = "title-$key",
        uploader = "up",
        coverUrl = "https://i0.hdslb.com/$key.jpg",
        durationMs = 123_456L,
    )

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun `round trips queue index position mode and speed`() = runTest {
        val (store, file) = newStore()
        val snapshot = PersistedPlayback(
            queue = listOf(track("BV1"), track("BV2"), track("BV3")),
            currentIndex = 1,
            positionMs = 42_000L,
            playMode = PlayMode.SHUFFLE,
            speed = 1.5f,
        )
        store.save(snapshot)
        assertTrue(file.exists())

        val loaded = store.load()!!
        assertEquals(snapshot.queue, loaded.queue)
        assertEquals(1, loaded.currentIndex)
        assertEquals(42_000L, loaded.positionMs)
        assertEquals(PlayMode.SHUFFLE, loaded.playMode)
        assertEquals(1.5f, loaded.speed, 0.0001f)
        assertEquals(PersistedPlayback.CURRENT_VERSION, loaded.version)
    }

    @Test
    fun `missing file loads as null`() = runTest {
        val (store, _) = newStore()
        assertNull(store.load())
    }

    @Test
    fun `corrupted file loads as null instead of crashing`() = runTest {
        val (store, file) = newStore()
        file.writeText("{not json at all")
        assertNull(store.load())
    }

    @Test
    fun `unknown json fields are ignored and defaults are applied`() = runTest {
        val (store, file) = newStore()
        file.writeText(
            """{"version":1,"queue":[{"bvid":"BV1","cid":7,"title":"t"}],
               "currentIndex":0,"positionMs":100,"unknownField":true}""",
        )
        val loaded = store.load()!!
        assertEquals(1, loaded.queue.size)
        assertEquals(PlayMode.SEQUENCE, loaded.playMode)
        assertEquals(1f, loaded.speed, 0.0001f)
    }

    @Test
    fun `sanitize clamps bad index position and speed`() {
        val queue = listOf(track("BV1"), track("BV2"))
        val dirty = PersistedPlayback(
            queue = queue,
            currentIndex = 99,
            positionMs = -5L,
            speed = 9f,
        ).sanitized()
        assertEquals(0, dirty.currentIndex)
        assertEquals(0L, dirty.positionMs)
        assertEquals(PlaybackMapping.MAX_SPEED, dirty.speed, 0.0001f)

        val empty = PersistedPlayback(queue = emptyList(), currentIndex = 3).sanitized()
        assertEquals(-1, empty.currentIndex)
    }

    @Test
    fun `sanitize drops unusable tracks`() {
        val dirty = PersistedPlayback(
            queue = listOf(
                Track(bvid = "", cid = 1L, title = "invalid"),
                Track(bvid = "BV2", cid = 0L, title = "invalid"),
                Track(bvid = "BV3", cid = 2L, title = "ok"),
            ),
            currentIndex = 2,
        ).sanitized()
        assertEquals(1, dirty.queue.size)
        assertEquals("BV3", dirty.queue.first().bvid)
        assertEquals(0, dirty.currentIndex)
    }

    @Test
    fun `clear removes the persisted file`() = runTest {
        val (store, file) = newStore()
        store.save(PersistedPlayback(queue = listOf(track("BV1")), currentIndex = 0))
        assertTrue(file.exists())
        store.clear()
        assertNull(store.load())
    }
}
