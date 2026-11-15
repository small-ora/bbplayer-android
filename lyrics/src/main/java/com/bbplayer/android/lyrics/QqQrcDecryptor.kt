package com.bbplayer.android.lyrics

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Decrypts QQ Music QRC (逐字) payloads.
 *
 * The `GetPlayLyricInfo` / `lyric_download.fcg` endpoints return the verbatim track as a hex string that is
 * `custom Triple-DES` encrypted and zlib compressed. QQ's DES is *not* standard DES: its key schedule uses a
 * modified PC-2 compression table (a long-standing QQ Music quirk), so `javax.crypto` / BouncyCastle DES cannot
 * decrypt it. This object ports the reference implementation (WXRIW/QQMusicDecoder `DESHelper.cs`, mirrored by
 * `QQMusicApi`'s `tripledes.py`) byte for byte; the algorithm is deterministic and pure Kotlin, no Android deps.
 *
 * Pipeline: hex string → bytes → Triple-DES ECB (QQ variant, key `!@#)(*$%123ZXC!@!@#)(NHL`) → zlib inflate → UTF-8
 * QRC text (an `<QrcInfos>` XML wrapper whose `LyricContent="..."` holds the timed lines).
 *
 * All entry points are total: malformed input returns `null` instead of throwing, so a broken QRC track can never
 * break lyric playback (callers fall back to LRC / estimated timing).
 */
internal object QqQrcDecryptor {

    /** QQ Music's hard-coded lyric key (ASCII). */
    private val KEY: ByteArray = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)

    private const val DES_ENCRYPT = 1
    private const val DES_DECRYPT = 0

    /**
     * Decrypts a hex-encoded QRC payload into its plaintext QRC/XML text.
     *
     * Whitespace inside [hex] is ignored (some endpoints wrap the hex across lines). Returns `null` for empty,
     * non-hex or non-block-aligned input, when the Triple-DES/zlib pipeline fails, or when the result is blank.
     */
    fun decrypt(hex: String): String? {
        val bytes = hexToBytes(hex) ?: return null
        if (bytes.isEmpty() || bytes.size % BLOCK_SIZE != 0) return null
        val cipher = tripleDesKeySetup(KEY, DES_DECRYPT) ?: return null
        val decrypted = ByteArray(bytes.size)
        var offset = 0
        while (offset < bytes.size) {
            val block = bytes.copyOfRange(offset, offset + BLOCK_SIZE)
            val plain = tripleDesCrypt(block, cipher)
            plain.copyInto(decrypted, offset)
            offset += BLOCK_SIZE
        }
        val inflated = inflate(decrypted) ?: return null
        return String(inflated, Charsets.UTF_8).takeIf { it.isNotBlank() }
    }

    /** Parses [hex] leniently; null when it contains a non-hex character or has an odd number of digits. */
    fun hexToBytes(hex: String): ByteArray? {
        val compact = hex.filterNot { it.isWhitespace() }
        if (compact.isEmpty() || compact.length % 2 != 0) return null
        val out = ByteArray(compact.length / 2)
        for (index in out.indices) {
            val high = hexDigit(compact[index * 2])
            val low = hexDigit(compact[index * 2 + 1])
            if (high < 0 || low < 0) return null
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    /**
     * Inflates [data]; QQ has shipped both zlib-wrapped and raw-deflate streams over the years, so both are tried.
     * Returns null when neither works.
     */
    internal fun inflate(data: ByteArray): ByteArray? {
        for (nowrap in NOWRAP_ATTEMPTS) {
            val result = runCatching {
                val inflater = Inflater(nowrap)
                try {
                    inflater.setInput(data)
                    val out = ByteArrayOutputStream(data.size.coerceAtLeast(64))
                    val buffer = ByteArray(8_192)
                    while (!inflater.finished()) {
                        val read = inflater.inflate(buffer)
                        if (read == 0) {
                            // Needs more input/dictionary: give up on this attempt instead of spinning forever.
                            if (inflater.needsInput() || inflater.needsDictionary()) break
                        } else {
                            out.write(buffer, 0, read)
                        }
                    }
                    out.toByteArray().takeIf { it.isNotEmpty() }
                } finally {
                    inflater.end()
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun hexDigit(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    // ---- QQ variant DES ----
    // Bit-level port of the reference C# / Python implementation. Kotlin `Int` is used as the 32-bit word and
    // `ushr` mirrors the reference's shifts on non-negative Python ints.

    private const val BLOCK_SIZE = 8
    private val NOWRAP_ATTEMPTS = booleanArrayOf(false, true)

    private val SBOX: Array<IntArray> = arrayOf(
        intArrayOf(
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
        ),
        intArrayOf(
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
        ),
        intArrayOf(
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
        ),
        intArrayOf(
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
        ),
        intArrayOf(
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
        ),
        intArrayOf(
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
        ),
        intArrayOf(
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
        ),
        intArrayOf(
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
        ),
    )

    private val KEY_RND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val KEY_PERM_C = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35,
    )
    private val KEY_PERM_D = intArrayOf(
        62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
    )
    private val KEY_COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54,
        29, 39, 50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )

    private fun bitnumIntr(a: Int, b: Int, c: Int): Int = ((a ushr (31 - b)) and 1) shl c

    private fun sboxBit(a: Int): Int = (a and 32) or ((a and 31) ushr 1) or ((a and 1) shl 4)

    private fun initialPermutation(input: ByteArray): Pair<Int, Int> {
        val v0 = (input[0].toInt() and 0xFF) or
            ((input[1].toInt() and 0xFF) shl 8) or
            ((input[2].toInt() and 0xFF) shl 16) or
            ((input[3].toInt() and 0xFF) shl 24)
        val v1 = (input[4].toInt() and 0xFF) or
            ((input[5].toInt() and 0xFF) shl 8) or
            ((input[6].toInt() and 0xFF) shl 16) or
            ((input[7].toInt() and 0xFF) shl 24)
        val s0 = ((v1 ushr 6) and 1 shl 31) or
            ((v1 ushr 14) and 1 shl 30) or
            ((v1 ushr 22) and 1 shl 29) or
            ((v1 ushr 30) and 1 shl 28) or
            ((v0 ushr 6) and 1 shl 27) or
            ((v0 ushr 14) and 1 shl 26) or
            ((v0 ushr 22) and 1 shl 25) or
            ((v0 ushr 30) and 1 shl 24) or
            ((v1 ushr 4) and 1 shl 23) or
            ((v1 ushr 12) and 1 shl 22) or
            ((v1 ushr 20) and 1 shl 21) or
            ((v1 ushr 28) and 1 shl 20) or
            ((v0 ushr 4) and 1 shl 19) or
            ((v0 ushr 12) and 1 shl 18) or
            ((v0 ushr 20) and 1 shl 17) or
            ((v0 ushr 28) and 1 shl 16) or
            ((v1 ushr 2) and 1 shl 15) or
            ((v1 ushr 10) and 1 shl 14) or
            ((v1 ushr 18) and 1 shl 13) or
            ((v1 ushr 26) and 1 shl 12) or
            ((v0 ushr 2) and 1 shl 11) or
            ((v0 ushr 10) and 1 shl 10) or
            ((v0 ushr 18) and 1 shl 9) or
            ((v0 ushr 26) and 1 shl 8) or
            ((v1 ushr 0) and 1 shl 7) or
            ((v1 ushr 8) and 1 shl 6) or
            ((v1 ushr 16) and 1 shl 5) or
            ((v1 ushr 24) and 1 shl 4) or
            ((v0 ushr 0) and 1 shl 3) or
            ((v0 ushr 8) and 1 shl 2) or
            ((v0 ushr 16) and 1 shl 1) or
            ((v0 ushr 24) and 1)
        val s1 = ((v1 ushr 7) and 1 shl 31) or
            ((v1 ushr 15) and 1 shl 30) or
            ((v1 ushr 23) and 1 shl 29) or
            ((v1 ushr 31) and 1 shl 28) or
            ((v0 ushr 7) and 1 shl 27) or
            ((v0 ushr 15) and 1 shl 26) or
            ((v0 ushr 23) and 1 shl 25) or
            ((v0 ushr 31) and 1 shl 24) or
            ((v1 ushr 5) and 1 shl 23) or
            ((v1 ushr 13) and 1 shl 22) or
            ((v1 ushr 21) and 1 shl 21) or
            ((v1 ushr 29) and 1 shl 20) or
            ((v0 ushr 5) and 1 shl 19) or
            ((v0 ushr 13) and 1 shl 18) or
            ((v0 ushr 21) and 1 shl 17) or
            ((v0 ushr 29) and 1 shl 16) or
            ((v1 ushr 3) and 1 shl 15) or
            ((v1 ushr 11) and 1 shl 14) or
            ((v1 ushr 19) and 1 shl 13) or
            ((v1 ushr 27) and 1 shl 12) or
            ((v0 ushr 3) and 1 shl 11) or
            ((v0 ushr 11) and 1 shl 10) or
            ((v0 ushr 19) and 1 shl 9) or
            ((v0 ushr 27) and 1 shl 8) or
            ((v1 ushr 1) and 1 shl 7) or
            ((v1 ushr 9) and 1 shl 6) or
            ((v1 ushr 17) and 1 shl 5) or
            ((v1 ushr 25) and 1 shl 4) or
            ((v0 ushr 1) and 1 shl 3) or
            ((v0 ushr 9) and 1 shl 2) or
            ((v0 ushr 17) and 1 shl 1) or
            ((v0 ushr 25) and 1)
        return s0 to s1
    }

    private fun inversePermutation(s0: Int, s1: Int): ByteArray {
        // bitnumIntr takes a bit index (bit 0 = MSB, i.e. `ushr 31`); the reference shifts are 31 - index.
        val data = ByteArray(8)
        data[3] = (bitnumIntr(s1, 7, 7) or bitnumIntr(s0, 7, 6) or bitnumIntr(s1, 15, 5) or
            bitnumIntr(s0, 15, 4) or bitnumIntr(s1, 23, 3) or bitnumIntr(s0, 23, 2) or
            bitnumIntr(s1, 31, 1) or bitnumIntr(s0, 31, 0)).toByte()
        data[2] = (bitnumIntr(s1, 6, 7) or bitnumIntr(s0, 6, 6) or bitnumIntr(s1, 14, 5) or
            bitnumIntr(s0, 14, 4) or bitnumIntr(s1, 22, 3) or bitnumIntr(s0, 22, 2) or
            bitnumIntr(s1, 30, 1) or bitnumIntr(s0, 30, 0)).toByte()
        data[1] = (bitnumIntr(s1, 5, 7) or bitnumIntr(s0, 5, 6) or bitnumIntr(s1, 13, 5) or
            bitnumIntr(s0, 13, 4) or bitnumIntr(s1, 21, 3) or bitnumIntr(s0, 21, 2) or
            bitnumIntr(s1, 29, 1) or bitnumIntr(s0, 29, 0)).toByte()
        data[0] = (bitnumIntr(s1, 4, 7) or bitnumIntr(s0, 4, 6) or bitnumIntr(s1, 12, 5) or
            bitnumIntr(s0, 12, 4) or bitnumIntr(s1, 20, 3) or bitnumIntr(s0, 20, 2) or
            bitnumIntr(s1, 28, 1) or bitnumIntr(s0, 28, 0)).toByte()
        data[7] = (bitnumIntr(s1, 3, 7) or bitnumIntr(s0, 3, 6) or bitnumIntr(s1, 11, 5) or
            bitnumIntr(s0, 11, 4) or bitnumIntr(s1, 19, 3) or bitnumIntr(s0, 19, 2) or
            bitnumIntr(s1, 27, 1) or bitnumIntr(s0, 27, 0)).toByte()
        data[6] = (bitnumIntr(s1, 2, 7) or bitnumIntr(s0, 2, 6) or bitnumIntr(s1, 10, 5) or
            bitnumIntr(s0, 10, 4) or bitnumIntr(s1, 18, 3) or bitnumIntr(s0, 18, 2) or
            bitnumIntr(s1, 26, 1) or bitnumIntr(s0, 26, 0)).toByte()
        data[5] = (bitnumIntr(s1, 1, 7) or bitnumIntr(s0, 1, 6) or bitnumIntr(s1, 9, 5) or
            bitnumIntr(s0, 9, 4) or bitnumIntr(s1, 17, 3) or bitnumIntr(s0, 17, 2) or
            bitnumIntr(s1, 25, 1) or bitnumIntr(s0, 25, 0)).toByte()
        data[4] = (bitnumIntr(s1, 0, 7) or bitnumIntr(s0, 0, 6) or bitnumIntr(s1, 8, 5) or
            bitnumIntr(s0, 8, 4) or bitnumIntr(s1, 16, 3) or bitnumIntr(s0, 16, 2) or
            bitnumIntr(s1, 24, 1) or bitnumIntr(s0, 24, 0)).toByte()
        return data
    }

    private fun f(state: Int, key: IntArray): Int {
        val t1 = ((state and 1) shl 31) or
            ((state and 0xF8000000.toInt()) ushr 1) or
            ((state and 0x1F800000) ushr 3) or
            ((state and 0x01F80000) ushr 5) or
            ((state and 0x001F8000) ushr 7)
        val t2 = ((state and 0x0001F800) shl 15) or
            ((state and 0x00001F80) shl 13) or
            ((state and 0x000001F8) shl 11) or
            ((state and 0x0000001F) shl 9) or
            ((state and 0x80000000.toInt()) ushr 23)

        val k0 = ((t1 ushr 24) and 0xFF) xor key[0]
        val k1 = ((t1 ushr 16) and 0xFF) xor key[1]
        val k2 = ((t1 ushr 8) and 0xFF) xor key[2]
        val k3 = ((t2 ushr 24) and 0xFF) xor key[3]
        val k4 = ((t2 ushr 16) and 0xFF) xor key[4]
        val k5 = ((t2 ushr 8) and 0xFF) xor key[5]

        val mixed = (SBOX[0][sboxBit(k0 ushr 2)] shl 28) or
            (SBOX[1][sboxBit(((k0 and 0x03) shl 4) or (k1 ushr 4))] shl 24) or
            (SBOX[2][sboxBit(((k1 and 0x0F) shl 2) or (k2 ushr 6))] shl 20) or
            (SBOX[3][sboxBit(k2 and 0x3F)] shl 16) or
            (SBOX[4][sboxBit(k3 ushr 2)] shl 12) or
            (SBOX[5][sboxBit(((k3 and 0x03) shl 4) or (k4 ushr 4))] shl 8) or
            (SBOX[6][sboxBit(((k4 and 0x0F) shl 2) or (k5 ushr 6))] shl 4) or
            SBOX[7][sboxBit(k5 and 0x3F)]

        return ((mixed ushr 16) and 1 shl 31) or
            ((mixed ushr 25) and 1 shl 30) or
            ((mixed ushr 12) and 1 shl 29) or
            ((mixed ushr 11) and 1 shl 28) or
            ((mixed ushr 3) and 1 shl 27) or
            ((mixed ushr 20) and 1 shl 26) or
            ((mixed ushr 4) and 1 shl 25) or
            ((mixed ushr 15) and 1 shl 24) or
            ((mixed ushr 31) and 1 shl 23) or
            ((mixed ushr 17) and 1 shl 22) or
            ((mixed ushr 9) and 1 shl 21) or
            ((mixed ushr 6) and 1 shl 20) or
            ((mixed ushr 27) and 1 shl 19) or
            ((mixed ushr 14) and 1 shl 18) or
            ((mixed ushr 1) and 1 shl 17) or
            ((mixed ushr 22) and 1 shl 16) or
            ((mixed ushr 30) and 1 shl 15) or
            ((mixed ushr 24) and 1 shl 14) or
            ((mixed ushr 8) and 1 shl 13) or
            ((mixed ushr 18) and 1 shl 12) or
            ((mixed ushr 0) and 1 shl 11) or
            ((mixed ushr 5) and 1 shl 10) or
            ((mixed ushr 29) and 1 shl 9) or
            ((mixed ushr 23) and 1 shl 8) or
            ((mixed ushr 13) and 1 shl 7) or
            ((mixed ushr 19) and 1 shl 6) or
            ((mixed ushr 2) and 1 shl 5) or
            ((mixed ushr 26) and 1 shl 4) or
            ((mixed ushr 10) and 1 shl 3) or
            ((mixed ushr 21) and 1 shl 2) or
            ((mixed ushr 28) and 1 shl 1) or
            ((mixed ushr 7) and 1)
    }

    private fun desCrypt(input: ByteArray, key: Array<IntArray>): ByteArray {
        var (s0, s1) = initialPermutation(input)
        for (index in 0..14) {
            val previousS1 = s1
            s1 = f(s1, key[index]) xor s0
            s0 = previousS1
        }
        s0 = f(s1, key[15]) xor s0
        return inversePermutation(s0, s1)
    }

    /** DES key schedule (QQ variant: modified PC-2 offsets). */
    internal fun keySchedule(key: ByteArray, mode: Int): Array<IntArray> {
        val schedule = Array(16) { IntArray(6) }
        val v0 = (key[0].toInt() and 0xFF) or
            ((key[1].toInt() and 0xFF) shl 8) or
            ((key[2].toInt() and 0xFF) shl 16) or
            ((key[3].toInt() and 0xFF) shl 24)
        val v1 = (key[4].toInt() and 0xFF) or
            ((key[5].toInt() and 0xFF) shl 8) or
            ((key[6].toInt() and 0xFF) shl 16) or
            ((key[7].toInt() and 0xFF) shl 24)

        var c = 0
        for (index in 0..27) {
            val bit = KEY_PERM_C[index]
            c = c or ((if (bit < 32) (v0 ushr (31 - bit)) and 1 else (v1 ushr (63 - bit)) and 1) shl (31 - index))
        }
        var d = 0
        for (index in 0..27) {
            val bit = KEY_PERM_D[index]
            d = d or ((if (bit < 32) (v0 ushr (31 - bit)) and 1 else (v1 ushr (63 - bit)) and 1) shl (31 - index))
        }
        for (round in 0..15) {
            val shift = KEY_RND_SHIFT[round]
            c = ((c shl shift) or (c ushr (28 - shift))) and 0xFFFFFFF0.toInt()
            d = ((d shl shift) or (d ushr (28 - shift))) and 0xFFFFFFF0.toInt()
            val target = if (mode == DES_DECRYPT) 15 - round else round
            schedule[target].fill(0)
            for (j in 0..23) {
                val bit = (c ushr (31 - KEY_COMPRESSION[j])) and 1
                schedule[target][j / 8] = schedule[target][j / 8] or (bit shl (7 - (j % 8)))
            }
            for (j in 24..47) {
                val bit = (d ushr (31 - (KEY_COMPRESSION[j] - 27))) and 1
                schedule[target][j / 8] = schedule[target][j / 8] or (bit shl (7 - (j % 8)))
            }
        }
        return schedule
    }

    /** QQ's 3DES key setup: decrypt = K3(D) → K2(E) → K1(D) over the reversed 8-byte sub-keys. */
    internal fun tripleDesKeySetup(key: ByteArray, mode: Int): Array<Array<IntArray>>? {
        if (key.size < 24) return null
        return if (mode == DES_ENCRYPT) {
            arrayOf(
                keySchedule(key.copyOfRange(0, 8), DES_ENCRYPT),
                keySchedule(key.copyOfRange(8, 16), DES_DECRYPT),
                keySchedule(key.copyOfRange(16, 24), DES_ENCRYPT),
            )
        } else {
            arrayOf(
                keySchedule(key.copyOfRange(16, 24), DES_DECRYPT),
                keySchedule(key.copyOfRange(8, 16), DES_ENCRYPT),
                keySchedule(key.copyOfRange(0, 8), DES_DECRYPT),
            )
        }
    }

    private fun tripleDesCrypt(block: ByteArray, keys: Array<Array<IntArray>>): ByteArray {
        var current = block
        for (key in keys) current = desCrypt(current, key)
        return current
    }

}
