package com.bbplayer.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 业务错误码 → 中文文案的纯函数单测（[apiMessage] / [httpMessage]）。
 *
 * 内容类仓储都走 [BiliApi] 的统一错误映射，因此这里覆盖 -101 / -404 / -412 即可保证
 * 收藏 / 合集 / 稍后再看 / UP 主的失败文案一致。
 */
class BiliApiErrorsTest {

    @Test
    fun `-101 未登录映射为登录提示`() {
        assertEquals("账号未登录，请先登录", apiMessage(-101L, null))
    }

    @Test
    fun `-404 映射为视频不存在`() {
        assertEquals("视频不存在或已被删除", apiMessage(-404L, "啥都木有"))
    }

    @Test
    fun `-412 映射为安全策略拦截`() {
        assertEquals("请求被B站安全策略拦截，请稍后重试", apiMessage(-412L, null))
    }

    @Test
    fun `-400 与 -403 映射为参数与权限错误`() {
        assertEquals("请求参数错误", apiMessage(-400L, null))
        assertEquals("访问被拒绝，视频可能需要登录或大会员", apiMessage(-403L, "访问权限不足"))
    }

    @Test
    fun `未知错误码优先使用服务端文案`() {
        assertEquals("服务繁忙", apiMessage(-500L, "服务繁忙"))
        assertEquals("B站接口错误（code=-500）", apiMessage(-500L, "  "))
        assertEquals("B站接口错误（code=-500）", apiMessage(-500L, null))
    }

    @Test
    fun `HTTP 状态码映射为中文 412 与 429 可重试`() {
        assertEquals("请求被B站安全策略拦截，请稍后重试", httpMessage(412))
        assertEquals("请求过于频繁，请稍后重试", httpMessage(429))
        assertEquals("B站服务暂时不可用，请稍后重试", httpMessage(503))
        assertEquals("请求的接口不存在", httpMessage(404))
        assertEquals("网络请求失败（HTTP 400），请重试", httpMessage(400))
    }
}
