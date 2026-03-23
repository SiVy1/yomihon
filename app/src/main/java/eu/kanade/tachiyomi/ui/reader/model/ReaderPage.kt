package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
    contentType: ContentType = ContentType.IMAGE,
    text: String? = null,
) : Page(index, url, imageUrl, null, contentType, text) {

    open lateinit var chapter: ReaderChapter
}
