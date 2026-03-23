package eu.kanade.tachiyomi.ui.reader.loader

import com.hippo.unifile.UniFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * Loader that extracts text from local PDF chapters and exposes it as text pages.
 */
internal class PdfTextPageLoader(
    private val file: UniFile,
) : PageLoader() {

    companion object {
        private const val EMPTY_PDF_FALLBACK = "This PDF has no extractable text. It may be image-scanned and require OCR."
    }

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val textPages = file.openInputStream().use { input ->
            PDDocument.load(input).use { document ->
                val stripper = PDFTextStripper()
                (1..document.numberOfPages)
                    .map { pageNumber ->
                        stripper.startPage = pageNumber
                        stripper.endPage = pageNumber
                        stripper.getText(document)
                    }
                    .flatMap { TextChunker.chunk(it) }
            }
        }

        val normalizedPages = if (textPages.isEmpty()) listOf(EMPTY_PDF_FALLBACK) else textPages

        return normalizedPages.mapIndexed { index, chunk ->
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
