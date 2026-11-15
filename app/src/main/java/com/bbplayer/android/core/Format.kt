package com.bbplayer.android.core

import java.util.Locale

object Format {
    fun duration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun count(n: Long): String = when {
        n >= 100_000_000 -> String.format(Locale.US, "%.1f亿", n / 100_000_000.0)
        n >= 10_000 -> String.format(Locale.US, "%.1f万", n / 10_000.0)
        else -> n.toString()
    }

    fun totalListening(ms: Long): String {
        val minutes = ms / 60000
        if (minutes < 60) return "${minutes} 分钟"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0L) "${h} 小时" else "${h} 小时 ${m} 分钟"
    }

    fun speedLabel(speed: Float): String =
        if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else String.format(Locale.US, "%.1fx", speed)
}
