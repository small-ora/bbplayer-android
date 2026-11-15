package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.ImportTaskRepository
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.AudioStream
import com.bbplayer.android.core.model.ExternalPlaylist
import com.bbplayer.android.core.model.ExternalSource
import com.bbplayer.android.core.model.ExternalTrack
import com.bbplayer.android.core.model.ImportMatch
import com.bbplayer.android.core.model.ImportProgress
import com.bbplayer.android.core.model.ImportStatus
import com.bbplayer.android.core.model.ImportTask
import com.bbplayer.android.core.model.SearchPage
import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BiliExternalPlaylistRepository.matchBatch] 纯 JVM 单测（fake MusicRepository，无网络）。
 *
 * 覆盖：分批边界 / nextIndex 递增 / 每批落盘 / 风控不抛 / 连续失败 FAILED / 完成 DONE /
 * onProgress / 可注入 sleep 限速。
 */
class BiliExternalPlaylistRepositoryBatchTest {

    // ------------------------------------------------------------ fakes

    private class FakeMusicRepository(
        private val handler: suspend (String) -> Result<SearchPage>,
    ) : MusicRepository {

        val queries = mutableListOf<String>()

        override suspend fun search(query: String, page: Int): Result<SearchPage> {
            queries += query
            return handler(query)
        }

        override suspend fun resolveInput(input: String): Result<List<Track>> = unused()

        override suspend fun getTrending(): Result<List<Track>> = unused()

        override suspend fun getRelated(bvid: String): Result<List<Track>> = unused()

        override suspend fun getVideoPages(bvid: String): Result<List<Track>> = unused()

        override suspend fun getAudioStream(
            track: Track,
            quality: AudioQuality?,
        ): Result<AudioStream> = unused()

        override suspend fun getVideoDetail(bvid: String): Result<Track> = unused()

        private fun <T> unused(): Result<T> = Result.failure(AppException("unused", retryable = false))
    }

    private class RecordingImportTaskRepository : ImportTaskRepository {
        val saved = mutableListOf<ImportTask>()
        var stored: ImportTask? = null
        var clears = 0

        override suspend fun load(): ImportTask? = stored

        override suspend fun save(task: ImportTask) {
            saved += task
            stored = task
        }

        override suspend fun clear() {
            clears++
            stored = null
        }
    }

    // ------------------------------------------------------------ fixtures

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

    private fun matches(count: Int) = (0 until count).map { index ->
        ImportMatch(index, externalTrack(index), matched = null, score = 0f)
    }

    private fun task(
        count: Int,
        nextIndex: Int = 0,
        matches: List<ImportMatch> = emptyList(),
    ) = ImportTask(
        input = "https://music.163.com/playlist?id=123456",
        playlist = playlist(count),
        matches = matches,
        nextIndex = nextIndex,
        status = ImportStatus.PAUSED,
        updatedAt = 0L,
    )

    private fun searchPage() = SearchPage(
        items = listOf(
            Track(
                bvid = "BV1xx411c7mD",
                cid = 1L,
                title = "夜曲",
                uploader = "周杰伦",
                durationMs = 180_000L,
            ),
        ),
        page = 1,
        hasMore = false,
    )

    private fun repository(
        music: MusicRepository,
        taskRepository: ImportTaskRepository? = null,
        minIntervalMs: Long = 0L,
        sleeps: MutableList<Long>? = null,
        retryAfterMs: Long = 30_000L,
        onBlock: () -> Unit = {},
    ) = BiliExternalPlaylistRepository(
        musicRepository = music,
        http = HttpFetcher(),
        minIntervalMs = minIntervalMs,
        sleep = { sleeps?.add(it) },
        importTaskRepository = taskRepository,
        clock = { 1_700_000_000_000L },
        securityCooldownRemainingMs = { retryAfterMs },
        markSecurityBlocked = onBlock,
    )

    // ------------------------------------------------------------ batch boundaries

    @Test
    fun `每批最多处理50首并在批末落盘`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val tasks = RecordingImportTaskRepository()

        val result = repository(music, tasks).matchBatch(task(120), 50, {})

