package com.bbplayer.android.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 登录 Cookie 解析：空格 / 分号 / 重复键 / 缺 SESSDATA / Set-Cookie / 回调 URL。 */
class CookieParserTest {

    @Test
    fun `解析标准 cookie 串`() {
        val parsed = CookieParser.parse(
            "SESSDATA=sess-value; bili_jct=jct-value; DedeUserID=42; DedeUserID__ckMd5=ck; sid=s1",
        )
        assertEquals("sess-value", parsed["SESSDATA"])
        assertEquals("jct-value", parsed["bili_jct"])
        assertEquals("42", parsed["DedeUserID"])
        assertEquals("ck", parsed["DedeUserID__ckMd5"])
        assertEquals("s1", parsed["sid"])
    }

    @Test
    fun `容忍空格换行与多余分号`() {
        val parsed = CookieParser.parse("  SESSDATA = sess ;;\n bili_jct = jct ; \r\n")
        assertEquals("sess", parsed["SESSDATA"])
        assertEquals("jct", parsed["bili_jct"])
    }

    @Test
    fun `重复键以最后一次为准`() {
        val parsed = CookieParser.parse("SESSDATA=first; bili_jct=j1; SESSDATA=second")
        assertEquals("second", parsed["SESSDATA"])
        assertEquals("j1", parsed["bili_jct"])
    }

    @Test
    fun `缺少 SESSDATA 时返回其余合法键`() {
        val parsed = CookieParser.parse("bili_jct=jct; foo=bar")
        assertEquals("jct", parsed["bili_jct"])
        assertFalse(parsed.containsKey("SESSDATA"))
        assertFalse("未知 cookie 不应保留", parsed.containsKey("foo"))
    }

    @Test
    fun `值中允许等号且只按第一个等号切分`() {
        val parsed = CookieParser.parse("SESSDATA=a=b=c%3D; sid=x=y")
        assertEquals("a=b=c%3D", parsed["SESSDATA"])
        assertEquals("x=y", parsed["sid"])
    }

    @Test
    fun `Cookie 属性与空片段被忽略`() {
        val parsed = CookieParser.parse("SESSDATA=s; Path=/; Domain=.bilibili.com; HttpOnly; Secure; =broken")
        assertEquals(mapOf("SESSDATA" to "s"), parsed)
    }

    @Test
    fun `容忍浏览器复制的 Cookie 前缀`() {
        val parsed = CookieParser.parse("Cookie: SESSDATA=s; bili_jct=j; DedeUserID=42")
        assertEquals("s", parsed["SESSDATA"])
        assertEquals("j", parsed["bili_jct"])
        assertEquals("42", parsed["DedeUserID"])
    }

    @Test
    fun `空串返回空 map`() {
        assertTrue(CookieParser.parse("").isEmpty())
        assertTrue(CookieParser.parse("   \n ; ").isEmpty())
    }

    @Test
    fun `Set-Cookie 只取第一个分号前的内容并保留登录键`() {
        val cookies = CookieParser.fromSetCookie(
            listOf(
                "SESSDATA=sess%2C1; Path=/; Domain=.bilibili.com; HttpOnly",
                "DedeUserID=42; Path=/",
                "bili_jct=jct; Path=/; Secure",
                "buvid3=should-be-ignored; Path=/",
            ),
        )
        assertEquals("sess%2C1", cookies["SESSDATA"])
        assertEquals("42", cookies["DedeUserID"])
        assertEquals("jct", cookies["bili_jct"])
        assertFalse(cookies.containsKey("buvid3"))
    }

    @Test
    fun `回调 URL 查询参数被解码且只保留登录键`() {
        val cookies = CookieParser.fromUrl(
            "https://passport.biligame.com/crossDomain?DedeUserID=42&DedeUserID__ckMd5=ck" +
                "&Expires=1700000000&SESSDATA=sess%2Cdata%2C123&bili_jct=jct123&gourl=https%3A%2F%2Fwww.bilibili.com&sid=sid1",
        )
        assertEquals("sess,data,123", cookies["SESSDATA"])
        assertEquals("jct123", cookies["bili_jct"])
        assertEquals("42", cookies["DedeUserID"])
        assertEquals("sid1", cookies["sid"])
        assertFalse(cookies.containsKey("gourl"))
        assertFalse(cookies.containsKey("Expires"))
    }

    @Test
    fun `URL 中的加号不被当成空格`() {
        val cookies = CookieParser.fromUrl("https://x/crossDomain?SESSDATA=a%2Bb+c")
        assertEquals("a+b+c", cookies["SESSDATA"])
    }

    @Test
    fun `merge 让后者覆盖前者且只保留登录键`() {
        val merged = CookieParser.merge(
            mapOf("SESSDATA" to "url", "bili_jct" to "url-jct", "unknown" to "x"),
            mapOf("DedeUserID" to "42", "SESSDATA" to "cookie"),
        )
        assertEquals("cookie", merged["SESSDATA"])
        assertEquals("url-jct", merged["bili_jct"])
        assertEquals("42", merged["DedeUserID"])
        assertFalse(merged.containsKey("unknown"))
    }

    @Test
    fun `toHeader 按 k=v 分号拼接`() {
        val header = CookieParser.toHeader(linkedMapOf("SESSDATA" to "s", "bili_jct" to "j"))
        assertEquals("SESSDATA=s; bili_jct=j", header)
    }
}
