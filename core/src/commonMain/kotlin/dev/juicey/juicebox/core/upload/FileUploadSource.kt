package dev.juicey.juicebox.core.upload

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.source

/** A file on local storage: upload staging copies, text pastes, iOS temp files. */
class FileUploadSource(
    private val path: Path,
    override val filename: String,
    override val mimeType: String,
    override val size: Long,
) : UploadSource {

    override fun open(offset: Long): Source =
        FileSystem.SYSTEM.openReadOnly(path).source(offset).buffer()

    companion object {
        fun of(
            path: String,
            filename: String,
            mimeType: String,
        ): FileUploadSource {
            val p = path.toPath()
            return FileUploadSource(p, filename, mimeType, FileSystem.SYSTEM.metadata(p).size ?: 0L)
        }
    }
}
