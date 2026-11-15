package com.bbplayer.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiliIdsTest {
    @Test
    fun decodesKnownBvidToAid() {
        assertEquals(2L, BiliIds.aidOf("BV1xx411c7mD"))
        assertEquals(170001L, BiliIds.aidOf("BV17x411w7KC"))
        assertEquals(455017605L, BiliIds.aidOf("BV1Q541167Qg"))
        assertEquals(882584971L, BiliIds.aidOf("BV1mK4y1C7Bz"))
    }

    @Test
    fun rejectsInvalidIds() {
        assertNull(BiliIds.aidOf(""))
        assertNull(BiliIds.aidOf("av170001"))
        assertNull(BiliIds.aidOf("BV1xx411c7m!"))
        assertNull(BiliIds.aidOf("BV1xx411c7mDD"))
    }
}
