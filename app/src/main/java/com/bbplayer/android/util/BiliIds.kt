package com.bbplayer.android.util

/**
 * Local, offline BV-id ↔ av-id conversion.
 *
 * The v5 favourite / watch-later contracts are keyed by numeric `aid`, while [com.bbplayer.android.core.model.Track]
 * only carries `bvid` (the core model is frozen). Bilibili's BV id is a reversible encoding of the
 * numeric av id, so we decode it locally instead of adding an extra `view?bvid=` round-trip in the UI
 * layer or changing the frozen model.
 *
 * Verified against official ids: `BV1xx411c7mD` → 2, `BV17x411w7KC` → 170001,
 * `BV1Q541167Qg` → 455017605, `BV1mK4y1C7Bz` → 882584971.
 */
object BiliIds {
    private const val TABLE = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF"
    private val POSITIONS = intArrayOf(11, 10, 3, 8, 4, 6)
    private val POW58 = longArrayOf(1L, 58L, 3364L, 195112L, 11316496L, 656356768L)
    private const val XOR = 177451812L
    private const val ADD = 8728348608L

    private val INDEX: IntArray = IntArray(128) { -1 }.also { table ->
        TABLE.forEachIndexed { index, char ->
            if (char.code < 128) table[char.code] = index
        }
    }

    /** Returns the numeric av id encoded in a standard 12-character `BV1…` id, or null. */
    fun aidOf(bvid: String): Long? {
        val value = bvid.trim()
        if (value.length != 12 || !value.startsWith("BV1")) return null
        var encoded = 0L
        for (i in POSITIONS.indices) {
            val code = value[POSITIONS[i]].code
            val digit = if (code in INDEX.indices) INDEX[code] else -1
            if (digit < 0) return null
            encoded += digit * POW58[i]
        }
        val aid = (encoded - ADD) xor XOR
        return aid.takeIf { it > 0L }
    }
}
