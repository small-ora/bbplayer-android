package com.bbplayer.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextUtilsTest {

    @Test
    fun `搜索标题去除 em 高亮标签`() {
        val raw = "【无损音质】<em class=\"keyword\">周杰伦</em> - 夜曲"
        assertEquals("【无损音质】周杰伦 - 夜曲", HtmlText.clean(raw))
    }

    @Test
    fun `HTML 实体解码且不二次解码`() {
        assertEquals("Tom & Jerry <3 \"hi\"", HtmlText.decodeEntities("Tom &amp; Jerry &lt;3 &quot;hi&quot;"))
        assertEquals("&lt;", HtmlText.decodeEntities("&amp;lt;"))
        assertEquals("中", HtmlText.decodeEntities("&#20013;"))
    }

    @Test
    fun `标题清洗折叠空白`() {
        assertEquals("A B C", HtmlText.clean("  A \n\t <span>B</span>   C  "))
        assertEquals("", HtmlText.clean(null))
        assertEquals("", HtmlText.clean(""))
    }

    @Test
    fun `时长 mm ss 解析为毫秒`() {
        assertEquals(228_000L, DurationParser.parseMs("3:48"))
        assertEquals(0L, DurationParser.parseMs("0:00"))
        assertEquals(65_000L, DurationParser.parseMs(" 1:05 "))
    }

    @Test
    fun `时长 hh mm ss 与异常输入`() {
        assertEquals(3_723_000L, DurationParser.parseMs("1:02:03"))
        assertEquals(228_000L, DurationParser.parseMs("228"))
        assertEquals(0L, DurationParser.parseMs("abc"))
        assertEquals(0L, DurationParser.parseMs(null))
        assertEquals(0L, DurationParser.parseMs("-5"))
    }

    @Test
    fun `秒数转毫秒并裁剪负数`() {
        assertEquals(228_000L, DurationParser.secondsToMs(228L))
        assertEquals(0L, DurationParser.secondsToMs(-1L))
        assertEquals(0L, DurationParser.secondsToMs(null))
    }
}
