package dev.juicey.juicebox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/config`. Every optional feature is gated on this, because a
 * self-hosted server may run an older build with parts disabled.
 */
@Serializable
data class ServerConfig(
    val status: String = "ok",
    val version: String = "",
    @SerialName("max_file_size_bytes") val maxFileSizeBytes: Long = 500L * 1024 * 1024,
    /**
     * The largest cap across all retention choices. Used as a floor for older
     * hosts that do not advertise per-retention limits yet.
     */
    @SerialName("max_file_size_hard_bytes") val maxFileSizeHardBytes: Long = 0L,
    /** Caps keyed by retention in hours, e.g. `"72": 8053063680`. */
    @SerialName("max_file_size_by_ttl_hours")
    val maxFileSizeByTtlHours: Map<String, Long> = emptyMap(),
    @SerialName("default_ttl_hours") val defaultTtlHours: Double = 24.0,
    @SerialName("allowed_ttl_hours") val allowedTtlHours: List<Double> =
        listOf(0.5, 1.0, 6.0, 12.0, 24.0, 72.0, 168.0),
    @SerialName("danger_level") val dangerLevel: String = "normal",
    @SerialName("quick_link") val quickLink: Boolean = false,
    @SerialName("custom_id") val customId: Boolean = true,
    @SerialName("direct_upload") val directUpload: Boolean = false,
    @SerialName("upload_mode") val uploadMode: String = "standard",
    @SerialName("tus_patch_max_bytes") val tusPatchMaxBytes: Long = 96L * 1024 * 1024,
    @SerialName("max_tus_parallel_parts") val maxTusParallelParts: Int = 64,
    @SerialName("direct_parallel_max_parts") val directParallelMaxParts: Int = 64,
    @SerialName("direct_parallel_max_part_bytes") val directParallelMaxPartBytes: Long =
        96L * 1024 * 1024,
    val cobalt: Boolean = false,
    val encrypted: Boolean = false,
    @SerialName("fetch_rate_limit_per_minute") val fetchRateLimitPerMinute: Int = 0,
    @SerialName("public_base_url") val publicBaseUrl: String = "",
    @SerialName("rate_limit_per_minute") val rateLimitPerMinute: Int = 60,
    val quic: Boolean = false,
    @SerialName("juicehost_url") val juicehostUrl: String = "",
    val error: String? = null,
) {
    val isHealthy: Boolean get() = status == "ok"
    val directPreference: Boolean get() = uploadMode == "direct-prefer" && directUpload

    /** Highest TTL the server will accept, used to clamp restored settings. */
    val maxTtlHours: Double get() = allowedTtlHours.maxOrNull() ?: defaultTtlHours

    /**
     * The cap for one retention choice. Newer hosts shrink the allowance as
     * retention grows, so a single global number is no longer enough. Older
     * hosts only send `max_file_size_bytes`, which stays the fallback.
     */
    fun maxFileSizeBytes(ttlHours: Double): Long {
        val key = if (ttlHours % 1.0 == 0.0) ttlHours.toLong().toString() else ttlHours.toString()
        val mapped = maxFileSizeByTtlHours[key]
            ?: maxFileSizeByTtlHours.entries.firstOrNull { (hours, _) ->
                hours.toDoubleOrNull()?.let { abs(it - ttlHours) < 0.001 } == true
            }?.value
        val fallback = maxFileSizeBytes.takeIf { it > 0 }
            ?: maxFileSizeHardBytes.takeIf { it > 0 }
            ?: Long.MAX_VALUE
        val hard = maxFileSizeHardBytes.takeIf { it > 0 } ?: Long.MAX_VALUE
        return (mapped ?: fallback).coerceAtMost(hard)
    }
}

private fun abs(value: Double): Double = if (value < 0) -value else value

@Serializable
data class FilePair(
    val id: String,
    val token: String,
)

/** Short server-side registry record; `token` is never returned by the API. */
@Serializable
data class ClientFileRecord(
    val id: String,
    val token: String = "",
    val n: String = "",
    val m: String = "",
    val s: Long = 0,
    val u: Long = 0,
    val e: Long = 0,
)

@Serializable
data class ClientFilesResponse(
    val files: List<ClientFileRecord> = emptyList(),
)

@Serializable
data class OwnedFilesResponse(
    val files: List<FileInfoResponse> = emptyList(),
)

/** `GET /file/{id}/info`; timestamps are Unix seconds. */
@Serializable
data class FileInfoResponse(
    val id: String,
    val filename: String = "",
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("uploaded_at") val uploadedAt: Long = 0,
    @SerialName("expires_at") val expiresAt: Long = 0,
    val url: String = "",
    @SerialName("raw_url") val rawUrl: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("delete_token") val deleteToken: String = "",
    @SerialName("storage_host") val storageHost: String? = null,
    val status: String = "ready",
    val protected: Boolean = false,
    @SerialName("is_encrypted") val isEncrypted: Boolean = false,
)

@Serializable
data class RenewResponse(
    val id: String,
    val url: String = "",
    @SerialName("raw_url") val rawUrl: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    val filename: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0,
    val custom: Boolean = false,
)

@Serializable
data class HostValidation(
    val status: String = "",
    val host: String = "",
    val error: String? = null,
    val message: String? = null,
) {
    val accepted: Boolean get() = status in setOf("valid", "ok", "online")
}
