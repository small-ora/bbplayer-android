package com.bbplayer.android.data

import com.bbplayer.android.core.model.ExternalTrack
import com.bbplayer.android.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchScorerTest {

    private fun external(
        title: String,
        artist: String = "",
        durationMs: Long = 0L,
    ) = ExternalTrack(title = title, artist = artist, durationMs = durationMs)

    private fun track(
        bvid: String = "BV1Ek4y1r7Rg",
        title: String,
        uploader: String = "",
        durationMs: Long = 0L,
    ) = Track(
        bvid = bvid,
        cid = 1L,
        title = title,
        uploader = uploader,
        durationMs = durationMs,
    )

    @Test
    fun `标题歌手时长完全一致得满分`() {
        val score = MatchScorer.score(
            external("夜曲", "周杰伦", 228_000L),
            track(title = "夜曲", uploader = "周杰伦", durationMs = 228_000L),
        )
        assertEquals(1f, score, 0.001f)
    }

    @Test
    fun `标题包含关系即可匹配（B站标题带修饰）`() {
        val match = MatchScorer.match(
            index = 0,
            external = external("夜曲", durationMs = 228_000L),
            candidates = listOf(
                track(
                    title = "【4K修复】周杰伦 - 夜曲 MV 2160P修复版",
                    uploader = "无损音乐合集",
                    durationMs = 232_000L,
                ),
            ),
        )
        assertNotNull(match.matched)
        assertEquals("BV1Ek4y1r7Rg", match.matched?.bvid)
        assertTrue(match.score >= MatchScorer.THRESHOLD)
    }

    @Test
    fun `标题相似度忽略括号修饰`() {
        assertTrue(MatchScorer.titleSimilarity("【无损音质】晴天", "晴天 (Live)") >= 0.8f)
        assertEquals(1f, MatchScorer.titleSimilarity("夜曲", "夜曲"), 0.001f)
        assertEquals(0f, MatchScorer.titleSimilarity("", "夜曲"), 0.001f)
    }

    @Test
    fun `UP主包含歌手名时歌手相似度高`() {
        assertTrue(MatchScorer.artistSimilarity("周杰伦", "周杰伦音乐电台") >= 0.8f)
        assertEquals(0f, MatchScorer.artistSimilarity("", "某UP主"), 0.001f)
    }

    @Test
    fun `时长差20秒刚好计分为零`() {
        assertEquals(1f, MatchScorer.durationScore(200_000L, 200_000L), 0.001f)
        assertEquals(0.5f, MatchScorer.durationScore(200_000L, 210_000L), 0.001f)
        assertEquals(0f, MatchScorer.durationScore(200_000L, 220_000L), 0.001f)
        assertEquals(0f, MatchScorer.durationScore(200_000L, 220_001L), 0.001f)
        // 任一方未知时长不计分
        assertEquals(0f, MatchScorer.durationScore(0L, 200_000L), 0.001f)
        assertEquals(0f, MatchScorer.durationScore(200_000L, 0L), 0.001f)
    }

    @Test
    fun `分数低于阈值时matched为null`() {
        val match = MatchScorer.match(
            index = 3,
            external = external("晴天", "周杰伦", 269_000L),
            candidates = listOf(track(title = "完全无关的视频标题", uploader = "某UP主", durationMs = 10_000L)),
        )
        assertNull(match.matched)
        assertTrue(match.score < MatchScorer.THRESHOLD)
        assertEquals(3, match.index)
    }

    @Test
    fun `没有候选时返回未匹配`() {
        val match = MatchScorer.match(0, external("夜曲", "周杰伦", 228_000L), emptyList())
        assertNull(match.matched)
        assertEquals(0f, match.score, 0.001f)
    }

    @Test
    fun `同分时取搜索排序靠前的候选`() {
        val candidates = listOf(
            track(bvid = "BV1first0000", title = "夜曲", uploader = "周杰伦", durationMs = 228_000L),
            track(bvid = "BV1second000", title = "夜曲", uploader = "周杰伦", durationMs = 228_000L),
        )
        val match = MatchScorer.match(0, external("夜曲", "周杰伦", 228_000L), candidates)
        assertEquals("BV1first0000", match.matched?.bvid)
    }

    @Test
    fun `候选里挑最高分`() {
        val candidates = listOf(
            track(bvid = "BV1bad000000", title = "告白气球", uploader = "某UP", durationMs = 100_000L),
            track(bvid = "BV1good00000", title = "夜曲 周杰伦", uploader = "周杰伦", durationMs = 228_000L),
        )
        val match = MatchScorer.match(0, external("夜曲", "周杰伦", 228_000L), candidates)
        assertEquals("BV1good00000", match.matched?.bvid)
    }
}
