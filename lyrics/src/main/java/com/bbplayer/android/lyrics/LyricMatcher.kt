package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.LyricCandidate
import com.bbplayer.android.core.model.Track

/** A candidate together with its match score, as produced by [LyricMatcher.ranked]. */
data class ScoredCandidate(val candidate: LyricCandidate, val score: Double)

/**
 * Scores online lyric candidates against a [Track]. Pure Kotlin, unit-testable.
 *
 * `score = 0.75 * titleSimilarity(cleaned displayTitle, candidate.title) + 0.25 * artistSimilarity(uploader, artist)`
 * Candidates below [MIN_SCORE] are rejected by [bestCandidate] so a wrong song's lyrics are never cached.
 */
object LyricMatcher {

    /** Minimum weighted score a candidate must reach to be auto-selected. */
    const val MIN_SCORE: Double = 0.5

    fun score(track: Track, candidate: LyricCandidate): Double {
        val query = TitleCleaner.clean(track.displayTitle)
        val title = TitleCleaner.titleScore(query, candidate.title)
        val artist = TitleCleaner.artistScore(track.uploader, candidate.artist)
        return TitleCleaner.TITLE_WEIGHT * title + TitleCleaner.ARTIST_WEIGHT * artist
    }

    /** Candidates sorted by score descending; stable, so provider order (QQ before NetEase) breaks ties. */
    fun ranked(track: Track, candidates: List<LyricCandidate>): List<ScoredCandidate> =
        candidates
            .mapIndexed { index, candidate -> Triple(index, candidate, score(track, candidate)) }
            .sortedWith(compareByDescending<Triple<Int, LyricCandidate, Double>> { it.third }.thenBy { it.first })
            .map { ScoredCandidate(it.second, it.third) }

    /** Highest-scoring candidate with `score >= minScore`, or null when nothing matches well enough. */
    fun bestCandidate(
        track: Track,
        candidates: List<LyricCandidate>,
        minScore: Double = MIN_SCORE,
    ): LyricCandidate? = ranked(track, candidates).firstOrNull { it.score >= minScore }?.candidate
}
