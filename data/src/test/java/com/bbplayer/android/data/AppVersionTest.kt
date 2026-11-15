package com.bbplayer.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `去掉v前缀`() {
        assertEquals("0.1.0", AppVersion.normalize("v0.1.0"))
        assertEquals("1.2.3", AppVersion.normalize(" V1.2.3 "))
        assertEquals("1.2.3", AppVersion.display("v1.2.3"))
    }

    @Test
    fun `新版大于旧版`() {
        assertTrue(AppVersion.compare("0.2.0", "v0.1.0") > 0)
        assertTrue(AppVersion.isNewer("v0.2.0", "0.1.0"))
        assertFalse(AppVersion.isNewer("v0.1.0", "0.1.0"))
        assertFalse(AppVersion.isNewer("0.0.9", "0.1.0"))
    }

    @Test
    fun `补零比较与数字大小`() {
        assertEquals(0, AppVersion.compare("1.2", "1.2.0"))
        assertEquals(0, AppVersion.compare("v1.2.0", "1.2.0"))
        assertTrue(AppVersion.compare("1.10.0", "1.9.0") > 0)
        assertTrue(AppVersion.compare("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `预发布版本低于同号正式版`() {
        assertEquals(-1, AppVersion.compare("0.2.0-rc.1", "0.2.0"))
        assertEquals(1, AppVersion.compare("0.2.0", "0.2.0-rc.1"))
        assertEquals(1, AppVersion.compare("0.2.0-rc.2", "0.2.0-rc.1"))
        assertTrue(AppVersion.isNewer("0.2.0", "0.2.0-rc.2"))
        assertFalse(AppVersion.isNewer("0.2.0-rc.1", "0.2.0"))
    }

    @Test
    fun `构建元数据不参与比较`() {
        assertEquals(0, AppVersion.compare("1.2.3+build.5", "1.2.3"))
    }

    @Test
    fun `无法解析的版本按相等处理`() {
        assertEquals(0, AppVersion.compare("abc", "1.2.3"))
        assertEquals(0, AppVersion.compare("", ""))
        assertFalse(AppVersion.isNewer("nightly", "0.1.0"))
    }
}
