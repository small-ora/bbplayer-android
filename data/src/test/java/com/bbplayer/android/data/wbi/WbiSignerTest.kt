package com.bbplayer.android.data.wbi

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.data.FakeWbiNav
import com.bbplayer.android.data.json
import com.bbplayer.android.data.navJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * WBI 签名纯 JVM 单测。
 *
 * 固定向量由独立 Python 脚本（urllib.parse.quote(safe='-_.~') + hashlib.md5）生成：
 * img=7cd084941338484aae1ad9425b84077c / sub=4932caff0ff746eab6f01bf08b70ac45
 * → mixin=ea1db124af3c7062474693fa704f4ff8。
 */
class WbiSignerTest {

    private val img = "7cd084941338484aae1ad9425b84077c"
    private val sub = "4932caff0ff746eab6f01bf08b70ac45"

    // ------------------------------------------------------------ mixinKey

    @Test
    fun `mixinKey 按 64 项置换表取前 32 位`() {
        assertEquals("ea1db124af3c7062474693fa704f4ff8", WbiSigner.mixinKey(img, sub))
        assertEquals(32, WbiSigner.mixinKey(img, sub).length)
    }

    @Test
    fun `mixinKey 输入不足时按可用字符截断`() {
        val key = WbiSigner.mixinKey("abcd", "efgh")
        assertEquals(8, key.length)
        val expected = WbiSigner.MIXIN_KEY_TABLE.filter { it < 8 }
            .map { "abcdefgh"[it] }
            .take(8)
            .joinToString("")
        assertEquals(expected, key)
    }

    @Test
    fun `mixinKey 空输入返回空串`() {
        assertEquals("", WbiSigner.mixinKey("", ""))
    }

    // -------------------------------------------------------- keyFromUrl

    @Test
    fun `keyFromUrl 去目录与扩展名`() {
        assertEquals(
            "7cd084941338484aae1ad9425b84077c",
            WbiSigner.keyFromUrl("https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png"),
        )
        assertEquals("abc123", WbiSigner.keyFromUrl("//i0.hdslb.com/bfs/wbi/abc123.png?x=1"))
        assertNull(WbiSigner.keyFromUrl(null))
        assertNull(WbiSigner.keyFromUrl("  "))
        assertNull(WbiSigner.keyFromUrl("https://i0.hdslb.com/bfs/wbi/"))
    }

    @Test
    fun `parseKeys 从 nav 响应解析并派生 mixinKey`() {
        val keys = WbiSigner.parseKeys(navJson())
        assertNotNull(keys)
        assertEquals(img, keys!!.imgKey)
        assertEquals(sub, keys.subKey)
        assertEquals("ea1db124af3c7062474693fa704f4ff8", keys.mixinKey)
    }

    @Test
    fun `parseKeys 缺 wbi_img 返回 null`() {
        assertNull(WbiSigner.parseKeys(json("""{"code":-101,"data":{}}""")))
        assertNull(WbiSigner.parseKeys(json("""{"code":-101}""")))
        assertNull(
            WbiSigner.parseKeys(
                json("""{"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/a.png"}}}"""),
            ),
        )
    }

    // ------------------------------------------ 参数排序 / 过滤 / 编码

    @Test
    fun `canonicalQuery 按 key 升序拼接`() {
        val query = WbiSigner.canonicalQuery(
            linkedMapOf("ps" to "30", "mid" to "2", "pn" to "1", "order" to "pubdate"),
        )
        assertEquals("mid=2&order=pubdate&pn=1&ps=30", query)
    }

    @Test
    fun `canonicalQuery 值过滤感叹号括号星号`() {
        assertEquals(
            "keyword=abcdef&mid=2",
            WbiSigner.canonicalQuery(linkedMapOf("mid" to "2", "keyword" to "a!b'c(d)e*f")),
        )
    }

    @Test
    fun `canonicalQuery UTF-8 百分号编码 空格与斜杠`() {
        val query = WbiSigner.canonicalQuery(linkedMapOf("keyword" to "周杰伦 夜曲", "path" to "a/b"))
        assertEquals(
            "keyword=%E5%91%A8%E6%9D%B0%E4%BC%A6%20%E5%A4%9C%E6%9B%B2&path=a%2Fb",
            query,
        )
    }

    @Test
    fun `encodeComponent 保留 RFC3986 unreserved`() {
        assertEquals("aZ0-_.~", WbiSigner.encodeComponent("aZ0-_.~"))
        assertEquals("%21%2A%27%28%29", WbiSigner.encodeComponent("!*'()"))
    }

    // ------------------------------------------------ md5 / 固定签名向量

