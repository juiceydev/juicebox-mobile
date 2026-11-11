package dev.juicey.juicebox.core.model

import dev.juicey.juicebox.core.DEFAULT_SERVER
import kotlinx.serialization.Serializable

object LinkVariant {
    const val PREVIEW = "preview"
    const val RAW = "raw"
    const val DOWNLOAD = "download"

    val all = listOf(PREVIEW, RAW, DOWNLOAD)

    fun label(variant: String): String = when (variant) {
        RAW -> "Raw"
        DOWNLOAD -> "Download"
        else -> "Preview"
    }
}

/** A storage node an upload can be pinned to. Null means "let the server pick". */
data class StorageHost(
    val name: String,
    val url: String,
    val region: String,
)

object StorageHosts {
    val official = listOf(
        StorageHost("Europe-1", "https://f-eu1.juicey.dev", "Europe"),
        StorageHost("Europe-2", "https://f-eu2.juicey.dev", "Europe"),
        StorageHost("US-1", "https://f-us1.juicey.dev", "United States"),
    )
}

/** Which palette the app paints with. */
object ThemeMode {
    /** The real Juicebox palette: burnt orange on juice-stained neutrals. */
    const val JUICEBOX = "juicebox"

    /** Material You: the wallpaper palette on Android 12 and newer. */
    const val YOU = "you"

    /** Static Material 3 palettes for people who want a different accent. */
    const val VIOLET = "violet"
    const val OCEAN = "ocean"
    const val FOREST = "forest"
    const val ROSE = "rose"

    /** The old name for the brand palette, kept so saved settings migrate. */
    const val LEGACY_ORANGE = "orange"

    val all = listOf(JUICEBOX, YOU, VIOLET, OCEAN, FOREST, ROSE)

    fun normalize(mode: String): String = when (mode) {
        LEGACY_ORANGE -> JUICEBOX
        in all -> mode
        else -> JUICEBOX
    }

    fun label(mode: String): String = when (mode) {
        YOU -> "Material You"
        VIOLET -> "Violet"
        OCEAN -> "Ocean"
        FOREST -> "Forest"
        ROSE -> "Rose"
        else -> "Juicebox"
    }
}

object ShareLinkMode {
    /** Send supported links to the server's cobalt pipeline. */
    const val FETCH = "fetch"

    /** Always upload the URL itself as paste.txt. */
    const val TEXT = "text"

    /** Let the share sheet ask each time. */
    const val ASK = "ask"
}

@Serializable
data class AppSettings(
    val serverUrl: String = DEFAULT_SERVER,
    val quickLink: Boolean = true,
    val autoCopy: Boolean = true,
    val shareLinkMode: String = ShareLinkMode.FETCH,
    val haptics: Boolean = true,
    val sounds: Boolean = false,
    /** iOS 26 glass surfaces; ignored on Android and older iOS builds. */
    val liquidGlass: Boolean = true,
    val reduceMotion: Boolean = false,
    /** Juicebox keeps the brand palette; you follows the wallpaper. */
    val themeMode: String = ThemeMode.JUICEBOX,
    /** The retention picked on the last upload; null follows the server. */
    val lastTtlHours: Double? = null,
    /** Lock every upload from this device behind one password. */
    val lockUploads: Boolean = false,
    val uploadPassword: String = "",
    /** Which link the app copies and shows first: preview, raw or download. */
    val linkVariant: String = LinkVariant.PREVIEW,
    /** Storage node URL; null lets the server choose. */
    val storageHost: String? = null,
    /** Hosts the person typed in, kept for the picker. */
    val customHosts: List<String> = emptyList(),
    val lastHost: String? = null,
    val onboardingDone: Boolean = false,
)

object LibraryStatus {
    const val QUEUED = "queued"
    const val UPLOADING = "uploading"
    const val READY = "ready"
    const val ERROR = "error"
    const val CANCELLED = "cancelled"
}

@Serializable
data class LibraryFile(
    val localId: String,
    val id: String = "",
    val filename: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val uploadedAt: Long = 0,
    val expiresAt: Long = 0,
    val url: String = "",
    val rawUrl: String = "",
    val downloadUrl: String = "",
    val deleteToken: String = "",
    val storageHost: String? = null,
    val protected: Boolean = false,
    val isEncrypted: Boolean = false,
    val status: String = LibraryStatus.READY,
    val error: String? = null,
    /** Base URL this file belongs to; the library is per-server. */
    val server: String = "",
    /** Content URI or path kept only while an upload is pending/retryable. */
    val sourceUri: String? = null,
    val sourcePath: String? = null,
) {
    val isPending: Boolean get() = status == LibraryStatus.QUEUED || status == LibraryStatus.UPLOADING
    val isFailed: Boolean get() = status == LibraryStatus.ERROR || status == LibraryStatus.CANCELLED
    val hasServerId: Boolean get() = id.isNotBlank()
    val canManage: Boolean get() = hasServerId && deleteToken.isNotBlank()

    fun isExpired(nowSeconds: Long): Boolean = expiresAt in 1..<nowSeconds

    /**
     * Registry entries only carry the preview link, so derive the other two by
     * swapping the path prefix. Uploads keep all three from the server.
     */
    fun linkFor(variant: String): String = when (variant) {
        LinkVariant.RAW -> rawUrl.ifBlank { swapPrefix(url, "f") }
        LinkVariant.DOWNLOAD -> downloadUrl.ifBlank { swapPrefix(url, "d") }
        else -> url.ifBlank { rawUrl }
    }

    private fun swapPrefix(value: String, prefix: String): String {
        if (value.isBlank()) return value
        val marker = "/v/"
        val index = value.indexOf(marker)
        return if (index >= 0) value.substring(0, index) + "/$prefix/" + value.substring(index + marker.length) else value
    }
}

@Serializable
data class LibraryExport(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val server: String = "",
    val files: List<LibraryFile> = emptyList(),
    /** Raw cookie jar, so a reinstall can recover the same anonymous identity. */
    val session: String? = null,
)

fun normalizeServerUrl(input: String): String {
    var value = input.trim()
    if (value.isEmpty()) return DEFAULT_SERVER
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
        value = "https://$value"
    }
    return value.trimEnd('/')
}
