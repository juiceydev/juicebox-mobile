package dev.juicey.juicebox.core.storage

import dev.juicey.juicebox.core.JuiceboxJson
import dev.juicey.juicebox.core.model.ClientFileRecord
import dev.juicey.juicebox.core.model.LibraryFile
import dev.juicey.juicebox.core.model.LibraryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Local library + the authoritative delete tokens. The server registry never
 * returns tokens, so this file is the only copy that can rename or delete.
 */
class LibraryStore(private val store: KeyValueStore) {

    private val mutex = Mutex()
    private var loaded = false
    private val _files = MutableStateFlow<List<LibraryFile>>(emptyList())
    val files: StateFlow<List<LibraryFile>> = _files.asStateFlow()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = store.read(KEY) ?: return
        runCatching {
            val decoded = JuiceboxJson.decodeFromString<List<LibraryFile>>(raw)
            _files.value = decoded
        }
    }

    private fun persist(files: List<LibraryFile>) {
        store.write(KEY, JuiceboxJson.encodeToString(files))
    }

    fun snapshot(): List<LibraryFile> {
        ensureLoaded()
        return _files.value
    }

    suspend fun load() = mutex.withLock { ensureLoaded() }

    suspend fun find(localId: String): LibraryFile? = mutex.withLock {
        ensureLoaded()
        _files.value.firstOrNull { it.localId == localId }
    }

    suspend fun upsert(file: LibraryFile) = mutex.withLock {
        ensureLoaded()
        val current = _files.value
        val withoutSame = current.filterNot {
            it.localId == file.localId ||
                (file.id.isNotBlank() && it.server == file.server && it.id == file.id)
        }
        val next = withoutSame + file
        _files.value = next
        persist(next)
    }

    suspend fun update(localId: String, transform: (LibraryFile) -> LibraryFile) = mutex.withLock {
        ensureLoaded()
        val next = _files.value.map { if (it.localId == localId) transform(it) else it }
        _files.value = next
        persist(next)
    }

    suspend fun remove(localId: String) = mutex.withLock {
        ensureLoaded()
        val next = _files.value.filterNot { it.localId == localId }
        _files.value = next
        persist(next)
    }

    suspend fun removeWhere(predicate: (LibraryFile) -> Boolean) = mutex.withLock {
        ensureLoaded()
        val next = _files.value.filterNot(predicate)
        _files.value = next
        persist(next)
    }

    /** Merge the server registry without ever dropping a locally-held token. */
    suspend fun mergeServer(
        server: String,
        records: List<ClientFileRecord>,
        publicBase: String = server,
    ) = mutex.withLock {
        ensureLoaded()
        val now = dev.juicey.juicebox.core.nowEpochSeconds()
        val existing = _files.value.filter { it.server == server }
        val byId = existing.associateBy { it.id }
        val merged = existing.toMutableList()

        for (record in records) {
            if (record.id.isBlank()) continue
            val current = byId[record.id]
            if (current != null) {
                val index = merged.indexOfFirst { it.localId == current.localId }
                if (index >= 0) {
                    merged[index] = current.copy(
                        filename = record.n.ifBlank { current.filename },
                        mimeType = record.m.ifBlank { current.mimeType },
                        sizeBytes = if (record.s > 0) record.s else current.sizeBytes,
                        uploadedAt = if (record.u > 0) record.u else current.uploadedAt,
                        expiresAt = if (record.e > 0) record.e else current.expiresAt,
                    )
                }
            } else {
                merged += LibraryFile(
                    localId = newLocalId(),
                    id = record.id,
                    filename = record.n,
                    mimeType = record.m,
                    sizeBytes = record.s,
                    uploadedAt = record.u,
                    expiresAt = record.e,
                    url = "${publicBase.trimEnd('/')}/v/${record.id}",
                    rawUrl = "${publicBase.trimEnd('/')}/f/${record.id}",
                    downloadUrl = "${publicBase.trimEnd('/')}/d/${record.id}",
                    deleteToken = "",
                    server = server,
                    status = LibraryStatus.READY,
                )
            }
        }

        // Expired entries are dead weight; pending ones are kept for retry.
        val kept = merged.filter { file ->
            !file.isExpired(now) || file.isPending || file.status == LibraryStatus.ERROR
        }
        val otherServers = _files.value.filter { it.server != server }
        val next = otherServers + kept.sortedByDescending { it.uploadedAt }
        _files.value = next
        persist(next)
    }

    suspend fun importFiles(files: List<LibraryFile>) = mutex.withLock {
        ensureLoaded()
        val current = _files.value.toMutableList()
        for (file in files) {
            if (file.localId.isBlank()) continue
            val index = current.indexOfFirst {
                it.localId == file.localId || (file.id.isNotBlank() && it.server == file.server && it.id == file.id)
            }
            if (index >= 0) current[index] = file else current += file
        }
        val next = current.sortedByDescending { it.uploadedAt }
        _files.value = next
        persist(next)
    }

    suspend fun clear() = mutex.withLock {
        loaded = true
        _files.value = emptyList()
        persist(emptyList())
    }

    companion object {
        private const val KEY = "library"

        fun newLocalId(): String {
            val chars = "0123456789abcdef"
            val sb = StringBuilder(20)
            repeat(20) { sb.append(chars[Random.nextInt(chars.length)]) }
            return sb.toString()
        }
    }
}
