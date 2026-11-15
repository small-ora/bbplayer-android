package com.bbplayer.android.playback

import com.bbplayer.android.core.model.PlayMode
import kotlin.random.Random

/**
 * Pure index arithmetic for queue navigation. No ExoPlayer involved, so every
 * [`PlayMode`] boundary can be unit tested on the JVM.
 *
 * Semantics (kept identical to the ExoPlayer repeat/shuffle mapping in
 * [PlaybackMapping] so hardware buttons and in-app buttons agree):
 *
 * - [PlayMode.SEQUENCE]    : walk to the end, then stop ([NO_INDEX]).
 * - [PlayMode.REPEAT_ALL]  : wrap around in both directions.
 * - [PlayMode.REPEAT_ONE]  : next/previous stay on the current track (restart).
 * - [PlayMode.SHUFFLE]     : follow the injected shuffle traversal, wrapping
 *                            around (random playback never dead-ends on its own).
 *
 * A negative [currentIndex] means "nothing selected yet": next starts at the
 * first playable index, previous returns [NO_INDEX].
 */
object QueueNavigator {
    const val NO_INDEX: Int = -1

    fun nextIndex(
        currentIndex: Int,
        size: Int,
        playMode: PlayMode,
        shuffleOrder: List<Int>? = null,
    ): Int {
        if (size <= 0) return NO_INDEX
        if (currentIndex !in 0 until size) {
            return if (playMode == PlayMode.SHUFFLE) {
                shuffleTraversal(size, shuffleOrder).firstOrNull() ?: NO_INDEX
            } else {
                0
            }
        }
        return when (playMode) {
            PlayMode.SEQUENCE -> if (currentIndex < size - 1) currentIndex + 1 else NO_INDEX
            PlayMode.REPEAT_ALL -> (currentIndex + 1) % size
            PlayMode.REPEAT_ONE -> currentIndex
            PlayMode.SHUFFLE -> {
                val order = shuffleTraversal(size, shuffleOrder)
                val position = order.indexOf(currentIndex)
                if (position < 0) order.first() else order[(position + 1) % order.size]
            }
        }
    }

    fun previousIndex(
        currentIndex: Int,
        size: Int,
        playMode: PlayMode,
        shuffleOrder: List<Int>? = null,
    ): Int {
        if (size <= 0) return NO_INDEX
        if (currentIndex !in 0 until size) {
            return if (playMode == PlayMode.SHUFFLE) {
                shuffleTraversal(size, shuffleOrder).lastOrNull() ?: NO_INDEX
            } else {
                NO_INDEX
            }
        }
        return when (playMode) {
            PlayMode.SEQUENCE -> if (currentIndex > 0) currentIndex - 1 else NO_INDEX
            PlayMode.REPEAT_ALL -> (currentIndex - 1 + size) % size
            PlayMode.REPEAT_ONE -> currentIndex
            PlayMode.SHUFFLE -> {
                val order = shuffleTraversal(size, shuffleOrder)
                val position = order.indexOf(currentIndex)
                if (position < 0) order.last() else order[(position - 1 + order.size) % order.size]
            }
        }
    }

    /** Clamp `playQueue(startIndex)` into `0 until size`; 0 for an empty queue. */
    fun clampStartIndex(startIndex: Int, size: Int): Int {
        if (size <= 0) return 0
        return startIndex.coerceIn(0, size - 1)
    }

    /** Physical insertion index for "play next" (after the current item). */
    fun insertionIndexAfterCurrent(currentIndex: Int, size: Int): Int {
        if (size <= 0) return 0
        if (currentIndex !in 0 until size) return size
        return currentIndex + 1
    }

    /**
     * Deterministic Fisher-Yates permutation of `0 until size`.
     * Injecting [random] (e.g. `Random(42)`) makes the shuffle order testable.
     */
    fun shuffleOrder(size: Int, random: Random): List<Int> {
        if (size <= 0) return emptyList()
        val order = MutableList(size) { it }
        for (i in size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = order[i]
            order[i] = order[j]
            order[j] = tmp
        }
        return order
    }

    /** True when [order] is a permutation of `0 until size`. */
    fun isValidShuffleOrder(order: List<Int>?, size: Int): Boolean {
        if (order == null || order.size != size) return false
        val seen = BooleanArray(size)
        for (index in order) {
            if (index < 0 || index >= size || seen[index]) return false
            seen[index] = true
        }
        return true
    }

    private fun shuffleTraversal(size: Int, shuffleOrder: List<Int>?): List<Int> =
        if (isValidShuffleOrder(shuffleOrder, size)) {
            shuffleOrder!!
        } else {
            // Deterministic fallback so behavior stays defined even without an order.
            List(size) { it }
        }
}
