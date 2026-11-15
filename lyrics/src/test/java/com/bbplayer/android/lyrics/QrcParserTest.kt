package com.bbplayer.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [QrcParser]: both word-tag orientations, relative timestamps and the XML wrapper. */
class QrcParserTest {

    @Test
    fun `legacy postfix tags map text before the tag to that tag`() {
        val lyrics = QrcParser.parse("[0,2000]晴(0,400)天(400,600)")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("晴天", line.text)
        assertEquals(listOf("晴", "天"), line.words.map { it.text })
        assertEquals(0L, line.words[0].startMs)
        assertEquals(400L, line.words[0].endMs)
        assertEquals(400L, line.words[1].startMs)
        assertEquals(1_000L, line.words[1].endMs)
        assertEquals(2_000L, line.endMs)
    }

    @Test
    fun `modern prefix tags keep the word after the tag`() {
        val lyrics = QrcParser.parse("[0,2000]<0,500>你<500,500>好")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("你好", line.text)
        assertEquals(listOf("你", "好"), line.words.map { it.text })
        assertEquals(0L, line.words[0].startMs)
        assertEquals(500L, line.words[0].endMs)
        assertEquals(1_000L, line.words[1].endMs)
    }

    @Test
    fun `word starts smaller than the line start are treated as line relative`() {
        val lyrics = QrcParser.parse("[10000,2000]你(0,500)好(500,500)")

        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals(10_000L, lyrics.lines[0].words[0].startMs)
        assertEquals(10_500L, lyrics.lines[0].words[1].startMs)
    }

    @Test
    fun `xml wrapper and html entities are decoded`() {
        val wrapped = """
            <?xml version="1.0" encoding="utf-8"?>
            <QrcInfos><LyricInfo><Lyric_1 LyricType="1"
            LyricContent="[ti:Test]&#10;[0,1000]你(0,500)好(500,500)"/></LyricInfo></QrcInfos>
        """.trimIndent()

        val lyrics = QrcParser.parse(wrapped)

        assertEquals(1, lyrics.lines.size)
        assertEquals("你好", lyrics.lines[0].text)
        assertEquals(2, lyrics.lines[0].words.size)
    }

    @Test
    fun `lines without word tags keep line level timing only`() {
        val lyrics = QrcParser.parse("[0,2000]只有整行时间\n[2000,2000]第二行")

        assertEquals(2, lyrics.lines.size)
        assertTrue(lyrics.lines.all { it.words.isEmpty() })
        assertEquals(2_000L, lyrics.lines[0].endMs)
        assertEquals(4_000L, lyrics.lines[1].endMs)
    }

    @Test
    fun `malformed input never throws and yields no timed lines`() {
        assertTrue(QrcParser.parse("").lines.isEmpty())
        assertTrue(QrcParser.parse("garbage without tags").lines.isEmpty())
        assertTrue(QrcParser.parse("[abc,def]no timestamps").lines.isEmpty())
    }

    @Test
    fun `translation track is merged by start time`() {
        val lyrics = QrcParser.parse("[0,2000]你(0,500)好", translationText = "[00:00.00]你好呀")

        assertNotNull(lyrics.lines.first().translation)
        assertEquals("你好呀", lyrics.lines.first().translation)
    }
}
