package dev.juicey.juicebox.core

import dev.juicey.juicebox.core.model.AppSettings
import dev.juicey.juicebox.core.model.HostValidation
import dev.juicey.juicebox.core.model.LibraryFile
import dev.juicey.juicebox.core.model.ServerConfig
import dev.juicey.juicebox.core.model.SiteAnnouncement
import dev.juicey.juicebox.core.model.StorageHost
import dev.juicey.juicebox.core.model.StorageHosts
import dev.juicey.juicebox.core.upload.FileUploadSource
import dev.juicey.juicebox.core.upload.UploadRequest
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import platform.Foundation.NSTemporaryDirectory

/** Cancels one observation started by [IosBridge]. */
class IosSubscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/** One retention choice, already labelled the way the UI shows it. */
data class IosTtlOption(
    val minutes: Int,
    val label: String,
)

private val FALLBACK_TTL_HOURS = listOf(0.5, 1.0, 6.0, 12.0, 24.0, 72.0, 168.0)

/**
 * The Swift-facing surface. Everything the app needs is a plain callback or a
 * value type, so Swift never has to touch coroutine flows or Ktor engines.
 */
class IosBridge(version: String) {

    private val container = IosAppContainer(version)
    private val core = container.core
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val appVersion: String = version

    // ---- reads -------------------------------------------------------------

    fun currentSettings(): AppSettings = container.settingsStore.current()

    fun currentServer(): String = core.currentServer()

    fun currentFiles(): List<LibraryFile> = core.libraryForCurrentServer()

    fun currentConfig(): ServerConfig? = core.config.value

    fun currentConnection(): ConnectionState = core.connection.value

    /** The Kotlin property is named `protected`, which does not survive the ObjC export. */
    fun isLocked(file: LibraryFile): Boolean = file.protected

    fun ttlOptions(): List<IosTtlOption> {
        val hours = core.config.value?.allowedTtlHours ?: FALLBACK_TTL_HOURS
        return hours.map { IosTtlOption((it * 60).roundToInt(), ttlLabel(it)) }
    }

    /** Last pick, or the server default when nothing has been chosen yet. */
    fun defaultTtlMinutes(): Int = (core.ttlForUpload() * 60).roundToInt()

    private fun ttlLabel(hours: Double): String = when {
        hours < 1 -> "${(hours * 60).toInt()}m"
        hours % 24.0 == 0.0 -> "${(hours / 24).toInt()}d"
        else -> "${hours.toInt()}h"
    }

    // ---- observations ------------------------------------------------------

    fun observeSettings(block: (AppSettings) -> Unit): IosSubscription =
        launch { container.settingsStore.settings.collect { block(it) } }

    fun observeLibrary(block: (List<LibraryFile>) -> Unit): IosSubscription =
        launch { core.library.collect { block(core.libraryForCurrentServer()) } }

    fun observeConnection(block: (ConnectionState) -> Unit): IosSubscription =
        launch { core.connection.collect { block(it) } }

    fun observeConfig(block: (ServerConfig?) -> Unit): IosSubscription =
        launch { core.config.collect { block(it) } }

    fun observeAnnouncement(block: (SiteAnnouncement?) -> Unit): IosSubscription =
        launch { core.announcement.collect { block(it) } }

    // ---- actions -----------------------------------------------------------

    fun initialise(completion: (String?) -> Unit) = run(completion) {
        core.initialise()
        core.connect()
        core.refreshAnnouncement()
    }

    fun connect(completion: (ServerConfig?, String?) -> Unit) = runWith(completion) {
        core.connect()
    }

    fun refreshLibrary(completion: (Int?, String?) -> Unit) = runWith(completion) {
        core.refreshLibrary()
    }

    fun refreshAnnouncement(completion: (String?) -> Unit) = run(completion) {
        core.refreshAnnouncement()
    }

    fun setServer(input: String, completion: (ServerConfig?, String?) -> Unit) {
        scope.launch {
            try {
                val config = core.setServer(input).getOrThrow()
                runCatching { core.refreshLibrary() }
                completion(config, null)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                completion(null, error.friendlyMessage())
            }
        }
    }

    fun deleteFile(localId: String, completion: (String?) -> Unit) = run(completion) {
        core.deleteFile(localId)
    }

    fun refreshFile(localId: String, completion: (LibraryFile?, String?) -> Unit) =
        runWith(completion) {
            core.refreshFile(localId)
        }

    fun forgetSession(completion: (String?) -> Unit) = run(completion) {
        core.forgetSession()
    }

    fun exportLibrary(includeSession: Boolean, completion: (String?, String?) -> Unit) =
        runWith(completion) {
            core.exportLibrary(includeSession)
        }

    fun importLibrary(raw: String, completion: (Int?, String?) -> Unit) =
        runWith(completion) {
            core.importLibrary(raw)
        }

    fun clearLibrary(completion: (String?) -> Unit) = run(completion) {
        container.libraryStore.clear()
        runCatching { core.syncRegistry() }
    }

    fun storageHosts(): List<StorageHost> = StorageHosts.official

