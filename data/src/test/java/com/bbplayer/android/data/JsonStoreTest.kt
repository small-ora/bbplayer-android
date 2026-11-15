package com.bbplayer.android.data

import com.bbplayer.android.core.model.FavoriteEntry
import com.bbplayer.android.core.model.Playlist
import com.bbplayer.android.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun track(bvid: String = "BV1q4FTeBEMj", cid: Long = 11L) = Track(
        bvid = bvid,
        cid = cid,
        title = "夜曲",
        uploader = "UP主",
        durationMs = 228_000L,
    )

    private fun store(dir: File) = JsonFileStore(File(dir, "library.json"), LibraryData.serializer())

    @Test
    fun `JSON 序列化往返`() {
        val data = LibraryData(
            playlists = listOf(
                Playlist(
                    id = "playlist-1",
                    name = "我的歌单",
                    createdAt = 1L,
                    updatedAt = 2L,
                    tracks = listOf(track()),
                ),
            ),
            favorites = listOf(FavoriteEntry(track(cid = 22L), addedAt = 3L)),
        )
        val store = store(temp.newFolder())
        store.write(data)
        assertEquals(data, store.read())
    }

    @Test
    fun `文件不存在时返回 null`() {
        assertNull(store(temp.newFolder()).read())
    }

    @Test
    fun `损坏文件按空数据处理且可被覆盖`() {
        val dir = temp.newFolder()
        val store = store(dir)
        File(dir, "library.json").writeText("{not-json")
        assertNull(store.read())

        val data = LibraryData(playlists = listOf(Playlist(id = "p", name = "歌单")))
        store.write(data)
        assertEquals(data, store.read())
    }

    @Test
    fun `原子写入不残留临时文件`() {
        val dir = temp.newFolder()
        val store = store(dir)
        store.write(LibraryData())
        store.write(LibraryData(playlists = listOf(Playlist(id = "p", name = "歌单"))))
        assertFalse(File(dir, "library.json.tmp").exists())
        assertTrue(File(dir, "library.json").exists())
        assertEquals(1, store.read()!!.playlists.size)
    }
}