        assertEquals(50, music.queries.size)
        assertEquals(50, result.task.nextIndex)
        assertEquals(50, result.task.matches.size)
        assertEquals(ImportStatus.PAUSED, result.task.status)
        assertFalse(result.blocked)
        assertEquals(1, tasks.saved.size)
        assertEquals(50, tasks.saved.single().nextIndex)
    }

    @Test
    fun `batchSize 非法时退回默认50首`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val result = repository(music).matchBatch(task(120), 0, {})
        assertEquals(50, result.task.nextIndex)
        assertEquals(50, music.queries.size)
    }

    @Test
    fun `batchSize 超过曲目总数时一次处理完`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val result = repository(music).matchBatch(task(3), Int.MAX_VALUE, {})
        assertEquals(3, result.task.nextIndex)
        assertEquals(ImportStatus.DONE, result.task.status)
        assertEquals(3, music.queries.size)
    }

    @Test
    fun `剩余不足一批时处理完并标记 DONE`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val tasks = RecordingImportTaskRepository()

        val result = repository(music, tasks)
            .matchBatch(task(120, nextIndex = 110, matches = matches(110)), 50, {})

        assertEquals(10, music.queries.size)
        assertEquals(120, result.task.nextIndex)
        assertEquals(120, result.task.matches.size)
        assertEquals(ImportStatus.DONE, result.task.status)
        assertEquals(ImportStatus.DONE, tasks.saved.single().status)
    }

    @Test
    fun `nextIndex 逐曲递增且匹配结果按序`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val result = repository(music).matchBatch(task(3), 50, {})

        assertEquals(listOf(0, 1, 2), result.task.matches.map { it.index })
        assertEquals(3, result.task.nextIndex)
        assertTrue(result.task.matches.all { it.matched != null })
        assertEquals(ImportStatus.DONE, result.task.status)
    }

    @Test
    fun `连续多次调用按 nextIndex 续跑并逐批落盘`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val tasks = RecordingImportTaskRepository()
        val repository = repository(music, tasks)

        val first = repository.matchBatch(task(120), 50, {})
        val second = repository.matchBatch(first.task, 50, {})
        val third = repository.matchBatch(second.task, 50, {})

        assertEquals(listOf(50, 100, 120), tasks.saved.map { it.nextIndex })
        assertEquals(
            listOf(ImportStatus.PAUSED, ImportStatus.PAUSED, ImportStatus.DONE),
            tasks.saved.map { it.status },
        )
        assertEquals(120, third.task.nextIndex)
        assertEquals(120, third.task.matches.size)
        assertEquals(120, music.queries.size)
    }

    @Test
    fun `已完成任务直接返回 DONE 且不再搜索`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val tasks = RecordingImportTaskRepository()
        val progress = mutableListOf<ImportProgress>()

        val result = repository(music, tasks)
            .matchBatch(task(3, nextIndex = 3, matches = matches(3)), 50, { progress += it })

        assertEquals(ImportStatus.DONE, result.task.status)
        assertTrue(music.queries.isEmpty())
        assertEquals(1, progress.size)
        assertTrue(progress.single().finished)
        assertEquals(1, tasks.saved.size)
    }

    @Test
    fun `空歌单直接 DONE`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val progress = mutableListOf<ImportProgress>()

        val result = repository(music).matchBatch(task(0), 50, { progress += it })

        assertEquals(ImportStatus.DONE, result.task.status)
        assertEquals(0, result.task.nextIndex)
        assertTrue(music.queries.isEmpty())
        assertTrue(progress.single().finished)
    }

    // ------------------------------------------------------------ risk control

    @Test
    fun `命中风控返回 blocked 不抛异常并保存暂停点`() = runBlocking {
        val music = FakeMusicRepository {
            Result.failure(AppException("请求被B站安全策略拦截，请稍后重试", retryable = true))
        }
        val tasks = RecordingImportTaskRepository()
        var marked = 0

        val result = repository(
            music = music,
            taskRepository = tasks,
            retryAfterMs = 45_000L,
            onBlock = { marked++ },
        ).matchBatch(task(10), 50, {})

        assertTrue(result.blocked)
        assertEquals(45_000L, result.retryAfterMs)
        assertEquals(ImportStatus.PAUSED, result.task.status)
        assertEquals(0, result.task.nextIndex)
        assertTrue(result.task.errorMessage!!.contains("安全策略"))
        assertEquals(1, marked)
        assertEquals(1, music.queries.size)
        assertEquals(1, tasks.saved.size)
        assertEquals(ImportStatus.PAUSED, tasks.saved.single().status)
        assertTrue(tasks.saved.single().errorMessage!!.contains("安全策略"))
    }

    @Test
    fun `-412 出现在 cause 链也能识别为风控`() = runBlocking {
        val music = FakeMusicRepository {
            Result.failure(
                AppException(
                    userMessage = "请求失败",
                    cause2 = IllegalStateException("bilibili code=-412"),
                    retryable = true,
                ),
            )
        }
        val result = repository(music, retryAfterMs = 12_345L)
            .matchBatch(task(5), 50, {})

        assertTrue(result.blocked)
        assertEquals(12_345L, result.retryAfterMs)
        assertEquals(ImportStatus.PAUSED, result.task.status)
        assertEquals("请求失败", result.task.errorMessage)
    }

    @Test
    fun `批内中途命中风控保留已匹配进度且不推进当前曲`() = runBlocking {
        var calls = 0
        val music = FakeMusicRepository {
            calls++
            if (calls == 3) {
                Result.failure(AppException("请求被B站安全策略拦截，请稍后重试", retryable = true))
            } else {
                Result.success(searchPage())
            }
        }
        val tasks = RecordingImportTaskRepository()
        val progress = mutableListOf<ImportProgress>()

        val result = repository(music, tasks).matchBatch(task(10), 50, { progress += it })

        assertTrue(result.blocked)
        assertEquals(2, result.task.nextIndex)
        assertEquals(2, result.task.matches.size)
        assertTrue(result.task.matches.all { it.matched != null })
        assertEquals(2, tasks.saved.single().nextIndex)
        assertTrue(progress.last().finished)
        assertEquals(2, progress.last().done)
    }

    // ------------------------------------------------------------ ordinary failures

    @Test
    fun `普通网络失败累计达到阈值返回 FAILED 不抛异常`() = runBlocking {
        val music = FakeMusicRepository {
            Result.failure(AppException("网络请求失败，请重试", retryable = true))
        }
        val tasks = RecordingImportTaskRepository()

        val result = repository(music, tasks).matchBatch(task(5), 50, {})

        assertFalse(result.blocked)
        assertEquals(3, music.queries.size)
        assertEquals(ImportStatus.FAILED, result.task.status)
        assertEquals("网络请求失败，请重试", result.task.errorMessage)
        // 前两次偶发失败按未匹配入库，第三次触发阈值停在原地等待续跑。
        assertEquals(2, result.task.nextIndex)
        assertEquals(2, result.task.matches.size)
        assertNull(result.task.matches.first().matched)
        assertEquals(ImportStatus.FAILED, tasks.saved.single().status)
    }

    @Test
    fun `偶发失败按未匹配记录并继续后续曲目`() = runBlocking {
        var calls = 0
        val music = FakeMusicRepository {
            calls++
            if (calls == 1) {
                Result.failure(AppException("网络超时，请重试", retryable = true))
            } else {
                Result.success(searchPage())
            }
        }

        val result = repository(music).matchBatch(task(3), 50, {})

        assertEquals(ImportStatus.DONE, result.task.status)
        assertEquals(3, result.task.matches.size)
        assertNull(result.task.matches[0].matched)
        assertNotNull(result.task.matches[1].matched)
        assertNotNull(result.task.matches[2].matched)
    }

    // ------------------------------------------------------------ progress / throttle

    @Test
    fun `每曲回调一次 progress 且仅最后一曲 finished`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val progress = mutableListOf<ImportProgress>()
        val repository = repository(music)

        val first = repository.matchBatch(task(3), 2, { progress += it })
        assertEquals(listOf(1, 2), progress.map { it.done })
        assertFalse(progress[0].finished)
        assertFalse(progress[1].finished)
        assertEquals(2, progress[1].matches.size)

        repository.matchBatch(first.task, 2, { progress += it })
        assertEquals(3, progress.size)
        assertEquals(3, progress.last().done)
        assertTrue(progress.last().finished)
        assertEquals(3, progress.last().matches.size)
    }

    @Test
    fun `两曲之间按 minIntervalMs 注入限速 sleep`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val sleeps = mutableListOf<Long>()

        repository(music, minIntervalMs = 1_200L, sleeps = sleeps)
            .matchBatch(task(3), 50, {})

        assertEquals(2, sleeps.size)
        assertTrue(sleeps.all { it >= 1_200L })
    }

    @Test
    fun `未注入任务仓库时也能正常返回结果`() = runBlocking {
        val music = FakeMusicRepository { Result.success(searchPage()) }
        val result = repository(music, taskRepository = null).matchBatch(task(2), 50, {})

        assertEquals(2, result.task.nextIndex)
        assertEquals(ImportStatus.DONE, result.task.status)
    }
}
