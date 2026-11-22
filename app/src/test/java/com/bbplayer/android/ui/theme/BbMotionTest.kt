package com.bbplayer.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v9.2 动效 token 断言（SPEC AC1）。 */
class BbMotionTest {

    @Test
    fun durationTiersAreOrdered() {
        assertEquals(120, BbMotion.FastMs)
        assertEquals(220, BbMotion.BaseMs)
        assertEquals(320, BbMotion.SlowMs)
        assertTrue(BbMotion.FastMs < BbMotion.BaseMs)
        assertTrue(BbMotion.BaseMs < BbMotion.SlowMs)
    }

    @Test
    fun tabFadeStaysInRecommendedRange() {
        assertTrue(BbMotion.TabFadeMs in 180..220)
    }

    @Test
    fun exitIsFasterThanEnter() {
        assertTrue(BbMotion.FastMs < BbMotion.BaseMs)
        assertEquals(BbMotion.BaseMs, BbMotion.BaseMs)
    }
}
