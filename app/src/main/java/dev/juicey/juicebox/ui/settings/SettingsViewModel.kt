package dev.juicey.juicebox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.juicey.juicebox.AppContainer
import dev.juicey.juicebox.core.ConnectionState
import dev.juicey.juicebox.core.friendlyMessage
import dev.juicey.juicebox.core.model.AppSettings
import dev.juicey.juicebox.core.model.ServerConfig
import dev.juicey.juicebox.core.model.ShareLinkMode
import dev.juicey.juicebox.core.model.StorageHost
import dev.juicey.juicebox.core.model.StorageHosts
import dev.juicey.juicebox.core.model.ThemeMode
import dev.juicey.juicebox.core.model.normalizeServerUrl
import dev.juicey.juicebox.core.nowEpochSeconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverInput: String = "",
    val savedServer: String = "",
    val connection: ConnectionState = ConnectionState(),
    val config: ServerConfig? = null,
    val settings: AppSettings = AppSettings(),
    val hasSession: Boolean = false,
    val libraryCount: Int = 0,
    val busy: Boolean = false,
    val hostInput: String = "",
    val hostValidating: Boolean = false,
    val hostOk: Boolean = false,
    val hostMessage: String? = null,
    /** Community-run nodes scraped from the site's host selector. */
    val communityHosts: List<StorageHost> = emptyList(),
    val communityHostsLoaded: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            serverInput = container.core.settings.value.serverUrl,
            savedServer = container.core.currentServer(),
            hostInput = container.core.settings.value.storageHost.orEmpty(),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Sharing never asks; an old "ask" preference just becomes "fetch".
        if (container.core.settings.value.shareLinkMode == ShareLinkMode.ASK) {
            container.settingsStore.update { it.copy(shareLinkMode = ShareLinkMode.FETCH) }
        }
        viewModelScope.launch {
            container.core.settings.collect { settings ->
                _state.update {
                    it.copy(
                        settings = settings,
                        serverInput = if (it.busy) it.serverInput else settings.serverUrl,
                        savedServer = normalizeServerUrl(settings.serverUrl),
                    )
                }
            }
        }
        viewModelScope.launch {
            container.core.connection.collect { connection ->
                _state.update { it.copy(connection = connection) }
            }
        }
        viewModelScope.launch {
            container.core.config.collect { config ->
                _state.update { it.copy(config = config) }
            }
        }
        viewModelScope.launch {
            container.core.library.collect { library ->
                val server = container.core.currentServer()
                _state.update {
                    it.copy(
                        libraryCount = library.count { file -> file.server == server },
                        hasSession = container.cookies.hasSession(),
                    )
                }
            }
        }
        refresh()
        loadCommunityHosts()
    }

    fun setServerInput(value: String) {
        _state.update { it.copy(serverInput = value.trim()) }
    }

    fun saveServer() {
        val target = _state.value.serverInput
        if (target.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            val result = container.core.setServer(target)
            result.onFailure { error ->
                _state.update { it.copy(error = error.friendlyMessage()) }
            }.onSuccess { config ->
                _state.update {
                    it.copy(
                        message = "Connected to ${config.publicBaseUrl.ifBlank { container.core.currentServer() }}",
                    )
                }
                runCatching { container.core.refreshLibrary() }
                loadCommunityHosts()
            }
            _state.update {
                it.copy(
                    busy = false,
                    savedServer = container.core.currentServer(),
                    hasSession = container.cookies.hasSession(),
                )
            }
        }
    }

    fun loadCommunityHosts() {
        viewModelScope.launch {
            runCatching { container.core.api.communityHosts() }
                .onSuccess { hosts ->
                    _state.update { it.copy(communityHosts = hosts, communityHostsLoaded = true) }
                }
                .onFailure {
                    _state.update { it.copy(communityHostsLoaded = true) }
                }
        }
    }

    fun refresh() {
        val connection = container.core.connection.value
        // Already checked (or mid-check): don't queue another round trip just
        // because the Settings tab was opened.
        val shouldConnect = !connection.connected && !connection.connecting
        viewModelScope.launch {
            if (shouldConnect) runCatching { container.core.connect() }
            _state.update { it.copy(hasSession = container.cookies.hasSession()) }
        }
    }

    fun setQuickLink(value: Boolean) = container.settingsStore.update { it.copy(quickLink = value) }
    fun setAutoCopy(value: Boolean) = container.settingsStore.update { it.copy(autoCopy = value) }
    fun setHaptics(value: Boolean) = container.settingsStore.update { it.copy(haptics = value) }
    fun setShareLinkMode(value: String) = container.settingsStore.update { it.copy(shareLinkMode = value) }
    fun setLinkVariant(value: String) = container.settingsStore.update { it.copy(linkVariant = value) }
    fun setThemeMode(value: String) = container.settingsStore.update {
        it.copy(themeMode = ThemeMode.normalize(value))
    }

    fun setLockUploads(value: Boolean) = container.settingsStore.update {
        if (value) it.copy(lockUploads = true)
        else it.copy(lockUploads = false, uploadPassword = "")
    }

    fun setUploadPassword(value: String) = container.settingsStore.update {
        it.copy(uploadPassword = value.take(128))
    }

    fun setHostInput(value: String) {
        _state.update { it.copy(hostInput = value.trim(), hostMessage = null, hostOk = false) }
    }

    /** Auto (null) or one of the official nodes. */
    fun selectHost(url: String?) {
        container.settingsStore.update { settings ->
            settings.copy(
                storageHost = url,
                // Official nodes get their own chips; never list them twice.
                customHosts = settings.customHosts.filterNot { custom ->
                    StorageHosts.official.any { it.url == custom }
                },
            )
        }
        _state.update {
            it.copy(
                hostInput = url.orEmpty(),
                hostMessage = if (url == null) "The server picks the closest node" else "Uploads pinned to this node",
                hostOk = true,
            )
        }
    }

    fun validateAndSaveHost() {
        val raw = _state.value.hostInput.trim()
        if (raw.isEmpty()) {
            selectHost(null)
            return
        }
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        viewModelScope.launch {
            _state.update { it.copy(hostValidating = true, hostMessage = null, hostOk = false) }
            runCatching { container.core.validateHost(url) }
                .onSuccess { result ->
                    if (result.accepted) {
                        val official = StorageHosts.official.firstOrNull { it.url == url.trimEnd('/') }
                        if (official != null) {
                            selectHost(official.url)
                            return@onSuccess
                        }
                        container.settingsStore.update { settings ->
                            settings.copy(
                                storageHost = url.trimEnd('/'),
                                customHosts = (settings.customHosts + url.trimEnd('/')).distinct().takeLast(8),
                            )
                        }
                        _state.update {
                            it.copy(
                                hostValidating = false,
                                hostOk = true,
                                hostInput = url.trimEnd('/'),
                                hostMessage = "Online",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                hostValidating = false,
                                hostOk = false,
                                hostMessage = "Not reachable",
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            hostValidating = false,
                            hostOk = false,
                            hostMessage = "Not reachable",
                        )
                    }
                }
        }
    }

    fun removeCustomHost(url: String) {
        container.settingsStore.update { settings ->
            settings.copy(
                customHosts = settings.customHosts.filterNot { it == url },
                storageHost = settings.storageHost?.takeUnless { it == url },
            )
        }
    }

    fun exportLibrary(includeSession: Boolean, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { container.core.exportLibrary(includeSession) }
                .onSuccess(onReady)
                .onFailure { error -> _state.update { it.copy(error = error.friendlyMessage()) } }
        }
    }

    fun importLibrary(raw: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.core.importLibrary(raw) }
                .onSuccess { count ->
                    _state.update { it.copy(message = "Restored $count files") }
                    onDone(count)
                }
                .onFailure { error -> _state.update { it.copy(error = error.friendlyMessage()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun forgetIdentity() {
        viewModelScope.launch {
            runCatching { container.core.forgetSession() }
            _state.update {
                it.copy(
                    hasSession = false,
                    message = "This device will start a new anonymous identity",
                )
            }
            runCatching { container.core.connect() }
        }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            runCatching { container.libraryStore.clear() }
            runCatching { container.core.syncRegistry() }
            _state.update { it.copy(message = "Local library cleared") }
        }
    }

    fun shareLinkModes(): List<Pair<String, String>> = listOf(
        ShareLinkMode.FETCH to "Fetch video",
        ShareLinkMode.TEXT to "Upload as text",
    )

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
