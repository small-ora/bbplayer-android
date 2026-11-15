package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.HistoryRepository
import com.bbplayer.android.core.model.DailyStat
import com.bbplayer.android.core.model.HistoryEntry
import com.bbplayer.android.core.model.ListeningSummary
import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.ZoneId

/** `filesDir/history.json` 的持久化结构。 */
@Serializable
data class HistoryData(
    val entries: List<HistoryEntry> = emptyList(),
)

/** `filesDir/resume.json` 的持久化结构（断点续播）。 */
@Serializable
data class ResumeData(
    val trackKey: String,
    val positionMs: Long,
    val updatedAt: Long = 0L,
)

/**
 * 基于 JSON 文件的播放历史 / 断点续播 / 听歌统计仓库。
 *
 * - 最多保留 [HistoryStats.MAX_ENTRIES] 条记录，新的在前；
 * - 写入防抖（默认 400ms），只有最后一次变更会落盘；
 * - 统计按本地时区聚合最近 84 天（缺失日期补 0，供热力图使用）。
 */
class JsonHistoryRepository(
    private val historyStore: JsonFileStore<HistoryData>,
    private val resumeStore: JsonFileStore<ResumeData>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 400L,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : HistoryRepository {

    /** 便捷构造：由 AppContainer 直接调用。 */
    constructor(context: Context) : this(
        historyStore = JsonFileStore(File(context.filesDir, HISTORY_FILE), HistoryData.serializer()),
        resumeStore = JsonFileStore(File(context.filesDir, RESUME_FILE), ResumeData.serializer()),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val stateMutex = Mutex()
    private val persistMutex = Mutex()
    private val history = MutableStateFlow(HistoryData())

    @Volatile
    private var resume: ResumeData? = null

    @Volatile
    private var loaded = false

    private var pendingPersist: Job? = null

    override fun recent(limit: Int): Flow<List<HistoryEntry>> = history
        .map { HistoryStats.sortedRecent(it.entries, limit) }
        .onStart { ensureLoaded() }
        .distinctUntilChanged()

    override suspend fun recordPlay(track: Track, listenedMs: Long, completed: Boolean) {
        val entry = HistoryEntry(
            track = track,
            playedAt = clock(),
            listenedMs = listenedMs.coerceAtLeast(0L),
            completed = completed,
        )
        withContext(io) {
            stateMutex.withLock {
                loadLocked()
                history.value = HistoryData(HistoryStats.trim(listOf(entry) + history.value.entries))
                schedulePersistLocked()
            }
        }
    }

    override suspend fun clearHistory() {
        withContext(io) {
            stateMutex.withLock {
                loadLocked()
                history.value = HistoryData()
                pendingPersist?.cancel()
                pendingPersist = null
                // 先等可能有正在进行的写落盘，再写空历史，避免旧快照覆盖清空结果。
                persistMutex.withLock {
                    runCatching { historyStore.write(history.value) }
                }
            }
        }
    }

    override fun dailyStats(days: Int): Flow<List<DailyStat>> = history
        .map { HistoryStats.aggregate(it.entries, days, clock(), zone) }
        .onStart { ensureLoaded() }
        .distinctUntilChanged()

    override fun summary(): Flow<ListeningSummary> = history
        .map { HistoryStats.summary(it.entries, clock(), HistoryStats.DEFAULT_DAYS, zone) }
        .onStart { ensureLoaded() }
        .distinctUntilChanged()

    override suspend fun saveResume(trackKey: String, positionMs: Long) {
        if (trackKey.isBlank()) return
        withContext(io) {
            stateMutex.withLock {
                loadLocked()
                resume = if (positionMs <= 0L) null else ResumeData(trackKey, positionMs, clock())
                schedulePersistLocked()
            }
        }
    }

    override suspend fun loadResume(): Pair<Track, Long>? = withContext(io) {
        stateMutex.withLock {
            loadLocked()
            val stored = resume ?: return@withLock null
            val track = history.value.entries.firstOrNull { it.track.key == stored.trackKey }?.track
            if (track == null) null else track to stored.positionMs
        }
    }

    // ------------------------------------------------------------ 内部实现

    private suspend fun ensureLoaded() {
        if (loaded) return
        withContext(io) {
            stateMutex.withLock { loadLocked() }
        }
    }

    /** 需在 [stateMutex] 内调用；文件读取是阻塞 IO，调用方需已在 IO 线程。 */
    private fun loadLocked() {
        if (loaded) return
        history.value = historyStore.read() ?: HistoryData()
        resume = resumeStore.read()
        loaded = true
    }

    /** 防抖落盘：取消上一次未执行的写入，只写最新快照。 */
    private fun schedulePersistLocked() {
        val historySnapshot = history.value
        val resumeSnapshot = resume
        pendingPersist?.cancel()
        pendingPersist = scope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            persistMutex.withLock {
                runCatching { historyStore.write(historySnapshot) }
                if (resumeSnapshot != null) {
                    runCatching { resumeStore.write(resumeSnapshot) }
                } else {
                    runCatching { resumeStore.delete() }
                }
            }
        }
    }

    companion object {
        private const val HISTORY_FILE = "history.json"
        private const val RESUME_FILE = "resume.json"
    }

    // ---------------- v2 insights ----------------

    override fun allHistory(): Flow<List<com.bbplayer.android.core.model.HistoryEntry>> = history
        .map { HistoryStats.sortedRecent(it.entries, it.entries.size) }
        .onStart { ensureLoaded() }
        .distinctUntilChanged()

    override fun ranking(limit: Int): Flow<List<com.bbplayer.android.core.model.ListeningRank>> = history
        .onStart { ensureLoaded() }
        .map { data ->
        val entries = data.entries
        entries.groupBy { it.track.key }
            .map { (_, plays) ->
                com.bbplayer.android.core.model.ListeningRank(
                    track = plays.maxByOrNull { it.playedAt }!!.track,
                    playCount = plays.size,
                    listenedMs = plays.sumOf { it.listenedMs },
                )
            }
            .sortedWith(compareByDescending<com.bbplayer.android.core.model.ListeningRank> { it.playCount }.thenByDescending { it.listenedMs })
            .take(limit)
    }

    override fun historyByDay(days: Int): Flow<List<com.bbplayer.android.core.model.DailyHistory>> = history
        .onStart { ensureLoaded() }
        .map { data ->
        val entries = data.entries
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        entries.filter { it.playedAt >= cutoff }
            .groupBy { entry ->
                java.time.Instant.ofEpochMilli(entry.playedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            }
            .map { (date, list) ->
                com.bbplayer.android.core.model.DailyHistory(
                    date = date,
                    entries = list.sortedByDescending { it.playedAt },
                    listenedMs = list.sumOf { it.listenedMs },
                )
            }
            .sortedByDescending { it.date }
    }
}
