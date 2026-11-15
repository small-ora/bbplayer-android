package com.bbplayer.android.playback.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.DownloadRepository
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.DownloadEntry
import com.bbplayer.android.core.model.DownloadStatus
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.playback.PlaybackConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Offline download manager (SPEC C1–C4), the v2 [DownloadRepository] impl.
 *
 * Pipeline per track:
 * `enqueue` -> resolve fresh URL through [MusicRepository.getAudioStream] ->
 * OkHttp streaming download with desktop UA + Bilibili `Referer` (Range resume
 * when a partial file exists) -> `filesDir/downloads/<bvid>_<cid>.m4s` ->
 * `filesDir/downloads.json` index -> `entries` StateFlow for the UI.
 *
 * Concurrency is bounded by [ParallelDownloadGate] which re-reads
 * [maxParallelProvider] on every slot acquisition so the settings slider takes
 * effect without recreating the manager. Progress callbacks are throttled by
 * [DownloadProgressThrottle] (>=250 ms or >=2%).
 *
 * Export (SPEC C4) copies the m4s bytes (ISOBMFF, no transcode) to
 * `Music/BBPlayer/<title> - <uploader>.m4a` via MediaStore.Files on API 29+,
 * or to the public Music dir on API 28 and below (falling back to the app's
 * external files dir when the WRITE_EXTERNAL_STORAGE permission was denied).
 */
