package com.bbplayer.android.lyrics

import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [QqQrcDecryptor] (QQ's custom Triple-DES + zlib pipeline) and its integration with [QrcParser].
 *
 * `qq_qrc_real.hex` is a real `GetPlayLyricInfo` payload captured anonymously on 2026-11-15 (晴天 / 周杰伦,
 * songmid `0039MnYb0qxYhV`); `qq_qrc_synthetic.hex` was produced offline with the reference implementation so the
 * legacy and modern word-tag orientations are both covered.
 */
class QqQrcDecryptorTest {

    private fun fixture(name: String): String {
        val stream = javaClass.getResourceAsStream("/$name")
        assertNotNull("missing test fixture $name", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
    }

    @Test
    fun `decrypts a real GetPlayLyricInfo payload into qrc text`() {
        val plain = QqQrcDecryptor.decrypt(fixture("qq_qrc_real.hex"))

        assertNotNull(plain)
        val text = plain!!
        assertTrue(text, text.contains("<QrcInfos>"))
        assertTrue(text, text.contains("LyricContent=\""))
        assertTrue(text, text.contains("[ti:晴天]"))
        assertTrue(text, text.contains("晴(0,160)")) // legacy postfix word tag survived decryption
    }

    @Test
    fun `decrypted real payload parses into word timed lines`() {
        val plain = QqQrcDecryptor.decrypt(fixture("qq_qrc_real.hex"))!!
        val lyrics = QrcParser.parse(plain)

        assertTrue("expected many timed lines, got ${lyrics.lines.size}", lyrics.lines.size >= 40)
        val first = lyrics.lines.first()
        assertEquals(0L, first.startMs)
        assertEquals("晴天 - 周杰伦 (Jay Chou)", first.text)
        assertTrue(first.words.size >= 8)
        assertEquals("晴", first.words.first().text)
        assertEquals(0L, first.words.first().startMs)
        assertEquals(160L, first.words.first().endMs)
        assertTrue("every non-metadata line should carry words", lyrics.lines.all { it.words.isNotEmpty() })
    }

    @Test
    fun `decrypts the synthetic fixture with both word tag orientations`() {
        val plain = QqQrcDecryptor.decrypt(fixture("qq_qrc_synthetic.hex"))!!
        val lyrics = QrcParser.parse(plain)

        assertEquals(2, lyrics.lines.size)
        assertEquals("你好", lyrics.lines[0].text)
        assertEquals(listOf("你", "好"), lyrics.lines[0].words.map { it.text })
        assertEquals("Helloworld", lyrics.lines[1].text)
        assertEquals(listOf("Hello", "world"), lyrics.lines[1].words.map { it.text })
    }

    @Test
    fun `rejects blank non hex odd length and corrupt input`() {
        assertNull(QqQrcDecryptor.decrypt(""))
        assertNull(QqQrcDecryptor.decrypt("   "))
        assertNull(QqQrcDecryptor.decrypt("not-a-hex-payload"))
        assertNull(QqQrcDecryptor.decrypt("ABCDE")) // odd number of hex digits
        assertNull(QqQrcDecryptor.decrypt("0000000000000000")) // valid block, not a zlib stream
    }

    @Test
    fun `hex parsing tolerates embedded whitespace`() {
        val hex = fixture("qq_qrc_real.hex")
        val pretty = hex.chunked(64).joinToString("\n  ")

        assertEquals(QqQrcDecryptor.decrypt(hex), QqQrcDecryptor.decrypt(pretty))
    }

    @Test
    fun `inflates both zlib wrapped and raw deflate streams`() {
        val payload = "[0,1000]你好".toByteArray(Charsets.UTF_8)
        val zlibWrapped = deflate(payload, nowrap = false)
        val rawDeflate = deflate(payload, nowrap = true)

        assertEquals(String(payload, Charsets.UTF_8), String(QqQrcDecryptor.inflate(zlibWrapped)!!, Charsets.UTF_8))
        assertEquals(String(payload, Charsets.UTF_8), String(QqQrcDecryptor.inflate(rawDeflate)!!, Charsets.UTF_8))
        assertNull(QqQrcDecryptor.inflate(byteArrayOf(1, 2, 3, 4)))
    }

    private fun deflate(data: ByteArray, nowrap: Boolean): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, nowrap)
        try {
            deflater.setInput(data)
            deflater.finish()
            val buffer = ByteArray(256)
            val length = deflater.deflate(buffer)
            return buffer.copyOf(length)
        } finally {
            deflater.end()
        }
    }
}
