package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.UpdateRepository
import com.bbplayer.android.core.model.ReleaseInfo

/**
 * [UpdateRepository] 的 GitHub Releases 实现（无需登录）。
 *
 * `GET https://api.github.com/repos/small-ora/bbplayer-android/releases/latest`
 * 要求带 UA（`BBPlayer-Android/<version>`，GitHub API 对无 UA 请求返回 403）。
 * 有新版本返回 [ReleaseInfo]，否则返回 `Result.success(null)`；网络失败返回 failure。
 */
class GitHubUpdateRepository internal constructor(
    private val http: HttpFetcher,
    private val endpoint: String,
    private val userAgentPrefix: String,
) : UpdateRepository {

    /** 生产构造：默认仓库地址 + `BBPlayer-Android` UA 前缀。 */
    constructor() : this(
        http = HttpFetcher(),
        endpoint = DEFAULT_ENDPOINT,
        userAgentPrefix = DEFAULT_USER_AGENT_PREFIX,
    )

    override suspend fun checkForUpdate(currentVersion: String): Result<ReleaseInfo?> = repoResult {
        val version = AppVersion.normalize(currentVersion).ifBlank { "unknown" }
        val root = http.getJsonObject(
            url = endpoint,
            headers = mapOf(
                "User-Agent" to "$userAgentPrefix/$version",
                "Accept" to "application/vnd.github+json",
            ),
            // 缺 tag_name 视为失败，不写 10 分钟缓存。
            validate = { payload ->
                if (payload.strOrNull("tag_name", "tagName") == null) {
                    throw AppException("更新信息解析失败，请稍后重试", retryable = true)
                }
            },
        )
        parseRelease(root, currentVersion)
    }

    // ------------------------------------------------------------ 内部实现

    /** 纯函数映射：GitHub latest release JSON → 有新版返回 ReleaseInfo，否则 null。 */
    internal fun parseRelease(root: kotlinx.serialization.json.JsonObject, currentVersion: String): ReleaseInfo? {
        val tagName = root.strOrNull("tag_name", "tagName")
            ?: throw AppException("更新信息解析失败，请稍后重试", retryable = true)
        val latestVersion = AppVersion.display(tagName)
        if (!AppVersion.isNewer(latestVersion, currentVersion)) return null
        val apkUrls = root.arrOrNull("assets")
            .toObjectList()
            .mapNotNull { it.strOrNull("browser_download_url", "browserDownloadUrl") }
            .filter { url -> url.substringBefore('?').endsWith(".apk", ignoreCase = true) }
        // Prefer a release build over the debug APK when both are attached.
        val apkUrl = apkUrls.firstOrNull { it.contains("release", ignoreCase = true) }
            ?: apkUrls.firstOrNull()
        return ReleaseInfo(
            version = latestVersion,
            tagName = tagName,
            url = root.strOrNull("html_url", "htmlUrl").orEmpty(),
            notes = root.strOrNull("body").orEmpty(),
            apkUrl = apkUrl,
            publishedAt = root.strOrNull("published_at", "publishedAt").orEmpty(),
        )
    }

    companion object {
        const val DEFAULT_ENDPOINT =
            "https://api.github.com/repos/small-ora/bbplayer-android/releases/latest"
        const val DEFAULT_USER_AGENT_PREFIX = "BBPlayer-Android"
    }
}
