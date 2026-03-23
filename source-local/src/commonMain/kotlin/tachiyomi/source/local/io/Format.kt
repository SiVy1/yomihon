package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension
import tachiyomi.source.local.io.Archive.isSupported as isArchiveSupported

sealed interface Format {
    data class Directory(val file: UniFile) : Format
    data class Archive(val file: UniFile) : Format
    data class Epub(val file: UniFile) : Format
    data class Text(val file: UniFile) : Format
    data class Pdf(val file: UniFile) : Format

    class UnknownFormatException : Exception()

    companion object {

        private val textExtensions = setOf("txt", "md")

        fun isSupported(file: UniFile): Boolean {
            return try {
                valueOf(file)
                true
            } catch (_: UnknownFormatException) {
                false
            }
        }

        fun valueOf(file: UniFile) = when {
            file.isDirectory -> Directory(file)
            file.extension.equals("epub", true) -> Epub(file)
            textExtensions.contains(file.extension.orEmpty().lowercase()) -> Text(file)
            file.extension.equals("pdf", true) -> Pdf(file)
            isArchiveSupported(file) -> Archive(file)
            else -> throw UnknownFormatException()
        }
    }
}
