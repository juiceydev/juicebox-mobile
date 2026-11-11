package dev.juicey.juicebox.core

import dev.juicey.juicebox.core.model.AppSettings
import dev.juicey.juicebox.core.model.ClientFileRecord
import dev.juicey.juicebox.core.model.FetchFileResponse
import dev.juicey.juicebox.core.model.FetchStartRequest
import dev.juicey.juicebox.core.model.FetchStatus
import dev.juicey.juicebox.core.model.HostValidation
import dev.juicey.juicebox.core.model.LibraryExport
import dev.juicey.juicebox.core.model.LibraryFile
import dev.juicey.juicebox.core.model.LibraryStatus
import dev.juicey.juicebox.core.model.RenewResponse
import dev.juicey.juicebox.core.model.ServerConfig
import dev.juicey.juicebox.core.model.SiteAnnouncement
import dev.juicey.juicebox.core.model.UploadResponse
import dev.juicey.juicebox.core.model.normalizeServerUrl
import dev.juicey.juicebox.core.net.ApiException
import dev.juicey.juicebox.core.net.JuiceboxApi
import dev.juicey.juicebox.core.net.PersistentCookieStorage
import dev.juicey.juicebox.core.storage.LibraryStore
import dev.juicey.juicebox.core.storage.SettingsStore
import dev.juicey.juicebox.core.upload.UploadEngine
import dev.juicey.juicebox.core.upload.UploadResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val error: String? = null,
    val checkedAt: Long = 0,
)

/**
 * Facade over the API, session, settings and library. One instance per app
 * process; everything Android-specific is injected through the stores.
 */
