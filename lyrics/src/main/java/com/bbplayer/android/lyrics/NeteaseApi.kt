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
 * Lyric:  `GET https://music.163.com/api/song/lyric?id=<id>&lv=1&kv=1&tv=-1&yv=-1`;
 * the translation lives in the separate `tlyric.lyric` field and is merged into the main LRC by the parser.
 * With `yv=-1` the response may additionally carry a verbatim `yrc.lyric` track (per-word timings) which wins over
 * the plain LRC when it parses successfully.
 */
internal object NeteaseApi {

    const val SOURCE = "netease"
    const val REFERER = "https://music.163.com"

    private const val SEARCH_ENDPOINT = "https://music.163.com/api/search/get/web"
    private const val LYRIC_ENDPOINT = "https://music.163.com/api/song/lyric"

    fun searchUrl(query: String): String =
        "$SEARCH_ENDPOINT?s=${urlEncode(query)}&type=1&limit=10&offset=0"

    fun lyricUrl(songId: String): String =
        "$LYRIC_ENDPOINT?id=${urlEncode(songId)}&lv=1&kv=1&tv=-1&yv=-1"

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

    /**
     * Parses `lrc.lyric`, the optional verbatim `yrc.lyric` (also accepted as a bare JSON string) and the translation
     * (`ytlrc`, falling back to `tlyric`). Returns null when the response has no usable lyric at all.
     *
     * Note: `romalrc`/`yromalrc` (romanisation) are not merged into the frozen core model — see the v2 lyrics
     * hand-off notes; callers can request them separately if the model gains a field.
     */
    fun parseLyric(body: String): LyricPayload? {
        val root = LyricsJson.parseObject(body) ?: return null
        val yrcElement = root["yrc"]
        val yrc = (yrcElement.asObjectOrNull()?.get("lyric").asTextOrNull() ?: yrcElement.asTextOrNull())
            ?.takeIf { it.isNotBlank() }
        val lyric = root["lrc"].asObjectOrNull()?.get("lyric").asTextOrNull()?.takeIf { it.isNotBlank() }
        if (lyric == null && yrc == null) return null
        val translation = (
            root["ytlrc"].asObjectOrNull()?.get("lyric").asTextOrNull()?.takeIf { it.isNotBlank() }
                ?: root["tlyric"].asObjectOrNull()?.get("lyric").asTextOrNull()?.takeIf { it.isNotBlank() }
            )
        return LyricPayload(lrc = lyric.orEmpty(), translation = translation, yrc = yrc)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
