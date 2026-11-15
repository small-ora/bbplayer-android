package com.bbplayer.android.lyrics

import android.content.ContextWrapper
import com.bbplayer.android.core.model.LyricLine
import com.bbplayer.android.core.model.LyricSource
import com.bbplayer.android.core.model.LyricWord
import com.bbplayer.android.core.model.Lyrics
import com.bbplayer.android.core.model.Track
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pure-JVM tests for the v4 repository changes:
 *  - provider preference ([LyricSource.AUTO] / QQ / NETEASE) for `fetch` and `searchCandidates`;
 *  - legacy-cache word backfill in `getCached` (estimated words, one writeback, no-op when already worded).
 *
 * HTTP is faked with an OkHttp interceptor so the real provider URLs / parsers are exercised without network access.
 */
class QqNeteaseLyricsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val track = Track(bvid = "BV1xx411c7mD", cid = 1001, title = "晴天", uploader = "周杰伦")

    private fun repository(
        api: FakeProviderApi,
        store: LyricsCacheStore = InMemoryLyricsCacheStore(),
        estimateWordTiming: Boolean = true,
        preference: LyricSource = LyricSource.AUTO,
    ): QqNeteaseLyricsRepository = QqNeteaseLyricsRepository(
        context = ContextWrapper(null),
        httpClient = OkHttpClient.Builder().addInterceptor(api).build(),
        cacheStore = store,
        offsetStore = InMemoryTrackOffsetStore(),
        clock = { 1_700_000_000_000L },
        estimateWordTiming = estimateWordTiming,
        sourcePreference = { preference },
    )

    // ---- source preference: fetch ----

    @Test
    fun `auto preference tries qq first and stops on success`() = runBlocking {
        val api = FakeProviderApi()
        val result = repository(api).fetch(track)

        assertTrue(result.getOrThrow().source, result.getOrThrow().source.startsWith("qq"))
        assertEquals("qq:estimated", result.getOrThrow().source)
        assertEquals(1, api.count("c.y.qq.com"))
        assertEquals(1, api.count("i.y.qq.com"))
        assertEquals(0, api.count("music.163.com"))
    }

    @Test
    fun `auto preference falls back to netease when qq search fails`() = runBlocking {
        val api = FakeProviderApi().apply { qqSearch = null }
        val result = repository(api).fetch(track)

        assertEquals("netease:estimated", result.getOrThrow().source)
        assertTrue(api.count("c.y.qq.com") > 0)
        assertEquals(1, api.count("/api/search/get/web"))
        assertEquals(1, api.count("/api/song/lyric"))
    }

    @Test
    fun `qq preference only queries qq`() = runBlocking {
        val api = FakeProviderApi()
        val result = repository(api, preference = LyricSource.QQ).fetch(track)

        assertEquals("qq:estimated", result.getOrThrow().source)
        assertEquals(0, api.count("music.163.com"))
        assertTrue(api.count("c.y.qq.com") > 0)
    }

    @Test
    fun `qq preference failure does not fall back to netease`() = runBlocking {
        val api = FakeProviderApi().apply { qqSearch = null }
        val store = InMemoryLyricsCacheStore()
        val result = repository(api, store = store, preference = LyricSource.QQ).fetch(track)

        assertTrue(result.isFailure)
        assertEquals(0, api.count("music.163.com"))
        assertTrue(store.read(track.key) == null)
    }

    @Test
    fun `netease preference only queries netease`() = runBlocking {
        val api = FakeProviderApi()
        val result = repository(api, preference = LyricSource.NETEASE).fetch(track)

        assertEquals("netease:estimated", result.getOrThrow().source)
        assertEquals(0, api.count("y.qq.com"))
        assertTrue(api.count("/api/search/get/web") > 0)
    }

    @Test
    fun `netease preference failure does not fall back to qq`() = runBlocking {
        val api = FakeProviderApi().apply { neteaseSearch = null }
        val result = repository(api, preference = LyricSource.NETEASE).fetch(track)

        assertTrue(result.isFailure)
        assertEquals(0, api.count("y.qq.com"))
    }

    // ---- source preference: searchCandidates ----

    @Test
    fun `auto preference merges qq candidates before netease candidates`() = runBlocking {
        val api = FakeProviderApi()
        val candidates = repository(api).searchCandidates(track).getOrThrow()

        assertEquals(listOf("qq", "netease"), candidates.map { it.source })
        assertEquals(1, api.count("c.y.qq.com"))
        assertEquals(1, api.count("/api/search/get/web"))
    }

    @Test
    fun `qq preference searches only qq`() = runBlocking {
        val api = FakeProviderApi()
        val candidates = repository(api, preference = LyricSource.QQ).searchCandidates(track).getOrThrow()

        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.source == "qq" })
        assertEquals(0, api.count("music.163.com"))
    }

    @Test
    fun `netease preference searches only netease`() = runBlocking {
        val api = FakeProviderApi()
        val candidates = repository(api, preference = LyricSource.NETEASE).searchCandidates(track).getOrThrow()

        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.source == "netease" })
        assertEquals(0, api.count("y.qq.com"))
    }

    @Test
    fun `single provider search failure is reported without querying the other`() = runBlocking {
        val api = FakeProviderApi().apply { qqSearch = null }
        val result = repository(api, preference = LyricSource.QQ).searchCandidates(track)

        assertTrue(result.isFailure)
        assertEquals(0, api.count("music.163.com"))
    }

    // ---- legacy cache backfill in getCached ----

    @Test
    fun `legacy cache without words gains estimated words and marker on read`() = runBlocking {
        val api = FakeProviderApi()
        val store = RecordingCacheStore()
        store.write(legacyEntry())

        val cached = repository(api, store = store).getCached(track.key)

        assertNotNull(cached)
        assertTrue(cached!!.lines.all { it.words.isNotEmpty() })
        assertEquals("qq:estimated", cached.source)
        assertTrue(EstimatedTiming.isEstimated(cached))
        assertTrue("getCached must not touch the network", api.requested.isEmpty())
    }

    @Test
    fun `legacy cache backfill is written back only once`() = runBlocking {
        val store = RecordingCacheStore()
        store.write(legacyEntry())
        store.resetCounts()
        val repo = repository(FakeProviderApi(), store = store)

        val first = repo.getCached(track.key)
        val second = repo.getCached(track.key)

        assertEquals(first, second)
        assertTrue(first!!.lines.all { it.words.isNotEmpty() })
        assertEquals(2, store.reads)
        assertEquals(1, store.writes)

        val persisted = store.read(track.key)!!.lyrics
        assertTrue(persisted.lines.all { it.words.isNotEmpty() })
        assertEquals("qq:estimated", persisted.source)
    }

    @Test
    fun `legacy backfill is persisted for a fresh store instance`() = runBlocking {
        val dir = temporaryFolder.newFolder("legacy")
        val fileStore = FileLyricsCacheStore(dir)
        fileStore.write(legacyEntry())

        repository(FakeProviderApi(), store = fileStore).getCached(track.key)

        val reopened = repository(FakeProviderApi(), store = FileLyricsCacheStore(dir)).getCached(track.key)
        assertNotNull(reopened)
        assertTrue(reopened!!.lines.all { it.words.isNotEmpty() })
        assertEquals("qq:estimated", reopened.source)
    }

    @Test
    fun `cache that already has words is returned untouched`() = runBlocking {
        val worded = Lyrics(
            lines = listOf(
                LyricLine(
                    startMs = 1_000L,
                    endMs = 3_000L,
                    text = "晴天",
                    words = listOf(LyricWord(1_000L, 3_000L, "晴天")),
                ),
            ),
            source = "qq",
        )
        val store = RecordingCacheStore()
        store.write(CachedLyrics(trackKey = track.key, lyrics = worded, source = "qq"))
        store.resetCounts()

        val cached = repository(FakeProviderApi(), store = store).getCached(track.key)

        assertSame(worded, cached)
        assertEquals("qq", cached!!.source)
        assertEquals(0, store.writes)
    }

    @Test
    fun `cache already marked estimated is not backfilled twice`() = runBlocking {
        val marked = legacyLyrics().copy(source = "qq:estimated")
        val store = RecordingCacheStore()
        store.write(CachedLyrics(trackKey = track.key, lyrics = marked, source = "qq:estimated"))
        store.resetCounts()

        val cached = repository(FakeProviderApi(), store = store).getCached(track.key)

        assertSame(marked, cached)
        assertTrue(cached!!.lines.all { it.words.isEmpty() })
        assertEquals(0, store.writes)
    }

    @Test
    fun `estimation disabled leaves legacy cache untouched`() = runBlocking {
        val legacy = legacyLyrics()
        val store = RecordingCacheStore()
        store.write(CachedLyrics(trackKey = track.key, lyrics = legacy, source = "qq"))
        store.resetCounts()

        val cached = repository(FakeProviderApi(), store = store, estimateWordTiming = false).getCached(track.key)

        assertSame(legacy, cached)
        assertTrue(cached!!.lines.all { it.words.isEmpty() })
        assertEquals("qq", cached.source)
        assertEquals(0, store.writes)
    }

    @Test
    fun `cache json round trip keeps estimated words`() {
        val store = FileLyricsCacheStore(temporaryFolder.newFolder("round-trip"))
        val lyrics = legacyLyrics().let(EstimatedTiming::apply)
        store.write(CachedLyrics(trackKey = track.key, lyrics = lyrics, source = "qq:estimated"))

        val restored = store.read(track.key)!!.lyrics

        assertEquals("qq:estimated", restored.source)
        assertEquals(lyrics.lines.size, restored.lines.size)
        assertTrue(restored.lines.all { it.words.isNotEmpty() })
        assertEquals(lyrics.lines[0].words, restored.lines[0].words)
    }

    @Test
    fun `default constructor keeps auto preference`() = runBlocking {
        val api = FakeProviderApi().apply { qqSearch = null }
        val repo = QqNeteaseLyricsRepository(
            context = ContextWrapper(null),
            httpClient = OkHttpClient.Builder().addInterceptor(api).build(),
            cacheStore = InMemoryLyricsCacheStore(),
            offsetStore = InMemoryTrackOffsetStore(),
        )

        val result = repo.fetch(track)

        assertEquals("netease:estimated", result.getOrThrow().source)
        assertEquals(0, api.count("i.y.qq.com"))
    }

    // ---- fixtures ----

    private fun legacyLyrics(source: String = "qq"): Lyrics =
        LrcParser.parse("[00:01.00]晴天\n[00:03.00]故事的小黄花", source = source)

    private fun legacyEntry(): CachedLyrics =
        CachedLyrics(trackKey = track.key, lyrics = legacyLyrics(), source = "qq", fetchedAt = 1L)

    /** In-memory store that counts reads/writes so the one-time backfill writeback can be asserted. */
    private class RecordingCacheStore(
        private val delegate: LyricsCacheStore = InMemoryLyricsCacheStore(),
    ) : LyricsCacheStore {
        var reads = 0
        var writes = 0

        fun resetCounts() {
            reads = 0
            writes = 0
        }

        override fun read(trackKey: String): CachedLyrics? {
            reads += 1
            return delegate.read(trackKey)
        }

        override fun write(entry: CachedLyrics) {
            writes += 1
            delegate.write(entry)
        }

        override fun clear() = delegate.clear()

        override fun sizeBytes(): Long = delegate.sizeBytes()
    }

    /** Routes real provider URLs to canned JSON; `null` means the request fails like a network error. */
    private class FakeProviderApi : Interceptor {
        val requested = CopyOnWriteArrayList<String>()

        @Volatile var qqSearch: String? = QQ_SEARCH
        @Volatile var qqLyric: String? = QQ_LYRIC
        @Volatile var qqLyricInfo: String? = QQ_LYRIC_INFO
        @Volatile var neteaseSearch: String? = NETEASE_SEARCH
        @Volatile var neteaseLyric: String? = NETEASE_LYRIC

        fun count(fragment: String): Int = requested.count { it.contains(fragment) }

        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url.toString()
            requested += url
            val body = when {
                url.startsWith("https://c.y.qq.com") -> qqSearch
                url.startsWith("https://i.y.qq.com") -> qqLyric
                url.startsWith("https://u.y.qq.com") -> qqLyricInfo
                url.startsWith("https://music.163.com/api/search/get/web") -> neteaseSearch
                url.startsWith("https://music.163.com/api/song/lyric") -> neteaseLyric
                else -> null
            } ?: throw IOException("fake network failure: $url")
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody(JSON))
                .build()
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        const val QQ_SEARCH =
            """{"data":{"song":{"list":[{"songmid":"qq-mid-1","songname":"晴天","singer":[{"name":"周杰伦"}]}]}}}"""
        const val QQ_LYRIC =
            """{"retcode":0,"lyric":"[00:01.00]晴天&#10;[00:03.00]故事的小黄花"}"""
        const val QQ_LYRIC_INFO = """{"code":0,"req":{"code":0,"data":{}}}"""
        const val NETEASE_SEARCH =
            """{"result":{"songs":[{"id":9273,"name":"晴天","artists":[{"name":"周杰伦"}]}]}}"""
        const val NETEASE_LYRIC =
            """{"lrc":{"lyric":"[00:01.00]晴天\n[00:03.00]故事的小黄花"}}"""
    }
}
