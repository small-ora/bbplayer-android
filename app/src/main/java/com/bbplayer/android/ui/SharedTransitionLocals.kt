package com.bbplayer.android.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.staticCompositionLocalOf

/** v9.2 P2-2：mini 播放器与全屏播放器封面共享元素需要的 Scope（由 AppRoot 提供）。 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** 当前 NavHost destination 的动画 Scope（用于目标封面的 sharedElement）。 */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** 封面共享元素 key：mini 封面与播放页封面必须一致。 */
const val PlayerCoverSharedKey: String = "player-cover"
