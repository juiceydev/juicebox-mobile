package dev.juicey.juicebox.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dev.juicey.juicebox.core.upload.UploadSource
import okio.Source
import okio.buffer
import okio.source
import java.io.IOException

/** Streams straight from a content URI; the provider re-opens on retries. */
class UriUploadSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val filename: String,
    override val mimeType: String,
    override val size: Long,
) : UploadSource {

    override fun open(offset: Long): Source {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
        if (offset > 0) {
            var remaining = offset
            while (remaining > 0) {
                val skipped = stream.skip(remaining)
                if (skipped <= 0) {
                    // Some providers report no skip support; fall back to reading.
                    val scratch = ByteArray(64 * 1024)
                    val read = stream.read(scratch)
                    if (read <= 0) break
                    remaining -= read
                } else {
                    remaining -= skipped
                }
            }
        }
        return stream.source().buffer()
    }

    companion object {
        fun from(resolver: ContentResolver, uri: Uri): UriUploadSource {
            val meta = resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                name to size
            }
            val name = meta?.first ?: uri.lastPathSegment ?: "file"
            val size = meta?.second?.takeIf { it >= 0 } ?: 0L
            val mime = resolver.getType(uri)
                ?: MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            return UriUploadSource(resolver, uri, name, mime, size)
        }
    }
}
