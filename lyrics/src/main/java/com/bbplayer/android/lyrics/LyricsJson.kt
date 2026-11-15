package com.bbplayer.android.lyrics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Tolerant JSON helpers shared by the online lyric providers (all provider responses are untrusted). */
internal object LyricsJson {

    private val JSON = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** JSONP wrapper: `MusicJsonCallback({...})` -> `{...}`; plain JSON is returned unchanged. */
    fun stripJsonp(body: String): String {
        val text = body.trim()
        if (text.isEmpty() || text.startsWith("{") || text.startsWith("[")) return text
        val match = Regex("""^[^(]*\((.*)\)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL).find(text)
            ?: return text
        return match.groupValues[1].trim()
    }

    /** Parses a response body into a JSON object; returns null (never throws) for non-JSON/HTML error pages. */
    fun parseObject(body: String): JsonObject? {
        val cleaned = stripJsonp(body)
        if (cleaned.isEmpty()) return null
        return runCatching { JSON.parseToJsonElement(cleaned) as? JsonObject }.getOrNull()
    }
}

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
internal fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray
internal fun JsonElement?.asTextOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
internal fun JsonElement?.asLongOrNull(): Long? = (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
internal fun JsonElement?.asIntOrNull(): Int? = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
