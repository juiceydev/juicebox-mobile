package dev.juicey.juicebox.core.net

import dev.juicey.juicebox.core.JuiceboxJson
import dev.juicey.juicebox.core.model.ClientFileRecord
import dev.juicey.juicebox.core.model.ClientFilesResponse
import dev.juicey.juicebox.core.model.DirectCompleteRequest
import dev.juicey.juicebox.core.model.DirectReserveRequest
import dev.juicey.juicebox.core.model.DirectReserveResponse
import dev.juicey.juicebox.core.model.FetchServicesResponse
import dev.juicey.juicebox.core.model.FetchStartRequest
import dev.juicey.juicebox.core.model.FetchStartResponse
import dev.juicey.juicebox.core.model.FetchStatus
import dev.juicey.juicebox.core.model.FileInfoResponse
import dev.juicey.juicebox.core.model.FilePair
import dev.juicey.juicebox.core.model.HostValidation
import dev.juicey.juicebox.core.model.OwnedFilesResponse
import dev.juicey.juicebox.core.model.ParallelCompleteRequest
import dev.juicey.juicebox.core.model.ParallelReserveRequest
import dev.juicey.juicebox.core.model.ParallelReserveResponse
import dev.juicey.juicebox.core.model.RelayReserveRequest
import dev.juicey.juicebox.core.model.RelayReserveResponse
import dev.juicey.juicebox.core.model.RenewResponse
import dev.juicey.juicebox.core.model.ServerConfig
import dev.juicey.juicebox.core.model.SiteAnnouncement
import dev.juicey.juicebox.core.model.StorageHost
import dev.juicey.juicebox.core.model.UploadResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
private data class ErrorEnvelope(
    val error: String = "",
    val message: String = "",
)

@Serializable
private data class JsonBody(val ok: Boolean = true)

data class TusPatchResult(
    val newOffset: Long,
    val response: UploadResponse?,
)

/** A parallel TUS part is either newly created, still live, or already final. */
data class TusCreateResult(
    val id: String,
    val offset: Long,
    val completing: Boolean,
)

/**
 * Thin, typed wrapper over the juiceback HTTP API. Every method either returns
 * the decoded payload or throws [ApiException]; transport failures surface as
 * the engine's IO exception and are handled by the upload engine.
 */
