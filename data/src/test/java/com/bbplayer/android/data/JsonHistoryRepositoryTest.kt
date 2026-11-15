package com.bbplayer.android.data

import com.bbplayer.android.core.model.HistoryEntry
import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class JsonHistoryRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun track(index: Int = 0) = Track(
        bvid = "BV1q4FTeBEMj",
        cid = index.toLong(),
        title = "夜曲$index",
        uploader = "UP主",
    )

    private fun historyStore(dir: File) = JsonFileStore(File(dir, "history.json"), HistoryData.serializer())

    private fun resumeStore(dir: File) = JsonFileStore(File(dir, "resume.json"), ResumeData.serializer())

    private fun newRepository(
        dir: File,
        scope: CoroutineScope,
        clock: () -> Long,
        debounceMs: Long = 0L,
    ) = JsonHistoryRepository(
        historyStore = historyStore(dir),
        resumeStore = resumeStore(dir),
        scope = scope,
        io = Dispatchers.Unconfined,
        clock = clock,
        debounceMs = debounceMs,
        zone = ZoneOffset.UTC,
    )

    @Test
    fun `记录播放并按时间倒序且最多保留 500 条`() = runTest {
        val dir = temp.newFolder()
        var tick = 0L
        val repo = newRepository(dir, backgroundScope, { tick })
        repeat(505) { index ->
            tick += 1_000
            repo.recordPlay(track(index), 1_000L, completed = false)
        }
        val recent = repo.recent(600).first()
        assertEquals(HistoryStats.MAX_ENTRIES, recent.size)
        assertEquals(504L, recent.first().track.cid)
        assertEquals(5L, recent.last().track.cid)
    }

    @Test
    fun `防抖落盘后可被新实例读取`() = runTest {
        val dir = temp.newFolder()
        val repo = newRepository(dir, backgroundScope, { 1_700_000_000_000L }, debounceMs = 50L)
        repo.recordPlay(track(1), 4_000L, completed = true)
        advanceTimeBy(100L)

        val reloaded = newRepository(dir, backgroundScope, { 1_700_000_000_000L })
        val recent = reloaded.recent(10).first()
        assertEquals(1, recent.size)
        assertEquals(4_000L, recent.first().listenedMs)
        assertEquals(true, recent.first().completed)
    }

    @Test
    fun `清空历史立即落盘`() = runTest {
        val dir = temp.newFolder()
        val store = historyStore(dir)
        val repo = JsonHistoryRepository(
            historyStore = store,
            resumeStore = resumeStore(dir),
            scope = backgroundScope,
            io = Dispatchers.Unconfined,
            clock = { 1L },
            debounceMs = 0L,
            zone = ZoneOffset.UTC,
        )
        repo.recordPlay(track(1), 1_000L, completed = false)
        runCurrent()
        assertEquals(1, repo.recent(10).first().size)

        repo.clearHistory()
        assertEquals(emptyList<HistoryEntry>(), repo.recent(10).first())
        assertEquals(0, store.read()!!.entries.size)
    }

    @Test
    fun `断点续播保存与读取`() = runTest {
        val dir = temp.newFolder()
        val repo = newRepository(dir, backgroundScope, { 1_700_000_000_000L })
        val played = track(1)
        repo.recordPlay(played, 1_000L, completed = false)
        runCurrent()

        repo.saveResume(played.key, 12_345L)
        runCurrent()
        val loaded = repo.loadResume()
        assertEquals(played.key, loaded!!.first.key)
        assertEquals(12_345L, loaded.second)

        // 位置归零视为清除断点
        repo.saveResume(played.key, 0L)
        runCurrent()
        assertNull(repo.loadResume())
    }

    @Test
    fun `未知曲目的断点返回 null`() = runTest {
        val dir = temp.newFolder()
        val repo = newRepository(dir, backgroundScope, { 1L })
        repo.saveResume("BVunknown:1", 5_000L)
        runCurrent()
        assertNull(repo.loadResume())
    }

    @Test
    fun `听歌统计聚合今日数据`() = runTest {
        val nowMs = Instant.parse("2026-11-15T12:00:00Z").toEpochMilli()
        val repo = newRepository(temp.newFolder(), backgroundScope, { nowMs })
        repo.recordPlay(track(1), 60_000L, completed = true)
        repo.recordPlay(track(2), 30_000L, completed = false)
        runCurrent()

        val daily = repo.dailyStats(84).first()
        assertEquals(84, daily.size)
        assertEquals("2026-11-15", daily.last().date)
        assertEquals(2, daily.last().playCount)
        assertEquals(90_000L, daily.last().listenedMs)

        val summary = repo.summary().first()
        assertEquals(2, summary.totalPlayCount)
        assertEquals(90_000L, summary.totalListenedMs)
    }

    @Test
    fun `断点续播数据跨实例恢复`() = runTest {
        val dir = temp.newFolder()
        val first = newRepository(dir, backgroundScope, { 1_700_000_000_000L })
        val played = track(3)
        first.recordPlay(played, 1_000L, completed = false)
        first.saveResume(played.key, 8_888L)
        runCurrent()

        val second = newRepository(dir, backgroundScope, { 1_700_000_000_000L })
        val loaded = second.loadResume()
        assertEquals(played.key, loaded!!.first.key)
        assertEquals(8_888L, loaded.second)
    }
}
