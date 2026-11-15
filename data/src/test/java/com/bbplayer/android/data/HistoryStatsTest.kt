package com.bbplayer.android.data

import com.bbplayer.android.core.model.HistoryEntry
import com.bbplayer.android.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryStatsTest {

    private fun track(bvid: String = "BV1q4FTeBEMj", cid: Long = 1L) =
        Track(bvid = bvid, cid = cid, title = "夜曲")

    private fun entry(playedAt: String, listenedMs: Long, completed: Boolean = false): HistoryEntry =
        HistoryEntry(
            track = track(),
            playedAt = Instant.parse(playedAt).toEpochMilli(),
            listenedMs = listenedMs,
            completed = completed,
        )

    private val now = Instant.parse("2026-11-15T12:00:00Z").toEpochMilli()

    @Test
    fun `按日聚合最近 5 天且补零`() {
        val entries = listOf(
            entry("2026-11-15T09:00:00Z", 60_000),
            entry("2026-11-15T10:00:00Z", 90_000),
            entry("2026-11-14T20:00:00Z", 30_000),
            entry("2026-11-12T20:00:00Z", 10_000),
            entry("2026-08-01T20:00:00Z", 999_000), // 超出 84/5 天范围
        )
        val stats = HistoryStats.aggregate(entries, days = 5, nowMs = now, zone = ZoneOffset.UTC)
        assertEquals(5, stats.size)
        assertEquals(
            listOf("2026-11-11", "2026-11-12", "2026-11-13", "2026-11-14", "2026-11-15"),
            stats.map { it.date },
        )
        assertEquals(0, stats[0].playCount)
        assertEquals(10_000L, stats[1].listenedMs)
        assertEquals(1, stats[1].playCount)
        assertEquals(0L, stats[2].listenedMs)
        assertEquals(30_000L, stats[3].listenedMs)
        assertEquals(150_000L, stats[4].listenedMs)
        assertEquals(2, stats[4].playCount)
    }

    @Test
    fun `跨日按本地时区归属`() {
        // UTC 14 日 23:30 = 东八区 15 日 07:30，应算到 15 日
        val entries = listOf(entry("2026-11-14T23:30:00Z", 12_000))
        val shanghai = ZoneId.of("Asia/Shanghai")
        val utcStats = HistoryStats.aggregate(entries, days = 2, nowMs = now, zone = ZoneOffset.UTC)
        val shStats = HistoryStats.aggregate(entries, days = 2, nowMs = now, zone = shanghai)
        // UTC 下该记录属于 11-14（前一日）；东八区下属于 11-15（今日）
        assertEquals(1, utcStats.first().playCount)
        assertEquals(0, utcStats.last().playCount)
        assertEquals(0, shStats.first().playCount)
        assertEquals(1, shStats.last().playCount)
        assertEquals("2026-11-15", shStats.last().date)
    }

    @Test
    fun `汇总累计时长与播放次数`() {
        val entries = listOf(
            entry("2026-11-15T09:00:00Z", 60_000, completed = true),
            entry("2026-11-14T09:00:00Z", 30_000),
        )
        val summary = HistoryStats.summary(entries, nowMs = now, days = 84, zone = ZoneOffset.UTC)
        assertEquals(90_000L, summary.totalListenedMs)
        assertEquals(2, summary.totalPlayCount)
        assertEquals(84, summary.dailyStats.size)
        assertEquals("2026-11-15", summary.dailyStats.last().date)
    }

    @Test
    fun `历史最多保留 500 条`() {
        val entries = (0 until 505).map {
            HistoryEntry(track = track(cid = it.toLong()), playedAt = it.toLong(), listenedMs = 1000L, completed = false)
        }
        val trimmed = HistoryStats.trim(entries)
        assertEquals(HistoryStats.MAX_ENTRIES, trimmed.size)
        assertEquals(entries.first(), trimmed.first())
    }

    @Test
    fun `最近播放按时间倒序截断`() {
        val entries = (0 until 10).map {
            HistoryEntry(track = track(cid = it.toLong()), playedAt = it.toLong(), listenedMs = 0L, completed = false)
        }.shuffled()
        val recent = HistoryStats.sortedRecent(entries, limit = 3)
        assertEquals(3, recent.size)
        assertEquals(listOf(9L, 8L, 7L), recent.map { it.playedAt })
        assertEquals(emptyList<HistoryEntry>(), HistoryStats.sortedRecent(entries, limit = 0))
    }
}
