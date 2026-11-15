package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateRepositoryTest {

    private fun json(text: String): JsonObject = BiliJson.parseToJsonElement(text).jsonObject

    private fun repository() = GitHubUpdateRepository(
        HttpFetcher(),
        "https://api.github.com/repos/small-ora/bbplayer-android/releases/latest",
        "BBPlayer-Android",
    )

    private val releaseJson = json(
        """
        {
          "tag_name": "v0.2.0",
          "name": "v0.2.0",
          "html_url": "https://github.com/small-ora/bbplayer-android/releases/tag/v0.2.0",
          "body": "**Full Changelog**: 非登录功能补齐",
          "published_at": "2026-11-15T06:30:41Z",
          "assets": [
            {"name": "app-debug.apk", "browser_download_url": "https://github.com/a/releases/download/v0.2.0/app-debug.apk"},
            {"name": "app-release-unsigned.apk", "browser_download_url": "https://github.com/a/releases/download/v0.2.0/app-release-unsigned.apk"},
            {"name": "checksums.txt", "browser_download_url": "https://github.com/a/releases/download/v0.2.0/checksums.txt"}
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `有新版返回ReleaseInfo并选中第一个apk`() {
        val info = repository().parseRelease(releaseJson, "0.1.0")
        assertEquals("0.2.0", info?.version)
        assertEquals("v0.2.0", info?.tagName)
        assertEquals("https://github.com/small-ora/bbplayer-android/releases/tag/v0.2.0", info?.url)
        assertTrue(info?.notes.orEmpty().contains("非登录功能"))
        assertEquals(
            "https://github.com/a/releases/download/v0.2.0/app-debug.apk",
            info?.apkUrl,
        )
        assertEquals("2026-11-15T06:30:41Z", info?.publishedAt)
    }

    @Test
    fun `当前已是最新时返回null`() {
        assertNull(repository().parseRelease(releaseJson, "v0.2.0"))
        assertNull(repository().parseRelease(releaseJson, "0.2.0"))
        assertNull(repository().parseRelease(releaseJson, "0.3.0"))
    }

    @Test
    fun `没有apk资源时apkUrl为null`() {
        val noApk = json(
            """
            {
              "tag_name": "v0.2.0",
              "html_url": "https://github.com/x",
              "assets": [{"name": "source.zip", "browser_download_url": "https://github.com/x/source.zip"}]
            }
            """.trimIndent(),
        )
        val info = repository().parseRelease(noApk, "0.1.0")
        assertNull(info?.apkUrl)
    }

    @Test
    fun `缺少tag_name时抛AppException`() {
        assertThrows(AppException::class.java) {
            repository().parseRelease(json("""{"assets": []}"""), "0.1.0")
        }
    }
}
