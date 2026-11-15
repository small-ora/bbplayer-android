package com.bbplayer.android.data

import android.content.Context
import com.bbplayer.android.core.contracts.ImportTaskRepository
import com.bbplayer.android.core.model.ImportTask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [ImportTaskRepository] 的 JSON 文件实现（`filesDir/import_task.json`）。
 *
 * - 复用 [JsonFileStore] 的原子写：先写 `.tmp` 再 rename，进程被杀不会留下半截 JSON；
 * - [load] 文件不存在 / JSON 损坏时返回 null（降级为空任务，不崩溃）；
 * - [save] 每次写入完整快照，幂等（重复保存同一 task 不会追加数据）；
 * - [save] 的 `updatedAt <= 0` 时用 [clock] 补时间戳，避免 UI 显示 1970；
 * - [clear] 删除正式文件与残留的 `.tmp`。
 *
 * 单实例即可，不暴露 Flow：导入任务只有一个，UI 通过 `load()` 恢复 + 批次结果更新。
 */
class FileImportTaskRepository(
    private val store: JsonFileStore<ImportTask>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ImportTaskRepository {

    /** 便捷构造：由 AppContainer 直接调用。 */
    constructor(context: Context) : this(
        JsonFileStore(File(context.filesDir, FILE_NAME), ImportTask.serializer()),
    )

    /** 测试 / 自定义路径构造。 */
    constructor(file: File, clock: () -> Long = System::currentTimeMillis) : this(
        JsonFileStore(file, ImportTask.serializer()),
        clock,
    )

    private val mutex = Mutex()

    override suspend fun load(): ImportTask? = withContext(io) {
        mutex.withLock { store.read() }
    }

    override suspend fun save(task: ImportTask) {
        withContext(io) {
            mutex.withLock {
                val stamped = if (task.updatedAt <= 0L) {
                    task.copy(updatedAt = clock())
                } else {
                    task
                }
                store.write(stamped)
            }
        }
    }

    override suspend fun clear() {
        withContext(io) {
            mutex.withLock { store.delete() }
        }
    }

    companion object {
        /** 导入任务快照文件名（位于 `context.filesDir` 下）。 */
        const val FILE_NAME = "import_task.json"
    }
}
