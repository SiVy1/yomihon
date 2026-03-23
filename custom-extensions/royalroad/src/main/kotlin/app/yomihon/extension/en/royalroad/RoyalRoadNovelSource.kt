package app.yomihon.extension.en.royalroad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.TextPage
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class RoyalRoadNovelSource : ParsedHttpSource() {

    override val name: String = "Royal Road"
    override val baseUrl: String = "https://www.royalroad.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/fictions/best-rated?page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/fictions/latest-updates?page=$page", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = if (encodedQuery.isBlank()) {
            "$baseUrl/fictions/trending?page=$page"
        } else {
            "$baseUrl/fictions/search?title=$encodedQuery&page=$page"
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector(): String = fictionCardSelector

    override fun popularMangaFromElement(element: Element): SManga = fictionFromElement(element)

    override fun popularMangaNextPageSelector(): String? = nextPageSelector

    override fun latestUpdatesSelector(): String = fictionCardSelector

    override fun latestUpdatesFromElement(element: Element): SManga = fictionFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = nextPageSelector

    override fun searchMangaSelector(): String = fictionCardSelector

    override fun searchMangaFromElement(element: Element): SManga = fictionFromElement(element)

    override fun searchMangaNextPageSelector(): String? = nextPageSelector

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.selectFirstOrNull(
            "h1[property=name]",
            "h1.fiction-title",
            "h1",
        )?.text().orEmpty()

        manga.author = document.selectFirstOrNull(
            "a[href*=/profile/]",
            ".fiction-info a[href*=/profile/]",
        )?.text()

        manga.artist = null

        manga.description = document.selectFirstOrNull(
            ".description .hidden-content",
            ".description",
            "div[property=description]",
        )?.text()

        manga.genre = document.select(
            ".fiction-tag",
            "a[href*=/fictions/search?tagsAdd]",
            ".tags a",
        ).map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }

        manga.status = parseStatus(document)

        manga.thumbnail_url = document.selectFirstOrNull(
            ".fiction-page-thumbnail img",
            "img[alt*=Cover]",
            "meta[property=og:image]",
        )?.let {
            if (it.tagName() == "meta") it.attr("abs:content") else it.attr("abs:src")
        }

        return manga
    }

    override fun chapterListSelector(): String = "table#chapters tbody tr, tr.chapter-row"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        val link = element.selectFirstOrNull(
            "td:first-child a[href*=/chapter/]",
            "a[href*=/chapter/]",
        )

        val chapterUrl = link?.attr("abs:href").orEmpty()
        chapter.setUrlWithoutDomain(chapterUrl)
        chapter.name = link?.text()?.trim().orEmpty().ifBlank { "Chapter" }
        chapter.chapter_number = parseChapterNumber(chapter.name)
        chapter.date_upload = parseChapterDate(element)

        return chapter
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)
        return chapters.distinctBy { it.url }
    }

    override fun pageListParse(document: Document): List<Page> {
        val content = document.selectFirstOrNull(
            ".chapter-content",
            "div.chapter-content",
            "article",
        ) ?: throw Exception("Unable to find chapter text content")

        content.select("script, style, iframe, .adsbygoogle, .author-note-portlet").remove()

        val blocks = content.select("h1, h2, h3, h4, h5, h6, p, blockquote, li, pre")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val rawText = if (blocks.isNotEmpty()) {
            blocks.joinToString("\n\n")
        } else {
            content.text().trim()
        }

        if (rawText.isBlank()) {
            throw Exception("Chapter text is empty")
        }

        return splitText(rawText, maxChars = 2800)
            .mapIndexed { index, chunk ->
                TextPage(index = index, url = "", text = chunk)
            }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Royal Road is a text-only source")
    }

    override fun chapterPageParse(response: Response): SChapter {
        return SChapter.create()
    }

    private fun fictionFromElement(element: Element): SManga {
        val manga = SManga.create()

        val link = element.selectFirstOrNull(
            "h2 a[href*=/fiction/]",
            "a.font-white[href*=/fiction/]",
            "a[href*=/fiction/]",
        )

        val title = link?.text()?.trim().orEmpty()
        val url = link?.attr("abs:href").orEmpty()

        manga.title = title
        manga.setUrlWithoutDomain(url)

        manga.thumbnail_url = element.selectFirstOrNull(
            "img[src]",
            "img[data-src]",
        )?.let { img ->
            img.attr("abs:src").ifBlank { img.attr("abs:data-src") }
        }

        return manga
    }

    private fun parseStatus(document: Document): Int {
        val statusText = document.select(".fiction-info, .stats-content, .fiction-stats")
            .text()
            .lowercase()

        return when {
            "completed" in statusText -> SManga.COMPLETED
            "dropped" in statusText || "cancelled" in statusText -> SManga.CANCELLED
            "hiatus" in statusText -> SManga.ON_HIATUS
            else -> SManga.ONGOING
        }
    }

    private fun parseChapterNumber(name: String): Float {
        val match = chapterNumberRegex.find(name)
        return match?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: -1f
    }

    private fun parseChapterDate(element: Element): Long {
        val time = element.selectFirstOrNull("time[unixtime]", "time[data-time]", "time[datetime]")
            ?: return 0L

        val unix = time.attr("unixtime").ifBlank { time.attr("data-time") }
        if (unix.isNotBlank()) {
            val seconds = unix.toLongOrNull()
            if (seconds != null) return seconds * 1000L
        }

        return 0L
    }

    private fun splitText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)

        val paragraphs = text.split(paragraphSplitRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val pages = mutableListOf<String>()
        var current = StringBuilder()

        for (paragraph in paragraphs) {
            if (paragraph.length >= maxChars) {
                if (current.isNotEmpty()) {
                    pages += current.toString().trim()
                    current = StringBuilder()
                }

                paragraph.chunked(maxChars).forEach { chunk ->
                    pages += chunk.trim()
                }
                continue
            }

            val projectedLength = current.length + if (current.isEmpty()) 0 else 2 + paragraph.length
            if (projectedLength > maxChars && current.isNotEmpty()) {
                pages += current.toString().trim()
                current = StringBuilder(paragraph)
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(paragraph)
            }
        }

        if (current.isNotEmpty()) {
            pages += current.toString().trim()
        }

        return pages.ifEmpty { listOf(text) }
    }

    private fun Document.selectFirstOrNull(vararg selectors: String): Element? {
        for (selector in selectors) {
            val match = selectFirst(selector)
            if (match != null) return match
        }
        return null
    }

    private fun Element.selectFirstOrNull(vararg selectors: String): Element? {
        for (selector in selectors) {
            val match = selectFirst(selector)
            if (match != null) return match
        }
        return null
    }

    private fun Element.select(vararg selectors: String): List<Element> {
        return selectors.flatMap { select(it) }
    }

    companion object {
        private const val fictionCardSelector =
            ".fiction-list-item, article.fiction-list-item, .fiction-list .fiction-list-item"
        private const val nextPageSelector = "a[rel=next], a[aria-label='Next']"
        private val chapterNumberRegex = Regex("(?:chapter|ch\\.?|c)\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        private val paragraphSplitRegex = Regex("\\n\\s*\\n")
    }
}