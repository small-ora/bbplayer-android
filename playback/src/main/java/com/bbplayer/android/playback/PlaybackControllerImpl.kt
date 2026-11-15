package com.bbplayer.android.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.model.PlayMode
import com.bbplayer.android.core.model.PlaybackState
import com.bbplayer.android.core.model.Track
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single ExoPlayer-backed [com.bbplayer.android.core.contracts.PlaybackController].
 *
 * Designed to be created once by the app DI container on the main thread:
 *
 * ```kotlin
 * PlaybackControllerImpl(context, applicationScope, musicRepository)
 * ```
 *
 * Threading: all public methods marshal onto the player's application looper.
 * `playQueue`/`restore` are `suspend` so callers can run them from
 * `viewModelScope` without blocking the main thread.
 *
 * Ownership: the controller owns the [ExoPlayer]; [PlaybackService] is started
 * once and attaches to that player to expose background playback, the media
 * notification and lock-screen controls (see [PlaybackServiceHolder]).
 */
class PlaybackControllerImpl(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val musicRepository: MusicRepository,
    /**
     * SPEC C3 offline priority: returns a fully downloaded file for a track key
     * (wired to the download manager by the app DI container). `null` = stream.
     */
    private val localFileProvider: (String) -> File? = { null },
    private val stateStore: PlaybackStateStore = PlaybackStateStore(
        PlaybackStateStore.defaultFile(appContext.filesDir),
    ),
    private val clock: () -> Long = System::currentTimeMillis,
) : com.bbplayer.android.core.contracts.PlaybackController {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val streamResolver = BiliStreamResolver(musicRepository, now = clock)
    private val dataSourceFactory = BbDataSourceFactory(streamResolver, appContext)

    private val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(appContext)
                .setDataSourceFactory(dataSourceFactory),
        )
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: Flow<PlaybackState> = _state.asStateFlow()

    private val sleepTimer = SleepTimer(scope = scope, now = clock, onExpire = { onSleepTimerExpired() })
    override val sleepTimerRemainingMs: Flow<Long> = sleepTimer.remainingMs

    // ---- sleep-timer fade-out (final 10 s) ----
    private var fadeJob: Job? = null

    @Volatile
    private var sleepTimerGeneration: Int = 0

    @Volatile
    private var fadeActive: Boolean = false

    @Volatile
    private var fadeBaseVolume: Float = 1f

    /** Physical queue order — the single source of truth (shuffle is a traversal). */
    private var queue: List<Track> = emptyList()

    private var playMode: PlayMode = PlayMode.SEQUENCE

    private var errorMessage: String? = null

    private var debouncePersistJob: Job? = null

    @Volatile
    private var pendingSnapshot: PersistedPlayback? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) persistNow()
            publish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publish()
            if (!isPlaying) persistNow() else schedulePersist()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            errorMessage = null
            publish()
            persistNow()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            publish()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            publish()
            schedulePersist()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "player error code=${error.errorCode}: ${error.message}", error)
            errorMessage = PlaybackErrorMapper.message(error.errorCode, error.cause ?: error)
            publish()
        }
    }

    private val tickerJob: Job = scope.launch {
        var tick = 0
        while (isActive) {
            delay(PlaybackConfig.POSITION_TICK_MS)
            tick += 1
            onPlayerThread {
                if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                    publish()
                    if (tick % 20 == 0) schedulePersist()
                }
            }
        }
    }

    init {
        require(Looper.myLooper() == Looper.getMainLooper()) {
            "PlaybackControllerImpl must be constructed on the main thread " +
                "(ExoPlayer requires a Looper thread)."
        }
        player.addListener(listener)
        PlaybackServiceHolder.attach(player, dataSourceFactory.artworkDataSourceFactory)
        ensureServiceStarted()
        publish()
    }

    // region PlaybackController — queue

    override suspend fun playQueue(
        tracks: List<Track>,
        startIndex: Int,
        playWhenReady: Boolean,
    ) = withContext(Dispatchers.Main.immediate) {
        if (tracks.isEmpty()) {
            clearQueue()
            return@withContext
        }
        queue = tracks.toList()
        errorMessage = null
        val start = QueueNavigator.clampStartIndex(startIndex, queue.size)
        player.setMediaItems(queue.map(::toMediaItem), start, 0L)
        applyPlayMode()
        player.prepare()
        player.playWhenReady = playWhenReady
        ensureServiceStarted()
        publish()
        persistNow()
    }

    override fun jumpTo(index: Int) = onPlayerThread {
        if (index in queue.indices) {
            player.seekTo(index, 0L)
            publish()
            schedulePersist()
        }
    }

    override fun addToQueueNext(track: Track) = onPlayerThread {
        val insertAt = QueueNavigator.insertionIndexAfterCurrent(
            currentIndex = player.currentMediaItemIndex,
            size = queue.size,
        ).coerceIn(0, queue.size)
        queue = queue.toMutableList().apply { add(insertAt, track) }
        player.addMediaItem(insertAt, toMediaItem(track))
        // ExoPlayer inserts randomly into its shuffle order; force the newly added
        // item to actually be the next one in shuffle traversal as well.
        moveInsertedItemToNextInShuffle(insertAt)
        publish()
        persistNow()
    }

    override fun addToQueueEnd(track: Track) = onPlayerThread {
        queue = queue + track
        player.addMediaItem(toMediaItem(track))
        publish()
        persistNow()
    }

    override fun removeFromQueue(index: Int) = onPlayerThread {
        if (index !in queue.indices || player.mediaItemCount == 0) return@onPlayerThread
        queue = queue.toMutableList().apply { removeAt(index) }
        player.removeMediaItem(index)
        publish()
        persistNow()
    }

    override fun clearQueue() = onPlayerThread {
        queue = emptyList()
        errorMessage = null
        player.clearMediaItems()
        publish()
        persistNow()
    }

    // endregion

    // region PlaybackController — transport

    override fun playPause() = onPlayerThread {
        when {
            player.playbackState == Player.STATE_ENDED -> playInternal()
            player.isPlaying || player.playWhenReady -> pauseInternal()
            else -> playInternal()
        }
    }

    override fun play() = onPlayerThread { playInternal() }

    override fun pause() = onPlayerThread { pauseInternal() }

    override fun next() = onPlayerThread {
        val size = player.mediaItemCount
        if (size == 0) return@onPlayerThread
        val target = QueueNavigator.nextIndex(
            currentIndex = player.currentMediaItemIndex,
            size = size,
            playMode = playMode,
            shuffleOrder = shuffleTraversalOrNull(),
        )
        if (target == QueueNavigator.NO_INDEX) {
            // SEQUENCE at the end of the queue: stop gracefully.
            player.pause()
        } else {
            player.seekTo(target, 0L)
            if (player.playWhenReady) player.play()
        }
        publish()
        schedulePersist()
    }

    override fun previous() = onPlayerThread {
        val size = player.mediaItemCount
        if (size == 0) return@onPlayerThread
        val target = QueueNavigator.previousIndex(
            currentIndex = player.currentMediaItemIndex,
            size = size,
            playMode = playMode,
            shuffleOrder = shuffleTraversalOrNull(),
        )
        if (target == QueueNavigator.NO_INDEX) {
            // Already at the first item in SEQUENCE mode: restart it.
            player.seekTo(0, 0L)
        } else {
            player.seekTo(target, 0L)
            if (player.playWhenReady) player.play()
        }
        publish()
        schedulePersist()
    }

    override fun seekTo(positionMs: Long) = onPlayerThread {
        if (player.mediaItemCount == 0) return@onPlayerThread
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
        schedulePersist()
    }

    override fun setPlayMode(mode: PlayMode) = onPlayerThread {
        if (playMode == mode) return@onPlayerThread
        playMode = mode
        applyPlayMode()
        publish()
        persistNow()
    }

    override fun setSpeed(speed: Float) = onPlayerThread {
        player.setPlaybackSpeed(PlaybackMapping.sanitizeSpeed(speed))
        publish()
        schedulePersist()
    }

    override fun setSleepTimer(minutes: Int?) = onPlayerThread {
        cancelSleepFade(restoreVolume = true)
        sleepTimer.set(minutes)
        if (SleepTimerMath.isEnabled(sleepTimer.endsAtMs)) startSleepFade()
        publish()
    }

    // endregion


    // region Sleep-timer fade-out (SPEC 4.5/4.8: last 10 s linear fade)

    private fun startSleepFade() {
        sleepTimerGeneration += 1
        val generation = sleepTimerGeneration
        fadeJob?.cancel()
        fadeJob = scope.launch {
            while (isActive) {
                val endsAtMs = sleepTimer.endsAtMs
                if (sleepTimerGeneration != generation || !SleepTimerMath.isEnabled(endsAtMs)) break
                val remainingMs = SleepTimerMath.remainingMs(endsAtMs, clock())
                if (remainingMs <= SleepFadeMath.FADE_WINDOW_MS) {
                    if (!fadeActive) {
                        fadeBaseVolume = withContext(Dispatchers.Main.immediate) { player.volume }
                        fadeActive = true
                    }
                    val target = SleepFadeMath.volumeAt(remainingMs, fadeBaseVolume)
                    withContext(Dispatchers.Main.immediate) { player.volume = target }
                }
                delay(SleepFadeMath.STEP_MS)
            }
        }
    }

    /** Restores the pre-fade volume and stops the ramp (cancel / re-set / expiry). */
    private fun cancelSleepFade(restoreVolume: Boolean) {
        sleepTimerGeneration += 1
        fadeJob?.cancel()
        fadeJob = null
        if (restoreVolume && fadeActive) {
            val base = fadeBaseVolume
            fadeActive = false
            onPlayerThread { player.volume = base }
        }
    }

    private fun onSleepTimerExpired() {
        cancelSleepFade(restoreVolume = true)
        onPlayerThread {
            if (player.isPlaying || player.playWhenReady) player.pause()
            publish()
            persistNow()
        }
    }

    // endregion

    // region Status-bar lyric line (SPEC G5)

    override fun setLyricLineProvider(provider: (() -> String?)?) {
        StatusBarLyricLine.setProvider(provider)
        notifyLyricLineChanged()
    }

    /** Appearance setting "状态栏歌词" toggle (driven by the app settings screen). */
    fun setLyricLineEnabled(enabled: Boolean) {
        StatusBarLyricLine.setEnabled(enabled)
        notifyLyricLineChanged()
    }

    /** Force the media notification to refresh the lyric line immediately. */
    fun notifyLyricLineChanged() {
        // MediaSessionService notification APIs must run on the main thread.
        mainHandler.post { PlaybackServiceHolder.notifyLyricLineChanged() }
    }

    // endregion

    // region Resume / persistence

    /**
     * Restores queue + index + position + play mode from disk.
     *
     * @return false when there is no saved queue (nothing to resume).
     */
    suspend fun restore(autoplay: Boolean = false): Boolean {
        val snapshot = stateStore.load() ?: return false
        if (snapshot.queue.isEmpty()) return false
        withContext(Dispatchers.Main.immediate) {
            queue = snapshot.queue
            playMode = snapshot.playMode
            errorMessage = null
            val start = QueueNavigator.clampStartIndex(snapshot.currentIndex, queue.size)
            player.setMediaItems(queue.map(::toMediaItem), start, snapshot.positionMs.coerceAtLeast(0L))
            player.setPlaybackSpeed(PlaybackMapping.sanitizeSpeed(snapshot.speed))
            applyPlayMode()
            player.prepare()
            player.playWhenReady = autoplay
            ensureServiceStarted()
            publish()
        }
        persistNow()
        return true
    }

    /** Releases the player. Called by the DI container when the app process ends. */
    fun release() {
        tickerJob.cancel()
        sleepTimer.cancel()
        fadeJob?.cancel()
        debouncePersistJob?.cancel()
        player.removeListener(listener)
        PlaybackServiceHolder.detach(player)
        runCatching { player.release() }
    }

    private fun schedulePersist() {
        onPlayerThread {
            pendingSnapshot = snapshot()
            if (debouncePersistJob?.isActive == true) return@onPlayerThread
            debouncePersistJob = scope.launch {
                delay(PlaybackConfig.PERSIST_DEBOUNCE_MS)
                pendingSnapshot?.let { persist(it) }
            }
        }
    }

    private fun persistNow() {
        onPlayerThread {
            val snapshot = snapshot()
            pendingSnapshot = snapshot
            persist(snapshot)
        }
    }

    private fun persist(snapshot: PersistedPlayback) {
        scope.launch { runCatching { stateStore.save(snapshot) } }
    }

    private fun snapshot(): PersistedPlayback = PersistedPlayback(
        queue = queue,
        currentIndex = effectiveCurrentIndex(),
        positionMs = currentPositionMs(),
        playMode = playMode,
        speed = player.playbackParameters.speed,
    )

    // endregion

    // region Internals

    private fun playInternal() {
        if (player.mediaItemCount == 0) return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(effectiveCurrentIndex().coerceAtLeast(0), 0L)
        }
        // Items may have been queued while nothing was loaded yet.
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        ensureServiceStarted()
        errorMessage = null
        player.play()
        publish()
    }

    private fun pauseInternal() {
        player.pause()
        publish()
        persistNow()
    }

    private fun applyPlayMode() {
        player.repeatMode = PlaybackMapping.repeatMode(playMode)
        val shuffle = PlaybackMapping.shuffleEnabled(playMode)
        if (shuffle && player.mediaItemCount > 0) {
            player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(player.mediaItemCount))
        }
        player.shuffleModeEnabled = shuffle
    }

    /** ExoPlayer's shuffle traversal as a plain index list (for [QueueNavigator]). */
    private fun shuffleTraversalOrNull(): List<Int>? {
        if (!player.shuffleModeEnabled) return null
        val size = player.mediaItemCount
        if (size <= 0) return null
        val order = player.shuffleOrder
        if (order.length != size) return null
        val traversal = ArrayList<Int>(size)
        var index = order.firstIndex
        var guard = 0
        while (index != C.INDEX_UNSET && guard <= size) {
            traversal.add(index)
            guard += 1
            index = order.getNextIndex(index)
        }
        return traversal.takeIf { QueueNavigator.isValidShuffleOrder(it, size) }
    }

    /**
     * Rebuilds the shuffle traversal so [insertedIndex] directly follows the
     * current item. No-op when shuffle is disabled.
     */
    private fun moveInsertedItemToNextInShuffle(insertedIndex: Int) {
        if (!player.shuffleModeEnabled) return
        val size = player.mediaItemCount
        if (size <= 1 || insertedIndex !in 0 until size) return
        val traversal = shuffleTraversalOrNull()?.toMutableList() ?: return
        if (traversal.size != size || !traversal.remove(insertedIndex)) return
        val current = player.currentMediaItemIndex
        val currentPosition = traversal.indexOf(current)
        val insertPosition = if (currentPosition >= 0) currentPosition + 1 else traversal.size
        traversal.add(insertPosition, insertedIndex)
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(traversal.toIntArray(), clock()))
    }

    private fun effectiveCurrentIndex(): Int {
        if (player.mediaItemCount == 0) return -1
        return player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
    }

    private fun currentPositionMs(): Long {
        if (player.mediaItemCount == 0) return 0L
        if (player.playbackState == Player.STATE_IDLE) return 0L
        return player.currentPosition.coerceAtLeast(0L)
    }

    private fun toMediaItem(track: Track): MediaItem {
        val quality = PlaybackMapping.qualityForTrack(track)
        val source = PlaybackSourceSelector.select(track.key, localFileProvider)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.displayTitle)
            .setArtist(track.uploader)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                track.coverUrl
                    .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
        val itemBuilder = MediaItem.Builder()
            .setMediaId(track.key)
            .setMediaMetadata(metadata)
        // Offline downloads play straight from disk (no resolver / no network);
        // everything else keeps the lazy bbplayer:// stream URI.
        when (source) {
            is PlaybackSource.Local -> itemBuilder.setUri(Uri.fromFile(source.file))
            PlaybackSource.Remote -> itemBuilder.setUri(TrackUri.build(track, quality))
        }
        return itemBuilder.build()
    }

    private fun publish() {
        if (Looper.myLooper() != player.applicationLooper) {
            mainHandler.post(::publish)
            return
        }
        val index = effectiveCurrentIndex()
        val track = queue.getOrNull(index)
        val duration = player.duration.let { if (it == C.TIME_UNSET || it <= 0L) track?.durationMs ?: 0L else it }
        _state.value = PlaybackState(
            track = track,
            queue = queue,
            currentIndex = index,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = currentPositionMs(),
            durationMs = duration,
            bufferedMs = if (player.mediaItemCount == 0) 0L else player.bufferedPosition.coerceAtLeast(0L),
            playMode = playMode,
            speed = player.playbackParameters.speed,
            sleepTimerEndsAt = sleepTimer.endsAtMs,
            errorMessage = errorMessage,
        )
    }

    private fun ensureServiceStarted() {
        runCatching {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
        }
    }

    private inline fun onPlayerThread(crossinline block: () -> Unit) {
        if (Looper.myLooper() == player.applicationLooper) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    // endregion

    private companion object {
        private const val TAG = "BBPlayback"
    }
}
