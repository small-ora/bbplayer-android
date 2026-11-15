package com.bbplayer.android.data.cache

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * HTTP 响应缓存：**内存 LRU + 磁盘持久化**。
 *
 * - 内存：按访问顺序的 [LinkedHashMap]（`accessOrder = true`），容量按条数（默认 512）；
 * - 磁盘：每个 key 一个 `<sha256(key)>.json`，内容为 `{key, expiresAt, value}`，
 *   value 是接口返回 `data` 节点的 JSON 文本；
 * - 写入：先写 `.tmp` 再原子 rename（与 [com.bbplayer.android.data.JsonFileStore] 一致），
 *   掉电/被杀不会留下半截 JSON；
 * - 启动：扫描目录重建索引，**损坏 / 字段缺失的文件直接忽略**，过期条目顺手删除；
 * - 淘汰：条数上限与总字节上限（默认 8MB）双约束，按 LRU 淘汰并删除对应文件；
 * - TTL：`get` 时惰性判断，过期即移除（不返回过期数据）。
 *
 * 所有方法线程安全（内部单锁），磁盘 IO 在同一把锁内完成：缓存条目小、写入频率低
 * （受 [RequestThrottle] 与 [InFlightDeduplicator] 约束），换取实现简单与计数准确。
 *
 * @param directory 缓存目录（生产为 `context.filesDir/api_cache`，测试用 TemporaryFolder）。
 * @param maxEntries 内存/磁盘最大条数。
 * @param maxBytes 磁盘占用上限（字节）。
 * @param clock 可注入时钟，便于测试 TTL。
 */
class HttpCacheStore(
    val directory: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class Entry(
        val key: String,
        val file: File,
        val value: String,
        val expiresAt: Long,
        val bytes: Long,
    )

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {}
    private var totalBytes: Long = 0L

    init {
        require(maxEntries > 0) { "maxEntries must be > 0" }
        require(maxBytes > 0) { "maxBytes must be > 0" }
        synchronized(lock) {
            if (!directory.exists()) directory.mkdirs()
            loadFromDisk()
        }
    }

    /** 命中且未过期返回 value；过期/不存在返回 null。 */
    fun get(key: String): String? = synchronized(lock) {
        val entry = entries[key] ?: return@synchronized null
        if (entry.expiresAt <= clock()) {
            drop(entry)
            return@synchronized null
        }
        entry.value
    }

    /**
     * 写入缓存（TTL 必须为正数）。同一 key 覆盖旧值。
     *
     * 注意：调用方必须只在**成功**拿到结果后调用，错误结果不进缓存。
     */
    fun put(key: String, value: String, ttlMs: Long) {
        require(ttlMs > 0L) { "ttlMs must be > 0" }
        synchronized(lock) {
            entries.remove(key)?.let { old ->
                totalBytes = (totalBytes - old.bytes).coerceAtLeast(0L)
                deleteQuietly(old.file)
            }
            val file = File(directory, CacheKey.fileName(key))
            val expiresAt = clock() + ttlMs
            val payload = encodeEntry(key, value, expiresAt)
            writeAtomically(file, payload)
            val entry = Entry(
                key = key,
                file = file,
                value = value,
                expiresAt = expiresAt,
                bytes = payload.toByteArray(Charsets.UTF_8).size.toLong(),
            )
            entries[key] = entry
            totalBytes += entry.bytes
            trim()
        }
    }

    /** 当前缓存条目数。 */
    fun entryCount(): Int = synchronized(lock) { entries.size }

    /** 当前缓存占用字节数（按写入内容计算，与磁盘文件大小一致）。 */
    fun sizeBytes(): Long = synchronized(lock) { totalBytes }

    /**
     * 清空缓存并删除磁盘文件，返回被清除的**有效条目数**
     * （损坏文件不计入，但会一并清理）。
     */
    fun clear(): Int = synchronized(lock) {
        val removed = entries.size
        entries.values.forEach { deleteQuietly(it.file) }
        entries.clear()
        totalBytes = 0L
        // 顺手清理损坏文件 / 残留 .tmp，保证目录真的干净。
        directory.listFiles()?.forEach { deleteQuietly(it) }
        removed
    }

    // ------------------------------------------------------------ 内部实现

    private fun loadFromDisk() {
        val files = directory.listFiles { file ->
            file.isFile && file.name.endsWith(SUFFIX)
        } ?: return
        // 按最后修改时间升序恢复，近似跨进程 LRU 顺序。
        files.sortedBy { it.lastModified() }.forEach { file ->
            val entry = readEntry(file) ?: return@forEach
            if (entry.expiresAt <= clock()) {
                deleteQuietly(file)
                return@forEach
            }
            entries[entry.key] = entry
            totalBytes += entry.bytes
        }
        trim()
    }

    private fun readEntry(file: File): Entry? = runCatching {
        val root = Json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject ?: return null
        val key = (root["key"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = (root["expiresAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
        val value = (root["value"] as? JsonPrimitive)?.content ?: return null
        Entry(
            key = key,
            file = file,
            value = value,
            expiresAt = expiresAt,
            bytes = file.length(),
        )
    }.getOrNull()

    private fun encodeEntry(key: String, value: String, expiresAt: Long): String {
        val root = JsonObject(
            mapOf(
                "key" to JsonPrimitive(key),
                "expiresAt" to JsonPrimitive(expiresAt),
                "value" to JsonPrimitive(value),
            ),
        )
        return root.toString()
    }

    private fun writeAtomically(target: File, payload: String) {
        val parent = target.absoluteFile.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        val temp = File(parent, target.name + TMP_SUFFIX)
        temp.writeText(payload, Charsets.UTF_8)
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

    private fun trim() {
        while (entries.size > maxEntries || totalBytes > maxBytes) {
            val eldest = entries.entries.firstOrNull() ?: break
            drop(eldest.value)
        }
    }

    private fun drop(entry: Entry) {
        entries.remove(entry.key)
        totalBytes = (totalBytes - entry.bytes).coerceAtLeast(0L)
        deleteQuietly(entry.file)
    }

    private fun deleteQuietly(file: File) {
        runCatching { if (file.exists()) file.delete() }
        runCatching { File(file.absoluteFile.parentFile, file.name + TMP_SUFFIX).delete() }
    }

    companion object {
        /** 默认最大条数。 */
        const val DEFAULT_MAX_ENTRIES = 512

        /** 默认磁盘占用上限：8MB。 */
        const val DEFAULT_MAX_BYTES = 8L * 1024L * 1024L

        private const val SUFFIX = ".json"
        private const val TMP_SUFFIX = ".tmp"
    }
}
