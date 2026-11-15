package com.bbplayer.android.playback

import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches the playback state and records listening history / resume points:
 * a play record is written when the track changes or playback stops, and the resume point
 * is persisted every few seconds so the app can restore it on the next launch.
 */
class PlaybackHistoryTracker(private val graph: AppGraph, private val scope: CoroutineScope) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            var current: Track? = null
            var playedMs = 0L
            var lastPosition = 0L
            var lastResumeSave = 0L
            var wasPlaying = false
            graph.playbackController.state.collectLatest { state ->
                val track = state.track
                if (track?.key != current?.key) {
                    current?.let { previous ->
                        runCatching {
                            graph.historyRepository.recordPlay(
                                track = previous,
                                listenedMs = playedMs,
                                completed = playedMs > 0 && lastPosition >= (previous.durationMs * 0.9).toLong(),
                            )
                        }
                    }
                    current = track
                    playedMs = 0L
                    lastResumeSave = 0L
                }
                if (state.isPlaying && track != null) {
                    playedMs += (state.positionMs - lastPosition).coerceIn(0L, 5_000L)
                } else if (wasPlaying && track != null && playedMs > 500L) {
                    // Paused / finished / errored: persist the listen so short tracks and the
                    // last track of a session are not lost from history & statistics.
                    runCatching {
                        graph.historyRepository.recordPlay(
                            track = track,
                            listenedMs = playedMs,
                            completed = playedMs > 0 && state.positionMs >= (track.durationMs * 0.9).toLong(),
                        )
                    }
                    playedMs = 0L
                }
                wasPlaying = state.isPlaying
                lastPosition = state.positionMs
                val now = System.currentTimeMillis()
                if (track != null && state.isPlaying && now - lastResumeSave > 5_000) {
                    lastResumeSave = now
                    runCatching { graph.historyRepository.saveResume(track.key, state.positionMs) }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
