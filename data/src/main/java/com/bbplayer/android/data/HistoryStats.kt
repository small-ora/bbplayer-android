package com.bbplayer.android.data

import com.bbplayer.android.core.model.DailyStat
import com.bbplayer.android.core.model.HistoryEntry
import com.bbplayer.android.core.model.ListeningSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 播放历史的纯函数聚合逻辑（便于单测）：
 * 按本地日期把 [HistoryEntry] 汇总成连续 N 天的 [DailyStat]，缺失日期补 0。
 */
internal object HistoryStats {

    const val MAX_ENTRIES = 500
    const val DEFAULT_DAYS = 84

    fun trim(entries: List<HistoryEntry>, max: Int = MAX_ENTRIES): List<HistoryEntry> =
        if (entries.size <= max) entries else entries.take(max)

    fun sortedRecent(entries: List<HistoryEntry>, limit: Int): List<HistoryEntry> =
        entries.sortedByDescending { it.playedAt }.take(limit.coerceAtLeast(0))

    /** 最近 [days] 天（含今天）的日统计，按日期从旧到新。 */
    fun aggregate(
        entries: List<HistoryEntry>,
        days: Int,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DailyStat> {
        if (days <= 0) return emptyList()
        val today = toDate(nowMs, zone)
        val firstDate = today.minusDays((days - 1).toLong())
        val listened = HashMap<LocalDate, Long>()
        val playCount = HashMap<LocalDate, Int>()
        for (entry in entries) {
            val date = toDate(entry.playedAt, zone)
            if (date.isBefore(firstDate) || date.isAfter(today)) continue
            listened[date] = (listened[date] ?: 0L) + entry.listenedMs.coerceAtLeast(0L)
            playCount[date] = (playCount[date] ?: 0) + 1
        }
        return (0 until days).map { offset ->
            val date = firstDate.plusDays(offset.toLong())
            DailyStat(
                date = date.toString(),
                listenedMs = listened[date] ?: 0L,
                playCount = playCount[date] ?: 0,
            )
        }
    }

    fun summary(
        entries: List<HistoryEntry>,
        nowMs: Long,
        days: Int = DEFAULT_DAYS,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ListeningSummary = ListeningSummary(
        totalListenedMs = entries.sumOf { it.listenedMs.coerceAtLeast(0L) },
        totalPlayCount = entries.size,
        dailyStats = aggregate(entries, days, nowMs, zone),
    )

    private fun toDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
