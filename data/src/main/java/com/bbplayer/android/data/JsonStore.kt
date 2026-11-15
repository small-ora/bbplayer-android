package com.bbplayer.android.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 原子 JSON 文件存储。
 *
 * 写入流程：先写 `xxx.json.tmp`，再通过 rename 覆盖正式文件，
 * 避免进程被杀/掉电时留下半截 JSON；读取时解析失败视为空数据（不崩溃）。
 */
class JsonFileStore<T>(
    val file: File,
    private val serializer: KSerializer<T>,
    private val json: Json = StoreJson,
) {

    fun read(): T? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            // 文件损坏时按空数据处理，下次写入会自动覆盖。
            null
        }
    }

    fun write(value: T) {
        val target = file.absoluteFile
        val parent = target.parentFile ?: error("无法确定文件目录: $target")
        if (!parent.exists()) parent.mkdirs()
        val temp = File(parent, "${target.name}.tmp")
        temp.writeText(json.encodeToString(serializer, value))
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun delete() {
        runCatching { if (file.exists()) file.delete() }
        runCatching { File(file.absoluteFile.parentFile, "${file.name}.tmp").delete() }
    }

    companion object {
        internal inline fun <reified T> create(file: File, json: Json = StoreJson): JsonFileStore<T> =
            JsonFileStore(file, serializer<T>(), json)
    }
}
