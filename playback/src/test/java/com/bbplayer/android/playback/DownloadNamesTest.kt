package com.bbplayer.android.playback

import com.bbplayer.android.core.model.Track
import com.bbplayer.android.playback.download.DownloadNames
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadNamesTest {
    private fun track(
        title: String = "Song Title",
        uploader: String = "Uploader",
        bvid: String = "BV1xx411c7mD",
        cid: Long = 123456L,
    ) = Track(bvid = bvid, cid = cid, title = title, uploader = uploader)

    @Test
    fun `sanitize replaces path separators and illegal characters`() {
        val sanitized = DownloadNames.sanitize("a/b:c*d?e\"f<g>h|i\\j")
        assertEquals("a_b_c_d_e_f_g_h_i_j", sanitized)
    }

    @Test
    fun `sanitize collapses whitespace trims dots and falls back for empty input`() {
        assertEquals("hello world", DownloadNames.sanitize("  hello \n\t world  "))
        assertEquals("track", DownloadNames.sanitize("..."))
        assertEquals("track", DownloadNames.sanitize(""))
        assertEquals("track", DownloadNames.sanitize("   "))
    }

    @Test
    fun `sanitize caps the length and keeps a non blank tail`() {
        val longName = "x".repeat(200)
        val sanitized = DownloadNames.sanitize(longName)
        assertEquals(DownloadNames.MAX_BASE_NAME_LENGTH, sanitized.length)
    }

    @Test
    fun `internal cache name is bvid_cid m4s and ignores the title`() {
        assertEquals("BV1xx411c7mD_123456.m4s", DownloadNames.internalFileName(track()))
        assertEquals(
            "BV1xx411c7mD_123456.m4s",
            DownloadNames.internalFileName(track(title = "totally different title")),
        )
    }

    @Test
    fun `export m4a name is sanitized title - uploader`() {
        assertEquals("Song_Title - Uploader.m4a", DownloadNames.exportFileName(track(title = "Song/Title")))
        assertEquals("Song_Title - Uploader.lrc", DownloadNames.exportLrcFileName(track(title = "Song/Title")))
    }

    @Test
    fun `export name omits uploader when blank and falls back to display title`() {
        assertEquals("Song Title.m4a", DownloadNames.exportFileName(track(uploader = "")))
        assertEquals("track.m4a", DownloadNames.exportFileName(track(title = "", uploader = "")))
    }

    @Test
    fun `public relative path is Music slash BBPlayer`() {
        assertEquals("Music/BBPlayer", DownloadNames.publicRelativePath())
    }

    @Test
    fun `track cid zero is still a valid cache name`() {
        assertEquals("BV1xx411c7mD_0.m4s", DownloadNames.internalFileName(track(cid = 0L)))
    }
}

class PlaybackSourceSelectorTest {
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("bbplayer-source").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun downloadedFile(content: String = "audio-bytes"): File =
        File(tempDir, "track.m4s").apply { writeText(content) }

    @Test
    fun `existing non empty file is used for offline playback`() {
        val file = downloadedFile()
        val source = PlaybackSourceSelector.select("BV1:1") { if (it == "BV1:1") file else null }
        assertTrue(source is PlaybackSource.Local)
        assertEquals(file, (source as PlaybackSource.Local).file)
    }

    @Test
    fun `missing file falls back to remote stream`() {
        val source = PlaybackSourceSelector.select("BV1:1") { null }
        assertEquals(PlaybackSource.Remote, source)
    }

    @Test
    fun `empty or directory paths are ignored`() {
        val emptyFile = File(tempDir, "empty.m4s").apply { createNewFile() }
        assertEquals(PlaybackSource.Remote, PlaybackSourceSelector.select("k") { emptyFile })
        assertEquals(PlaybackSource.Remote, PlaybackSourceSelector.select("k") { tempDir })
    }

    @Test
    fun `provider failure falls back to remote instead of crashing playback`() {
        val source = PlaybackSourceSelector.select("BV1:1") { error("download index corrupt") }
        assertEquals(PlaybackSource.Remote, source)
        assertFalse(source is PlaybackSource.Local)
    }
}
