package dev.juicey.juicebox.core.upload

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer

/**
 * A file on local storage: upload staging copies, text pastes, iOS temp files.
 * The [FileSystem] is passed in because `FileSystem.SYSTEM` only exists per
 * platform, not in common metadata.
 */
class FileUploadSource(
    private val fileSystem: FileSystem,
    private val path: Path,
    override val filename: String,
    override val mimeType: String,
    override val size: Long,
) : UploadSource {

    override fun open(offset: Long): Source =
        fileSystem.openReadOnly(path).source(offset).buffer()

    companion object {
        fun of(
            fileSystem: FileSystem,
            path: String,
            filename: String,
            mimeType: String,
        ): FileUploadSource {
            val p = path.toPath()
            return FileUploadSource(
                fileSystem = fileSystem,
                path = p,
                filename = filename,
                mimeType = mimeType,
                size = fileSystem.metadata(p).size ?: 0L,
            )
        }
    }
}
