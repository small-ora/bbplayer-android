package com.bbplayer.android.playback

import com.bbplayer.android.core.model.DownloadEntry
import com.bbplayer.android.core.model.DownloadStatus
import com.bbplayer.android.core.model.Track
import com.bbplayer.android.playback.download.DownloadIndex
import com.bbplayer.android.playback.download.DownloadIndexSanitizer
import com.bbplayer.android.playback.download.DownloadIndexStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadIndexTest {
    private lateinit var tempDir: File
    private lateinit var indexFile: File

    private fun track(bvid: String = "BV1xx411c7mD", cid: Long = 1L) =
        Track(bvid = bvid, cid = cid, title = "Title", uploader = "Up")

    private fun entry(
        status: DownloadStatus,
        track: Track = track(),
        progress: Float = 0f,
        filePath: String = "",
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
    ) = DownloadEntry(
        track = track,
        status = status,
        progress = progress,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        filePath = filePath,
    )

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("bbplayer-index").toFile()
        indexFile = File(tempDir, "downloads.json")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `index round trips through the json store`() = runBlocking {
        val store = DownloadIndexStore(indexFile)
        val file = File(tempDir, "a.m4s").apply { writeText("12345") }
        val entries = listOf(
            entry(
                DownloadStatus.COMPLETED,
                progress = 1f,
                filePath = file.path,
                downloadedBytes = 5L,
                totalBytes = 5L,
            ),
            entry(DownloadStatus.FAILED, track = track(cid = 2L)),
        )
        store.save(entries)
        val loaded = store.load()
        assertEquals(entries, loaded)
        assertTrue(indexFile.isFile)
    }

    @Test
    fun `missing or corrupt index loads as empty instead of crashing`() = runBlocking {
        val store = DownloadIndexStore(indexFile)
        assertEquals(emptyList<DownloadEntry>(), store.load())
        indexFile.writeText("{not json at all")
        assertEquals(emptyList<DownloadEntry>(), store.load())
    }

    @Test
    fun `interrupted downloads migrate to paused and keep the partial file info`() = runBlocking {
        val store = DownloadIndexStore(indexFile)
        val downloaded = File(tempDir, "part.m4s").apply { writeText("partial") }
        val json = DownloadIndexStore.defaultJson()
        val payload = DownloadIndex(
            entries = listOf(
                entry(DownloadStatus.DOWNLOADING, filePath = downloaded.path, downloadedBytes = 3L),
                entry(DownloadStatus.QUEUED, track = track(cid = 2L)),
            ),
        )
        indexFile.writeText(json.encodeToString(DownloadIndex.serializer(), payload))

        val loaded = store.load()
        assertEquals(2, loaded.size)
        assertEquals(DownloadStatus.PAUSED, loaded[0].status)
        assertEquals(DownloadIndexSanitizer.MSG_INTERRUPTED, loaded[0].errorMessage)
        assertEquals(DownloadStatus.PAUSED, loaded[1].status)
    }

    @Test
    fun `completed entry whose file disappeared is demoted to failed`() = runBlocking {
        val store = DownloadIndexStore(indexFile)
        val payload = DownloadIndex(
            entries = listOf(
                entry(DownloadStatus.COMPLETED, filePath = File(tempDir, "gone.m4s").path, progress = 1f),
            ),
        )
        val json = DownloadIndexStore.defaultJson()
        indexFile.writeText(json.encodeToString(DownloadIndex.serializer(), payload))

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(DownloadStatus.FAILED, loaded[0].status)
        assertEquals(DownloadIndexSanitizer.MSG_FILE_MISSING, loaded[0].errorMessage)
        assertEquals(0f, loaded[0].progress, 0f)
    }

    @Test
    fun `progress and byte counters are clamped while invalid rows are dropped`() {
        val goodTrack = track()
        val entries = listOf(
            entry(DownloadStatus.FAILED, track = goodTrack, progress = 3.5f, downloadedBytes = -7L),
            entry(DownloadStatus.FAILED, track = Track(bvid = "", cid = 1L, title = "bad")),
            // duplicate key: the last row wins
            entry(DownloadStatus.COMPLETED, track = goodTrack, progress = 1f, filePath = "/tmp/x.m4s"),
        )
        val sanitized = DownloadIndexSanitizer.sanitize(entries) { it == "/tmp/x.m4s" }
        assertEquals(1, sanitized.size)
        assertEquals(DownloadStatus.COMPLETED, sanitized[0].status)
        val clamped = DownloadIndexSanitizer.sanitize(
            listOf(entry(DownloadStatus.FAILED, progress = -1f, downloadedBytes = -5L)),
        ) { true }
        assertEquals(0f, clamped[0].progress, 0f)
        assertEquals(0L, clamped[0].downloadedBytes)
    }

    @Test
    fun `unknown json fields and new statuses are tolerated`() = runBlocking {
        indexFile.writeText(
            """{"version":99,"futureField":true,"entries":[{"track":{"bvid":"BV1","cid":9,"title":"T","uploader":"","uploaderMid":0,"coverUrl":"","durationMs":0,"playCount":0,"pageTitle":"","pubDateSec":0,"qualityId":0},"status":"COMPLETED","progress":1.0,"downloadedBytes":4,"totalBytes":4,"filePath":"${File(tempDir, "ok.m4s")}","qualityId":30280,"createdAt":0,"newField":"x"}]}""",
        )
        File(tempDir, "ok.m4s").writeText("data")
        val loaded = DownloadIndexStore(indexFile).load()
        assertEquals(1, loaded.size)
        assertEquals(DownloadStatus.COMPLETED, loaded[0].status)
        assertEquals(1f, loaded[0].progress, 0f)
    }

    @Test
    fun `entry key is track bvid cid`() {
        assertEquals("BV1xx411c7mD:1", entry(DownloadStatus.FAILED).key)
        assertNull(entry(DownloadStatus.FAILED).filePath.takeIf { it.isNotBlank() })
    }
}
