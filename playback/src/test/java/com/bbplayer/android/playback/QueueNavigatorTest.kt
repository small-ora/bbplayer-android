package com.bbplayer.android.playback

import com.bbplayer.android.core.model.PlayMode
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueNavigatorTest {
    private val shuffle = listOf(2, 0, 4, 1, 3)

    @Test
    fun `sequence walks forward then stops at the end`() {
        assertEquals(1, QueueNavigator.nextIndex(0, 3, PlayMode.SEQUENCE))
        assertEquals(2, QueueNavigator.nextIndex(1, 3, PlayMode.SEQUENCE))
        assertEquals(QueueNavigator.NO_INDEX, QueueNavigator.nextIndex(2, 3, PlayMode.SEQUENCE))
    }

    @Test
    fun `sequence previous stops at the first item`() {
        assertEquals(1, QueueNavigator.previousIndex(2, 3, PlayMode.SEQUENCE))
        assertEquals(0, QueueNavigator.previousIndex(1, 3, PlayMode.SEQUENCE))
        assertEquals(QueueNavigator.NO_INDEX, QueueNavigator.previousIndex(0, 3, PlayMode.SEQUENCE))
    }

    @Test
    fun `repeat all wraps in both directions`() {
        assertEquals(0, QueueNavigator.nextIndex(2, 3, PlayMode.REPEAT_ALL))
        assertEquals(2, QueueNavigator.previousIndex(0, 3, PlayMode.REPEAT_ALL))
        assertEquals(1, QueueNavigator.nextIndex(0, 3, PlayMode.REPEAT_ALL))
        assertEquals(1, QueueNavigator.previousIndex(2, 3, PlayMode.REPEAT_ALL))
    }

    @Test
    fun `repeat one stays on the current track`() {
        assertEquals(1, QueueNavigator.nextIndex(1, 3, PlayMode.REPEAT_ONE))
        assertEquals(1, QueueNavigator.previousIndex(1, 3, PlayMode.REPEAT_ONE))
        assertEquals(2, QueueNavigator.nextIndex(2, 3, PlayMode.REPEAT_ONE))
        assertEquals(0, QueueNavigator.previousIndex(0, 3, PlayMode.REPEAT_ONE))
    }

    @Test
    fun `shuffle follows the injected traversal and wraps`() {
        // traversal: 2 -> 0 -> 4 -> 1 -> 3 -> wrap to 2
        assertEquals(0, QueueNavigator.nextIndex(2, 5, PlayMode.SHUFFLE, shuffle))
        assertEquals(4, QueueNavigator.nextIndex(0, 5, PlayMode.SHUFFLE, shuffle))
        assertEquals(1, QueueNavigator.nextIndex(4, 5, PlayMode.SHUFFLE, shuffle))
        assertEquals(2, QueueNavigator.nextIndex(3, 5, PlayMode.SHUFFLE, shuffle))
        // previous wraps backwards
        assertEquals(3, QueueNavigator.previousIndex(2, 5, PlayMode.SHUFFLE, shuffle))
        assertEquals(2, QueueNavigator.previousIndex(0, 5, PlayMode.SHUFFLE, shuffle))
    }

    @Test
    fun `single track behavior per mode`() {
        assertEquals(QueueNavigator.NO_INDEX, QueueNavigator.nextIndex(0, 1, PlayMode.SEQUENCE))
        assertEquals(QueueNavigator.NO_INDEX, QueueNavigator.previousIndex(0, 1, PlayMode.SEQUENCE))
        assertEquals(0, QueueNavigator.nextIndex(0, 1, PlayMode.REPEAT_ALL))
        assertEquals(0, QueueNavigator.previousIndex(0, 1, PlayMode.REPEAT_ALL))
        assertEquals(0, QueueNavigator.nextIndex(0, 1, PlayMode.REPEAT_ONE))
        assertEquals(0, QueueNavigator.previousIndex(0, 1, PlayMode.REPEAT_ONE))
        assertEquals(0, QueueNavigator.nextIndex(0, 1, PlayMode.SHUFFLE, listOf(0)))
    }

    @Test
    fun `empty queue always yields no index`() {
        for (mode in PlayMode.entries) {
            assertEquals(QueueNavigator.NO_INDEX, QueueNavigator.nextIndex(0, 0, mode))
            assertEquals(QueueNavigator.NO_INDEX, QueueNavigator.previousIndex(0, 0, mode))
        }
    }

    @Test
    fun `negative current index starts at the first playable item`() {
        assertEquals(0, QueueNavigator.nextIndex(-1, 4, PlayMode.SEQUENCE))
        assertEquals(0, QueueNavigator.nextIndex(-1, 4, PlayMode.REPEAT_ALL))
        assertEquals(QueueNavigator.NO_INDEX, QueueNavigator.previousIndex(-1, 4, PlayMode.SEQUENCE))
        assertEquals(QueueNavigator.NO_INDEX, QueueNavigator.previousIndex(-1, 4, PlayMode.REPEAT_ALL))
    }

    @Test
    fun `clampStartIndex keeps index inside queue`() {
        assertEquals(0, QueueNavigator.clampStartIndex(-3, 5))
        assertEquals(4, QueueNavigator.clampStartIndex(99, 5))
        assertEquals(3, QueueNavigator.clampStartIndex(3, 5))
        assertEquals(0, QueueNavigator.clampStartIndex(0, 0))
    }

    @Test
    fun `insertion index after current handles boundaries`() {
        assertEquals(0, QueueNavigator.insertionIndexAfterCurrent(-1, 0))
        assertEquals(1, QueueNavigator.insertionIndexAfterCurrent(0, 3))
        assertEquals(3, QueueNavigator.insertionIndexAfterCurrent(2, 3))
        assertEquals(3, QueueNavigator.insertionIndexAfterCurrent(-1, 3))
    }

    @Test
    fun `shuffle order is a deterministic permutation for a fixed seed`() {
        val first = QueueNavigator.shuffleOrder(8, Random(42))
        val second = QueueNavigator.shuffleOrder(8, Random(42))
        assertEquals(first, second)
        assertEquals((0 until 8).toList(), first.sorted())
        assertTrue(QueueNavigator.isValidShuffleOrder(first, 8))
        val other = QueueNavigator.shuffleOrder(8, Random(7))
        assertFalse(first == other && first == (0 until 8).toList())
    }

    @Test
    fun `invalid shuffle order falls back to physical order`() {
        assertEquals(1, QueueNavigator.nextIndex(0, 3, PlayMode.SHUFFLE, listOf(0, 0, 2)))
        assertEquals(2, QueueNavigator.nextIndex(1, 3, PlayMode.SHUFFLE, null))
        assertFalse(QueueNavigator.isValidShuffleOrder(listOf(0, 1), 3))
        assertFalse(QueueNavigator.isValidShuffleOrder(listOf(0, 1, 3), 3))
        assertTrue(QueueNavigator.isValidShuffleOrder(listOf(2, 1, 0), 3))
    }
}