class BiliDownloadManager(
    context: Context,
    private val scope: CoroutineScope,
    private val musicRepository: MusicRepository,
    private val qualityProvider: () -> AudioQuality,
    private val maxParallelProvider: () -> Int = { 1 },
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : DownloadRepository {

    private val appContext = context.applicationContext
    private val downloadsDir = File(appContext.filesDir, DownloadNames.DOWNLOAD_DIR)
    private val indexStore = DownloadIndexStore(DownloadIndexStore.defaultFile(appContext.filesDir))
    private val gate = ParallelDownloadGate(maxParallelProvider)

    private val _entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    override val entries: Flow<List<DownloadEntry>> = _entries.asStateFlow()

    /** Convenience for the UI: same stream as [entries] but as a StateFlow. */
    val state: StateFlow<List<DownloadEntry>> get() = _entries.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Call>()
    private val lock = Any()
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            runCatching { loadIndex() }
            ready.complete(Unit)
        }
    }

    // region DownloadRepository

    override suspend fun enqueue(track: Track): Result<Unit> {
        ready.await()
        if (track.bvid.isBlank()) {
            return Result.failure(AppException("无法下载：缺少视频 ID", retryable = false))
        }
        synchronized(lock) {
            val existing = _entries.value.firstOrNull { it.key == track.key }
            when (existing?.status) {
                // Idempotent: already queued / downloading, nothing to do.
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> return Result.success(Unit)
                // Completed and still on disk: nothing to do.
                DownloadStatus.COMPLETED -> {
                    if (localFileFor(track.key) != null) return Result.success(Unit)
                }
                else -> Unit
            }
            if (existing == null) {
                _entries.update { it + newEntry(track) }
            } else {
                updateEntry(track.key) {
                    it.copy(
                        status = DownloadStatus.QUEUED,
                        progress = 0f,
                        downloadedBytes = partialBytes(track),
                        errorMessage = null,
                    )
                }
            }
            persistIndex()
            startDownload(track)
        }
        return Result.success(Unit)
    }

    override suspend fun cancel(trackKey: String) {
        ready.await()
        synchronized(lock) {
            jobs.remove(trackKey)?.cancel()
            calls.remove(trackKey)?.cancel()
            updateEntry(trackKey) { it.copy(status = DownloadStatus.PAUSED) }
        }
        persistIndex()
    }

    override suspend fun remove(trackKey: String): Result<Unit> {
        ready.await()
        synchronized(lock) {
            jobs.remove(trackKey)?.cancel()
            calls.remove(trackKey)?.cancel()
            val entry = _entries.value.firstOrNull { it.key == trackKey }
            entry?.filePath?.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { File(path).delete() }
            }
            _entries.update { list -> list.filterNot { it.key == trackKey } }
        }
        persistIndex()
        return Result.success(Unit)
    }

    override fun localFileFor(trackKey: String): File? {
        val entry = _entries.value.firstOrNull { it.key == trackKey } ?: return null
        if (entry.status != DownloadStatus.COMPLETED) return null
        return entry.filePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }

    override suspend fun clearCompleted() {
        ready.await()
        synchronized(lock) {
            val completed = _entries.value.filter { it.status == DownloadStatus.COMPLETED }
            completed.forEach { entry ->
                entry.filePath.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
            }
            _entries.update { list -> list.filterNot { it.status == DownloadStatus.COMPLETED } }
        }
        persistIndex()
    }

    override suspend fun export(trackKey: String, lrc: String?): Result<String> =
        withContext(ioDispatcher) {
            ready.await()
            val entry = _entries.value.firstOrNull { it.key == trackKey && it.status == DownloadStatus.COMPLETED }
                ?: return@withContext Result.failure(AppException("该曲目尚未下载完成", retryable = false))
            val source = entry.filePath.takeIf { it.isNotBlank() }?.let(::File)
                ?.takeIf { it.isFile && it.length() > 0L }
                ?: return@withContext Result.failure(AppException("下载文件已丢失，请重新下载", retryable = false))
            val exported = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    exportToMediaStore(source, entry.track, lrc)
                } else {
                    exportToLegacyPublicMusic(source, entry.track, lrc)
                }
            }.recoverCatching {
                // A locked / unmounted volume must not lose the export: fall back
                // to the app-owned external directory (always readable by the app).
                exportToAppExternalDir(source, entry.track, lrc)
            }
            exported.fold(
                onSuccess = { Result.success(it) },
                onFailure = { error ->
                    Result.failure(
                        if (error is AppException) error else AppException("导出失败：${error.toUserMessage()}"),
                    )
                },
            )
        }

    // endregion

    // region Download pipeline

    private suspend fun loadIndex() {
        val loaded = indexStore.load()
        // Refresh partially downloaded byte counters from the files that survived
        // the process restart so a resumed download shows the right progress.
        val refreshed = loaded.map { entry ->
            when (entry.status) {
                DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                    entry.copy(downloadedBytes = entry.track.let { partialBytes(it) })
                else -> entry
            }
        }
        _entries.value = refreshed
    }

    private fun newEntry(track: Track): DownloadEntry {
        val partial = partialBytes(track)
        return DownloadEntry(
            track = track,
            status = DownloadStatus.QUEUED,
            progress = 0f,
            downloadedBytes = partial,
            totalBytes = 0L,
            filePath = "",
            qualityId = qualityProvider().id,
            createdAt = now(),
        )
    }

    private fun startDownload(track: Track) {
        val key = track.key
        if (jobs.containsKey(key)) return
        jobs[key] = scope.launch {
            try {
                gate.withPermit { runDownload(track) }
            } finally {
                jobs.remove(key)
            }
        }
    }

    private suspend fun runDownload(track: Track) {
        val key = track.key
        updateEntry(key) { it.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null) }
        persistIndex()
        try {
            val quality = qualityProvider()
            val stream = musicRepository.getAudioStream(track, quality).getOrElse { throw it }
            val file = downloadStream(track, stream.url, stream.backupUrls)
            updateEntry(key) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    downloadedBytes = file.length(),
                    totalBytes = file.length(),
                    filePath = file.absolutePath,
                    errorMessage = null,
                )
            }
        } catch (cancelled: CancellationException) {
            updateEntry(key) { it.copy(status = DownloadStatus.PAUSED) }
            throw cancelled
        } catch (error: Throwable) {
            updateEntry(key) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = error.toUserMessage(),
                )
            }
        } finally {
            persistIndex()
        }
    }

    /** Tries the primary URL then the backup mirrors; a partial file is Range-resumed. */
    private suspend fun downloadStream(
        track: Track,
        primaryUrl: String,
        backupUrls: List<String>,
    ): File {
        var lastError: Throwable? = null
        val urls = (listOf(primaryUrl) + backupUrls).filter { it.isNotBlank() }.distinct()
        for (url in urls) {
            cancelIfActive(track.key)
            try {
                return downloadFromUrl(track, url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw (lastError ?: AppException("下载失败，请稍后重试"))
    }

    private suspend fun downloadFromUrl(track: Track, url: String): File = withContext(ioDispatcher) {
        val key = track.key
        val target = File(downloadsDir, DownloadNames.internalFileName(track))
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        var existingBytes = if (target.isFile) target.length() else 0L

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Referer", PlaybackConfig.BILIBILI_REFERER)
            .header("User-Agent", PlaybackConfig.DESKTOP_USER_AGENT)
            .header("Accept", "*/*")
        if (existingBytes > 0L) requestBuilder.header("Range", "bytes=$existingBytes-")

        val call = okHttpClient.newCall(requestBuilder.build())
        calls[key] = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw AppException(
                        "下载失败（HTTP ${response.code}）",
                        retryable = response.code >= 500 || response.code in RETRYABLE_STATUS,
                    )
                }
                val append = existingBytes > 0L && response.code == 206
                if (!append) existingBytes = 0L
                val body = response.body ?: throw AppException("下载失败：服务器返回空内容")
                val contentLength = body.contentLength()
                val totalBytes = when {
                    contentLength > 0L -> existingBytes + contentLength
                    else -> 0L
                }
                val throttle = DownloadProgressThrottle()
                var downloaded = existingBytes
                FileOutputStream(target, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (throttle.shouldEmit(now(), downloaded, totalBytes)) {
                                publishProgress(key, downloaded, totalBytes)
                            }
                        }
                    }
                    output.flush()
                }
                if (totalBytes > 0L && downloaded < totalBytes) {
                    throw AppException("下载不完整（$downloaded/$totalBytes 字节）")
                }
                if (target.length() <= 0L) throw AppException("下载失败：文件为空")
                publishProgress(key, target.length(), target.length())
                target
            }
        } finally {
            calls.remove(key)
        }
    }

    private fun publishProgress(trackKey: String, downloadedBytes: Long, totalBytes: Long) {
        updateEntry(trackKey) { entry ->
            entry.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = DownloadProgressThrottle.fractionOf(downloadedBytes, totalBytes),
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
            )
        }
    }

    private fun updateEntry(trackKey: String, transform: (DownloadEntry) -> DownloadEntry) {
        _entries.update { list ->
            list.map { entry -> if (entry.key == trackKey) transform(entry) else entry }
        }
    }

    private fun persistIndex() {
        val snapshot = _entries.value
        scope.launch { runCatching { indexStore.save(snapshot) } }
    }

    private fun partialBytes(track: Track): Long =
        File(downloadsDir, DownloadNames.internalFileName(track)).let { if (it.isFile) it.length() else 0L }

    private suspend fun cancelIfActive(trackKey: String) {
        if (currentCoroutineContext()[Job]?.isActive == false) {
            throw CancellationException("download cancelled")
        }
        if (jobs[trackKey]?.isActive == false) throw CancellationException("download cancelled")
    }

    // endregion

    // region Export

    private fun exportToMediaStore(source: File, track: Track, lrc: String?): String {
        val resolver = appContext.contentResolver
        val relativePath = DownloadNames.publicRelativePath()
        val audioUri = insertPending(
            displayName = DownloadNames.exportFileName(track),
            mimeType = DownloadNames.EXPORT_MIME_TYPE,
            relativePath = relativePath,
        ) ?: throw AppException("导出失败：无法在媒体库创建文件")
        try {
            resolver.openOutputStream(audioUri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法打开输出流")
            resolver.update(audioUri, pendingCleared(), null, null)
        } catch (error: Throwable) {
            runCatching { resolver.delete(audioUri, null, null) }
            throw error
        }
        if (!lrc.isNullOrBlank()) {
            writeMediaStoreSidecar(
                displayName = DownloadNames.exportLrcFileName(track),
                content = lrc,
                relativePath = relativePath,
            )
        }
        return audioUri.toString()
    }

    private fun insertPending(displayName: String, mimeType: String, relativePath: String): android.net.Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return appContext.contentResolver.insert(mediaFilesCollection(), values)
    }

    private fun writeMediaStoreSidecar(displayName: String, content: String, relativePath: String) {
        val uri = insertPending(displayName, "text/plain", relativePath) ?: return
        try {
            appContext.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            }
            appContext.contentResolver.update(uri, pendingCleared(), null, null)
        } catch (ignored: Throwable) {
            runCatching { appContext.contentResolver.delete(uri, null, null) }
        }
    }

    private fun pendingCleared(): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.IS_PENDING, 0)
    }

    private fun mediaFilesCollection(): android.net.Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @Suppress("DEPRECATION")
    private fun exportToLegacyPublicMusic(source: File, track: Track, lrc: String?): String {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            DownloadNames.PUBLIC_SUBDIR,
        )
        val granted = appContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching {
                if (!publicDir.exists()) publicDir.mkdirs()
                if (publicDir.isDirectory) {
                    val target = File(publicDir, DownloadNames.exportFileName(track))
                    source.copyTo(target, overwrite = true)
                    if (!lrc.isNullOrBlank()) {
                        File(publicDir, DownloadNames.exportLrcFileName(track))
                            .writeText(lrc, Charsets.UTF_8)
                    }
                    return target.absolutePath
                }
            }
        }
        return exportToAppExternalDir(source, track, lrc)
    }

    private fun exportToAppExternalDir(source: File, track: Track, lrc: String?): String {
        val base = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: appContext.filesDir
        val dir = File(base, DownloadNames.PUBLIC_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, DownloadNames.exportFileName(track))
        source.copyTo(target, overwrite = true)
        if (!lrc.isNullOrBlank()) {
            File(dir, DownloadNames.exportLrcFileName(track)).writeText(lrc, Charsets.UTF_8)
        }
        return target.absolutePath
    }

    // endregion

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        val RETRYABLE_STATUS = setOf(403, 408, 416, 429)

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
