package com.bbplayer.android.data

import com.bbplayer.android.core.model.ExternalPlaylist
import com.bbplayer.android.core.model.ExternalSource
import com.bbplayer.android.core.model.ExternalTrack
import com.bbplayer.android.core.model.ImportMatch
import com.bbplayer.android.core.model.ImportStatus
import com.bbplayer.android.core.model.ImportTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [FileImportTaskRepository] 纯 JVM 单测：原子落盘、覆盖、损坏降级、clear。
 */
class FileImportTaskRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun externalTrack(index: Int) = ExternalTrack(
        title = "夜曲 $index",
        artist = "周杰伦",
        durationMs = 180_000L,
    )

    private fun playlist(count: Int) = ExternalPlaylist(
        source = ExternalSource.NETEASE,
        sourceId = "123456",
        name = "测试歌单",
        tracks = (0 until count).map(::externalTrack),
    )

    private fun task(
        updatedAt: Long = 123L,
        status: ImportStatus = ImportStatus.PAUSED,
        nextIndex: Int = 1,
    ) = ImportTask(
        input = "https://music.163.com/playlist?id=123456",
        playlist = playlist(3),
        matches = listOf(ImportMatch(0, externalTrack(0), matched = null, score = 0f)),
        nextIndex = nextIndex,
        status = status,
        updatedAt = updatedAt,
        errorMessage = null,
    )

    private fun newRepository(
        file: File = File(temp.root, FileImportTaskRepository.FILE_NAME),
        clock: () -> Long = { 42L },
    ) = FileImportTaskRepository(file, clock)

    @Test
    fun `未保存时 load 返回 null`() = runBlocking {
        assertNull(newRepository().load())
    }

    @Test
    fun `保存后可以完整读回`() = runBlocking {
        val repository = newRepository()
        val original = task()
        repository.save(original)
        assertEquals(original, repository.load())
    }

    @Test
    fun `重复保存覆盖为最新快照`() = runBlocking {
        val repository = newRepository()
        repository.save(task(status = ImportStatus.RUNNING, nextIndex = 1))
        val latest = task(status = ImportStatus.DONE, nextIndex = 3)
        repository.save(latest)

        val loaded = repository.load()!!
        assertEquals(ImportStatus.DONE, loaded.status)
        assertEquals(3, loaded.nextIndex)
        assertEquals(1, loaded.matches.size)
    }

    @Test
    fun `重复保存同一任务幂等`() = runBlocking {
        val repository = newRepository()
        val original = task()
        repository.save(original)
        val firstWrite = File(temp.root, FileImportTaskRepository.FILE_NAME).readText()
        repository.save(original)
        val secondWrite = File(temp.root, FileImportTaskRepository.FILE_NAME).readText()

        assertEquals(firstWrite, secondWrite)
        assertEquals(original, repository.load())
    }

    @Test
    fun `损坏 JSON 返回 null 且之后可重新写入`() = runBlocking {
        val repository = newRepository()
        File(temp.root, FileImportTaskRepository.FILE_NAME).writeText("{ not a valid json")
        assertNull(repository.load())

        val recovered = task(status = ImportStatus.RUNNING, nextIndex = 2)
        repository.save(recovered)
        assertEquals(recovered, repository.load())
    }

    @Test
    fun `clear 删除文件且再次保存可用`() = runBlocking {
        val file = File(temp.root, FileImportTaskRepository.FILE_NAME)
        val repository = newRepository(file)
        repository.save(task())
        assertTrue(file.exists())

        repository.clear()
        assertFalse(file.exists())
        assertNull(repository.load())

        repository.save(task(status = ImportStatus.RUNNING))
        assertEquals(ImportStatus.RUNNING, repository.load()!!.status)
    }

    @Test
    fun `updatedAt 为 0 时用 clock 补充`() = runBlocking {
        val repository = newRepository(clock = { 999L })
        repository.save(task(updatedAt = 0L))
        assertEquals(999L, repository.load()!!.updatedAt)

        repository.save(task(updatedAt = 456L))
        assertEquals(456L, repository.load()!!.updatedAt)
    }
}