class JuiceboxCore(
    val api: JuiceboxApi,
    val cookies: PersistentCookieStorage,
    val client: ClientInfo,
    private val settingsStore: SettingsStore,
    private val libraryStore: LibraryStore,
) {

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val library: StateFlow<List<LibraryFile>> = libraryStore.files

    private val _config = MutableStateFlow<ServerConfig?>(null)
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _announcement = MutableStateFlow<SiteAnnouncement?>(null)
    val announcement: StateFlow<SiteAnnouncement?> = _announcement.asStateFlow()

    val uploadEngine = UploadEngine(api, client) {
        _config.value ?: api.config().also { _config.value = it }
    }

    fun currentServer(): String = normalizeServerUrl(settingsStore.current().serverUrl)

    /** Files for the active server, newest first. */
    fun libraryForCurrentServer(): List<LibraryFile> {
        val server = currentServer()
        return libraryStore.snapshot()
            .filter { it.server == server }
            .sortedByDescending { it.uploadedAt }
    }

    suspend fun initialise() {
        libraryStore.load()
        runCatching { connect() }.onFailure { error ->
            _connection.value = ConnectionState(
                connected = false,
                connecting = false,
                error = error.friendlyMessage(),
                checkedAt = nowEpochSeconds(),
            )
        }
    }

    private val connectMutex = Mutex()

    suspend fun connect(): ServerConfig = connectMutex.withLock {
        val cached = _config.value
        val state = _connection.value
        // A caller that queued behind an in-flight check just got its answer.
        if (cached != null && state.connected &&
            nowEpochSeconds() - state.checkedAt < 20
        ) {
            return@withLock cached
        }
        _connection.value = _connection.value.copy(connecting = true, error = null)
        try {
            // `/api/config` is exempt from session creation, so the two calls
            // are independent. Running them together halves a cold check.
            val config = coroutineScope {
                val session = async { api.ensureSession() }
                val config = async { api.config() }
                session.await()
                config.await()
            }
            _config.value = config
            _connection.value = ConnectionState(
                connected = true,
                connecting = false,
                error = null,
                checkedAt = nowEpochSeconds(),
            )
            clampTtlToServer(config)
            config
        } catch (error: Throwable) {
            _connection.value = ConnectionState(
                connected = false,
                connecting = false,
                error = error.friendlyMessage(),
                checkedAt = nowEpochSeconds(),
            )
            throw error
        }
    }

    suspend fun setServer(input: String): Result<ServerConfig> {
        val normalized = normalizeServerUrl(input)
        if (normalized != currentServer()) {
            _config.value = null
            settingsStore.setServer(normalized)
        }
        return runCatching { connect() }
    }

    suspend fun refreshConfig(): ServerConfig = runCatching { api.config() }
        .onSuccess { _config.value = it }
        .getOrThrow()

    /** Best-effort: an unreachable backend or a malformed banner is simply no banner. */
    suspend fun refreshAnnouncement() {
        runCatching { api.announcement() }.onSuccess { _announcement.value = it }
    }

    private fun clampTtlToServer(config: ServerConfig) {
        val current = settingsStore.current().lastTtlHours ?: return
        if (config.allowedTtlHours.isNotEmpty() && current !in config.allowedTtlHours) {
            settingsStore.update { it.copy(lastTtlHours = null) }
        }
    }

    fun ttlForUpload(): Double {
        val settings = settingsStore.current()
        val config = _config.value
        val chosen = settings.lastTtlHours
        if (chosen != null && config != null && chosen in config.allowedTtlHours) return chosen
        return config?.defaultTtlHours ?: 24.0
    }

    // ---- library ----------------------------------------------------------

    suspend fun refreshLibrary(): Int {
        api.ensureSession()
        val server = currentServer()
        val records = api.clientFiles()
        // Files live on the CDN host, not on the API host.
        val publicBase = _config.value?.publicBaseUrl?.takeIf { it.isNotBlank() } ?: server
        libraryStore.mergeServer(server, records, publicBase)
        return records.size
    }

    /** Ask the server whether a storage node is reachable and usable. */
    suspend fun validateHost(host: String): HostValidation {
        api.ensureSession()
        return api.validateHost(host.trim().trimEnd('/'))
    }

    suspend fun recordUpload(
        result: UploadResult,
        sourceUri: String? = null,
        sourcePath: String? = null,
    ): LibraryFile {
        val response = result.response
        val now = nowEpochSeconds()
        val token = response.deleteToken.ifEmpty { result.reserved?.deleteToken.orEmpty() }
        val file = LibraryFile(
            localId = LibraryStore.newLocalId(),
            id = response.id,
            filename = response.filename.ifBlank { "upload" },
            mimeType = response.mimeType,
            sizeBytes = response.sizeBytes,
            uploadedAt = now,
            expiresAt = if (response.expiresAt > 0) response.expiresAt else now + (ttlForUpload() * 3600).toLong(),
            url = response.url.ifEmpty { result.reserved?.url.orEmpty() },
            rawUrl = response.rawUrl.ifEmpty { result.reserved?.rawUrl.orEmpty() },
            downloadUrl = response.downloadUrl.ifEmpty { result.reserved?.downloadUrl.orEmpty() },
            deleteToken = token,
            protected = response.protected,
            isEncrypted = response.isEncrypted,
            status = LibraryStatus.READY,
            server = currentServer(),
            sourceUri = sourceUri,
            sourcePath = sourcePath,
        )
        libraryStore.upsert(file)
        runCatching { syncRegistry() }
        return file
    }

    suspend fun recordFetch(file: FetchFileResponse, sourceUri: String? = null): LibraryFile {
        val now = nowEpochSeconds()
        val local = LibraryFile(
            localId = LibraryStore.newLocalId(),
            id = file.id,
            filename = file.filename.ifBlank { "media" },
            mimeType = file.mimeType,
            sizeBytes = file.sizeBytes,
            uploadedAt = now,
            expiresAt = if (file.expiresAt > 0) file.expiresAt else now + (ttlForUpload() * 3600).toLong(),
            url = file.url,
            rawUrl = file.rawUrl,
            downloadUrl = file.downloadUrl,
            deleteToken = file.deleteToken,
            protected = file.protected,
            isEncrypted = file.isEncrypted,
            status = LibraryStatus.READY,
            server = currentServer(),
            sourceUri = sourceUri,
        )
        libraryStore.upsert(local)
        runCatching { syncRegistry() }
        return local
    }

    /** Push the current server's library so a paired web session sees the same list. */
    suspend fun syncRegistry() {
        val server = currentServer()
        val records = libraryStore.snapshot()
            .filter {
                it.server == server &&
                    it.id.isNotBlank() &&
                    it.deleteToken.isNotBlank() &&
                    it.status == LibraryStatus.READY
            }
            .sortedByDescending { it.uploadedAt }
            .take(500)
            .map {
                ClientFileRecord(
                    id = it.id,
                    token = it.deleteToken,
                    n = it.filename,
                    m = it.mimeType,
                    s = it.sizeBytes,
                    u = it.uploadedAt,
                    e = it.expiresAt,
                )
            }
        api.putClientFiles(records)
    }

    suspend fun deleteFile(localId: String) {
        val file = libraryStore.find(localId) ?: return
        if (file.id.isNotBlank() && file.deleteToken.isNotBlank()) {
            try {
                api.deleteFile(file.id, file.deleteToken)
            } catch (error: ApiException) {
                // 404 already gone; 403 means we truly lack the token.
                if (!error.isNotFound) throw error
            }
        }
        libraryStore.remove(localId)
        runCatching { syncRegistry() }
    }

    suspend fun renameFile(localId: String, customId: String?): RenewResponse {
        val file = libraryStore.find(localId) ?: error("Unknown file")
        require(file.id.isNotBlank()) { "File has no server id" }
        val response = api.renew(file.id, file.deleteToken, customId?.trim()?.takeIf { it.isNotEmpty() })
        libraryStore.update(localId) {
            it.copy(
                id = response.id,
                url = response.url.ifEmpty { it.url },
                rawUrl = response.rawUrl.ifEmpty { it.rawUrl },
                downloadUrl = response.downloadUrl.ifEmpty { it.downloadUrl },
                filename = response.filename.ifEmpty { it.filename },
                expiresAt = if (response.expiresAt > 0) response.expiresAt else it.expiresAt,
            )
        }
        runCatching { syncRegistry() }
        return response
    }

    suspend fun refreshFile(localId: String): LibraryFile? {
        val file = libraryStore.find(localId) ?: return null
        if (file.id.isBlank()) return file
        return runCatching {
            val info = api.fileInfo(file.id)
            libraryStore.update(localId) {
                it.copy(
                    filename = info.filename.ifBlank { it.filename },
                    mimeType = info.mimeType.ifBlank { it.mimeType },
                    sizeBytes = if (info.sizeBytes > 0) info.sizeBytes else it.sizeBytes,
                    expiresAt = if (info.expiresAt > 0) info.expiresAt else it.expiresAt,
                    url = info.url.ifEmpty { it.url },
                    rawUrl = info.rawUrl.ifEmpty { it.rawUrl },
                    downloadUrl = info.downloadUrl.ifEmpty { it.downloadUrl },
                    storageHost = info.storageHost ?: it.storageHost,
                    protected = info.protected,
                    isEncrypted = info.isEncrypted,
                    status = LibraryStatus.READY,
                )
            }
            libraryStore.find(localId)
        }.getOrNull()
    }

    // ---- cobalt fetch -----------------------------------------------------

    suspend fun fetchMedia(
        url: String,
        password: String? = null,
        audioOnly: Boolean = false,
        videoQuality: String = "max",
        videoContainer: String = "auto",
        audioFormat: String = "best",
        betterAudio: Boolean = true,
        onProgress: suspend (FetchStatus) -> Unit = {},
        timeoutMs: Long = 15 * 60 * 1000,
    ): FetchFileResponse {
        val jobId = api.startFetch(
            FetchStartRequest(
                url = url,
                password = password,
                audioOnly = audioOnly,
                videoQuality = videoQuality,
                videoContainer = videoContainer,
                audioFormat = audioFormat,
                betterAudio = betterAudio,
            ),
        )
        val deadline = nowEpochMillis() + timeoutMs
        while (nowEpochMillis() < deadline) {
            val status = api.fetchStatus(jobId)
            onProgress(status)
            status.file?.let { return it }
            if (status.status == "failed" || status.error.isNotBlank()) {
                throw ApiException(
                    code = "FETCH_FAILED",
                    message = status.error.ifBlank { "The server could not fetch that link" },
                    status = 422,
                )
            }
            delay(1_000)
        }
        throw ApiException("FETCH_TIMEOUT", "The fetch is taking too long", 408)
    }

    // ---- backup -----------------------------------------------------------

    suspend fun exportLibrary(includeSession: Boolean): String {
        val export = LibraryExport(
            exportedAt = nowEpochSeconds(),
            server = currentServer(),
            files = libraryStore.snapshot(),
            session = if (includeSession) cookies.exportRaw() else null,
        )
        return JuiceboxJson.encodeToString(export)
    }

    suspend fun importLibrary(raw: String): Int {
        val export = JuiceboxJson.decodeFromString<LibraryExport>(raw)
        export.session?.let { cookies.importRaw(it) }
        libraryStore.importFiles(export.files)
        runCatching { connect() }
        runCatching { refreshLibrary() }
        return export.files.size
    }

    suspend fun forgetSession() {
        cookies.clear()
        _config.value = null
        _connection.value = ConnectionState()
    }
}

fun Throwable.friendlyMessage(): String = when (this) {
    is ApiException -> message
    else -> message?.takeIf { it.isNotBlank() } ?: "Network error"
}
