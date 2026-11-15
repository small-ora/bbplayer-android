package com.bbplayer.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for title cleaning / normalization used by online matching (AC D2/D5). */
class TitleCleanerTest {

    @Test
    fun `clean removes bracketed noise markers`() {
        assertEquals("夜に駆ける", TitleCleaner.clean("夜に駆ける【MV】"))
        assertEquals("晴天", TitleCleaner.clean("【官方MV】晴天"))
        assertEquals("晴天", TitleCleaner.clean("[1080P]晴天"))
        assertEquals("晴天", TitleCleaner.clean("晴天（中日双语）"))
        assertEquals("晴天", TitleCleaner.clean("晴天(4K)"))
    }

    @Test
    fun `clean keeps the content of a meaningful bracket group`() {
        assertEquals("夜に駆ける", TitleCleaner.clean("「夜に駆ける」"))
        assertEquals("夜に駆ける", TitleCleaner.clean("『夜に駆ける』"))
    }

    @Test
    fun `clean removes feat and ft suffixes`() {
        assertEquals("Song", TitleCleaner.clean("Song feat. Artist"))
        assertEquals("Song", TitleCleaner.clean("Song (feat. Artist)"))
        assertEquals("Song", TitleCleaner.clean("Song ft. Artist"))
        assertEquals("Song", TitleCleaner.clean("Song featuring Artist"))
    }

    @Test
    fun `clean removes cover-from suffix`() {
        assertEquals("某曲", TitleCleaner.clean("某曲 翻自 米津玄師"))
    }

    @Test
    fun `clean splits on separators and keeps the first segment`() {
        assertEquals("Title", TitleCleaner.clean("Title | 某某UP主"))
        assertEquals("Title", TitleCleaner.clean("Title ／ 某某UP主"))
        assertEquals("Title", TitleCleaner.clean("Title / 4K"))
        assertEquals("Title", TitleCleaner.clean("Title ｜ 投稿者"))
    }

    @Test
    fun `clean removes standalone quality and noise tokens`() {
        assertEquals("晴天", TitleCleaner.clean("晴天 4K 完整版"))
        assertEquals("晴天", TitleCleaner.clean("晴天 高音质 动态歌词"))
        assertEquals("晴天", TitleCleaner.clean("晴天 official"))
        assertEquals("晴天", TitleCleaner.clean("晴天 1080P"))
    }

    @Test
    fun `clean keeps an ordinary title untouched`() {
        assertEquals("夜に駆ける", TitleCleaner.clean("夜に駆ける"))
        assertEquals("Lemon", TitleCleaner.clean("Lemon"))
    }

    @Test
    fun `clean never returns blank`() {
        assertTrue(TitleCleaner.clean("【纯音乐】").isNotBlank())
        assertTrue(TitleCleaner.clean("   ").isBlank())
    }

    @Test
    fun `normalize lowercases and strips punctuation and whitespace`() {
        assertEquals("helloworld夜に駆ける", TitleCleaner.normalize("Hello, World! 夜に駆ける"))
        assertEquals("abc123", TitleCleaner.normalize("A B C 1-2-3"))
    }

    @Test
    fun `normalize converts full width characters`() {
        assertEquals("abc123", TitleCleaner.normalize("ＡＢＣ１２３"))
    }

    @Test
    fun `title score rewards exact and containment matches`() {
        assertEquals(1.0, TitleCleaner.titleScore("夜に駆ける", "夜に駆ける"), 1e-9)
        assertTrue(TitleCleaner.titleScore("夜に駆ける", "夜に駆ける (向夜晚奔去)") > 0.85)
        assertTrue(TitleCleaner.titleScore("夜に駆ける", "晴天") < 0.3)
    }

    @Test
    fun `artist score matches uploader against singer`() {
        assertTrue(TitleCleaner.artistScore("YOASOBI", "YOASOBI (ヨアソビ)") > 0.8)
        assertTrue(TitleCleaner.artistScore("YOASOBI", "周杰伦") < 0.2)
    }
}
