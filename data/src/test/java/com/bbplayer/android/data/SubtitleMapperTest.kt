package com.bbplayer.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleMapperTest {

    private fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text).jsonObject

    private val playerData = json(
        """
        {
          "subtitle": {
            "allow_submit": false,
            "subtitles": [
              {"id": 1, "lan": "en", "lan_doc": "English", "subtitle_url": "https://aisubtitle.hdslb.com/bfs/subtitle/1.json"},
              {"id": 2, "lan": "zh-CN", "lan_doc": "中文（中国）", "subtitle_url": "//aisubtitle.hdslb.com/bfs/subtitle/2.json"},
              {"id": 3, "lan": "zh-Hans", "lan_doc": "中文（简体）", "subtitle_url": "https://aisubtitle.hdslb.com/bfs/subtitle/3.json"}
            ]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `优先选中文字幕并补全协议`() {
        val ref = SubtitleMapper.pickSubtitle(playerData)
        assertEquals("zh-CN", ref?.language)
        assertEquals("中文（中国）", ref?.languageLabel)
        assertEquals("https://aisubtitle.hdslb.com/bfs/subtitle/2.json", ref?.url)
    }

    @Test
    fun `没有中文时回退任意可用字幕`() {
        val ref = SubtitleMapper.pickSubtitle(
            json(
                """
                {"subtitle":{"subtitles":[
                  {"lan":"en","lan_doc":"English","subtitle_url":"https://aisubtitle.hdslb.com/a.json"}
                ]}}
                """.trimIndent(),
            ),
        )
        assertEquals("en", ref?.language)
    }

    @Test
    fun `无字幕返回null`() {
        assertNull(SubtitleMapper.pickSubtitle(json("""{"subtitle":{"subtitles":[]}}""")))
        assertNull(SubtitleMapper.pickSubtitle(json("""{}""")))
    }

    @Test
    fun `非法host与http字幕地址被拒绝`() {
        assertFalse(SubtitleMapper.isTrustedSubtitleUrl("https://evil.com/a.json"))
        assertFalse(SubtitleMapper.isTrustedSubtitleUrl("http://aisubtitle.hdslb.com/a.json"))
        assertFalse(SubtitleMapper.isTrustedSubtitleUrl("https://hdslb.com.evil.com/a.json"))
        assertFalse(SubtitleMapper.isTrustedSubtitleUrl("https://hdslb.com/a.json"))
        assertFalse(SubtitleMapper.isTrustedSubtitleUrl("not a url"))
        assertFalse(SubtitleMapper.isTrustedSubtitleUrl(null))

        assertTrue(SubtitleMapper.isTrustedSubtitleUrl("https://aisubtitle.hdslb.com/a.json"))
        assertTrue(SubtitleMapper.isTrustedSubtitleUrl("https://i0.hdslb.com/bfs/subtitle/a.json"))
        assertTrue(SubtitleMapper.isTrustedSubtitleUrl("https://api.bilibili.com/x/a.json"))
    }

    @Test
    fun `只保留合法字幕地址`() {
        val ref = SubtitleMapper.pickSubtitle(
            json(
                """
                {"subtitle":{"subtitles":[
                  {"lan":"zh-CN","lan_doc":"中文","subtitle_url":"https://evil.com/a.json"},
                  {"lan":"en","lan_doc":"English","subtitle_url":"https://aisubtitle.hdslb.com/a.json"}
                ]}}
                """.trimIndent(),
            ),
        )
        assertEquals("en", ref?.language)
        assertNull(
            SubtitleMapper.pickSubtitle(
                json("""{"subtitle":{"subtitles":[{"lan":"zh-CN","subtitle_url":"https://evil.com/a.json"}]}}"""),
            ),
        )
    }

    @Test
    fun `字幕JSON秒转毫秒并排序`() {
        val root = json(
            """
            {
              "font_size": 0.4,
              "body": [
                {"from": 3.0, "to": 5.25, "content": "第二句"},
                {"from": 0.0, "to": 1.5, "content": "第一句"},
                {"from": 1.5, "to": 3.0, "content": "   "},
                {"from": 10.0, "to": 11.0, "content": "第三句"}
              ]
            }
            """.trimIndent(),
        )
        val cues = SubtitleMapper.parseCues(root)
        assertEquals(3, cues.size)
        assertEquals(0L, cues[0].startMs)
        assertEquals(1500L, cues[0].endMs)
        assertEquals("第一句", cues[0].text)
        assertEquals(3000L, cues[1].startMs)
        assertEquals(5250L, cues[1].endMs)
        assertEquals("第二句", cues[1].text)
        assertEquals(10_000L, cues[2].startMs)
    }

    @Test
    fun `空字幕体返回空列表`() {
        assertTrue(SubtitleMapper.parseCues(json("""{"body":[]}""")).isEmpty())
        assertTrue(SubtitleMapper.parseCues(json("""{}""")).isEmpty())
    }
}
