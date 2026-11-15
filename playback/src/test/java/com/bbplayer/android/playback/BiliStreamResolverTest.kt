package com.bbplayer.android.playback

import com.bbplayer.android.core.contracts.AppException
import com.bbplayer.android.core.contracts.MusicRepository
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.AudioStream
import com.bbplayer.android.core.model.SearchPage
import com.bbplayer.android.core.model.Track
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BiliStreamResolverTest {
    private val track = Track(bvid = "BV1xx411c7mD", cid = 99L, title = "demo")

    private class FakeMusicRepository(
        private val resolveAudio: suspend (Track, AudioQuality?) -> Result<AudioStream>,
    ) : MusicRepository {
        var audioCalls = 0
            private set
        val requestedQualities = mutableListOf<AudioQuality?>()

        override suspend fun getAudioStream(track: Track, quality: AudioQuality?): Result<AudioStream> {
            audioCalls += 1
            requestedQualities += quality
            return resolveAudio(track, quality)
        }

        override suspend fun search(query: String, page: Int): Result<SearchPage> =
            Result.success(SearchPage.empty(page))

        override suspend fun resolveInput(input: String): Result<List<Track>> = Result.success(emptyList())
        override suspend fun getTrending(): Result<List<Track>> = Result.success(emptyList())
        override suspend fun getRelated(bvid: String): Result<List<Track>> = Result.success(emptyList())
        override suspend fun getVideoPages(bvid: String): Result<List<Track>> = Result.success(emptyList())
        override suspend fun getVideoDetail(bvid: String): Result<Track> =
            Result.failure(UnsupportedOperationException("not used"))
    }

    private fun stream(url: String, qualityId: Int = AudioQuality.Q192.id) =
        AudioStream(url = url, qualityId = qualityId, mimeType = "audio/mp4")

    @Test
    fun `resolves and caches per track and quality`() = runTest {
        val repo = FakeMusicRepository { _, _ -> Result.success(stream("https://cdn/1.m4s")) }
        val resolver = BiliStreamResolver(repo, now = { 1_000L })

        assertEquals("https://cdn/1.m4s", resolver.resolve(track, AudioQuality.Q192).url)
        assertEquals("https://cdn/1.m4s", resolver.resolve(track, AudioQuality.Q192).url)
        assertEquals(1, repo.audioCalls)
        assertEquals(1, resolver.size)
        assertNotNull(resolver.cached(track, AudioQuality.Q192))
        assertNull(resolver.cached(track, AudioQuality.Q128))
    }

    @Test
    fun `cache expires after ttl`() = runTest {
        var now = 0L
        var counter = 0
        val repo = FakeMusicRepository { _, _ ->
            counter += 1
            Result.success(stream("https://cdn/$counter.m4s"))
        }
        val resolver = BiliStreamResolver(repo, ttlMs = 1_000L, now = { now })

        assertEquals("https://cdn/1.m4s", resolver.resolve(track, null).url)
        now = 999L
        assertEquals("https://cdn/1.m4s", resolver.resolve(track, null).url)
        now = 1_000L
        assertEquals("https://cdn/2.m4s", resolver.resolve(track, null).url)
        assertEquals(2, repo.audioCalls)
    }

    @Test
    fun `force refresh bypasses a fresh cache entry`() = runTest {
        var counter = 0
        val repo = FakeMusicRepository { _, _ ->
            counter += 1
            Result.success(stream("https://cdn/$counter.m4s"))
        }
        val resolver = BiliStreamResolver(repo, now = { 0L })
        resolver.resolve(track, null)
        resolver.resolve(track, null, forceRefresh = true)
        assertEquals(2, repo.audioCalls)
        assertEquals("https://cdn/2.m4s", resolver.cached(track, null)!!.url)
    }

    @Test
    fun `requested quality failure falls back to highest available`() = runTest {
        val repo = FakeMusicRepository { _, quality ->
            if (quality == AudioQuality.Q64) {
                Result.failure(AppException("该音质不可用"))
            } else {
                Result.success(stream("https://cdn/highest.m4s", AudioQuality.Q192.id))
            }
        }
        val resolver = BiliStreamResolver(repo, now = { 0L })

        val resolved = resolver.resolve(track, AudioQuality.Q64)
        assertEquals("https://cdn/highest.m4s", resolved.url)
        assertEquals(listOf(AudioQuality.Q64, null), repo.requestedQualities)
        // the fallback stream is cached under the requested slot
        assertNotNull(resolver.cached(track, AudioQuality.Q64))
    }

    @Test
    fun `failure surfaces an AppException with a chinese message`() = runTest {
        val repo = FakeMusicRepository { _, _ ->
            Result.failure(IOException("connection reset"))
        }
        val resolver = BiliStreamResolver(repo, now = { 0L })
        try {
            resolver.resolve(track, null)
            fail("expected AppException")
        } catch (error: AppException) {
            assertTrue(error.userMessage.isNotBlank())
            assertTrue(error.retryable)
        }
    }

    @Test
    fun `invalidate forces a fresh resolution`() = runTest {
        var counter = 0
        val repo = FakeMusicRepository { _, _ ->
            counter += 1
            Result.success(stream("https://cdn/$counter.m4s"))
        }
        val resolver = BiliStreamResolver(repo, now = { 0L })
        val first = resolver.resolve(track, null)
        resolver.invalidate(track.key)
        assertNull(resolver.cached(track, null))
        val second = resolver.resolve(track, null)
        assertEquals(2, repo.audioCalls)
        assertTrue(first.url != second.url)
    }

    @Test
    fun `cache keys differ per quality`() {
        val resolver = BiliStreamResolver(
            FakeMusicRepository { _, _ -> Result.success(stream("https://cdn")) },
            now = { 0L },
        )
        assertEquals("BV1:99@0", resolver.cacheKey("BV1:99", null))
        assertEquals("BV1:99@30216", resolver.cacheKey("BV1:99", AudioQuality.Q64))
        assertEquals("BV1:99@30232", resolver.cacheKey("BV1:99", AudioQuality.Q128))
    }
}
