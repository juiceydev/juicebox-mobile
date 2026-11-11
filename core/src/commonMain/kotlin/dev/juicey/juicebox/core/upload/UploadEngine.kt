package dev.juicey.juicebox.core.upload

import dev.juicey.juicebox.core.ClientInfo
import dev.juicey.juicebox.core.DIRECT_PART_CONCURRENCY
import dev.juicey.juicebox.core.DIRECT_PART_TARGET_BYTES
import dev.juicey.juicebox.core.DIRECT_SINGLE_MAX_BYTES
import dev.juicey.juicebox.core.TUS_CHUNK_MAX
import dev.juicey.juicebox.core.TUS_CHUNK_MIN
import dev.juicey.juicebox.core.TUS_CHUNK_START
import dev.juicey.juicebox.core.TUS_PARALLEL_CHUNK
import dev.juicey.juicebox.core.TUS_PARALLEL_PARTS
import dev.juicey.juicebox.core.TUS_PARALLEL_THRESHOLD_BYTES
import dev.juicey.juicebox.core.TUS_THRESHOLD_BYTES
import dev.juicey.juicebox.core.model.DirectReserveRequest
import dev.juicey.juicebox.core.model.FileInfoResponse
import dev.juicey.juicebox.core.model.ParallelReserveRequest
import dev.juicey.juicebox.core.model.RelayReserveRequest
import dev.juicey.juicebox.core.model.ReservedFile
import dev.juicey.juicebox.core.model.ServerConfig
import dev.juicey.juicebox.core.model.UploadResponse
import dev.juicey.juicebox.core.net.ApiException
import dev.juicey.juicebox.core.net.JuiceboxApi
import dev.juicey.juicebox.core.net.TusCreateResult
import dev.juicey.juicebox.core.nowEpochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

enum class UploadTransport { DIRECT, PARALLEL, TUS, TUS_PARALLEL, RELAY }

