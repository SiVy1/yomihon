package eu.kanade.tachiyomi.ui.reader.loader

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import java.nio.charset.StandardCharsets

/**
 * Loader that prepares local plain-text chapters into reader pages.
 */
internal class LocalTextPageLoader(
    private val file: UniFile,
) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val text = file.openInputStream().use {
            String(it.readBytes(), StandardCharsets.UTF_8)
        }

        return TextChunker.chunk(text).mapIndexed { index, chunk ->
            ReaderPage(
                index = index,
                url = file.uri.toString(),
                contentType = Page.ContentType.TEXT,
                text = chunk,
            ).apply {
                status = Page.State.Ready
            }
        }
    }
}
