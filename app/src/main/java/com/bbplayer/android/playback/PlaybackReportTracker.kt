package com.bbplayer.android.playback

import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * v5: periodically reports playback progress to Bilibili history when the user enabled it
 * (default off) and is logged in. Failures are silently ignored: reporting is best-effort.
 */
class PlaybackReportTracker(private val graph: AppGraph, private val scope: CoroutineScope) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            var lastReportAt = 0L
            var lastTrackKey = ""
            graph.playbackController.state.collectLatest { state ->
                val track = state.track ?: return@collectLatest
                if (!state.isPlaying) return@collectLatest
                val now = System.currentTimeMillis()
                val trackChanged = track.key != lastTrackKey
                // First progress after a track change, then every 20s.
                if (!trackChanged && now - lastReportAt < 20_000L) return@collectLatest
                lastReportAt = now
                lastTrackKey = track.key
                graph.playbackReporter.report(track, state.positionMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