    /**
     * Fixture rows for the screenshot workflow. The app only asks for these
     * when it was launched with JUICEBOX_DEMO_LIBRARY=1.
     */
    fun screenshotFiles(): List<LibraryFile> {
        val now = nowEpochSeconds()
        val day = 86_400L
        val server = core.currentServer()
        fun demo(
            id: String,
            name: String,
            mime: String,
            size: Long,
            expiresIn: Long,
        ) = LibraryFile(
            localId = id,
            id = id,
            filename = name,
            mimeType = mime,
            sizeBytes = size,
            uploadedAt = now - 120,
            expiresAt = now + expiresIn,
            url = "$server/f/$id",
            rawUrl = "$server/f/$id",
            downloadUrl = "$server/d/$id",
            deleteToken = "demo-$id",
            status = "ready",
            server = server,
        )
        return listOf(
            demo("demo-video", "launch-teaser.mp4", "video/mp4", 48_233_472, 3 * day),
            demo("demo-image", "screenshot.png", "image/png", 2_411_724, day),
            demo("demo-audio", "song-demo.mp3", "audio/mpeg", 6_291_456, 7 * day),
            demo("demo-pdf", "notes.pdf", "application/pdf", 214_528, 12 * 3_600L),
        )
    }

    fun communityHosts(completion: (List<StorageHost>?, String?) -> Unit) =
        runWith(completion) {
            core.api.communityHosts()
        }

    fun validateStorageHost(url: String, completion: (HostValidation?, String?) -> Unit) =
        runWith(completion) {
            core.validateHost(url)
        }

    fun rememberCustomHost(url: String) = update {
        it.copy(customHosts = (it.customHosts + url).distinct().takeLast(8))
    }

    fun uploadFile(
        path: String,
        filename: String,
        mimeType: String,
        ttlMinutes: Int,
        onProgress: (Double) -> Unit,
        completion: (LibraryFile?, String?) -> Unit,
    ) {
        scope.launch {
            try {
                val settings = container.settingsStore.current()
                val source = FileUploadSource.of(FileSystem.SYSTEM, path, filename, mimeType)
                val result = core.uploadEngine.upload(
                    UploadRequest(
                        source = source,
                        ttlHours = ttlMinutes / 60.0,
                        host = settings.storageHost,
                        password = effectivePassword(settings),
                        quickLink = settings.quickLink,
                        onProgress = { progress -> onProgress(progress.fraction.toDouble()) },
                    ),
                )
                val file = core.recordUpload(result, sourcePath = path)
                completion(file, null)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                completion(null, error.friendlyMessage())
            }
        }
    }

    fun saveText(
        text: String,
        ttlMinutes: Int,
        onProgress: (Double) -> Unit,
        completion: (LibraryFile?, String?) -> Unit,
    ) {
        if (text.isBlank()) {
            completion(null, "Nothing to save")
            return
        }
        val path = "${NSTemporaryDirectory()}juicebox-paste-${nowEpochMillis()}.txt"
        try {
            FileSystem.SYSTEM.write(path.toPath()) { writeUtf8(text) }
        } catch (error: Throwable) {
            completion(null, error.friendlyMessage())
            return
        }
        uploadFile(
            path = path,
            filename = "paste.txt",
            mimeType = "text/plain",
            ttlMinutes = ttlMinutes,
            onProgress = onProgress,
            completion = completion,
        )
    }

    // ---- settings ----------------------------------------------------------

    fun setThemeMode(value: String) = update { it.copy(themeMode = value) }

    fun setQuickLink(value: Boolean) = update { it.copy(quickLink = value) }

    fun setAutoCopy(value: Boolean) = update { it.copy(autoCopy = value) }

    fun setHaptics(value: Boolean) = update { it.copy(haptics = value) }

    fun setLiquidGlass(value: Boolean) = update { it.copy(liquidGlass = value) }

    fun setLinkVariant(value: String) = update { it.copy(linkVariant = value) }

    fun setShareLinkMode(value: String) = update { it.copy(shareLinkMode = value) }

    fun setLockUploads(value: Boolean) = update { it.copy(lockUploads = value) }

    fun setUploadPassword(value: String) = update { it.copy(uploadPassword = value) }

    /** Zero means "follow the server again". */
    fun setLastTtlMinutes(minutes: Int) = update {
        val hours = minutes / 60.0
        it.copy(lastTtlHours = hours.takeIf { value -> value > 0 })
    }

    fun setStorageHost(url: String?) = update { it.copy(storageHost = url) }

    // ---- internals ---------------------------------------------------------

    private fun effectivePassword(settings: AppSettings): String? =
        if (settings.lockUploads && settings.uploadPassword.isNotBlank()) {
            settings.uploadPassword
        } else {
            null
        }

    private fun update(transform: (AppSettings) -> AppSettings) {
        container.settingsStore.update(transform)
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit): IosSubscription =
        IosSubscription(scope.launch(block = block))

    private fun run(completion: (String?) -> Unit, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                completion(null)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                completion(error.friendlyMessage())
            }
        }
    }

    private fun <T> runWith(
        completion: (T?, String?) -> Unit,
        block: suspend () -> T,
    ) {
        scope.launch {
            try {
                completion(block(), null)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                completion(null, error.friendlyMessage())
            }
        }
    }
}
