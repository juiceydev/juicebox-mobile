package dev.juicey.juicebox.upload

import android.net.Uri
import dev.juicey.juicebox.AppContainer
import dev.juicey.juicebox.core.nowEpochSeconds
import dev.juicey.juicebox.data.UriUploadSource
import java.io.File
import java.io.FileOutputStream

/**
 * Turns a picked URI, a text payload or a fetched link into a persisted task.
 * Client uploads, share targets and retries all go through here so the queue
 * only ever contains files the service can read.
 */
class TaskFactory(private val container: AppContainer) {

    suspend fun fromUri(
        uri: Uri,
        ttlHours: Double,
        password: String? = uploadPassword(),
        displayName: String? = null,
        mimeType: String? = null,
        host: String? = container.core.settings.value.storageHost,
    ): UploadTask {
        val meta = UriUploadSource.from(container.appContext.contentResolver, uri)
        val localId = container.tasks.newTaskId()
        val directory = File(container.pendingDir(), localId).apply { mkdirs() }
        val name = safeName(displayName ?: meta.filename)
        val destination = File(directory, name)

        container.appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not read $name")

        return UploadTask(
            localId = localId,
            kind = TaskKind.FILE,
            cachePath = destination.absolutePath,
            filename = displayName ?: meta.filename,
            mimeType = (mimeType ?: meta.mimeType).ifBlank { "application/octet-stream" },
            sizeBytes = destination.length(),
            ttlHours = ttlHours,
            host = host,
            password = password,
            quickLink = container.core.settings.value.quickLink,
            sourceUri = uri.toString(),
            createdAt = nowEpochSeconds(),
        ).also { container.tasks.put(it) }
    }

    suspend fun fromText(
        text: String,
        ttlHours: Double,
        password: String? = uploadPassword(),
        host: String? = container.core.settings.value.storageHost,
    ): UploadTask {
        val localId = container.tasks.newTaskId()
        val directory = File(container.pendingDir(), localId).apply { mkdirs() }
        val destination = File(directory, "paste.txt")
        destination.writeText(text)

        return UploadTask(
            localId = localId,
            kind = TaskKind.FILE,
            cachePath = destination.absolutePath,
            filename = "paste.txt",
            mimeType = "text/plain",
            sizeBytes = destination.length(),
            ttlHours = ttlHours,
            host = host,
            password = password,
            quickLink = container.core.settings.value.quickLink,
            createdAt = nowEpochSeconds(),
        ).also { container.tasks.put(it) }
    }

    suspend fun fromFetch(
        url: String,
        ttlHours: Double,
        password: String? = uploadPassword(),
        audioOnly: Boolean = false,
        videoQuality: String = "max",
        videoContainer: String = "auto",
        audioFormat: String = "best",
        betterAudio: Boolean = true,
    ): UploadTask {
        val localId = container.tasks.newTaskId()
        val sourceHost = runCatching { java.net.URI(url).host }.getOrNull() ?: "media"
        val task = UploadTask(
            localId = localId,
            kind = TaskKind.FETCH,
            filename = sourceHost.removePrefix("www."),
            mimeType = "video/*",
            ttlHours = ttlHours,
            password = password,
            quickLink = container.core.settings.value.quickLink,
            audioOnly = audioOnly,
            videoQuality = videoQuality,
            videoContainer = videoContainer,
            audioFormat = audioFormat,
            betterAudio = betterAudio,
            fetchUrl = url.trim(),
            createdAt = nowEpochSeconds(),
        )
        container.tasks.put(task)
        return task
    }

    private fun uploadPassword(): String? {
        val settings = container.core.settings.value
        if (!settings.lockUploads) return null
        return settings.uploadPassword.takeIf { it.isNotBlank() }
    }

    fun safeName(name: String): String {
        val cleaned = name
            .replace('/', '_')
            .replace('\\', '_')
            .filterNot { it.isISOControl() }
            .trim()
        val bounded = if (cleaned.length > 120) cleaned.takeLast(120) else cleaned
        return bounded.ifBlank { "file" }
    }
}
