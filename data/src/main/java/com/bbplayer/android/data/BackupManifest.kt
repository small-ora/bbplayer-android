package com.bbplayer.android.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 本地备份的清单与路径安全校验（纯函数，可单测）。
 *
 * 只备份用户数据：本地歌单/收藏、播放历史、断点续播、播放器状态、歌词缓存。
 * 不包含任何账号/凭据（本项目本身无登录态）。
 */
internal object BackupManifest {

    /** filesDir 根目录下允许备份/恢复的 JSON 文件（顺序即 zip 内顺序）。 */
    val DATA_FILES: List<String> = listOf(
        "library.json",
        "history.json",
        "resume.json",
        "playback_state.json",
    )

    val DATA_FILE_SET: Set<String> = DATA_FILES.toSet()

    /** androidx DataStore 设置文件（相对 filesDir），恢复后需重启应用生效。 */
    const val SETTINGS_DIR = "datastore"
    const val SETTINGS_FILE = "datastore/bbplayer_settings.preferences_pb"

    /** 歌词缓存目录（filesDir 下的 lyrics，内含每曲一个 json）。 */
    const val LYRICS_DIR = "lyrics"

    const val PREFIX = "bbplayer-backup-"
    const val EXTENSION = ".zip"

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    private val WINDOWS_DRIVE = Regex("""^[A-Za-z]:""")

    /** `bbplayer-backup-yyyyMMdd-HHmm.zip`（本地时区）。 */
    fun exportName(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        PREFIX + TIMESTAMP.format(Instant.ofEpochMilli(nowMs).atZone(zone)) + EXTENSION

    /**
     * zip 条目名 → 相对 filesDir 的安全路径；非法路径或不在白名单内返回 null。
     *
     * 防 zip slip：拒绝绝对路径、Windows 盘符、`.`/`..`/空目录段；
     * 只允许根目录白名单 JSON 与 `lyrics/<单层文件名>`。
     */
    fun sanitizeEntry(name: String): String? {
        val path = name.replace('\\', '/').trim()
        if (path.isEmpty()) return null
        if (path.startsWith("/")) return null
        if (WINDOWS_DRIVE.containsMatchIn(path)) return null
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return when {
            segments.size == 1 -> segments[0].takeIf { it in DATA_FILE_SET }
            segments.size == 2 && segments[0] == LYRICS_DIR -> {
                val fileName = segments[1]
                if (fileName.isBlank()) null else "$LYRICS_DIR/$fileName"
            }

            segments.size == 2 && segments[0] == SETTINGS_DIR ->
                segments[1].takeIf { it == "bbplayer_settings.preferences_pb" }
                    ?.let { "$SETTINGS_DIR/$it" }

            else -> null
        }
    }

    fun isAllowedEntry(name: String): Boolean = sanitizeEntry(name) != null
}
