package dev.juicey.juicebox.core.storage

import dev.juicey.juicebox.core.JuiceboxJson
import dev.juicey.juicebox.core.model.AppSettings
import dev.juicey.juicebox.core.model.ThemeMode
import dev.juicey.juicebox.core.model.normalizeServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(private val store: KeyValueStore) {

    private val initial: AppSettings = store.read(KEY)?.let {
        runCatching { JuiceboxJson.decodeFromString<AppSettings>(it) }.getOrNull()
    }?.let { settings ->
        settings.copy(themeMode = ThemeMode.normalize(settings.themeMode))
    } ?: AppSettings()

    private val _settings = MutableStateFlow(initial)
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        store.write(KEY, JuiceboxJson.encodeToString(next))
    }

    fun setServer(url: String) {
        update { it.copy(serverUrl = normalizeServerUrl(url)) }
    }

    private companion object {
        const val KEY = "settings"
    }
}
