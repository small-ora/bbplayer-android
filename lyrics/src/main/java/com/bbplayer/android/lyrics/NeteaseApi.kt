package com.bbplayer.android.lyrics

import com.bbplayer.android.core.model.LyricCandidate
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * NetEase Cloud Music anonymous endpoints (read-only, no cookie/login).
 *
 * Search: `GET https://music.163.com/api/search/get/web?s=<query>&type=1&limit=10&offset=0`
 * with `Referer: https://music.163.com`.
 * Lyric:  `GET https://music.163.com/api/song/lyric?id=<id>&lv=1&kv=1&tv=-1`;
 * the translation lives in the separate `tlyric.lyric` field and is merged into the main LRC by the parser.
 */
internal object NeteaseApi {

    const val SOURCE = "netease"
    const val REFERER = "https://music.163.com"

    private const val SEARCH_ENDPOINT = "https://music.163.com/api/search/get/web"
    private const val LYRIC_ENDPOINT = "https://music.163.com/api/song/lyric"

    fun searchUrl(query: String): String =
        "$SEARCH_ENDPOINT?s=${urlEncode(query)}&type=1&limit=10&offset=0"

    fun lyricUrl(songId: String): String = "$LYRIC_ENDPOINT?id=${urlEncode(songId)}&lv=1&kv=1&tv=-1"

    suspend fun search(client: OkHttpClient, query: String): List<LyricCandidate> {
        val body = LyricsHttp.getString(client, searchUrl(query), REFERER)
        return withContext(Dispatchers.Default) { parseSearch(body) }
    }

    suspend fun fetchLyric(client: OkHttpClient, songId: String): LyricPayload {
        val body = LyricsHttp.getString(client, lyricUrl(songId), REFERER)
        return withContext(Dispatchers.Default) { parseLyric(body) }
            ?: throw IOException("网易云未返回歌词")
    }

    /** Parses `result.songs[]{id,name,artists[].name}`; malformed entries are skipped. */
    fun parseSearch(body: String): List<LyricCandidate> {
        val root = LyricsJson.parseObject(body) ?: return emptyList()
        val songs = root["result"].asObjectOrNull()?.get("songs").asArrayOrNull() ?: return emptyList()
        return songs.mapNotNull { element ->
            val song = element.asObjectOrNull() ?: return@mapNotNull null
            val id = song["id"].asLongOrNull() ?: song["id"].asTextOrNull()?.toLongOrNull()
            if (id == null) return@mapNotNull null
            val title = song["name"].asTextOrNull() ?: return@mapNotNull null
            val artist = song["artists"].asArrayOrNull()
                ?.mapNotNull { it.asObjectOrNull()?.get("name").asTextOrNull() }
                ?.filter { it.isNotBlank() }
                ?.joinToString(", ")
                .orEmpty()
            LyricCandidate(
                title = title,
                artist = artist,
                source = SOURCE,
                sourceId = id.toString(),
            )
        }
    }

    /** Parses `lrc.lyric` + `tlyric.lyric`; returns null when there is no main lyric. */
    fun parseLyric(body: String): LyricPayload? {
        val root = LyricsJson.parseObject(body) ?: return null
        val lyric = root["lrc"].asObjectOrNull()?.get("lyric").asTextOrNull()
        if (lyric.isNullOrBlank()) return null
        val translation = root["tlyric"].asObjectOrNull()
            ?.get("lyric").asTextOrNull()
            ?.takeIf { it.isNotBlank() }
        return LyricPayload(lrc = lyric, translation = translation)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