data class UploadProgress(
    val sentBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (sentBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

data class ReservedLink(
    val id: String,
    val url: String,
    val rawUrl: String,
    val downloadUrl: String,
    val deleteToken: String,
)

data class UploadRequest(
    val source: UploadSource,
    val ttlHours: Double,
    val host: String? = null,
    val password: String? = null,
    /** Report the share link as soon as the server has minted it. */
    val quickLink: Boolean = false,
    /** Force a transport; null lets the planner decide. */
    val transport: UploadTransport? = null,
    val onReserved: suspend (ReservedLink) -> Unit = {},
    val onProgress: suspend (UploadProgress) -> Unit = {},
)

data class UploadResult(
    val response: UploadResponse,
    val transport: UploadTransport,
    val reserved: ReservedLink?,
)

/**
 * Chooses the fastest path the server actually supports:
 *
 * - unprotected + direct-capable: one request for small files, one-hop
 *   parallel parts for everything else;
 * - protected: TUS, because the server rejects passwords on direct/parallel;
 * - no direct support: parallel TUS for big files, sequential TUS or relay
 *   where a whole-file request is still cheaper.
 */
fun planTransport(
    config: ServerConfig,
    sizeBytes: Long,
    hasPassword: Boolean,
): UploadTransport {
    if (!hasPassword && config.directUpload) {
        return if (sizeBytes <= DIRECT_SINGLE_MAX_BYTES) {
            UploadTransport.DIRECT
        } else {
            UploadTransport.PARALLEL
        }
    }

    if (!hasPassword &&
        sizeBytes >= TUS_PARALLEL_THRESHOLD_BYTES &&
        config.maxTusParallelParts >= 2
    ) {
        return UploadTransport.TUS_PARALLEL
    }

    return if (sizeBytes > TUS_THRESHOLD_BYTES) UploadTransport.TUS else UploadTransport.RELAY
}

class UploadEngine(
    private val api: JuiceboxApi,
    private val client: ClientInfo,
    private val configProvider: suspend () -> ServerConfig,
) {

    suspend fun upload(request: UploadRequest): UploadResult {
        require(request.source.size > 0) { "Refusing to upload an empty file" }

        val config = configProvider()
        val planned = request.transport
            ?: planTransport(config, request.source.size, !request.password.isNullOrEmpty())

        // A protected reservation cannot be adopted by TUS; the server rejects
        // that combination. Let the TUS completion mint the link instead.
        val earlyReserveTransport =
            planned == UploadTransport.TUS ||
                planned == UploadTransport.TUS_PARALLEL ||
                planned == UploadTransport.RELAY
        val canAdoptReservation = request.password.isNullOrEmpty() ||
            planned == UploadTransport.RELAY
        val needsEarlyReserve =
            request.quickLink && earlyReserveTransport && canAdoptReservation
        val preReserved = if (needsEarlyReserve) reserveRelay(request) else null
        if (preReserved != null) request.onReserved(preReserved)

        return try {
            when (planned) {
                UploadTransport.DIRECT -> direct(request, config)
                UploadTransport.PARALLEL -> parallel(request, config)
                UploadTransport.TUS -> tus(request, config, preReserved)
                UploadTransport.TUS_PARALLEL -> tusParallel(request, config, preReserved)
                UploadTransport.RELAY -> relay(request, config, preReserved)
            }
        } catch (unavailable: TransportUnavailable) {
            // The fast path is advertised but unusable (older node, no public
            // host, a proxy rejecting the body). Fall back gracefully.
            val fallback = fallbackTransport(config, request, planned)
            val reserved = preReserved ?: when {
                !request.quickLink -> null
                fallback == UploadTransport.RELAY -> reserveRelay(request)
                request.password.isNullOrEmpty() -> reserveRelay(request)
                else -> null
            }
            if (reserved != null && preReserved == null) request.onReserved(reserved)
            try {
                when (fallback) {
                    UploadTransport.TUS_PARALLEL -> tusParallel(request, config, reserved)
                    UploadTransport.TUS -> tus(request, config, reserved)
                    else -> relay(request, config, reserved)
                }
            } catch (second: TransportUnavailable) {
                if (fallback == UploadTransport.TUS_PARALLEL) {
                    tus(request, config, reserved)
                } else {
                    throw second
                }
            }
        }
    }

    private fun fallbackTransport(
        config: ServerConfig,
        request: UploadRequest,
        planned: UploadTransport,
    ): UploadTransport {
        val size = request.source.size
        val unprotected = request.password.isNullOrEmpty()
        if (size > TUS_THRESHOLD_BYTES) {
            return if (unprotected &&
                planned != UploadTransport.TUS_PARALLEL &&
                size >= TUS_PARALLEL_THRESHOLD_BYTES &&
                config.maxTusParallelParts >= 2
            ) {
                UploadTransport.TUS_PARALLEL
            } else {
                UploadTransport.TUS
            }
        }
        return UploadTransport.RELAY
    }

    // ---- direct single request -------------------------------------------

    private suspend fun direct(request: UploadRequest, config: ServerConfig): UploadResult {
        val reserve = reserveDirect(request)
        val reserved = reserve.toLink()
        if (request.quickLink) request.onReserved(reserved)

        val reporter = ProgressReporter(request.source.size, request.onProgress)
        try {
            withRetry(attempts = 3, onRetry = { _, _ -> reporter.reset() }) {
                try {
                    api.putBytes(
                        uploadUrl = reserve.uploadUrl,
                        ticket = reserve.ticket,
                        content = ByteRangeContent(
                            source = request.source,
                            offset = 0,
                            length = request.source.size,
                        ) { sent -> reporter.report(sent) },
                    )
                } catch (error: ApiException) {
                    // A retried attempt can land while the previous response is lost.
                    if (error.status != 409) throw error
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            runCatching { api.deleteFile(reserve.fileId, reserve.deleteToken) }
            throw if (error is TransportUnavailable) error else TransportUnavailable(error)
        }
        reporter.complete()

        val response = completeDirect(reserve.fileId, reserve.ticket, reserved)
        return UploadResult(response, UploadTransport.DIRECT, reserved)
    }

    private suspend fun reserveDirect(request: UploadRequest) =
        try {
            withRetry(attempts = 3) {
                api.reserveDirect(
                    DirectReserveRequest(
                        filename = request.source.filename,
                        mimeType = request.source.mimeType.ifBlank { "application/octet-stream" },
                        fileSize = request.source.size,
                        ttlHours = request.ttlHours,
                        host = request.host,
                        password = request.password,
                    ),
                )
            }
        } catch (error: ApiException) {
            if (error.isAuth) throw error
            throw TransportUnavailable(error)
        }

    private suspend fun completeDirect(
        fileId: String,
        ticket: String,
        reserved: ReservedLink,
    ): UploadResponse = try {
        api.completeDirect(fileId, ticket)
    } catch (error: ApiException) {
        if (error.status == 409 || error.status == 404 || error.status == 400) {
            // The bytes are stored but the completion call was replayed.
            runCatching { api.fileInfo(fileId) }.getOrNull()
                ?.withFallback(reserved)
                ?: throw error
        } else {
            throw error
        }
    }

    // ---- direct parallel --------------------------------------------------

    private suspend fun parallel(request: UploadRequest, config: ServerConfig): UploadResult {
        val size = request.source.size
        val target = minOf(DIRECT_PART_TARGET_BYTES, config.directParallelMaxPartBytes)
            .coerceAtLeast(1)
        val maxParts = config.directParallelMaxParts.coerceIn(2, 64)
        val numParts = ((size + target - 1) / target)
            .toInt()
            .coerceIn(2, maxParts)

        val reserve = try {
            withRetry(attempts = 3) {
                api.reserveParallel(
                    ParallelReserveRequest(
                        filename = request.source.filename,
                        mimeType = request.source.mimeType.ifBlank { "application/octet-stream" },
                        fileSize = size,
                        ttlHours = request.ttlHours,
                        numParts = numParts,
                        host = request.host,
                    ),
                )
            }
        } catch (error: ApiException) {
            if (error.isAuth) throw error
            throw TransportUnavailable(error)
        }

        val reserved = ReservedLink(
            id = reserve.fileId,
            url = reserve.url,
            rawUrl = reserve.rawUrl,
            downloadUrl = reserve.downloadUrl,
            deleteToken = reserve.deleteToken,
        )
        if (request.quickLink) request.onReserved(reserved)

        val ordered = reserve.parts.sortedBy { it.partIndex }
        if (ordered.isEmpty()) throw TransportUnavailable(null)

        var nextOffset = 0L
        val parts = ordered.map { part ->
            val start = nextOffset
            nextOffset += part.partSize
            Triple(part, start, part.partSize)
        }

        val reporter = ProgressReporter(size, request.onProgress)
        val aggregate = AggregateProgress(ordered.size, reporter)
        val gate = Semaphore(minOf(DIRECT_PART_CONCURRENCY, parts.size).coerceAtLeast(1))

        try {
            coroutineScope {
                parts.map { (part, offset, length) ->
                    async {
                        gate.withPermit {
                            if (length <= 0) return@withPermit
                            var partSent = 0L
                            withRetry(
                                attempts = 3,
                                onRetry = { _, _ ->
                                    partSent = 0
                                    aggregate.update(part.partIndex, 0)
                                },
                            ) {
                                try {
                                    api.putBytes(
                                        uploadUrl = part.uploadUrl,
                                        ticket = part.ticket,
                                        content = ByteRangeContent(
                                            source = request.source,
                                            offset = offset,
                                            length = length,
                                        ) { sent ->
                                            partSent = sent
                                            aggregate.update(part.partIndex, sent)
                                        },
                                    )
                                } catch (error: ApiException) {
                                    // A retried part may already be stored; the
                                    // node reports it as a conflict, not a failure.
                                    if (error.status != 409) throw error
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            runCatching { api.deleteFile(reserve.fileId, reserve.deleteToken) }
            throw if (error is TransportUnavailable) error else TransportUnavailable(error)
        }
        reporter.complete()

        val partIds = ordered.map { it.partId }
        val response = completeParallel(reserve.fileId, reserve.ticket, partIds, reserved)
        return UploadResult(response, UploadTransport.PARALLEL, reserved)
    }

    private suspend fun completeParallel(
        fileId: String,
        ticket: String,
        partIds: List<String>,
        reserved: ReservedLink,
    ): UploadResponse = try {
        api.completeParallel(fileId, ticket, partIds)
    } catch (error: ApiException) {
        if (error.status == 409 || error.status == 404 || error.status == 400) {
            runCatching { api.fileInfo(fileId) }.getOrNull()
                ?.withFallback(reserved)
                ?: throw error
        } else {
            throw error
        }
    }

    // ---- tus --------------------------------------------------------------

    private suspend fun tus(
        request: UploadRequest,
        config: ServerConfig,
        reserved: ReservedLink?,
    ): UploadResult {
        val size = request.source.size
        val metadata = tusMetadata(request, reserved)

        val reporter = ProgressReporter(size, request.onProgress)
        var tusId = api.tusCreate(metadata, size)
        var offset = 0L
        var chunk = TUS_CHUNK_START
        var completed: UploadResponse? = null

        try {
            while (offset < size) {
                val startOffset = offset
                val startMs = nowEpochMillis()
                val chunkNow = chunk

                val result = withRetry(
                    attempts = 4,
                    onRetry = { _, _ ->
                        val serverOffset = api.tusOffset(tusId)
                            ?: throw TusSessionLost()
                        offset = serverOffset
                        reporter.reset(offset)
                    },
                ) {
                    val remaining = size - offset
                    if (remaining <= 0) return@withRetry null
                    val length = minOf(chunkNow, remaining)
                    api.tusPatch(
                        id = tusId,
                        offset = offset,
                        content = ByteRangeContent(
                            source = request.source,
                            offset = offset,
                            length = length,
                        ) { sent -> reporter.report(offset + sent) },
                    )
                }

                if (result == null) break
                offset = result.newOffset
                result.response?.let { completed = it }
                reporter.report(offset, force = true)

                val elapsed = (nowEpochMillis() - startMs).coerceAtLeast(1)
                chunk = adaptChunk(chunkNow, offset - startOffset, elapsed)
            }
        } catch (lost: TusSessionLost) {
            if (reserved != null) {
                throw ApiException(
                    code = "UPLOAD_EXPIRED",
                    message = "The resumable upload expired. Please try again.",
                    status = 410,
                )
            }
            // No reservation to preserve: start a fresh session and go again.
            return tus(request.copy(transport = UploadTransport.TUS), config, null)
        }

        val response = completed
            ?: reserved?.let { runCatching { api.fileInfo(it.id) }.getOrNull() }
                ?.withFallback(reserved)
            ?: throw ApiException(
                code = "UPLOAD_INCOMPLETE",
                message = "The upload finished but the server did not confirm it",
                status = 502,
            )
        return UploadResult(response, UploadTransport.TUS, reserved)
    }

    /**
     * Splits one file across several TUS resources. The server concatenates
     * the parts when the last one finalizes; protected files stay sequential
     * because the server rejects that combination.
     */
    private suspend fun tusParallel(
        request: UploadRequest,
        config: ServerConfig,
        reserved: ReservedLink?,
    ): UploadResult {
        val size = request.source.size
        val maxParts = config.maxTusParallelParts.coerceIn(0, 64)
        if (maxParts < 2) throw TransportUnavailable(null)
        val parts = minOf(TUS_PARALLEL_PARTS, maxParts)
        val step = (size + parts - 1) / parts
        val sessionId = "${client.name}-${nowEpochMillis()}-${Random.nextInt(100_000, 999_999)}"
        val baseMetadata = tusMetadata(request, reserved)

        fun partMetadata(index: Int): Map<String, String> = baseMetadata + mapOf(
            "session_id" to sessionId,
            "part_index" to index.toString(),
            "total_parts" to parts.toString(),
        )

        val created = try {
            coroutineScope {
                (0 until parts).map { index ->
                    async {
                        val length = minOf(step, size - index * step)
                        val result = withRetry(attempts = 3) {
                            api.tusCreatePart(partMetadata(index), length)
                        }
                        if (result.completing) {
                            throw ApiException(
                                code = "TUS_PART_COMPLETE",
                                message = "Part is already complete",
                                status = 409,
                            )
                        }
                        result
                    }
                }.awaitAll()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ApiException) {
            if (error.isAuth) throw error
            throw TransportUnavailable(error)
        } catch (error: Throwable) {
            throw TransportUnavailable(error)
        }

        val reporter = ProgressReporter(size, request.onProgress)
        val aggregate = AggregateProgress(parts, reporter)
        val completedLock = Mutex()
        var completed: UploadResponse? = null

        try {
            coroutineScope {
                created.mapIndexed { index, part ->
                    async {
                        val partBase = index * step
                        val length = minOf(step, size - partBase)
                        val response = uploadTusPart(
                            source = request.source,
                            index = index,
                            partBase = partBase,
                            partLength = length,
                            initial = part,
                            metadata = partMetadata(index),
                            aggregate = aggregate,
                        )
                        if (response != null) {
                            completedLock.withLock {
                                if (completed == null) completed = response
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Let the caller retry on the sequential path instead of failing
            // the whole upload after the reservation was already minted.
            runCatching {
                coroutineScope {
                    created.forEach { part -> async { runCatching { api.tusDelete(part.id) } } }
                }
            }
            throw if (error is TransportUnavailable) error else TransportUnavailable(error)
        }
        reporter.complete()

        val response = completedLock.withLock { completed }
            ?: reserved?.let { runCatching { api.fileInfo(it.id) }.getOrNull() }
                ?.withFallback(reserved)
            ?: throw ApiException(
                code = "UPLOAD_INCOMPLETE",
                message = "The upload finished but the server did not confirm it",
                status = 502,
            )
        return UploadResult(response, UploadTransport.TUS_PARALLEL, reserved)
    }

    private suspend fun uploadTusPart(
        source: UploadSource,
        index: Int,
        partBase: Long,
        partLength: Long,
        initial: TusCreateResult,
        metadata: Map<String, String>,
        aggregate: AggregateProgress,
    ): UploadResponse? {
        var id = initial.id
        var offset = initial.offset.coerceIn(0, partLength)
        var chunk = TUS_PARALLEL_CHUNK
        var completed: UploadResponse? = null

        aggregate.update(index, offset)
        while (offset < partLength) {
            val startOffset = offset
            val startMs = nowEpochMillis()
            val chunkNow = chunk

            val result = try {
                withRetry(
                    attempts = 4,
                    onRetry = { _, _ ->
                        val serverOffset = api.tusOffset(id)
                        if (serverOffset != null) {
                            offset = serverOffset
                            aggregate.update(index, offset)
                        }
                    },
                ) {
                    val remaining = partLength - offset
                    if (remaining <= 0) return@withRetry null
                    val length = minOf(chunkNow, remaining)
                    api.tusPatch(
                        id = id,
                        offset = offset,
                        content = ByteRangeContent(
                            source = source,
                            offset = partBase + offset,
                            length = length,
                        ) { sent -> aggregate.update(index, offset + sent) },
                    )
                }
            } catch (lost: ApiException) {
                if (lost.status != 404 && lost.status != 409) throw lost
                val serverOffset = api.tusOffset(id)
                if (serverOffset != null) {
                    offset = serverOffset
                    aggregate.update(index, offset)
                    continue
                }
                val recreated = api.tusCreatePart(metadata, partLength)
                if (recreated.completing) return completed
                id = recreated.id
                offset = recreated.offset
                aggregate.update(index, offset)
                continue
            }

            if (result == null) break
            offset = result.newOffset
            result.response?.let { completed = it }
            aggregate.update(index, offset)

            val elapsed = (nowEpochMillis() - startMs).coerceAtLeast(1)
            chunk = adaptChunk(chunkNow, offset - startOffset, elapsed)
        }
        return completed
    }

    // ---- relay multipart --------------------------------------------------

    private suspend fun relay(
        request: UploadRequest,
        config: ServerConfig,
        reserved: ReservedLink?,
    ): UploadResult {
        val fields = buildList {
            add("ttl_hours" to request.ttlHours.toString())
            add("client" to client.name)
            request.host?.let { add("host" to it) }
            add("upload_mode" to config.uploadMode)
            reserved?.let { add("reserve_id" to it.id) }
            request.password?.let { add("password" to it) }
        }

        val reporter = ProgressReporter(request.source.size, request.onProgress)
        val boundary = randomBoundary()

        val response = withRetry(
            attempts = if (reserved != null) 3 else 2,
            onRetry = { _, _ -> reporter.reset() },
        ) {
            api.uploadRelay(
                content = MultipartUploadContent(
                    fields = fields,
                    file = request.source,
                    boundary = boundary,
                    onProgress = { sent -> reporter.report(sent) },
                ),
                deleteToken = reserved?.deleteToken,
            )
        }
        reporter.complete()
        return UploadResult(response, UploadTransport.RELAY, reserved)
    }

    private suspend fun reserveRelay(request: UploadRequest): ReservedLink? = try {
        val reserve = api.reserveRelay(
            RelayReserveRequest(
                filename = request.source.filename,
                mimeType = request.source.mimeType.ifBlank { "application/octet-stream" },
                ttlHours = request.ttlHours,
                host = request.host,
                fileSize = request.source.size,
                password = request.password,
            ),
        )
        ReservedLink(
            id = reserve.id,
            url = reserve.url,
            rawUrl = reserve.rawUrl,
            downloadUrl = reserve.downloadUrl,
            deleteToken = reserve.deleteToken,
        )
    } catch (error: ApiException) {
        if (request.quickLink && error.status in 400..499) null else throw error
    }

    private fun tusMetadata(
        request: UploadRequest,
        reserved: ReservedLink?,
    ): Map<String, String> = buildMap {
        put("filename", request.source.filename)
        put("mimetype", request.source.mimeType.ifBlank { "application/octet-stream" })
        put("ttl", request.ttlHours.toString())
        put("file_size", request.source.size.toString())
        put("client", client.name)
        request.host?.let { put("host", it) }
        request.password?.let { put("password", it) }
        reserved?.let {
            put("reserve_id", it.id)
            put("delete_token", it.deleteToken)
        }
    }

    private fun adaptChunk(current: Long, bytesSent: Long, elapsedMs: Long): Long {
        if (bytesSent <= 0) return TUS_CHUNK_START
        val bytesPerMs = bytesSent.toDouble() / elapsedMs
        val target = (bytesPerMs * 4_000).toLong()
        return when {
            elapsedMs < 2_000 -> (current * 2).coerceAtMost(TUS_CHUNK_MAX)
            elapsedMs > 8_000 -> (current / 2).coerceAtLeast(TUS_CHUNK_MIN)
            else -> target.coerceIn(TUS_CHUNK_MIN, TUS_CHUNK_MAX)
        }
    }
}

private class TransportUnavailable(cause: Throwable?) : Exception(cause)

private class TusSessionLost : Exception()

private fun dev.juicey.juicebox.core.model.DirectReserveResponse.toLink() = ReservedLink(
    id = fileId,
    url = url,
    rawUrl = rawUrl,
    downloadUrl = downloadUrl,
    deleteToken = deleteToken,
)

private fun FileInfoResponse.withFallback(reserved: ReservedLink?): UploadResponse = UploadResponse(
    id = id,
    url = url.ifEmpty { reserved?.url.orEmpty() },
    rawUrl = rawUrl.ifEmpty { reserved?.rawUrl.orEmpty() },
    downloadUrl = downloadUrl.ifEmpty { reserved?.downloadUrl.orEmpty() },
    filename = filename,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    expiresAt = expiresAt,
    deleteToken = deleteToken.ifEmpty { reserved?.deleteToken.orEmpty() },
    status = status,
    protected = protected,
    isEncrypted = isEncrypted,
)

private suspend fun <T> withRetry(
    attempts: Int,
    onRetry: suspend (Int, Throwable) -> Unit = { _, _ -> },
    block: suspend (Int) -> T,
): T {
    var attempt = 1
    while (true) {
        try {
            return block(attempt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val retryable = when (error) {
                is ApiException -> error.retryable
                is TusSessionLost -> false
                is TransportUnavailable -> false
                else -> true
            }
            if (!retryable || attempt >= attempts) throw error
            delay((500L shl (attempt - 1)).coerceAtMost(4_000L))
            onRetry(attempt, error)
            attempt++
        }
    }
}

private class ProgressReporter(
    private val total: Long,
    private val emit: suspend (UploadProgress) -> Unit,
) {
    private val mutex = Mutex()
    private var lastMs = 0L
    private var lastBytes = 0L
    private var speed = 0.0

    suspend fun report(sent: Long, force: Boolean = false) {
        val progress = mutex.withLock {
            val now = nowEpochMillis()
            if (lastMs == 0L) {
                lastMs = now
                lastBytes = sent
            }
            val elapsed = now - lastMs
            if (!force && elapsed < 100) return
            if (elapsed > 0) {
                val instant = (sent - lastBytes).coerceAtLeast(0) * 1000.0 / elapsed
                speed = if (speed <= 0.0) instant else speed * 0.7 + instant * 0.3
                lastMs = now
                lastBytes = sent
            }
            UploadProgress(sent.coerceIn(0, total), total, speed.toLong())
        }
        emit(progress)
    }

    suspend fun reset(sent: Long = 0) {
        mutex.withLock {
            lastMs = 0
            lastBytes = sent
            speed = 0.0
        }
        emit(UploadProgress(sent.coerceIn(0, total), total, 0))
    }

    suspend fun complete() {
        mutex.withLock {
            lastMs = 0
            lastBytes = total
        }
        emit(UploadProgress(total, total, 0))
    }
}

private class AggregateProgress(
    partCount: Int,
    private val reporter: ProgressReporter,
) {
    private val mutex = Mutex()
    private val sent = LongArray(partCount)

    suspend fun update(index: Int, bytes: Long) {
        if (index !in sent.indices) return
        val sum = mutex.withLock {
            sent[index] = bytes
            sent.sum()
        }
        reporter.report(sum)
    }
}
