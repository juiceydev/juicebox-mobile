package dev.juicey.juicebox.ui.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.juicey.juicebox.AppContainer
import dev.juicey.juicebox.core.ConnectionState
import dev.juicey.juicebox.core.friendlyMessage
import dev.juicey.juicebox.core.model.LinkVariant
import dev.juicey.juicebox.core.model.ServerConfig
import dev.juicey.juicebox.core.model.SiteAnnouncement
import dev.juicey.juicebox.core.nowEpochSeconds
import dev.juicey.juicebox.data.UriUploadSource
import dev.juicey.juicebox.ui.formatBytes
import dev.juicey.juicebox.upload.TaskKind
import dev.juicey.juicebox.upload.TaskStatus
import dev.juicey.juicebox.upload.UploadQueue
import dev.juicey.juicebox.upload.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
)

data class SessionItem(
    val id: String,
    val name: String,
    val status: String,
    val fraction: Float,
    val speedBytes: Long,
    val sizeBytes: Long,
    val link: String?,
    val rawLink: String?,
    val downloadLink: String?,
    val deleteToken: String?,
    val libraryId: String?,
    val error: String?,
)

data class UploadUiState(
    val config: ServerConfig? = null,
    val connection: ConnectionState = ConnectionState(),
    val mode: Int = 0,
    val ttls: List<Double> = listOf(0.5, 1.0, 6.0, 12.0, 24.0, 72.0, 168.0),
    val ttlHours: Double = 24.0,
    val selected: List<PickedFile> = emptyList(),
    val text: String = "",
    val videoUrl: String = "",
    val audioOnly: Boolean = false,
    val quality: String = "max",
    val container: String = "auto",
    val betterAudio: Boolean = true,
    val audioFormat: String = "best",
    val linkVariant: String = LinkVariant.PREVIEW,
    val lockUploads: Boolean = false,
    val announcement: SiteAnnouncement? = null,
    val serverUrl: String = "https://box.juicey.dev",
    val busy: Boolean = false,
    val error: String? = null,
    val items: List<SessionItem> = emptyList(),
) {
    val maxFileSize: Long get() = config?.maxFileSizeBytes ?: (500L * 1024 * 1024)
    val canVideo: Boolean get() = config?.cobalt == true
    val uploading: Boolean get() = items.any { it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING }
}

class UploadViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state.asStateFlow()

    private var tracked = mutableSetOf<String>()
    private var ttlTouched = false
    private var wasConnected = false

    init {
        viewModelScope.launch {
            container.core.config.collect { config ->
                if (config != null) {
                    _state.update { current ->
                        val stored = container.core.settings.value.lastTtlHours
                        val ttl = when {
                            ttlTouched -> current.ttlHours
                            stored != null && stored in config.allowedTtlHours -> stored
                            config.allowedTtlHours.contains(config.defaultTtlHours) -> config.defaultTtlHours
                            else -> current.ttlHours
                        }
                        current.copy(config = config, ttls = config.allowedTtlHours, ttlHours = ttl)
                    }
                }
            }
        }
        viewModelScope.launch {
            container.core.connection.collect { connection ->
                _state.update { it.copy(connection = connection) }
                if (connection.connected && !wasConnected) {
                    wasConnected = true
                    container.core.refreshAnnouncement()
                }
            }
        }
        viewModelScope.launch {
            container.core.announcement.collect { announcement ->
                _state.update { it.copy(announcement = announcement) }
            }
        }
        viewModelScope.launch {
            container.core.settings.collect { settings ->
                _state.update {
                    it.copy(
                        linkVariant = settings.linkVariant,
                        lockUploads = settings.lockUploads && settings.uploadPassword.isNotBlank(),
                        serverUrl = container.core.currentServer(),
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(container.tasks.tasks, container.runner.progress) { tasks, progress ->
                val now = nowEpochSeconds()
                tasks.filter { task ->
                    tracked.contains(task.localId) ||
                        task.status == TaskStatus.QUEUED ||
                        task.status == TaskStatus.RUNNING ||
                        now - task.createdAt < RECENT_SECONDS
                }.sortedByDescending { it.createdAt }.map { task ->
                    val p = progress[task.localId]
                    SessionItem(
                        id = task.localId,
                        name = task.filename.ifBlank { "Upload" },
                        status = task.status,
                        fraction = p?.fraction ?: if (task.status == TaskStatus.DONE) 1f else 0f,
                        speedBytes = p?.bytesPerSecond ?: 0,
                        sizeBytes = task.sizeBytes,
                        link = task.resultLink,
                        rawLink = task.resultRawLink,
                        downloadLink = task.resultDownloadLink,
                        deleteToken = task.resultDeleteToken,
                        libraryId = task.resultLocalId,
                        error = task.error,
                    )
                }
            }.collect { items ->
                _state.update { it.copy(items = items) }
            }
        }
        refreshConnection()
        applyDefaults()
    }

    private fun applyDefaults() {
        val settings = container.core.settings.value
        val config = container.core.config.value
        val ttl = config?.let { cfg ->
            settings.lastTtlHours?.takeIf { it in cfg.allowedTtlHours } ?: cfg.defaultTtlHours
        } ?: 24.0
        _state.update { it.copy(ttlHours = ttl) }
    }

    fun refreshConnection() {
        viewModelScope.launch {
            runCatching { container.core.connect() }
        }
    }

    fun setMode(index: Int) {
        _state.update { it.copy(mode = index, error = null) }
    }

    fun setTtl(hours: Double) {
        ttlTouched = true
        _state.update { it.copy(ttlHours = hours) }
        container.settingsStore.update { it.copy(lastTtlHours = hours) }
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val picked = uris.mapNotNull { uri ->
            runCatching { UriUploadSource.from(container.appContext.contentResolver, uri) }
                .getOrNull()
                ?.let { PickedFile(uri, it.filename, it.size, it.mimeType) }
        }
        _state.update { current ->
            val room = current.maxFileSize
            val tooBig = picked.firstOrNull { it.size > room }
            if (tooBig != null && tooBig.size > 0) {
                current.copy(
                    error = "${tooBig.name} is larger than ${formatBytes(room)}",
                )
            } else {
                val existing = current.selected.map { it.uri.toString() }.toSet()
                current.copy(
                    selected = current.selected + picked.filterNot { it.uri.toString() in existing },
                    error = null,
                )
            }
        }
    }

    fun removeFile(uri: Uri) {
        _state.update { it.copy(selected = it.selected.filterNot { f -> f.uri == uri }) }
    }

    fun setText(value: String) {
        _state.update { it.copy(text = value) }
    }

    fun setVideoUrl(value: String) {
        _state.update { it.copy(videoUrl = value) }
    }

    fun setAudioOnly(value: Boolean) {
        _state.update { it.copy(audioOnly = value) }
    }

    fun setQuality(value: String) {
        _state.update { it.copy(quality = value) }
    }

    fun setContainer(value: String) {
        _state.update { it.copy(container = value) }
    }

    fun setBetterAudio(value: Boolean) {
        _state.update { it.copy(betterAudio = value) }
    }

    fun setAudioFormat(value: String) {
        _state.update { it.copy(audioFormat = value) }
    }

    fun setLinkVariant(value: String) {
        _state.update { it.copy(linkVariant = value) }
        container.settingsStore.update { it.copy(linkVariant = value) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun upload() {
        val snapshot = _state.value
        if (snapshot.busy) return

        when (snapshot.mode) {
            0 -> if (snapshot.selected.isEmpty()) return
            1 -> if (snapshot.videoUrl.isBlank()) return
            else -> if (snapshot.text.isBlank()) return
        }

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val created = withContext(Dispatchers.IO) { buildTasks(snapshot) }
                tracked += created.map { it.localId }
                UploadQueue.enqueueAll(container.appContext, created.map { it.localId })
                _state.update { it.copy(busy = false, selected = emptyList(), text = "", videoUrl = "") }
            } catch (error: Throwable) {
                _state.update { it.copy(busy = false, error = error.friendlyMessage()) }
            }
        }
    }

    private suspend fun buildTasks(snapshot: UploadUiState): List<UploadTask> {
        val factory = container.taskFactory
        val host = container.core.settings.value.storageHost
        val tasks = mutableListOf<UploadTask>()

        when (snapshot.mode) {
            0 -> for (file in snapshot.selected) {
                tasks += factory.fromUri(
                    uri = file.uri,
                    ttlHours = snapshot.ttlHours,
                    displayName = file.name,
                    mimeType = file.mimeType,
                    host = host,
                )
            }

            1 -> tasks += factory.fromFetch(
                url = snapshot.videoUrl,
                ttlHours = snapshot.ttlHours,
                audioOnly = snapshot.audioOnly,
                videoQuality = snapshot.quality,
                videoContainer = snapshot.container,
                audioFormat = snapshot.audioFormat,
                betterAudio = snapshot.betterAudio,
            )

            else -> tasks += factory.fromText(
                text = snapshot.text + "\n",
                ttlHours = snapshot.ttlHours,
                host = host,
            )
        }
        return tasks
    }


    fun retry(localId: String) {
        viewModelScope.launch {
            container.tasks.update(localId) {
                it.copy(status = TaskStatus.QUEUED, error = null)
            }
            UploadQueue.retry(container.appContext, localId)
        }
    }

    fun copyLink(link: String): Boolean = container.runner.copyLink(link)

    fun deleteSession(item: SessionItem) {
        viewModelScope.launch {
            val file = item.libraryId?.let { id ->
                container.core.library.value.firstOrNull { it.localId == id }
            } ?: container.core.library.value.firstOrNull {
                (item.link != null && it.url == item.link) || (item.rawLink != null && it.rawUrl == item.rawLink)
            }
            runCatching { if (file != null) container.core.deleteFile(file.localId) }
                .onFailure { error ->
                    _state.update { it.copy(error = error.friendlyMessage()) }
                    return@launch
                }
            runCatching { container.tasks.remove(item.id) }
            tracked.remove(item.id)
        }
    }

    private companion object {
        /** Finished uploads stay in the session list for this long. */
        const val RECENT_SECONDS = 15 * 60
    }
}
