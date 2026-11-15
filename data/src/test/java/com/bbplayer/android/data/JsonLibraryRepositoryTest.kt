package com.bbplayer.android.data

import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonLibraryRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun track(bvid: String = "BV1q4FTeBEMj", cid: Long = 11L) = Track(
        bvid = bvid,
        cid = cid,
        title = "夜曲",
        uploader = "UP主",
        coverUrl = "https://i0.hdslb.com/a.jpg",
    )

    private fun newRepository(dir: File) = JsonLibraryRepository(
        store = JsonFileStore(File(dir, "library.json"), LibraryData.serializer()),
        io = Dispatchers.Unconfined,
        clock = { 1_700_000_000_000L },
    )

    @Test
    fun `新建歌单并添加曲目`() = runBlocking {
        val repo = newRepository(temp.newFolder())
        val playlist = repo.createPlaylist("  我的歌单  ")
        assertEquals("我的歌单", playlist.name)
        assertTrue(playlist.id.isNotBlank())

        assertTrue(repo.addTrack(playlist.id, track()))
        val loaded = repo.getPlaylist(playlist.id)!!
        assertEquals(1, loaded.tracks.size)
        assertEquals("夜曲", loaded.tracks.first().title)
        assertTrue(loaded.updatedAt > 0L)
    }

    @Test
    fun `重复添加同一曲目返回 false`() = runBlocking {
        val repo = newRepository(temp.newFolder())
        val playlist = repo.createPlaylist("歌单")
        assertTrue(repo.addTrack(playlist.id, track(cid = 11L)))
        assertFalse(repo.addTrack(playlist.id, track(cid = 11L)))
        // 不同 cid 视为不同曲目
        assertTrue(repo.addTrack(playlist.id, track(cid = 22L)))
        assertEquals(2, repo.getPlaylist(playlist.id)!!.tracks.size)
    }

    @Test
    fun `移除曲目与重命名删除歌单`() = runBlocking {
        val repo = newRepository(temp.newFolder())
        val playlist = repo.createPlaylist("歌单")
        repo.addTrack(playlist.id, track())
        repo.removeTrack(playlist.id, track().key)
        assertTrue(repo.getPlaylist(playlist.id)!!.tracks.isEmpty())

        repo.renamePlaylist(playlist.id, "新名字")
        assertEquals("新名字", repo.getPlaylist(playlist.id)!!.name)

        repo.deletePlaylist(playlist.id)
        assertNull(repo.getPlaylist(playlist.id))
        assertTrue(repo.playlists.first().isEmpty())
    }

    @Test
    fun `空歌单名使用默认名且删除不存在的歌单不崩溃`() = runBlocking {
        val repo = newRepository(temp.newFolder())
        assertEquals("未命名歌单", repo.createPlaylist("   ").name)
        repo.deletePlaylist("not-exist")
        repo.renamePlaylist("not-exist", "x")
        assertEquals(1, repo.playlists.first().size)
    }

    @Test
    fun `收藏切换状态正确`() = runBlocking {
        val repo = newRepository(temp.newFolder())
        val track = track()
        assertFalse(repo.isFavorite(track.key))
        assertTrue(repo.toggleFavorite(track))
        assertTrue(repo.isFavorite(track.key))
        assertFalse(repo.toggleFavorite(track))
        assertFalse(repo.isFavorite(track.key))
        assertTrue(repo.favorites.first().isEmpty())
    }

    @Test
    fun `数据跨实例持久化`() = runBlocking {
        val dir = temp.newFolder()
        val first = newRepository(dir)
        val playlist = first.createPlaylist("持久化歌单")
        first.addTrack(playlist.id, track())
        first.toggleFavorite(track())

        // 模拟进程重启：新实例读取同一目录
        val second = newRepository(dir)
        val restored = second.playlists.first()
        assertEquals(1, restored.size)
        assertEquals("持久化歌单", restored.first().name)
        assertEquals(1, restored.first().tracks.size)
        assertTrue(second.favorites.first().isNotEmpty())
        assertTrue(second.isFavorite(track().key))
    }
}
