package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 播放历史上报：开关 / 登录保护 / 表单字段 / 失败静默。 */
class PlaybackReporterImplTest {

    private val track = Track(bvid = "BV1q4FTeBEMj", cid = 777L, title = "夜曲")

    private class Harness(
        enabled: Boolean = true,
        loggedIn: Boolean = true,
        private val csrf: String? = "jct-token",
        private val target: ReportTarget? = ReportTarget(aid = 123L, cid = 888L),
        private val bvid: String = "BV1q4FTeBEMj",
    ) {
        val enabledFlow = MutableStateFlow(enabled)
        var resolveCalls = 0
        var submitted: Map<String, String>? = null
        var failSubmit = false

        val reporter = PlaybackReporterImpl(
            enabledFlow = enabledFlow,
            loggedIn = { loggedIn },
            csrfToken = { csrf },
            resolveVideo = { bvid ->
                resolveCalls++
                if (bvid == this.bvid) target else null
            },
            submit = { form ->
                if (failSubmit) throw IOException("network down")
                submitted = form
            },
        )
    }

    @Test
    fun `关闭时不上报也不解析视频`() = runTest {
        val harness = Harness(enabled = false)

        val result = harness.reporter.report(track, 12_000L)

        assertTrue(result.isSuccess)
        assertEquals(0, harness.resolveCalls)
        assertNull(harness.submitted)
    }

    @Test
    fun `未登录时不上报也不发请求`() = runTest {
        val harness = Harness(loggedIn = false)

        val result = harness.reporter.report(track, 12_000L)

        assertTrue(result.isSuccess)
        assertEquals(0, harness.resolveCalls)
        assertNull(harness.submitted)
    }

    @Test
    fun `开启且登录时提交 aid cid progress 与 csrf`() = runTest {
        val harness = Harness()

        val result = harness.reporter.report(track, 12_345L)

        assertTrue(result.isSuccess)
        assertEquals(
            mapOf(
                "aid" to "123",
                "cid" to "777",
                "progress" to "12",
                "csrf" to "jct-token",
            ),
            harness.submitted,
        )
    }

    @Test
    fun `track 没有 cid 时使用 view 解析出的首P cid`() = runTest {
        val harness = Harness()

        harness.reporter.report(track.copy(cid = 0L), 5_000L).getOrThrow()

        assertEquals("888", harness.submitted!!["cid"])
    }

    @Test
    fun `进度毫秒向下取整为秒且负值按零`() = runTest {
        val harness = Harness()
        harness.reporter.report(track, 9_999L).getOrThrow()
        assertEquals("9", harness.submitted!!["progress"])

        harness.reporter.report(track, -500L).getOrThrow()
        assertEquals("0", harness.submitted!!["progress"])
    }

    @Test
    fun `csrf 缺失时返回 failure 且不发请求`() = runTest {
        val harness = Harness(csrf = null)

        val error = harness.reporter.report(track, 1_000L).exceptionOrNull()

        assertTrue(error is AppException)
        assertNull(harness.submitted)
    }

    @Test
    fun `无法解析视频时返回 failure 且不发请求`() = runTest {
        val harness = Harness(target = null)

        val error = harness.reporter.report(track, 1_000L).exceptionOrNull()

        assertTrue(error is AppException)
        assertNull(harness.submitted)
    }

    @Test
    fun `解析出的 cid 也无效时返回 failure`() = runTest {
        val harness = Harness(target = ReportTarget(aid = 123L, cid = 0L))

        val error = harness.reporter.report(track.copy(cid = 0L), 1_000L).exceptionOrNull()

        assertTrue(error is AppException)
        assertNull(harness.submitted)
    }

    @Test
    fun `提交失败收敛为 Result failure 不抛出`() = runTest {
        val harness = Harness().apply { failSubmit = true }

        val error = harness.reporter.report(track, 1_000L).exceptionOrNull()

        assertTrue(error is AppException)
    }

    @Test
    fun `enabled 暴露偏好流`() = runTest {
        val harness = Harness(enabled = true)
        assertTrue(harness.reporter.enabled.first())

        harness.enabledFlow.value = false
        assertFalse(harness.reporter.enabled.first())
    }

    @Test
    fun `上报表单字段固定且可读`() {
        assertEquals(
            linkedMapOf("aid" to "1", "cid" to "2", "progress" to "3", "csrf" to "c"),
            buildReportForm(1L, 2L, 3L, "c"),
        )
        assertEquals(
            linkedMapOf("aid" to "1", "cid" to "2", "progress" to "3"),
            buildReportForm(1L, 2L, 3L, "  "),
        )
    }
}
