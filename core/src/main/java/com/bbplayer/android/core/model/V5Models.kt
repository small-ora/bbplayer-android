package com.bbplayer.android.core.model

import kotlinx.serialization.Serializable

/** Logged-in Bilibili account profile. */
@Serializable
data class BiliUser(
    val mid: Long,
    val name: String,
    val avatarUrl: String = "",
    val level: Int = 0,
    val vip: Boolean = false,
    val sign: String = "",
)

@Serializable
data class QrCodeInfo(
    val url: String,
    val qrKey: String,
    val expiresAtMs: Long = 0L,
)

@Serializable
data class QrPollResult(
    val state: QrPollState,
    val message: String = "",
    val user: BiliUser? = null,
)

@Serializable
enum class QrPollState { WAITING, SCANNED, EXPIRED, SUCCESS, ERROR }

@Serializable
data class FavoriteFolder(
    val id: Long,
    val title: String,
    val coverUrl: String = "",
    val mediaCount: Int = 0,
    val isDefault: Boolean = false,
)

@Serializable
data class BiliCollection(
    val id: Long,
    val title: String,
    val coverUrl: String = "",
    val count: Int = 0,
    val description: String = "",
)

@Serializable
data class UploaderInfo(
    val mid: Long,
    val name: String,
    val avatarUrl: String = "",
    val sign: String = "",
    val follower: Long = 0L,
    val followed: Boolean = false,
)

@Serializable
data class TrackPage(
    val items: List<Track> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val total: Int = 0,
)
