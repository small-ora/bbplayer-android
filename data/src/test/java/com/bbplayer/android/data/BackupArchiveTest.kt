package com.bbplayer.android.data

import com.bbplayer.android.core.contracts.AppException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun writeZip(target: File, entries: Map<String, String>) {
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun entriesOf(zip: File): List<String> =
        ZipFile(zip).use { file -> file.entries().asSequence().map { it.name }.sorted().toList() }

    // ------------------------------------------------------------ 清单/命名

    @Test
    fun `导出文件名按本地时间格式化`() {
        val millis = LocalDateTime.of(2026, 11, 15, 14, 49)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals("bbplayer-backup-20261115-1449.zip", BackupManifest.exportName(millis, zone))
    }

    @Test
    fun `白名单文件与歌词目录允许恢复`() {
        for (name in BackupManifest.DATA_FILES) {
            assertEquals(name, BackupManifest.sanitizeEntry(name))
        }
        assertEquals("lyrics/abc123.json", BackupManifest.sanitizeEntry("lyrics/abc123.json"))
        assertEquals("lyrics/abc123.json", BackupManifest.sanitizeEntry("lyrics\\abc123.json"))
        assertTrue(BackupManifest.isAllowedEntry("library.json"))
    }

    @Test
    fun `路径穿越与未知文件被拒绝`() {
        val illegal = listOf(
            "../evil.json",
            "/etc/passwd",
            "C:\\evil.json",
            "C:/evil.json",
            "library.json/../../evil",
            "lyrics/../../library.json",
            "lyrics/",
            "lyrics/sub/nested.json",
            "unknown.json",
            "a/b/c.json",
            "",
        )
        for (name in illegal) {
            assertEquals("非法条目不应通过: $name", null, BackupManifest.sanitizeEntry(name))
            assertFalse(BackupManifest.isAllowedEntry(name))
        }
    }

    // ------------------------------------------------------------ 导出

    @Test
    fun `导出包含白名单JSON与歌词缓存`() {
        val filesDir = temp.newFolder("files")
        val cacheDir = temp.newFolder("cache")
        File(filesDir, "library.json").writeText("""{"playlists":[]}""")
        File(filesDir, "history.json").writeText("""{"entries":[]}""")
        File(filesDir, "resume.json").writeText("""{"trackKey":"a"}""")
        File(filesDir, "playback_state.json").writeText("""{"currentIndex":0}""")
        File(filesDir, "unrelated.txt").writeText("skip me")
        File(filesDir, "lyrics").mkdirs()
        File(filesDir, "lyrics/sha1.json").writeText("""{"lines":[]}""")
        File(filesDir, "lyrics/readme.txt").writeText("skip me too")

        val repo = ZipBackupRepository(filesDir, cacheDir, clock = { 0L }, zone = zone)
        val backup = repo.export()

        assertTrue(backup.isFile)
        assertTrue(backup.name.startsWith("bbplayer-backup-"))
        assertEquals(
            listOf(
                "history.json",
                "library.json",
                "lyrics/sha1.json",
                "playback_state.json",
                "resume.json",
            ),
            entriesOf(backup),
        )
    }

    @Test
    fun `没有数据时也生成空备份zip`() {
        val filesDir = temp.newFolder("empty-files")
        val cacheDir = temp.newFolder("empty-cache")
        val backup = ZipBackupRepository(filesDir, cacheDir, clock = { 0L }, zone = zone).export()
        assertTrue(backup.isFile)
        assertTrue(entriesOf(backup).isEmpty())
    }

    // ------------------------------------------------------------ 恢复

    @Test
    fun `非白名单条目中止恢复且不覆盖已有文件`() {
        val filesDir = temp.newFolder("restore-files")
        val cacheDir = temp.newFolder("restore-cache")
        File(filesDir, "library.json").writeText("old")
        val zip = File(temp.root, "backup.zip")
        writeZip(
            zip,
            mapOf(
                "library.json" to "new-library",
                "lyrics/a.json" to "new-lyric",
                "unrelated.txt" to "should not restore",
            ),
        )

        val repo = ZipBackupRepository(filesDir, cacheDir, clock = { 0L }, zone = zone)
        // 非白名单条目会让整体恢复中止（先校验后落盘）。
        assertThrows(AppException::class.java) { repo.importFrom(zip) }
        assertEquals("old", File(filesDir, "library.json").readText())
    }

    @Test
    fun `恢复合法备份返回文件数`() {
        val filesDir = temp.newFolder("restore-ok-files")
        val cacheDir = temp.newFolder("restore-ok-cache")
        File(filesDir, "library.json").writeText("old")
        val zip = File(temp.root, "backup-ok.zip")
        writeZip(
            zip,
            mapOf(
                "library.json" to "new-library",
                "lyrics/a.json" to "new-lyric",
            ),
        )
        val restored = ZipBackupRepository(filesDir, cacheDir, clock = { 0L }, zone = zone).importFrom(zip)
        assertEquals(2, restored)
        assertEquals("new-library", File(filesDir, "library.json").readText())
        assertEquals("new-lyric", File(filesDir, "lyrics/a.json").readText())
    }

    @Test
    fun `zip slip条目被拒绝且不落盘`() {
        val filesDir = temp.newFolder("slip-files")
        val cacheDir = temp.newFolder("slip-cache")
        val zip = File(temp.root, "slip.zip")
        writeZip(zip, mapOf("../evil.json" to "evil", "library.json" to "ok"))

        val repo = ZipBackupRepository(filesDir, cacheDir, clock = { 0L }, zone = zone)
        val error = assertThrows(AppException::class.java) { repo.importFrom(zip) }
        assertTrue(error.userMessage.contains("非法路径"))
        assertFalse(File(filesDir.parentFile, "evil.json").exists())
        assertFalse(File(filesDir, "library.json").exists())
    }

    @Test
    fun `损坏zip与非zip文件抛AppException`() {
        val filesDir = temp.newFolder("broken-files")
        val cacheDir = temp.newFolder("broken-cache")
        val broken = File(temp.root, "broken.zip")
        broken.writeText("this is definitely not a zip file")

        val repo = ZipBackupRepository(filesDir, cacheDir, clock = { 0L }, zone = zone)
        assertThrows(AppException::class.java) { repo.importFrom(broken) }
        assertThrows(AppException::class.java) {
            repo.importFrom(File(temp.root, "missing.zip"))
        }
    }

    @Test
    fun `恢复后临时目录被清理`() {
        val filesDir = temp.newFolder("cleanup-files")
        val cacheDir = temp.newFolder("cleanup-cache")
        val zip = File(temp.root, "cleanup.zip")
        writeZip(zip, mapOf("library.json" to "x"))
        ZipBackupRepository(filesDir, cacheDir, clock = { 0L }, zone = zone).importFrom(zip)
        val leftovers = cacheDir.listFiles()?.filter { it.name.startsWith("restore-") }.orEmpty()
        assertTrue(leftovers.isEmpty())
    }
}
