package com.bbplayer.android.data

import com.bbplayer.android.core.model.CommentPage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentMapperTest {

    private fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text).jsonObject

    private val topLevel = json(
        """
        {
          "page": {"num": 1, "size": 20, "count": 4717, "acount": 4717},
          "replies": [
            {
              "rpid": 3387535493,
              "oid": 753394975,
              "like": 11328,
              "ctime": 1598018234,
              "rcount": 118,
              "member": {
                "uname": "asd丹-",
                "avatar": "https://i0.hdslb.com/bfs/face/abc.jpg"
              },
              "content": {"message": "一群嗜血的蚂蚁 &amp; 被腐肉所吸引"}
            },
            {
              "rpid": 2984472168,
              "like": 5933,
              "ctime": 1591097021,
              "rcount": 24,
              "member": {"uname": "桃若李", "avatar": "//i1.hdslb.com/bfs/face/def.jpg"},
              "content": {"message": "时隔n年发现回忆都是暖色调"},
              "replies": [
                {
                  "rpid": 4014584782,
                  "like": 7,
                  "ctime": 1611798001,
                  "rcount": 0,
                  "member": {"uname": "白嫩的肌肤2333", "avatar": "https://i1.hdslb.com/bfs/face/ghi.jpg"},
                  "content": {"message": "楼中楼回复"}
                }
              ]
            }
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `一级评论映射全部字段`() {
        val page = CommentMapper.commentsPage(topLevel, 1)
        assertEquals(2, page.items.size)
        assertEquals(4717, page.total)
        assertTrue(page.hasMore)

        val first = page.items.first()
        assertEquals(3387535493L, first.id)
        assertEquals("asd丹-", first.userName)
        assertEquals("https://i0.hdslb.com/bfs/face/abc.jpg", first.avatarUrl)
        assertEquals("一群嗜血的蚂蚁 & 被腐肉所吸引", first.content)
        assertEquals(11328L, first.likeCount)
        assertEquals(1598018234L, first.createdAtSec)
        assertEquals(118, first.replyCount)
        assertTrue(first.replies.isEmpty())
    }

    @Test
    fun `楼中楼在父评论里嵌套映射`() {
        val page = CommentMapper.commentsPage(topLevel, 1)
        val parent = page.items[1]
        assertEquals(1, parent.replies.size)
        val reply = parent.replies.first()
        assertEquals(4014584782L, reply.id)
        assertEquals("白嫩的肌肤2333", reply.userName)
        assertEquals("楼中楼回复", reply.content)
        // `//` 开头的头像补全协议并升级 https
        assertEquals("https://i1.hdslb.com/bfs/face/def.jpg", parent.avatarUrl)
    }

    @Test
    fun `最后一页hasMore为false`() {
        val lastPage = json(
            """{"page":{"num":236,"size":20,"count":4717},"replies":[{"rpid":1,"content":{"message":"x"},"member":{"uname":"a"}}]}""",
        )
        val page = CommentMapper.commentsPage(lastPage, 236)
        assertEquals(236, page.page)
        assertFalse(page.hasMore)
    }

    @Test
    fun `空评论返回空列表且total为0`() {
        val page = CommentMapper.commentsPage(json("""{"page":{"num":1,"size":20,"count":0},"replies":null}"""), 1)
        assertTrue(page.items.isEmpty())
        assertEquals(0, page.total)
        assertEquals(1, page.page)
        assertFalse(page.hasMore)
    }

    @Test
    fun `楼中楼分页映射`() {
        val replies = json(
            """
            {
              "page": {"num": 1, "size": 20, "count": 118},
              "replies": [
                {"rpid": 4014584782, "like": 7, "ctime": 1611798001, "member": {"uname": "A"}, "content": {"message": "一"}},
                {"rpid": 4014584783, "like": 3, "ctime": 1611798002, "member": {"uname": "B"}, "content": {"message": "二"}}
              ]
            }
            """.trimIndent(),
        )
        val page: CommentPage = CommentMapper.commentsPage(replies, 1)
        assertEquals(2, page.items.size)
        assertEquals(118, page.total)
        assertTrue(page.hasMore)
        assertEquals("二", page.items[1].content)
    }

    @Test
    fun `已删除用户与脏数据兜底`() {
        val data = json(
            """
            {
              "page": {"num": 1, "size": 20, "count": 2},
              "replies": [
                {"rpid": 11, "content": {"message": "无 member"}},
                {"content": {"message": "缺 rpid"}},
                {"rpid": 12, "content": {"message": ""}, "member": {"uname": "", "avatar": ""}}
              ]
            }
            """.trimIndent(),
        )
        val page = CommentMapper.commentsPage(data, 1)
        assertEquals(2, page.items.size)
        assertEquals("未知用户", page.items.first().userName)
        assertEquals("", page.items.first().avatarUrl)
        assertEquals("", page.items[1].content)
    }

    @Test
    fun `bvid详情提取aid`() {
        val view = json(
            """{"bvid":"BV1Ek4y1r7Rg","aid":753394975,"cid":460901307,"title":"夜曲"}""",
        )
        assertEquals(753394975L, CommentMapper.aidFromView(view))
        assertNull(CommentMapper.aidFromView(json("""{"bvid":"BV1"}""")))
    }

    @Test
    fun `评论错误码映射中文提示`() {
        assertEquals("评论不存在或已关闭", CommentMapper.commentErrorMessage(-404, null))
        assertTrue(CommentMapper.commentErrorMessage(-412, null).contains("安全策略"))
        assertTrue(CommentMapper.commentErrorMessage(-509, null).contains("频繁"))
        assertEquals("服务器炸了", CommentMapper.commentErrorMessage(-500, "服务器炸了"))
        assertEquals("回复不存在或已删除", CommentMapper.replyErrorMessage(-404, null))
    }
}
