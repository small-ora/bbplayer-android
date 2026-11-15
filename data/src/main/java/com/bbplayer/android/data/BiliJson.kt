package com.bbplayer.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** B 站接口 JSON 解析器：宽松模式，忽略未知字段，容忍数字/字符串混用。 */
internal val BiliJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** 本地持久化 JSON：保留默认值，便于序列化往返。 */
internal val StoreJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun parseJsonObject(text: String): JsonObject? =
    runCatching { BiliJson.parseToJsonElement(text) as? JsonObject }.getOrNull()

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonArray?.toObjectList(): List<JsonObject> =
    this?.mapNotNull { it as? JsonObject }.orEmpty()

/**
 * 依次尝试多个候选字段名并返回第一个非空字符串。
 * 用于兼容 B 站同时返回 camelCase / snake_case 的情况（baseUrl / base_url）。
 */
internal fun JsonObject.strOrNull(vararg keys: String): String? {
    for (key in keys) {
        val primitive = get(key) as? JsonPrimitive ?: continue
        if (primitive is JsonNull) continue
        primitive.contentOrNull?.let { return it }
    }
    return null
}

internal fun JsonObject.longOrNull(vararg keys: String): Long? {
    for (key in keys) {
        val value = (get(key) as? JsonPrimitive)?.longOrNull
        if (value != null) return value
    }
    return null
}

internal fun JsonObject.intOrNull(vararg keys: String): Int? {
    for (key in keys) {
        val value = (get(key) as? JsonPrimitive)?.intOrNull
        if (value != null) return value
    }
    return null
}

internal fun JsonObject.doubleOrNull(vararg keys: String): Double? {
    for (key in keys) {
        val value = (get(key) as? JsonPrimitive)?.doubleOrNull
        if (value != null) return value
    }
    return null
}

internal fun JsonObject.booleanOrNull(vararg keys: String): Boolean? {
    for (key in keys) {
        val value = (get(key) as? JsonPrimitive)?.booleanOrNull
        if (value != null) return value
    }
    return null
}

internal fun JsonObject.objOrNull(vararg keys: String): JsonObject? {
    for (key in keys) {
        (get(key) as? JsonObject)?.let { return it }
    }
    return null
}

internal fun JsonObject.arrOrNull(vararg keys: String): JsonArray? {
    for (key in keys) {
        (get(key) as? JsonArray)?.let { return it }
    }
    return null
}

/**
 * 读取字符串列表：字段可能是单个字符串（base_url / backup_url）或字符串数组（backupUrl）。
 */
internal fun JsonObject.stringList(vararg keys: String): List<String> {
    val result = mutableListOf<String>()
    for (key in keys) {
        when (val element = get(key)) {
            is JsonArray -> element.forEach { item ->
                (item as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let(result::add)
            }

            is JsonPrimitive -> element.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let(result::add)

            else -> Unit
        }
    }
    return result.distinct()
}
