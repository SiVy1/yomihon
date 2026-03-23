package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import mihon.core.archive.EpubReader

/**
 * Loader that prepares epub text spine entries into reader pages.
 */
internal class EpubTextPageLoader(
    private val reader: EpubReader,
) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val textPages = reader.getTextFromPages()
            .flatMap { TextChunker.chunk(it) }

        return textPages.mapIndexed { index, chunk ->
            ReaderPage(
                index = index,
                contentType = Page.ContentType.TEXT,
                text = chunk,
            ).apply {
                status = Page.State.Ready
            }
        }
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
