package dev.juicey.juicebox.core.upload

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import okio.Source
import okio.buffer

private const val BUFFER_SIZE = 64 * 1024

/** Streams a byte range without ever buffering the file in memory. */
class ByteRangeContent(
    private val source: UploadSource,
    private val offset: Long,
    private val length: Long,
    private val onProgress: suspend (Long) -> Unit = {},
) : OutgoingContent.WriteChannelContent() {

    override val contentType: ContentType = ContentType.Application.OctetStream
    override val contentLength: Long = length

    override suspend fun writeTo(channel: ByteWriteChannel) {
        var sent = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        source.open(offset).buffer().use { input ->
            while (sent < length) {
                val want = minOf(BUFFER_SIZE.toLong(), length - sent).toInt()
                val read = input.read(buffer, 0, want)
                if (read == -1) break
                channel.writeFully(buffer, 0, read)
                sent += read
                onProgress(sent)
            }
        }
        if (sent != length) {
            throw IllegalStateException("Source ended at $sent of $length bytes")
        }
    }
}

/**
 * Hand-rolled multipart body: Ktor's multipart builder wants the whole payload
 * in memory on some engines, and a 500 MB file would not survive that.
 */
class MultipartUploadContent(
    private val fields: List<Pair<String, String>>,
    private val file: UploadSource,
    private val boundary: String,
    private val onProgress: suspend (Long) -> Unit = {},
    private val onFileBytes: suspend (Long) -> Unit = {},
) : OutgoingContent.WriteChannelContent() {

    override val contentType: ContentType =
        ContentType.MultiPart.FormData.withParameter("boundary", boundary)

    private val prefix: ByteArray = buildString {
        for ((name, value) in fields) {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"").append(escapeName(name)).append("\"\r\n\r\n")
            append(value).append("\r\n")
        }
        append("--").append(boundary).append("\r\n")
        append("Content-Disposition: form-data; name=\"file\"; filename=\"")
            .append(escapeName(file.filename)).append("\"\r\n")
        append("Content-Type: ").append(file.mimeType.ifBlank { "application/octet-stream" }).append("\r\n\r\n")
    }.encodeToByteArray()

    private val suffix: ByteArray = "\r\n--$boundary--\r\n".encodeToByteArray()

    override val contentLength: Long = prefix.size + file.size + suffix.size

    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.writeFully(prefix, 0, prefix.size)

        var sent = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        file.open(0).buffer().use { input ->
            while (true) {
                val read = input.read(buffer, 0, BUFFER_SIZE)
                if (read == -1) break
                channel.writeFully(buffer, 0, read)
                sent += read
                onFileBytes(sent)
                onProgress(prefix.size + sent)
            }
        }
        if (sent != file.size) {
            throw IllegalStateException("Source ended at $sent of ${file.size} bytes")
        }
        channel.writeFully(suffix, 0, suffix.size)
        onProgress(contentLength)
    }

    private fun escapeName(value: String): String =
        value.replace("\"", "%22").replace("\r", "").replace("\n", "")
}

internal fun randomBoundary(): String {
    val chars = "0123456789abcdef"
    val sb = StringBuilder("----Juicebox")
    repeat(24) { sb.append(chars[((kotlin.random.Random.nextDouble() * 16).toInt())]) }
    return sb.toString()
}
