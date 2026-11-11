package dev.juicey.juicebox.upload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dev.juicey.juicebox.core.JuiceboxCore
import dev.juicey.juicebox.core.friendlyMessage
import dev.juicey.juicebox.core.upload.UploadProgress
import dev.juicey.juicebox.core.upload.UploadRequest
import dev.juicey.juicebox.core.upload.FileUploadSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File

/**
 * Executes one queued task end to end: transport, library entry, clipboard.
 * The same runner serves in-app uploads and background WorkManager jobs.
 */
class UploadRunner(
    private val context: Context,
    private val core: JuiceboxCore,
    private val tasks: UploadTaskStore,
) {

    private val _progress = MutableStateFlow<Map<String, UploadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, UploadProgress>> = _progress.asStateFlow()

    private val notifications = NotificationManagerCompat.from(context)

    suspend fun execute(task: UploadTask): UploadTask {
        Log.i(TAG, "execute ${task.localId} kind=${task.kind}")
        tasks.update(task.localId) { it.copy(status = TaskStatus.RUNNING, error = null) }
        val running = task.copy(status = TaskStatus.RUNNING)

        return try {
            when (task.kind) {
                TaskKind.FETCH -> runFetch(running)
                else -> runFile(running)
            }
        } catch (cancelled: CancellationException) {
            Log.w(TAG, "cancelled ${task.localId}")
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "failed ${task.localId}", error)
            val message = error.friendlyMessage()
            tasks.update(task.localId) {
                it.copy(status = TaskStatus.FAILED, error = message)
            }
            _progress.update { it - task.localId }
            notify(task, UploadNotifications.failure(context, task, message))
            running.copy(status = TaskStatus.FAILED, error = message)
        }
    }

    private suspend fun runFile(task: UploadTask): UploadTask {
        val path = task.cachePath ?: task.sourceUri
            ?: throw IllegalStateException("The file is no longer available")

        val config = core.config.value ?: core.connect()
        val localFile = File(path)
        if (!localFile.exists() && task.cachePath != null) {
            throw IllegalStateException("The cached copy was cleaned up, share the file again")
        }
        val size = localFile.length().takeIf { it > 0 } ?: task.sizeBytes
        if (config.maxFileSizeBytes > 0 && size > config.maxFileSizeBytes) {
            throw IllegalArgumentException(
                "Too large - this server allows ${formatBytes(config.maxFileSizeBytes)}",
            )
        }

        val source = FileUploadSource.of(
            path = path,
            filename = task.filename.ifBlank { localFile.name },
            mimeType = task.mimeType.ifBlank { "application/octet-stream" },
        )

        val result = core.uploadEngine.upload(
            UploadRequest(
                source = source,
                ttlHours = task.ttlHours,
                host = task.host,
                password = task.password,
                quickLink = task.quickLink,
                transport = null,
                onProgress = { progress ->
                    _progress.update { it + (task.localId to progress) }
                },
            ),
        )

        Log.i(TAG, "upload ${task.localId} transport=${result.transport} size=$size")
        val link = result.response.url.ifBlank { result.reserved?.url.orEmpty() }
        val rawLink = result.response.rawUrl.ifBlank { result.reserved?.rawUrl.orEmpty() }
        val downloadLink = result.response.downloadUrl.ifBlank {
            result.reserved?.downloadUrl.orEmpty()
        }
        val token = result.response.deleteToken.ifBlank { result.reserved?.deleteToken.orEmpty() }
        val recorded = core.recordUpload(
            result = result,
            sourceUri = task.sourceUri,
            sourcePath = task.cachePath,
        )
        _progress.update { it - task.localId }

        val preferred = preferredLink(link, rawLink, downloadLink)
        val copied = if (core.settings.value.autoCopy) copyLink(preferred) else false
        tasks.update(task.localId) {
            it.copy(
                status = TaskStatus.DONE,
                resultLink = link,
                resultRawLink = rawLink,
                resultDownloadLink = downloadLink,
                resultDeleteToken = token,
                resultLocalId = recorded.localId,
                error = null,
            )
        }
        notify(task, UploadNotifications.success(context, task, preferred, copied))
        vibrate()
        cleanup(task)
        return task.copy(status = TaskStatus.DONE)
    }

    private suspend fun runFetch(task: UploadTask): UploadTask {
        val url = task.fetchUrl ?: throw IllegalStateException("No link to fetch")
        val file = withContext(Dispatchers.IO) {
            core.fetchMedia(
                url = url,
                password = task.password,
                audioOnly = task.audioOnly,
                videoQuality = task.videoQuality,
                videoContainer = task.videoContainer,
                audioFormat = task.audioFormat,
                betterAudio = task.betterAudio,
                onProgress = { status ->
                    val received = status.bytesReceived
                    val total = status.totalBytes
                    if (received != null && total != null && total > 0) {
                        _progress.update {
                            it + (task.localId to UploadProgress(
                                sentBytes = received,
                                totalBytes = total,
                                bytesPerSecond = 0,
                            ))
                        }
                    }
                },
            )
        }
        val recorded = core.recordFetch(file, sourceUri = task.fetchUrl)
        _progress.update { it - task.localId }
        val preferred = preferredLink(file.url, file.rawUrl, file.downloadUrl)
        val copied = if (core.settings.value.autoCopy) copyLink(preferred) else false
        tasks.update(task.localId) {
            it.copy(
                status = TaskStatus.DONE,
                resultLink = file.url,
                resultRawLink = file.rawUrl,
                resultDownloadLink = file.downloadUrl,
                resultDeleteToken = file.deleteToken,
                resultLocalId = recorded.localId,
                error = null,
            )
        }
        notify(task, UploadNotifications.success(context, task, preferred, copied))
        vibrate()
        return task.copy(status = TaskStatus.DONE)
    }

    private fun preferredLink(preview: String, raw: String, download: String): String =
        when (core.settings.value.linkVariant) {
            dev.juicey.juicebox.core.model.LinkVariant.RAW -> raw.ifBlank { preview }
            dev.juicey.juicebox.core.model.LinkVariant.DOWNLOAD -> download.ifBlank { preview }
            else -> preview.ifBlank { raw }
        }

    fun markCopied(localId: String) {
        runCatching { notifications.cancel(UploadNotifications.notificationId(localId)) }
    }

    fun copyLink(link: String): Boolean {
        if (link.isBlank()) return false
        return try {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Juicebox link", link))
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun vibrate() {
        if (!core.settings.value.haptics) return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
            // The same short, low-amplitude click Pixel uses for a confirmed
            // action - not the long buzz of a notification.
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                @Suppress("DEPRECATION")
                VibrationEffect.createOneShot(10, 40)
            }
            vibrator.vibrate(effect)
        }
    }

    private fun cleanup(task: UploadTask) {
        val path = task.cachePath ?: return
        runCatching { FileSystem.SYSTEM.delete(path.toPath()) }
        runCatching {
            val dir = File(path).parentFile ?: return@runCatching
            if (dir.list().isNullOrEmpty()) dir.delete()
            val parent = dir.parentFile
            if (parent != null && parent.name == CACHE_DIR && parent.list().isNullOrEmpty()) {
                parent.delete()
            }
        }
    }

    private fun notify(task: UploadTask, notification: android.app.Notification) {
        runCatching {
            notifications.notify(UploadNotifications.notificationId(task.localId), notification)
        }
    }

    companion object {
        private const val TAG = "JBUpload"
        const val CACHE_DIR = "uploads"

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble() / 1024
            var index = 0
            while (value >= 1024 && index < units.lastIndex) {
                value /= 1024
                index++
            }
            return if (value >= 100) {
                "${value.toInt()} ${units[index]}"
            } else {
                String.format("%.1f %s", value, units[index])
            }
        }
    }
}
