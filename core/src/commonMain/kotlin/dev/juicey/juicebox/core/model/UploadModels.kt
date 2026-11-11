package dev.juicey.juicebox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Standard successful-upload payload from every transport. */
@Serializable
data class UploadResponse(
    val id: String,
    val url: String = "",
    @SerialName("raw_url") val rawUrl: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    val filename: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("delete_token") val deleteToken: String = "",
    val status: String = "ready",
    val protected: Boolean = false,
    @SerialName("is_encrypted") val isEncrypted: Boolean = false,
) {
    fun asFileInfo(): FileInfoResponse = FileInfoResponse(
        id = id,
        filename = filename,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        uploadedAt = 0,
        expiresAt = expiresAt,
        url = url,
        rawUrl = rawUrl,
        downloadUrl = downloadUrl,
        deleteToken = deleteToken,
        status = status,
        protected = protected,
        isEncrypted = isEncrypted,
    )
}

@Serializable
data class DirectReserveRequest(
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("ttl_hours") val ttlHours: Double,
    val host: String? = null,
    val password: String? = null,
)

@Serializable
data class DirectReserveResponse(
    val ticket: String,
    @SerialName("file_id") val fileId: String,
    @SerialName("upload_url") val uploadUrl: String,
    val url: String = "",
    @SerialName("raw_url") val rawUrl: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("delete_token") val deleteToken: String = "",
)

@Serializable
data class DirectCompleteRequest(
    @SerialName("file_id") val fileId: String,
    val ticket: String,
)

@Serializable
data class ParallelReserveRequest(
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("ttl_hours") val ttlHours: Double,
    @SerialName("num_parts") val numParts: Int,
    val host: String? = null,
)

@Serializable
data class ParallelPart(
    @SerialName("part_index") val partIndex: Int,
    @SerialName("part_id") val partId: String,
    @SerialName("part_size") val partSize: Long,
    val ticket: String,
    @SerialName("upload_url") val uploadUrl: String,
)

@Serializable
data class ParallelReserveResponse(
    val ticket: String,
    @SerialName("file_id") val fileId: String,
    val url: String = "",
    @SerialName("raw_url") val rawUrl: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0,
    val parts: List<ParallelPart> = emptyList(),
    @SerialName("delete_token") val deleteToken: String = "",
)

@Serializable
data class ParallelCompleteRequest(
    @SerialName("file_id") val fileId: String,
    val ticket: String,
    @SerialName("part_ids") val partIds: List<String>,
)

@Serializable
data class RelayReserveRequest(
    val filename: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("ttl_hours") val ttlHours: Double? = null,
    val host: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    val password: String? = null,
)

@Serializable
data class RelayReserveResponse(
    val id: String,
    val url: String = "",
    @SerialName("raw_url") val rawUrl: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("delete_token") val deleteToken: String = "",
    val status: String = "",
    val protected: Boolean = false,
    @SerialName("is_encrypted") val isEncrypted: Boolean = false,
)

/** The URL and token we can show before a byte is sent. */
data class ReservedFile(
    val id: String,
    val url: String,
    val rawUrl: String,
    val downloadUrl: String,
    val deleteToken: String,
)

@Serializable
data class FetchStartRequest(
    val url: String,
    @SerialName("audio_only") val audioOnly: Boolean = false,
    @SerialName("video_quality") val videoQuality: String? = null,
    @SerialName("video_container") val videoContainer: String? = null,
    @SerialName("audio_format") val audioFormat: String? = null,
    @SerialName("better_audio") val betterAudio: Boolean = false,
    @SerialName("youtube_video_codec") val youtubeVideoCodec: String? = null,
    val password: String? = null,
)

@Serializable
data class FetchStartResponse(
    @SerialName("job_id") val jobId: String,
)

@Serializable
data class FetchFileResponse(
    val id: String,
    val filename: String = "",
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    val url: String = "",
    @SerialName("raw_url") val rawUrl: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("delete_token") val deleteToken: String = "",
    val protected: Boolean = false,
    @SerialName("is_encrypted") val isEncrypted: Boolean = false,
) {
    fun asFileInfo(): FileInfoResponse = FileInfoResponse(
        id = id,
        filename = filename,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        expiresAt = expiresAt,
        url = url,
        rawUrl = rawUrl,
        downloadUrl = downloadUrl,
        deleteToken = deleteToken,
        status = "ready",
        protected = protected,
        isEncrypted = isEncrypted,
    )
}

@Serializable
data class FetchStatus(
    @SerialName("job_id") val jobId: String = "",
    val status: String = "",
    val error: String = "",
    val file: FetchFileResponse? = null,
    val stage: String? = null,
    @SerialName("bytes_received") val bytesReceived: Long? = null,
    @SerialName("total_bytes") val totalBytes: Long? = null,
) {
    val isDone: Boolean get() = status == "done" || status == "failed" || file != null
}

@Serializable
data class FetchServicesResponse(
    val services: List<String> = emptyList(),
)
