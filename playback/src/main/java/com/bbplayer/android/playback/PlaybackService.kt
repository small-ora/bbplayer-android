package com.bbplayer.android.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground shell for background playback.
 *
 * Architecture note (see also [PlaybackControllerImpl]): the single ExoPlayer
 * instance is owned by the app-scoped controller so that UI/controller state and
 * playback survive activity changes; this service takes that player, exposes it
 * through a [MediaSession] and lets Media3 drive the foreground notification,
 * lock screen and media-button control. [PlaybackServiceHolder] is the (tiny)
 * hand-off point between the two.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var bitmapLoader: BitmapLoader? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lyricLineTicker: Job? = null
    private var lastLyricLine: String? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlaybackServiceHolder.player
        if (player == null) {
            // Service revived without the app-scoped controller (e.g. process
            // restart). Nothing to attach to: stop quietly instead of crashing.
            stopSelf()
            return
        }
        player.setWakeMode(C.WAKE_MODE_NETWORK)

        val sessionBuilder = MediaSession.Builder(this, player)
            .setId(PlaybackConfig.SESSION_ID)

        PlaybackServiceHolder.artworkDataSourceFactory?.let { dataSourceFactory ->
            val loader = DataSourceBitmapLoader.Builder(this)
                .setDataSourceFactory(dataSourceFactory)
                .setMakeShared(true)
                .build()
            bitmapLoader = loader
            sessionBuilder.setBitmapLoader(loader)
        }
        sessionActivityPendingIntent()?.let(sessionBuilder::setSessionActivity)

        // Status-bar lyric line: the second notification row reads the current
        // lyric line at build time; the ticker below refreshes it on change.
        setMediaNotificationProvider(LyricNotificationProvider(this))

        val session = sessionBuilder.build()
        mediaSession = session
        addSession(session)

        PlaybackServiceHolder.setLyricLineRefresher { triggerNotificationUpdateSafely() }
        startLyricLineTicker()
    }

    /**
     * Polls the current lyric line every 500 ms and asks Media3 to rebuild the
     * notification when it changes. Cheap (one string compare) and independent
     * from the lyrics UI, so either side can come and go.
     */
    private fun startLyricLineTicker() {
        lyricLineTicker?.cancel()
        lastLyricLine = StatusBarLyricLine.current()
        lyricLineTicker = serviceScope.launch {
            while (isActive) {
                delay(LYRIC_LINE_POLL_MS)
                if (mediaSession == null) break
                val line = StatusBarLyricLine.current()
                if (line != lastLyricLine) {
                    lastLyricLine = line
                    triggerNotificationUpdateSafely()
                }
            }
        }
    }

    private fun triggerNotificationUpdateSafely() {
        if (mediaSession == null) return
        runCatching { triggerNotificationUpdate() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = PlaybackServiceHolder.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        lyricLineTicker?.cancel()
        lyricLineTicker = null
        PlaybackServiceHolder.setLyricLineRefresher(null)
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        bitmapLoader = null
        super.onDestroy()
    }

    private fun sessionActivityPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val LYRIC_LINE_POLL_MS = 500L
    }
}

/**
 * Hand-off registry between the app-scoped controller (which owns [ExoPlayer])
 * and [PlaybackService] (which owns the [MediaSession] + notification).
 *
 * Only one controller exists per process (held by the app DI container), so a
 * single slot is enough; it is never used for anything else.
 */
object PlaybackServiceHolder {
    @Volatile
    var player: ExoPlayer? = null
        private set

    @Volatile
    var artworkDataSourceFactory: DataSource.Factory? = null
        private set

    fun attach(player: ExoPlayer, artworkDataSourceFactory: DataSource.Factory?) {
        this.player = player
        this.artworkDataSourceFactory = artworkDataSourceFactory
    }

    fun detach(player: ExoPlayer) {
        if (this.player === player) {
            this.player = null
            this.artworkDataSourceFactory = null
        }
    }

    @Volatile
    private var lyricLineRefresher: (() -> Unit)? = null

    /** Called by [PlaybackService] so the controller can refresh the notification. */
    fun setLyricLineRefresher(refresher: (() -> Unit)?) {
        this.lyricLineRefresher = refresher
    }

    /** Force an immediate notification refresh for a new lyric line (SPEC G5). */
    fun notifyLyricLineChanged() {
        lyricLineRefresher?.invoke()
    }
}
