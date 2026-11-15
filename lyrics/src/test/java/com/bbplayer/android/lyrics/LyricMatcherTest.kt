package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.LyricCandidate
import com.bbplayer.android.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for candidate scoring / selection (AC D2/D5). */
class LyricMatcherTest {

    private val track = Track(
        bvid = "BV1xx411c7mD",
        cid = 123456,
        title = "夜に駆ける【MV】",
        uploader = "YOASOBI",
    )

    private fun candidate(title: String, artist: String, sourceId: String = title) =
        LyricCandidate(title = title, artist = artist, source = QqMusicApi.SOURCE, sourceId = sourceId)

    @Test
    fun `exact title and artist scores full marks`() {
        val score = LyricMatcher.score(track, candidate("夜に駆ける", "YOASOBI"))
        assertEquals(1.0, score, 1e-9)
    }

    @Test
    fun `cleaned title noise is ignored`() {
        val score = LyricMatcher.score(track, candidate("夜に駆ける", "YOASOBI"))
        assertTrue(score >= LyricMatcher.MIN_SCORE)
    }

    @Test
    fun `title only match still passes with a different artist`() {
        val score = LyricMatcher.score(track, candidate("夜に駆ける", "某翻唱歌手"))
        assertEquals(TitleCleaner.TITLE_WEIGHT, score, 1e-9)
        assertTrue(score >= LyricMatcher.MIN_SCORE)
    }

    @Test
    fun `best candidate is the highest scoring one`() {
        val candidates = listOf(
            candidate("晴天", "周杰伦", "bad-1"),
            candidate("夜に駆ける (向夜晚奔去)", "YOASOBI (ヨアソビ)", "good-1"),
            candidate("夜に駆ける", "YOASOBI", "good-2"),
        )

        val best = LyricMatcher.bestCandidate(track, candidates)
        assertEquals("good-2", best?.sourceId)
    }

    @Test
    fun `candidates below the threshold are rejected`() {
        val candidates = listOf(
            candidate("晴天", "周杰伦", "bad-1"),
            candidate("稻香", "周杰伦", "bad-2"),
        )
        assertNull(LyricMatcher.bestCandidate(track, candidates))
        assertNull(LyricMatcher.bestCandidate(track, emptyList()))
    }

    @Test
    fun `ranked keeps provider order for equal scores`() {
        val candidates = listOf(
            candidate("夜に駆ける", "YOASOBI", "qq-1"),
            candidate("夜に駆ける", "YOASOBI", "netease-1"),
        )
        val ranked = LyricMatcher.ranked(track, candidates)

        assertEquals(2, ranked.size)
        assertEquals("qq-1", ranked[0].candidate.sourceId)
        assertTrue(ranked[0].score >= ranked[1].score)
    }

    @Test
    fun `ranked is sorted by score descending`() {
        val ranked = LyricMatcher.ranked(
            track,
            listOf(
                candidate("晴天", "周杰伦", "low"),
                candidate("夜に駆ける", "YOASOBI", "high"),
            ),
        )
        assertEquals("high", ranked[0].candidate.sourceId)
        assertEquals("low", ranked[1].candidate.sourceId)
    }
}
