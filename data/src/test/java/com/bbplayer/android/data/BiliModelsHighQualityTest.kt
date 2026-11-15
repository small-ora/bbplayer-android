package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.model.AudioQuality
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 高音质选流：登录 + 开启时 FLAC → Dolby → 最高 AAC；关闭时保持最高 AAC。 */
class BiliModelsHighQualityTest {

    private fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text) as JsonObject

    private fun audio(id: Int, url: String) =
        """{"id":$id,"baseUrl":"$url","backupUrl":["$url.bak"],"bandwidth":320000}"""

    private fun dash(
        aac: String,
        flac: String? = null,
        dolby: String? = null,
    ): JsonObject {
        val extras = buildString {
            if (flac != null) append(""","flac":{"display":true,"audio":$flac}""")
            if (dolby != null) append(""","dolby":{"type":1,"audio":$dolby}""")
        }
        return json("""{"dash":{"audio":[$aac]$extras},"volume":{"measured_i":-14.5}}""")
    }

    @Test
    fun `开启高音质优先 FLAC`() {
        val data = dash(
            aac = "${audio(30280, "https://cdn/aac.m4s")},${audio(30232, "https://cdn/aac128.m4s")}",
            flac = """{"id":30251,"baseUrl":"https://cdn/flac.m4s"}""",
            dolby = """{"id":30250,"baseUrl":"https://cdn/dolby.m4s"}""",
        )

        val stream = BiliMapper.audioStream(data, AudioQuality.Q192.id, preferHighQuality = true)

        assertEquals(30251, stream.qualityId)
        assertEquals("https://cdn/flac.m4s", stream.url)
        assertEquals(-14.5, stream.measuredLoudnessDb!!, 0.0001)
    }

    @Test
    fun `没有 FLAC 时回退 Dolby`() {
        val data = dash(
            aac = audio(30280, "https://cdn/aac.m4s"),
            dolby = """{"id":30250,"baseUrl":"https://cdn/dolby.m4s"}""",
        )

        val stream = BiliMapper.audioStream(data, AudioQuality.Q192.id, preferHighQuality = true)

        assertEquals(30250, stream.qualityId)
        assertEquals("https://cdn/dolby.m4s", stream.url)
    }

    @Test
    fun `没有无损时回退最高 AAC`() {
        val data = dash(
            aac = "${audio(30216, "https://cdn/aac64.m4s")},${audio(30280, "https://cdn/aac192.m4s")}",
        )

        val stream = BiliMapper.audioStream(data, AudioQuality.Q64.id, preferHighQuality = true)

        assertEquals(30280, stream.qualityId)
        assertEquals("https://cdn/aac192.m4s", stream.url)
    }

    @Test
    fun `关闭高音质时即使服务端返回 FLAC 也只选 AAC`() {
        val data = dash(
            aac = audio(30280, "https://cdn/aac.m4s"),
            flac = """{"id":30251,"baseUrl":"https://cdn/flac.m4s"}""",
            dolby = """{"id":30250,"baseUrl":"https://cdn/dolby.m4s"}""",
        )

        val stream = BiliMapper.audioStream(data, AudioQuality.Q192.id, preferHighQuality = false)

        assertEquals(30280, stream.qualityId)
        assertEquals("https://cdn/aac.m4s", stream.url)
    }

    @Test
    fun `flac audio 为数组时也能解析`() {
        val data = dash(
            aac = audio(30280, "https://cdn/aac.m4s"),
            flac = """[{"id":30251,"baseUrl":"https://cdn/flac-array.m4s"}]""",
        )

        val stream = BiliMapper.audioStream(data, AudioQuality.Q192.id, preferHighQuality = true)

        assertEquals(30251, stream.qualityId)
        assertEquals("https://cdn/flac-array.m4s", stream.url)
    }

    @Test
    fun `无损条目缺 id 时按标准档位标注`() {
        val data = dash(
            aac = audio(30280, "https://cdn/aac.m4s"),
            flac = """{"baseUrl":"https://cdn/flac-no-id.m4s"}""",
            dolby = """{"baseUrl":"https://cdn/dolby-no-id.m4s"}""",
        )

        val flacStream = BiliMapper.audioStream(data, AudioQuality.Q192.id, preferHighQuality = true)
        assertEquals(AudioQuality.HI_RES.id, flacStream.qualityId)

        val dolbyOnly = dash(
            aac = audio(30280, "https://cdn/aac.m4s"),
            dolby = """{"baseUrl":"https://cdn/dolby-no-id.m4s"}""",
        )
        val dolbyStream = BiliMapper.audioStream(dolbyOnly, AudioQuality.Q192.id, preferHighQuality = true)
        assertEquals(AudioQuality.DOLBY.id, dolbyStream.qualityId)
    }

    @Test
    fun `无 dash 时保留 durl 回退`() {
        val data = json("""{"durl":[{"url":"https://cdn/legacy.flv","backup_url":["https://cdn/backup.flv"]}]}""")

        val stream = BiliMapper.audioStream(data, AudioQuality.Q192.id, preferHighQuality = true)

        assertEquals("https://cdn/legacy.flv", stream.url)
        assertEquals(listOf("https://cdn/backup.flv"), stream.backupUrls)
        assertEquals(AudioQuality.Q192.id, stream.qualityId)
    }

    @Test
    fun `完全没有音频流时抛不可重试错误`() {
        val data = json("""{"dash":{"audio":[]}}""")
        val error = assertThrows(AppException::class.java) {
            BiliMapper.audioStream(data, AudioQuality.Q192.id, preferHighQuality = true)
        }
        assertEquals(false, error.retryable)
    }

    @Test
    fun `AudioQuality 识别新增档位`() {
        assertEquals(AudioQuality.DOLBY, AudioQuality.fromId(30250))
        assertEquals(AudioQuality.HI_RES, AudioQuality.fromId(30251))
        assertEquals(AudioQuality.Q192, AudioQuality.fromId(30280))
        assertEquals(AudioQuality.Q192, AudioQuality.fromId(99999))
    }

    @Test
    fun `请求具体 AAC 档位时按请求档位返回`() {
        val data = dash(
            aac = "${audio(30232, "https://cdn/aac128.m4s")},${audio(30280, "https://cdn/aac192.m4s")}",
        )

        val stream = BiliMapper.audioStream(data, AudioQuality.Q128.id)
        assertEquals(30232, stream.qualityId)
        assertEquals("https://cdn/aac128.m4s", stream.url)
    }
}
