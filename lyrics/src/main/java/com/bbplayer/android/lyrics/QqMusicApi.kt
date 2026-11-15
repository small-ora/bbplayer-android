package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.LyricCandidate
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** A provider lyric payload: original LRC plus an optional separate translation track. */
internal data class LyricPayload(val lrc: String, val translation: String? = null)

/**
 * QQ Music anonymous endpoints (read-only, no cookie/login).
 *
 * Search: `GET https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=10&w=<query>&t=0` with
 * `Referer: https://y.qq.com/`.
 * Lyric:  `GET https://i.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=<mid>&g_tk=5381&format=json&
 *         inCharset=utf8&outCharset=utf-8&nobase64=1` with the same Referer. The response is JSON with `lyric` and
 * (optionally) `trans` fields, HTML-entity escaped (`&#10;` = newline) and sometimes wrapped in a JSONP callback.
 */
internal object QqMusicApi {

    const val SOURCE = "qq"
    const val REFERER = "https://y.qq.com/"

    private const val SEARCH_ENDPOINT = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val LYRIC_ENDPOINT = "https://i.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"

    fun searchUrl(query: String): String =
        "$SEARCH_ENDPOINT?format=json&p=1&n=10&w=${urlEncode(query)}&t=0"

    fun lyricUrl(songMid: String): String =
        "$LYRIC_ENDPOINT?songmid=${urlEncode(songMid)}&g_tk=5381&format=json" +
            "&inCharset=utf8&outCharset=utf-8&nobase64=1"

    suspend fun search(client: OkHttpClient, query: String): List<LyricCandidate> {
        val body = LyricsHttp.getString(client, searchUrl(query), REFERER)
        return withContext(Dispatchers.Default) { parseSearch(body) }
    }

    suspend fun fetchLyric(client: OkHttpClient, songMid: String): LyricPayload {
        val body = LyricsHttp.getString(client, lyricUrl(songMid), REFERER)
        return withContext(Dispatchers.Default) { parseLyric(body) }
            ?: throw IOException("QQ 音乐未返回歌词")
    }

    /** Parses the search response into candidates; malformed entries are skipped. */
    fun parseSearch(body: String): List<LyricCandidate> {
        val root = LyricsJson.parseObject(body) ?: return emptyList()
        val songs = root["data"].asObjectOrNull()
            ?.get("song").asObjectOrNull()
            ?.get("list").asArrayOrNull()
            ?: return emptyList()
        return songs.mapNotNull { element ->
            val song = element.asObjectOrNull() ?: return@mapNotNull null
            val songMid = song["songmid"].asTextOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = song["songname"].asTextOrNull() ?: return@mapNotNull null
            val artist = song["singer"].asArrayOrNull()
                ?.mapNotNull { it.asObjectOrNull()?.get("name").asTextOrNull() }
                ?.filter { it.isNotBlank() }
                ?.joinToString(", ")
                .orEmpty()
            LyricCandidate(
                title = unescapeHtmlEntities(title),
                artist = unescapeHtmlEntities(artist),
                source = SOURCE,
                sourceId = songMid,
            )
        }
    }

    /** Parses the lyric response; returns null when the provider reports an error or has no lyric. */
    fun parseLyric(body: String): LyricPayload? {
        val root = LyricsJson.parseObject(body) ?: return null
        val retCode = root["retcode"].asIntOrNull() ?: root["code"].asIntOrNull()
        if (retCode != null && retCode != 0) return null
        val lyric = root["lyric"].asTextOrNull()?.let(::unescapeHtmlEntities)
        if (lyric.isNullOrBlank()) return null
        val translation = root["trans"].asTextOrNull()
            ?.let(::unescapeHtmlEntities)
            ?.takeIf { it.isNotBlank() }
        return LyricPayload(lrc = lyric, translation = translation)
    }

    /** Decodes the HTML entities QQ uses inside its JSON strings (`&#10;`, `&quot;`, ...). */
    fun unescapeHtmlEntities(text: String): String {
        if (text.isEmpty() || !text.contains('&')) return text
        var current = text
        repeat(2) { // at most two passes, so double-escaped entities are handled without risking infinite loops
            if (!current.contains('&')) return current
            current = current
                .replace("&#10;", "\n")
                .replace("&#13;", "\r")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace(NUMERIC_ENTITY) { match ->
                    match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
                }
                .replace(HEX_ENTITY) { match ->
                    match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
                }
                .replace("&amp;", "&")
        }
        return current
    }

    private val NUMERIC_ENTITY = Regex("""&#(\d{1,7});""")
    private val HEX_ENTITY = Regex("""&#[xX]([0-9a-fA-F]{1,6});""")

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
