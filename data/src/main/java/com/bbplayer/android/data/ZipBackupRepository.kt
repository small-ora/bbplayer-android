package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * [BackupRepository] 的 zip 实现。
 *
 * 导出：把 `filesDir` 下的白名单 JSON（[BackupManifest.DATA_FILES]）与 `lyrics/`
 * 歌词缓存打包到 `cacheDir/bbplayer-backup-yyyyMMdd-HHmm.zip`。
 * 恢复：先校验 zip 条目名白名单（防 zip slip），解压到临时目录后再覆盖 `filesDir`，
 * 返回恢复的文件数；损坏/非法 zip 抛 [AppException]。
 */
class ZipBackupRepository(
    private val filesDir: File,
    private val cacheDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : BackupRepository {

    /** 便捷构造：由 AppContainer 直接调用。 */
    constructor(context: Context) : this(context.filesDir, context.cacheDir)

    override suspend fun exportZip(): Result<File> = repoResult {
        withContext(Dispatchers.IO) { export() }
    }

    override suspend fun importZip(file: File): Result<Int> = repoResult {
        withContext(Dispatchers.IO) { importFrom(file) }
    }

    // ------------------------------------------------------------ 内部实现

    internal fun export(): File {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw AppException("备份失败：无法创建缓存目录", retryable = true)
        }
        val target = File(cacheDir, BackupManifest.exportName(clock(), zone))
        val temp = File(cacheDir, "${target.name}.tmp")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
                for (name in BackupManifest.DATA_FILES) {
                    val source = File(filesDir, name)
                    if (source.isFile) writeEntry(zip, name, source)
                }
                // DataStore settings (restored after app restart).
                File(filesDir, BackupManifest.SETTINGS_FILE).takeIf { it.isFile }?.let {
                    writeEntry(zip, BackupManifest.SETTINGS_FILE, it)
                }
                val lyricsDir = File(filesDir, BackupManifest.LYRICS_DIR)
                lyricsDir.listFiles()
                    ?.asSequence()
                    ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
                    ?.sortedBy { it.name }
                    ?.forEach { lrc ->
                        writeEntry(zip, "${BackupManifest.LYRICS_DIR}/${lrc.name}", lrc)
                    }
            }
            moveReplacing(temp, target)
            return target
        } catch (e: IOException) {
            runCatching { temp.delete() }
            throw AppException("备份导出失败，请稍后重试", e, retryable = true)
        }
    }

    internal fun importFrom(file: File): Int {
        if (!file.isFile) throw AppException("备份文件不存在", retryable = false)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val staging = File(cacheDir, "restore-${clock()}-${System.nanoTime()}")
        if (!staging.mkdirs()) throw AppException("恢复失败：无法创建临时目录", retryable = true)
        try {
            val entries = extractToStaging(file, staging)
            if (entries == 0) throw AppException("备份文件为空或格式不正确", retryable = false)
            return try {
                copyStagedIntoFilesDir(staging)
            } catch (e: IOException) {
                throw AppException("恢复备份失败，请稍后重试", e, retryable = true)
            }
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    /** 解压到临时目录；任何非法条目/损坏 zip 直接抛错（不落半个文件到 filesDir）。 */
    private fun extractToStaging(file: File, staging: File): Int {
        var entries = 0
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val safePath = BackupManifest.sanitizeEntry(entry.name)
                        ?: throw AppException("备份文件包含非法路径，已中止恢复", retryable = false)
                    val outFile = File(staging, safePath.replace('/', File.separatorChar))
                    ensureInside(staging, outFile)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output -> zip.copyTo(output) }
                    entries++
                }
            }
        } catch (e: AppException) {
            throw e
        } catch (e: IOException) {
            throw AppException("备份文件已损坏或格式不正确", e, retryable = false)
        } catch (e: IllegalArgumentException) {
            throw AppException("备份文件已损坏或格式不正确", e, retryable = false)
        }
        return entries
    }

    private fun copyStagedIntoFilesDir(staging: File): Int {
        var restored = 0
        staging.walkTopDown()
            .filter { it.isFile }
            .forEach { source ->
                val relative = source.relativeTo(staging).path.replace(File.separatorChar, '/')
                val safePath = BackupManifest.sanitizeEntry(relative)
                    ?: throw AppException("备份文件包含非法路径，已中止恢复", retryable = false)
                val destination = File(filesDir, safePath.replace('/', File.separatorChar))
                ensureInside(filesDir, destination)
                destination.parentFile?.mkdirs()
                Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                restored++
            }
        return restored
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, source: File) {
        zip.putNextEntry(ZipEntry(name))
        source.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    /** 双保险：规范化路径必须仍在根目录内（防 zip slip）。 */
    private fun ensureInside(root: File, target: File) {
        val rootPath = root.canonicalFile.toPath()
        val targetPath = target.canonicalFile.toPath()
        if (!targetPath.startsWith(rootPath)) {
            throw AppException("备份文件包含非法路径，已中止恢复", retryable = false)
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
