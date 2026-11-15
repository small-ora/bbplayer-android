package com.bbplayer.android.core.contracts

import com.bbplayer.android.core.model.BiliCollection
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.FavoriteFolder
import com.bbplayer.android.core.model.QrCodeInfo
import com.bbplayer.android.core.model.QrPollResult
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.core.model.TrackPage
import com.bbplayer.android.core.model.UploaderInfo
import kotlinx.coroutines.flow.Flow

/** Read-only cookie access for other data-layer components (never logged / never persisted here). */
interface AuthCookieProvider {
    fun cookieHeader(): String?
    fun cookieValue(name: String): String?
    fun csrfToken(): String?
    fun isLoggedIn(): Boolean
}

/**
 * Account login (v5).
 *
 * Credentials are stored by the implementation with Android Keystore AES-GCM encryption and are
 * never logged or uploaded anywhere except bilibili.com. QR login is the primary flow; manual
 * cookie paste is a power-user fallback.
 */
interface AuthRepository : AuthCookieProvider {
    fun session(): Flow<BiliUser?>

    suspend fun generateQrCode(): Result<QrCodeInfo>
    suspend fun pollQrCode(qrKey: String): Result<QrPollResult>

    /** Manual fallback: validates then stores an encrypted cookie string. */
    suspend fun loginWithCookie(rawCookie: String): Result<BiliUser>

    suspend fun refreshUser(): Result<BiliUser?>
    suspend fun logout()
}

/** Bilibili favourites (收藏夹). Requires login. */
interface FavoritesRepository {
    suspend fun folders(): Result<List<FavoriteFolder>>
    suspend fun folderTracks(mediaId: Long, page: Int = 1, pageSize: Int = 40): Result<TrackPage>

    /** Adds [addTo] / removes [removeFrom] for a video; empty ids leave the side untouched. */
    suspend fun updateFavorite(aid: Long, addTo: List<Long>, removeFrom: List<Long>): Result<Unit>
    suspend fun isFavorited(aid: Long): Result<List<Long>>
}

/** Bilibili 合集 / 系列 (collected collections + season contents). Requires login. */
interface CollectionRepository {
    suspend fun myCollections(): Result<List<BiliCollection>>
    suspend fun collectionTracks(seasonId: Long, page: Int = 1): Result<TrackPage>
}

/** 稍后再看 (watch later). Requires login. */
interface WatchLaterRepository {
    suspend fun list(): Result<List<Track>>
    suspend fun add(aid: Long, cid: Long? = null): Result<Unit>
    suspend fun remove(aid: Long): Result<Unit>
    suspend fun clear(): Result<Unit>
}

/** UP 主主页 / 关注 (uses WBI-signed space endpoints). Requires login. */
interface UploaderRepository {
    suspend fun info(mid: Long): Result<UploaderInfo>
    suspend fun videos(mid: Long, page: Int = 1, keyword: String = ""): Result<TrackPage>
    suspend fun setFollow(mid: Long, follow: Boolean): Result<Unit>
}

/** Optional: report playback progress to Bilibili history (disabled by default). */
interface PlaybackReporter {
    val enabled: Flow<Boolean>
    suspend fun report(track: Track, progressMs: Long): Result<Unit>
}