    @Test
    fun `md5Hex 固定输入固定输出`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", WbiSigner.md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", WbiSigner.md5Hex("abc"))
    }

    @Test
    fun `signWithKey 固定向量 排序过滤编码 md5`() {
        val signed = WbiSigner.signWithKey(
            params = linkedMapOf(
                "ps" to "30",
                "mid" to "2",
                "pn" to "1",
                "keyword" to "周杰伦 夜曲",
                "order" to "pubdate",
            ),
            mixinKey = WbiSigner.mixinKey(img, sub),
            wts = 1_700_000_000L,
        )
        assertEquals("1700000000", signed["wts"])
        assertEquals("7c91c2b30169856c52f520ce367fccf0", signed["w_rid"])
        assertEquals(
            "keyword=%E5%91%A8%E6%9D%B0%E4%BC%A6%20%E5%A4%9C%E6%9B%B2&mid=2&order=pubdate&pn=1&ps=30&wts=1700000000",
            WbiSigner.canonicalQuery(signed - "w_rid"),
        )
    }

    @Test
    fun `signWithKey 过滤字符后的固定向量`() {
        val signed = WbiSigner.signWithKey(
            params = linkedMapOf("mid" to "2", "keyword" to "a!b'c(d)e*f"),
            mixinKey = WbiSigner.mixinKey(img, sub),
            wts = 1_700_000_123L,
        )
        assertEquals("b564a8d41687a968ee3f5650c852ea31", signed["w_rid"])
        assertEquals("keyword=abcdef&mid=2&wts=1700000123", WbiSigner.canonicalQuery(signed - "w_rid"))
    }

    @Test
    fun `signWithKey 纯 ASCII 固定向量`() {
        val signed = WbiSigner.signWithKey(
            params = linkedMapOf("mid" to "123456", "ps" to "30"),
            mixinKey = WbiSigner.mixinKey(
                "abcdef0123456789abcdef0123456789",
                "0123456789abcdef0123456789abcdef",
            ),
            wts = 1_699_999_999L,
        )
        assertEquals("9b3653df3fec1b66a0eed01d4770f772", signed["w_rid"])
    }

    // ------------------------------------------------ 按日期缓存 key

    @Test
    fun `sign 同一天只拉取一次 nav`() = runBlocking {
        var now = 1_700_000_000_000L
        val nav = FakeWbiNav(listOf(navJson()))
        val signer = WbiSigner(nav, { now }, ZoneOffset.UTC)

        signer.sign(mapOf("mid" to "2"))
        signer.sign(mapOf("mid" to "3"))
        assertEquals(1, nav.calls)

        // 同一天内推进（起始 22:13，加 30 分钟不跨 UTC 日期）
        now += 30 * 60 * 1000L
        signer.sign(mapOf("mid" to "4"))
        assertEquals(1, nav.calls)
    }

    @Test
    fun `sign 跨本地日期重新拉取 nav`() = runBlocking {
        var now = 1_700_000_000_000L
        val nav = FakeWbiNav(listOf(navJson(), navJson(sub = "0123456789abcdef0123456789abcdef")))
        val signer = WbiSigner(nav, { now }, ZoneOffset.UTC)

        signer.sign(mapOf("mid" to "2"))
        assertEquals(1, nav.calls)

        // +1 天（本地日期变化）
        now += 24 * 60 * 60 * 1000L
        val signed = signer.sign(mapOf("mid" to "2"))
        assertEquals(2, nav.calls)
        assertTrue(signed["w_rid"]!!.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun `按注入时区判断本地日期`() = runBlocking {
        // 2023-11-14T15:59Z = 2023-11-14 23:59 (Asia/Shanghai)
        var now = 1_699_977_540_000L
        val nav = FakeWbiNav(listOf(navJson(), navJson()))
        val signer = WbiSigner(nav, { now }, ZoneId.of("Asia/Shanghai"))

        signer.sign(mapOf("mid" to "2"))
        assertEquals(1, nav.calls)

        // +2 分钟 → 上海时间跨到 11-15，UTC 仍是 11-14
        now += 2 * 60 * 1000L
        signer.sign(mapOf("mid" to "2"))
        assertEquals(2, nav.calls)
    }

    @Test
    fun `跨天拉取失败时退回旧 key`() = runBlocking {
        var now = 1_700_000_000_000L
        val nav = FakeWbiNav(listOf(navJson(), null))
        val signer = WbiSigner(nav, { now }, ZoneOffset.UTC)

        signer.sign(mapOf("mid" to "2"))
        now += 24 * 60 * 60 * 1000L
        val signed = signer.sign(mapOf("mid" to "2"))

        assertEquals(2, nav.calls)
        // 仍用第一天 key 签名，wts 用新值 → w_rid 可由固定向量复算。
        assertEquals(
            WbiSigner.signWithKey(
                params = mapOf("mid" to "2"),
                mixinKey = WbiSigner.mixinKey(img, sub),
                wts = 1_700_086_400L,
            )["w_rid"],
            signed["w_rid"],
        )
    }

    @Test
    fun `首次拉取失败且无旧 key 抛可重试 AppException`() = runBlocking {
        val signer = WbiSigner(FakeWbiNav(listOf(null)), { 1_700_000_000_000L }, ZoneOffset.UTC)
        val error = runCatching { signer.sign(mapOf("mid" to "2")) }.exceptionOrNull()
        assertTrue(error is AppException)
        error as AppException
        assertEquals("B站签名服务不可用，请稍后重试", error.userMessage)
        assertTrue(error.retryable)
    }

    @Test
    fun `sign 保留原参数不被修改`() = runBlocking {
        val params = linkedMapOf("mid" to "2")
        val signed = wbiForTest().sign(params)
        assertEquals(mapOf("mid" to "2"), params)
        assertEquals("2", signed["mid"])
        assertTrue(signed.containsKey("w_rid"))
        assertTrue(signed.containsKey("wts"))
    }

    private fun wbiForTest(): WbiSigner = WbiSigner(
        nav = FakeWbiNav(listOf(navJson())),
        clock = { 1_700_000_000_000L },
        zone = ZoneOffset.UTC,
    )
}
