package dev.juicey.juicebox.core.upload

import okio.Buffer
import okio.Source

/**
 * A file the engine can read twice (retries and parallel parts need offsets).
 * The Android app implements this over a content URI; tests use [BytesUploadSource].
 */
interface UploadSource {
    val size: Long
    val filename: String
    val mimeType: String

    /** Opens a stream positioned at [offset]. The caller closes it. */
    fun open(offset: Long = 0): Source
}

class BytesUploadSource(
    private val bytes: ByteArray,
    override val filename: String,
    override val mimeType: String,
) : UploadSource {
    override val size: Long = bytes.size.toLong()

    override fun open(offset: Long): Source = Buffer().apply {
        write(bytes, offset.toInt(), bytes.size - offset.toInt())
    }
}

internal fun Source.skipFully(offset: Long) {
    var remaining = offset
    val scratch = Buffer()
    while (remaining > 0) {
        val skipped = read(scratch, minOf(remaining, 64L * 1024))
        if (skipped == -1L) break
        remaining -= skipped
        scratch.clear()
    }
}
