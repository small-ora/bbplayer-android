package com.bbplayer.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputResolverTest {

    @Test
    fun `纯 BV 号解析`() {
        assertEquals(VideoInput.Bvid("BV1q4FTeBEMj"), InputResolver.resolve("BV1q4FTeBEMj"))
    }

    @Test
    fun `小写 BV 前缀规范化`() {
        assertEquals(VideoInput.Bvid("BV1q4FTeBEMj"), InputResolver.resolve("bv1q4FTeBEMj"))
    }

    @Test
    fun `完整视频链接提取 BV`() {
        val result = InputResolver.resolve("https://www.bilibili.com/video/BV1q4FTeBEMj?p=2&t=12")
        assertEquals(VideoInput.Bvid("BV1q4FTeBEMj"), result)
    }

    @Test
    fun `文本内嵌 BV 号也能识别`() {
        assertEquals(
            VideoInput.Bvid("BV1xx411c7mD"),
            InputResolver.resolve("分享一个视频 BV1xx411c7mD 好听"),
        )
    }

    @Test
    fun `AV 号解析`() {
        assertEquals(VideoInput.Aid(170001L), InputResolver.resolve("av170001"))
        assertEquals(VideoInput.Aid(170001L), InputResolver.resolve("AV170001"))
    }

    @Test
    fun `完整链接中的 AV 号解析`() {
        assertEquals(
            VideoInput.Aid(170001L),
            InputResolver.resolve("https://www.bilibili.com/video/av170001"),
        )
    }

    @Test
    fun `b23 短链识别并补全协议`() {
        assertEquals(
            VideoInput.ShortLink("https://b23.tv/AbCd12"),
            InputResolver.resolve("b23.tv/AbCd12"),
        )
        assertEquals(
            VideoInput.ShortLink("https://b23.tv/AbCd12"),
            InputResolver.resolve("https://b23.tv/AbCd12"),
        )
    }

    @Test
    fun `普通关键词不被误判`() {
        assertEquals(VideoInput.Keyword("周杰伦 夜曲"), InputResolver.resolve(" 周杰伦 夜曲 "))
        assertEquals(VideoInput.Keyword("travel 2"), InputResolver.resolve("travel 2"))
        assertEquals(VideoInput.Keyword(""), InputResolver.resolve("   "))
    }

    @Test
    fun `从 canonical 链接提取视频 ID`() {
        val html =
            """<html><head><link rel="canonical" href="https://www.bilibili.com/video/BV1xx411c7mD"></head></html>"""
        val href = InputResolver.canonicalHref(html)
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", href)
        assertEquals(VideoInput.Bvid("BV1xx411c7mD"), InputResolver.extractVideoRef(href))
    }

    @Test
    fun `canonical 缺失时回退到页面内视频链接`() {
        val html = """<a href="https://m.bilibili.com/video/BV1ab411c7XY">视频</a>"""
        val href = InputResolver.canonicalHref(html)
        assertTrue(href!!.contains("BV1ab411c7XY"))
        assertEquals(VideoInput.Bvid("BV1ab411c7XY"), InputResolver.extractVideoRef(href))
    }
}
