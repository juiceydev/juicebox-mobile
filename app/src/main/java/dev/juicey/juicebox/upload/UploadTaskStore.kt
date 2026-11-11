package dev.juicey.juicebox.upload

import dev.juicey.juicebox.core.JuiceboxJson
import dev.juicey.juicebox.core.nowEpochSeconds
import dev.juicey.juicebox.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

object TaskKind {
    const val FILE = "file"
    const val FETCH = "fetch"
}

object TaskStatus {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

@Serializable
data class UploadTask(
    val localId: String,
    val kind: String = TaskKind.FILE,
    /** Cached copy of the payload; removed once the upload finishes. */
    val cachePath: String? = null,
    val filename: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val ttlHours: Double = 24.0,
    val host: String? = null,
    val password: String? = null,
    val quickLink: Boolean = true,
    /** Cobalt options for fetched media. */
    val audioOnly: Boolean = false,
    val videoQuality: String = "max",
    val videoContainer: String = "auto",
    val audioFormat: String = "best",
    val betterAudio: Boolean = true,
    /** Where the library entry points back to, for previews. */
    val sourceUri: String? = null,
    val fetchUrl: String? = null,
    val createdAt: Long = 0,
    val status: String = TaskStatus.QUEUED,
    val error: String? = null,
    val resultLink: String? = null,
    val resultRawLink: String? = null,
    val resultDownloadLink: String? = null,
    val resultDeleteToken: String? = null,
    /** Library entry this task created, so the session card can delete it. */
    val resultLocalId: String? = null,
) {
    val isTerminal: Boolean
        get() = status == TaskStatus.DONE ||
            status == TaskStatus.FAILED ||
            status == TaskStatus.CANCELLED
}

/**
 * Queue survives process death so a killed share upload resumes when the
 * service restarts; the payload is already in the app cache.
 */
class UploadTaskStore(private val store: KeyValueStore) {

    private val mutex = Mutex()
    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val tasks: StateFlow<List<UploadTask>> = _tasks.asStateFlow()

    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = store.read(KEY) ?: return
        runCatching {
            _tasks.value = JuiceboxJson.decodeFromString<List<UploadTask>>(raw)
        }
    }

    fun snapshot(): List<UploadTask> {
        ensureLoaded()
        return _tasks.value
    }

    fun active(): List<UploadTask> = snapshot()
        .filter { it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING }

    suspend fun put(task: UploadTask) = mutex.withLock {
        ensureLoaded()
        val next = _tasks.value.filterNot { it.localId == task.localId } + task
        commit(next)
    }

    suspend fun update(localId: String, transform: (UploadTask) -> UploadTask) = mutex.withLock {
        ensureLoaded()
        commit(_tasks.value.map { if (it.localId == localId) transform(it) else it })
    }

    suspend fun remove(localId: String) = mutex.withLock {
        ensureLoaded()
        commit(_tasks.value.filterNot { it.localId == localId })
    }

    /** Results stay reachable for a day, then the library is the only copy. */
    suspend fun clearTerminal(nowSeconds: Long = nowEpochSeconds()) = mutex.withLock {
        ensureLoaded()
        commit(_tasks.value.filterNot { it.isTerminal && nowSeconds - it.createdAt > 86_400 })
    }

    private fun commit(next: List<UploadTask>) {
        _tasks.value = next
        store.write(KEY, JuiceboxJson.encodeToString(next))
    }

    fun newTaskId(): String =
        "task-${nowEpochSeconds()}-${(1000..9999).random()}"

    private companion object {
        const val KEY = "upload_tasks"
    }
}
