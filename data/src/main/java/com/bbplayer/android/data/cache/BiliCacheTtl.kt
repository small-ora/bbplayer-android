package com.bbplayer.android.data.cache

/**
 * 接口缓存 TTL 表（唯一事实来源，改策略只改这里）。
 *
 * | 接口 | key 组成 | TTL |
 * |---|---|---|
 * | `search/type` | keyword + page + page_size | 10 分钟 |
 * | `view` | bvid / aid | 24 小时 |
 * | `pagelist` | bvid | 24 小时 |
 * | `playurl` | bvid + cid + quality | 30 分钟 |
 * | `ranking/v2` | rid + ps | 10 分钟 |
 * | `archive/related` | bvid | 30 分钟 |
 * | `search/square` | limit | 30 分钟 |
 * | `suggest`（s.search.bilibili.com） | term | 5 分钟 |
 * | `reply` / `reply/reply` | oid + pn + ps + sort / root | 5 分钟 |
 * | `player/v2`（字幕列表）+ 字幕 JSON | bvid + cid / 字幕 URL | 24 小时 |
 * | b23.tv 短链解析 | 规范化短链 URL | 24 小时 |
 * | 网易云 / QQ 歌单解析 | 歌单 URL / ID | 30 分钟 |
 * | GitHub Releases 更新检查 | endpoint | 10 分钟 |
 */
internal object BiliCacheTtl {

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE

    const val SEARCH = 10 * MINUTE
    const val VIEW = 24 * HOUR
    const val PAGELIST = 24 * HOUR
    const val PLAYURL = 30 * MINUTE
    const val RANKING = 10 * MINUTE
    const val RELATED = 30 * MINUTE
    const val SUGGEST = 5 * MINUTE
    const val SEARCH_SQUARE = 30 * MINUTE
    const val COMMENT = 5 * MINUTE
    const val SUBTITLE = 24 * HOUR
    const val SHORT_LINK = 24 * HOUR
    const val EXTERNAL_PLAYLIST = 30 * MINUTE
    const val GITHUB_UPDATE = 10 * MINUTE

    /** B 站 Web API 路径 → TTL；返回 null 表示该接口不缓存（写操作 / 账号相关）。 */
    fun forBiliPath(path: String): Long? = when ("/" + path.trimStart('/')) {
        "/x/web-interface/search/type" -> SEARCH
        "/x/web-interface/view" -> VIEW
        "/x/player/pagelist" -> PAGELIST
        "/x/player/playurl" -> PLAYURL
        "/x/web-interface/ranking/v2" -> RANKING
        "/x/web-interface/archive/related" -> RELATED
        "/x/web-interface/search/square" -> SEARCH_SQUARE
        "/x/player/v2" -> SUBTITLE
        "/x/v2/reply", "/x/v2/reply/reply" -> COMMENT
        else -> null
    }

    /** 外站 / 跨域 URL → TTL；返回 null 表示不缓存。 */
    fun forUrl(url: String): Long? = when {
        url.contains("s.search.bilibili.com/main/suggest") -> SUGGEST
        url.contains("api.github.com") || url.contains("github.com/repos/") -> GITHUB_UPDATE
        url.contains("music.163.com") -> EXTERNAL_PLAYLIST
        url.contains("c.y.qq.com") || url.contains("u.y.qq.com") -> EXTERNAL_PLAYLIST
        url.contains("163cn.tv") -> SHORT_LINK
        url.contains("hdslb.com") -> SUBTITLE
        else -> null
    }
}
