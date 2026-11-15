package com.bbplayer.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverMapperTest {

    private fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text).jsonObject

    @Test
    fun `联想词去重去HTML并截断到8条`() {
        val root = json(
            """
            {
              "code": 0,
              "result": {
                "tag": [
                  {"value": "周杰伦歌曲全集", "name": "<em>周杰伦</em>歌曲全集"},
                  {"value": "周杰伦", "name": "<em>周杰伦</em>"},
                  {"value": "周杰伦", "name": "重复项"},
                  {"value": "周杰伦mv"},
                  {"value": "周杰伦演唱会"},
                  {"value": "周杰伦是谁"},
                  {"value": "周杰伦你会饶舌吗"},
                  {"value": "周杰伦在华语乐坛的地位有多高"},
                  {"value": "周杰伦田馥甄"},
                  {"value": "周杰伦专辑"}
                ]
              }
            }
            """.trimIndent(),
        )
        val suggestions = DiscoverMapper.suggestions(root)
        assertEquals(DiscoverMapper.MAX_SUGGESTIONS, suggestions.size)
        assertEquals(8, suggestions.size)
        assertEquals("周杰伦歌曲全集", suggestions.first())
        assertTrue(suggestions.none { it.contains("<em") })
        assertEquals(suggestions.distinct(), suggestions)
    }

    @Test
    fun `联想词空响应返回空列表`() {
        assertEquals(emptyList<String>(), DiscoverMapper.suggestions(json("""{"code":0,"result":{"tag":[]}}""")))
        assertEquals(emptyList<String>(), DiscoverMapper.suggestions(json("""{"code":0}""")))
    }

    @Test
    fun `热搜关键词映射并兼容show_name`() {
        val data = json(
            """
            {
              "trending": {
                "title": "bilibili热搜",
                "list": [
                  {"keyword": "BLG夺冠", "show_name": "BLG夺冠", "heat_score": 35043703},
                  {"keyword": "", "show_name": "只有展示名"},
                  {"keyword": "国产中单和超级上单有多强"},
                  {"keyword": "BLG夺冠"}
                ]
              }
            }
            """.trimIndent(),
        )
        val hot = DiscoverMapper.hotKeywords(data)
        assertEquals(3, hot.size)
        assertEquals("BLG夺冠", hot.first())
        assertEquals("只有展示名", hot[1])
        assertEquals(hot.distinct(), hot)
    }

    @Test
    fun `热搜空数据返回空列表`() {
        assertEquals(emptyList<String>(), DiscoverMapper.hotKeywords(json("""{"trending":{"list":[]}}""")))
        assertEquals(emptyList<String>(), DiscoverMapper.hotKeywords(json("""{}""")))
    }
}
