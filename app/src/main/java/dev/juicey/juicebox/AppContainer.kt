package dev.juicey.juicebox

import android.content.Context
import dev.juicey.juicebox.core.ClientInfo
import dev.juicey.juicebox.core.JuiceboxCore
import dev.juicey.juicebox.core.model.normalizeServerUrl
import dev.juicey.juicebox.core.net.JuiceboxApi
import dev.juicey.juicebox.core.net.PersistentCookieStorage
import dev.juicey.juicebox.core.net.createJuiceboxHttpClient
import dev.juicey.juicebox.core.platformHttpClientEngine
import dev.juicey.juicebox.core.storage.LibraryStore
import dev.juicey.juicebox.core.storage.SettingsStore
import dev.juicey.juicebox.data.SharedPrefsKeyValueStore
import dev.juicey.juicebox.upload.TaskFactory
import dev.juicey.juicebox.upload.TaskStatus
import dev.juicey.juicebox.upload.UploadNotifications
import dev.juicey.juicebox.upload.UploadQueue
import dev.juicey.juicebox.upload.UploadRunner
import dev.juicey.juicebox.upload.UploadTaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.io.File

/**
 * Manual DI: one graph for the process, created eagerly so activities,
 * WorkManager jobs and the library all share the same session.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val prefs = appContext.getSharedPreferences("juicebox", Context.MODE_PRIVATE)
    private val keyValue = SharedPrefsKeyValueStore(prefs)

    val settingsStore = SettingsStore(keyValue)
    val libraryStore = LibraryStore(keyValue)
    val cookies = PersistentCookieStorage(keyValue)

    private val clientInfo = ClientInfo(
        name = "android",
        userAgent = "Juicebox-Android/${BuildConfig.VERSION_NAME}",
    )

    private val httpClient = createJuiceboxHttpClient(platformHttpClientEngine(), cookies, clientInfo)

    val api = JuiceboxApi(httpClient) {
        normalizeServerUrl(settingsStore.current().serverUrl)
    }

    val core = JuiceboxCore(api, cookies, clientInfo, settingsStore, libraryStore)

    val tasks = UploadTaskStore(keyValue)
    val runner = UploadRunner(appContext, core, tasks)
    val taskFactory = TaskFactory(this)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Set by the activity so an in-app upload can report its progress. */
    private val _activeUpload = MutableStateFlow<String?>(null)
    val activeUpload = _activeUpload.asStateFlow()

    private val _foreground = MutableStateFlow(false)
    val foreground = _foreground.asStateFlow()

    fun markUploading(localId: String?) {
        _activeUpload.value = localId
    }

    fun setForeground(value: Boolean) {
        _foreground.value = value
    }

    init {
        // Mirror active transfers in a silent notification only while the app
        // is away; the in-app session card covers the foreground case.
        scope.launch {
            var shown = false
            var lastKey: String? = null
            combine(_foreground, tasks.tasks, runner.progress) { fg, list, progress ->
                Triple(fg, list, progress)
            }.collect { (fg, list, progress) ->
                val active = list.filter {
                    it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING
                }
                if (!fg && active.isNotEmpty()) {
                    val current = active.firstOrNull()?.let { progress[it.localId] }
                    val key = buildString {
                        append(active.size)
                        active.forEach { append('|').append(it.localId) }
                        append('|').append(current?.sentBytes ?: -1L)
                    }
                    if (key != lastKey) {
                        lastKey = key
                        UploadNotifications.showOngoing(appContext, active, progress)
                    }
                    shown = true
                } else if (shown) {
                    lastKey = null
                    shown = false
                    UploadNotifications.cancelOngoing(appContext)
                }
            }
        }
    }

    fun pendingDir(): File {
        val dir = File(appContext.filesDir, UploadRunner.CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun onAppStart() {
        scope.launch {
            tasks.clearTerminal()
            runCatching { core.initialise() }
            runCatching { UploadQueue.resumePending(this@AppContainer) }
        }
    }
}
