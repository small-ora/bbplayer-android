package com.bbplayer.android.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

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

        val session = sessionBuilder.build()
        mediaSession = session
        addSession(session)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                // White silhouette icon bundled by the playback module.
                setSmallIcon(R.drawable.ic_stat_bbplayer)
            },
        )
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
}
