package com.bbplayer.android.playback

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * Media notification provider that shows the current lyric line in the second
 * row (SPEC G5 "状态栏歌词").
 *
 * Implementation choice: instead of re-building the notification (which would
 * drop Media3's [androidx.media3.session.MediaStyleNotificationHelper.MediaStyle]
 * compact view / media buttons), we subclass Media3's default provider and only
 * override [getNotificationContentText], i.e. the line under the title. The
 * notification is rebuilt by Media3 whenever the session/player emits an event,
 * and [PlaybackService] additionally calls `triggerNotificationUpdate()` when
 * the lyric row changes (~500 ms poll); see [StatusBarLyricLine].
 *
 * Limitation (documented in the delivery report): while a lyric line is shown
 * it replaces the artist line of the notification; the artist is still visible
 * inside the app and in the lock-screen expanded view's metadata handling.
 */
class LyricNotificationProvider(
    context: Context,
) : DefaultMediaNotificationProvider(context) {

    init {
        // White silhouette icon bundled by the playback module.
        setSmallIcon(R.drawable.ic_stat_bbplayer)
    }

    override fun getNotificationContentText(mediaMetadata: MediaMetadata): CharSequence {
        StatusBarLyricLine.current()?.let { return it }
        return super.getNotificationContentText(mediaMetadata) ?: ""
    }
}