class JuiceboxApi(
    private val client: HttpClient,
    private val baseUrl: () -> String,
) {

    fun absolute(pathOrUrl: String): String = when {
        pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") -> pathOrUrl
        else -> baseUrl().trimEnd('/') + pathOrUrl
    }

    // ---- session & config -------------------------------------------------

    /**
     * A check should fail fast: a dead host must not hold the UI hostage for
     * the 90s transfer budget used by uploads.
     */
    private fun HttpRequestBuilder.quickTimeout() = timeout {
        requestTimeoutMillis = CHECK_REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CHECK_CONNECT_TIMEOUT_MS
        socketTimeoutMillis = CHECK_REQUEST_TIMEOUT_MS
    }

    /** Any non-exempt call creates the session cookie; `/api/config` does not. */
    suspend fun ensureSession() {
        val response = client.get(absolute("/api/client-files")) { quickTimeout() }
        if (!response.status.isSuccess()) throw response.toApiException()
    }

    suspend fun config(): ServerConfig {
        val response = client.get(absolute("/api/config")) { quickTimeout() }
        if (!response.status.isSuccess()) throw response.toApiException()
        val config = response.body<ServerConfig>()
        if (!config.isHealthy) {
            throw ApiException(
                code = "SERVER_DEGRADED",
                message = config.error ?: "Server is unavailable",
                status = 503,
            )
        }
        return config
    }

    suspend fun validateHost(host: String): HostValidation {
        val response = client.post(absolute("/api/validate-host")) {
            quickTimeout()
            setBody(mapOf("host" to host))
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    /**
     * The JSON API only knows the official nodes. Community nodes are rendered
     * into the site's host selector as unofficial rows, so read them from the
     * page instead of guessing a list.
     */
    suspend fun communityHosts(): List<StorageHost> {
        val response = client.get(baseUrl().trimEnd('/') + "/") { quickTimeout() }
        if (!response.status.isSuccess()) return emptyList()
        return parseCommunityHosts(response.bodyAsText())
    }

    /** Returns null when the site has no active announcement (JSON literal `null`). */
    suspend fun announcement(): SiteAnnouncement? {
        val response = client.get(absolute("/api/announcement"))
        if (!response.status.isSuccess()) throw response.toApiException()
        val text = response.bodyAsText().trim()
        if (text.isEmpty() || text == "null") return null
        return runCatching {
            JuiceboxJson.decodeFromString<SiteAnnouncement>(text)
        }.getOrNull()
    }

    // ---- library ----------------------------------------------------------

    suspend fun clientFiles(): List<ClientFileRecord> {
        val response = client.get(absolute("/api/client-files"))
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body<ClientFilesResponse>().files
    }

    suspend fun putClientFiles(files: List<ClientFileRecord>) {
        val response = client.post(absolute("/api/client-files")) {
            setBody(mapOf("files" to files))
        }
        if (!response.status.isSuccess()) throw response.toApiException()
    }

    suspend fun ownedFiles(pairs: List<FilePair>): List<FileInfoResponse> {
        val response = client.post(absolute("/api/owned-files")) {
            setBody(mapOf("pairs" to pairs))
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body<OwnedFilesResponse>().files
    }

    suspend fun fileInfo(id: String): FileInfoResponse {
        val response = client.get(absolute("/file/$id/info"))
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    suspend fun renew(id: String, deleteToken: String, customId: String?): RenewResponse {
        val response = client.post(absolute("/file/$id/renew")) {
            if (deleteToken.isNotEmpty()) header("X-Delete-Token", deleteToken)
            setBody(mapOf("custom_id" to customId))
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    suspend fun deleteFile(id: String, deleteToken: String) {
        val response = client.delete(absolute("/file/$id")) {
            if (deleteToken.isNotEmpty()) header("X-Delete-Token", deleteToken)
        }
        if (!response.status.isSuccess()) throw response.toApiException()
    }

    // ---- uploads ----------------------------------------------------------

    suspend fun reserveDirect(request: DirectReserveRequest): DirectReserveResponse {
        val response = client.post(absolute("/upload/direct/reserve")) { setBody(request) }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    suspend fun completeDirect(fileId: String, ticket: String): UploadResponse {
        val response = client.post(absolute("/upload/direct/complete")) {
            setBody(DirectCompleteRequest(fileId, ticket))
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    suspend fun reserveParallel(request: ParallelReserveRequest): ParallelReserveResponse {
        val response = client.post(absolute("/upload/direct/parallel/reserve")) { setBody(request) }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    suspend fun completeParallel(
        fileId: String,
        ticket: String,
        partIds: List<String>,
    ): UploadResponse {
        val response = client.post(absolute("/upload/direct/parallel/complete")) {
            setBody(ParallelCompleteRequest(fileId, ticket, partIds))
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    suspend fun reserveRelay(request: RelayReserveRequest): RelayReserveResponse {
        val response = client.post(absolute("/upload/reserve")) { setBody(request) }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    suspend fun uploadRelay(
        content: OutgoingContent,
        deleteToken: String?,
    ): UploadResponse {
        val response = client.post(absolute("/upload")) {
            if (!deleteToken.isNullOrEmpty()) header("X-Delete-Token", deleteToken)
            setBody(content)
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 60_000
            }
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    /** Raw bytes straight to a storage node, authenticated by the ticket. */
    suspend fun putBytes(
        uploadUrl: String,
        ticket: String,
        content: OutgoingContent,
    ) {
        val response = client.post(absolute(uploadUrl)) {
            header(HttpHeaders.Authorization, "Bearer $ticket")
            header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            setBody(content)
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 60_000
            }
        }
        if (!response.status.isSuccess()) throw response.toApiException()
    }

    // ---- tus --------------------------------------------------------------

    suspend fun tusCreate(metadata: Map<String, String>, length: Long): String {
        val response = client.post(absolute("/api/tus")) {
            header("Tus-Resumable", "1.0.0")
            header("Upload-Length", length.toString())
            header("Upload-Metadata", encodeTusMetadata(metadata))
            header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
        }
        if (response.status.value != 201) throw response.toApiException()
        val location = response.headers[HttpHeaders.Location]
            ?: throw ApiException("TUS_NO_LOCATION", "Server did not return an upload location", 502)
        return location.substringAfterLast('/')
    }

    /**
     * Creates one part of a parallel TUS session. A retry can land on a live
     * sibling (Location + Upload-Offset) or on one that is already finalizing.
     */
    suspend fun tusCreatePart(metadata: Map<String, String>, length: Long): TusCreateResult {
        val response = client.post(absolute("/api/tus")) {
            header("Tus-Resumable", "1.0.0")
            header("Upload-Length", length.toString())
            header("Upload-Metadata", encodeTusMetadata(metadata))
            header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
        }
        return when (response.status.value) {
            201 -> TusCreateResult(
                id = response.headers[HttpHeaders.Location]
                    ?.substringAfterLast('/')
                    ?: throw ApiException("TUS_NO_LOCATION", "Server did not return an upload location", 502),
                offset = 0,
                completing = false,
            )
            409 -> {
                val completing = response.headers["X-Juicebox-Part-Completing"] == "1"
                val location = response.headers[HttpHeaders.Location]?.substringAfterLast('/')
                when {
                    completing -> TusCreateResult(
                        id = location.orEmpty(),
                        offset = length,
                        completing = true,
                    )
                    location != null -> TusCreateResult(
                        id = location,
                        offset = response.headers["Upload-Offset"]?.toLongOrNull() ?: 0,
                        completing = false,
                    )
                    else -> throw response.toApiException()
                }
            }
            else -> throw response.toApiException()
        }
    }

    suspend fun tusOffset(id: String): Long? {
        val response = client.get(absolute("/api/tus/$id")) {
            header("Tus-Resumable", "1.0.0")
        }
        return when {
            response.status.isSuccess() ->
                response.headers["Upload-Offset"]?.toLongOrNull()
            response.status.value == 404 -> null
            else -> throw response.toApiException()
        }
    }

    suspend fun tusPatch(
        id: String,
        offset: Long,
        content: OutgoingContent,
    ): TusPatchResult {
        val response = client.patch(absolute("/api/tus/$id")) {
            header("Tus-Resumable", "1.0.0")
            header("Upload-Offset", offset.toString())
            header(HttpHeaders.ContentType, "application/offset+octet-stream")
            setBody(content)
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 60_000
            }
        }
        return when {
            response.status.value == 200 -> TusPatchResult(
                newOffset = response.headers["Upload-Offset"]?.toLongOrNull() ?: (offset + content.contentLength!!),
                response = response.body(),
            )
            response.status.value == 204 || response.status.value == 202 -> TusPatchResult(
                newOffset = response.headers["Upload-Offset"]?.toLongOrNull() ?: (offset + content.contentLength!!),
                response = null,
            )
            else -> throw response.toApiException()
        }
    }

    suspend fun tusDelete(id: String) {
        runCatching { client.delete(absolute("/api/tus/$id")) }
    }

    // ---- cobalt fetch -----------------------------------------------------

    suspend fun startFetch(request: FetchStartRequest): String {
        val response = client.post(absolute("/api/fetch")) { setBody(request) }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body<FetchStartResponse>().jobId
    }

    suspend fun fetchStatus(jobId: String): FetchStatus {
        val response = client.get(absolute("/api/fetch/$jobId"))
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body()
    }

    suspend fun fetchServices(): List<String> {
        val response = client.get(absolute("/api/fetch/services"))
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.body<FetchServicesResponse>().services
    }
}

private const val CHECK_REQUEST_TIMEOUT_MS = 10_000L
private const val CHECK_CONNECT_TIMEOUT_MS = 5_000L

private suspend fun HttpResponse.toApiException(): ApiException {
    val text = runCatching { bodyAsText() }.getOrElse { "" }
    val envelope = text.takeIf { it.isNotBlank() }?.let {
        runCatching { JuiceboxJson.decodeFromString<ErrorEnvelope>(it) }.getOrNull()
    }
    val code = envelope?.error?.takeIf { it.isNotBlank() } ?: "HTTP_${status.value}"
    val message = envelope?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(status.value)
    return ApiException(code, message, status.value)
}

private fun defaultMessage(status: Int): String = when (status) {
    400 -> "The server rejected the request"
    401, 403 -> "Not allowed to do that"
    404 -> "Not found"
    408 -> "The request timed out"
    409 -> "That name is already taken"
    410 -> "The file has expired"
    413 -> "The file is too large"
    429 -> "Too many requests, please wait a moment"
    in 500..599 -> "The server had a problem, try again"
    else -> "Request failed ($status)"
}

private val HOST_ROW = Regex("<tr\\b[^>]*data-official=\"false\"[^>]*>", RegexOption.IGNORE_CASE)
private val HOST_ATTR = Regex("data-(url|name|region)=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

internal fun parseCommunityHosts(html: String): List<StorageHost> =
    HOST_ROW.findAll(html).mapNotNull { row ->
        val attrs = HOST_ATTR.findAll(row.value).associate {
            it.groupValues[1].lowercase() to it.groupValues[2]
        }
        val url = attrs["url"].orEmpty().trim().trimEnd('/')
        if (url.isEmpty()) return@mapNotNull null
        StorageHost(
            name = attrs["name"].orEmpty().ifBlank { url },
            url = url,
            region = attrs["region"].orEmpty(),
        )
    }.distinctBy { it.url }.toList()

/** TUS metadata: `key base64(value)` pairs joined by commas. */
fun encodeTusMetadata(metadata: Map<String, String>): String =
    metadata.entries.joinToString(",") { (key, value) ->
        "$key ${base64(value.encodeToByteArray())}"
    }

private const val BASE64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

internal fun base64(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xff
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else 0
        out.append(BASE64_ALPHABET[b0 shr 2])
        out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
        out.append(if (i + 1 < bytes.size) BASE64_ALPHABET[((b1 and 0x0f) shl 2) or (b2 shr 6)] else '=')
        out.append(if (i + 2 < bytes.size) BASE64_ALPHABET[b2 and 0x3f] else '=')
        i += 3
    }
    return out.toString()
}
