package app.yomihon.extension.en.subsplease

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.TorrentAnimeSourceBase
import eu.kanade.tachiyomi.source.model.AnimesPage
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.TorrentDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SubsPleaseAnimeSource : TorrentAnimeSourceBase() {

    private val network: NetworkHelper by injectLazy()
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient
        get() = network.client

    private val headers: Headers by lazy {
        Headers.Builder()
            .add("User-Agent", network.defaultUserAgentProvider())
            .build()
    }

    override val id: Long by lazy { generateId(name, lang, versionId) }
    override val name: String = "SubsPlease"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    private val baseUrl = "https://subsplease.org"
    private val versionId = 1
    private val timezone = "Etc/UTC"

    override fun getFilterList(): FilterList = FilterList()

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        return Observable.fromCallable {
            if (page != 1) return@fromCallable AnimesPage(emptyList(), false)

            val document = fetchDocument("$baseUrl/shows/")
            val animes = document.select(".all-shows-link a[href]")
                .map(::animeFromShowLink)
                .distinctBy { it.url }

            AnimesPage(animes, false)
        }
    }

    override fun fetchSearchAnime(page: Int, query: String, filters: FilterList): Observable<AnimesPage> {
        return Observable.fromCallable {
            if (page != 1) return@fromCallable AnimesPage(emptyList(), false)
            if (query.isBlank()) return@fromCallable fetchPopularAnime(page).toBlocking().single()

            val releases = fetchReleaseFeed(
                function = "search",
                extraParams = mapOf("s" to query.trim()),
            )

            val animes = releases
                .mapNotNull(::animeFromRelease)
                .distinctBy { it.url }

            AnimesPage(animes, false)
        }
    }

    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> {
        return Observable.fromCallable {
            if (page != 1) return@fromCallable AnimesPage(emptyList(), false)

            val animes = fetchReleaseFeed("latest")
                .mapNotNull(::animeFromRelease)
                .distinctBy { it.url }

            AnimesPage(animes, false)
        }
    }

    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
        return Observable.fromCallable {
            val document = fetchDocument(baseUrl + anime.url)
            anime.copy().apply {
                title = document.selectFirst("h1.entry-title")?.text().orEmpty().ifBlank { anime.title }
                description = document.select(".series-syn p")
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .ifBlank { null }
                thumbnail_url = document.selectFirst("#secondary img[src], .sidebar img[src], img[src]")
                    ?.attr("abs:src")
                    ?.ifBlank { null }
                    ?: anime.thumbnail_url
                initialized = true
            }
        }
    }

    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        return Observable.fromCallable {
            val document = fetchDocument(baseUrl + anime.url)
            val sid = document.selectFirst("#show-release-table")?.attr("sid").orEmpty()
            if (sid.isBlank()) return@fromCallable emptyList()

            val episodes = fetchShowEpisodes(sid)
            episodes.map { (releaseTitle, release) ->
                episodeFromRelease(
                    showUrl = anime.url,
                    sid = sid,
                    releaseTitle = releaseTitle,
                    release = release,
                )
            }
        }
    }

    override fun fetchTorrentDescriptors(episode: SEpisode): Observable<List<TorrentDescriptor>> {
        return Observable.fromCallable {
            val episodeParams = parseEpisodeUrl(episode.url)
            val sid = episodeParams["sid"].orEmpty()
            if (sid.isBlank()) return@fromCallable emptyList()

            val releaseTitle = episodeParams["release"]
                ?.let(::decodeQueryValue)
                ?.ifBlank { null }
                ?: episode.name

            val release = fetchShowEpisodes(sid)
                .firstOrNull { (title, _) -> title == releaseTitle }
                ?.second
                ?: return@fromCallable emptyList()

            val publishedAt = release["release_date"].asString()?.let(::parseReleaseDate)
            release["downloads"].asArray().mapNotNull { download ->
                val downloadObject = download as? JsonObject ?: return@mapNotNull null
                val magnet = downloadObject["magnet"].asString()
                val torrentUrl = downloadObject["torrent"].asString()
                val rawQuality = downloadObject["res"].asString()
                val quality = rawQuality?.let { if (it.endsWith("p")) it else "${it}p" }

                TorrentDescriptor(
                    releaseTitle = releaseTitle,
                    magnetUri = magnet,
                    torrentUrl = torrentUrl,
                    infoHash = magnet?.let(::extractInfoHash),
                    sizeBytes = magnet?.let(::extractMagnetSize),
                    quality = quality,
                    fileNameHint = magnet?.let(::extractDisplayName),
                    publishedAt = publishedAt,
                    subtitleHint = "Embedded or sidecar subtitles",
                )
            }
        }
    }

    private fun fetchReleaseFeed(
        function: String,
        extraParams: Map<String, String> = emptyMap(),
    ): List<JsonObject> {
        val payload = fetchJson(
            apiUrl(
                function = function,
                extraParams = extraParams,
            ),
        )
        return when (payload) {
            is JsonArray -> payload.mapNotNull { it as? JsonObject }
            is JsonObject -> payload.values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
    }

    private fun fetchShowEpisodes(sid: String): List<Pair<String, JsonObject>> {
        val payload = fetchJson(
            apiUrl(
                function = "show",
                extraParams = mapOf("sid" to sid),
            ),
        )
        val episodes = payload.jsonObject["episode"] as? JsonObject ?: return emptyList()
        return episodes.entries
            .mapNotNull { entry ->
                val release = entry.value as? JsonObject ?: return@mapNotNull null
                entry.key to release
            }
    }

    private fun fetchDocument(url: String): Document {
        return client.newCall(GET(url, headers))
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Request failed: ${response.code} $url")
                }
                Jsoup.parse(response.body.string(), baseUrl)
            }
    }

    private fun fetchJson(url: String): JsonElement {
        return client.newCall(GET(url, headers))
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Request failed: ${response.code} $url")
                }
                json.parseToJsonElement(response.body.string())
            }
    }

    private fun animeFromShowLink(element: Element): SAnime {
        return SAnime.create().apply {
            title = element.attr("title").ifBlank { element.text().trim() }
            url = normalizeShowPath(element.attr("href"))
        }
    }

    private fun animeFromRelease(release: JsonObject): SAnime? {
        val title = release["show"].asString() ?: return null
        val page = release["page"].asString() ?: return null

        return SAnime.create().apply {
            this.title = title
            url = normalizeShowPath(page)
            thumbnail_url = release["image_url"].asString()?.let(::absoluteUrl)
        }
    }

    private fun episodeFromRelease(
        showUrl: String,
        sid: String,
        releaseTitle: String,
        release: JsonObject,
    ): SEpisode {
        val episodeLabel = release["episode"].asString().orEmpty()
        return SEpisode.create().apply {
            url = buildEpisodeUrl(showUrl, sid, releaseTitle)
            name = releaseTitle
            date_upload = release["release_date"].asString()?.let(::parseReleaseDate) ?: 0L
            episode_number = parseEpisodeNumber(episodeLabel)
            season_number = parseSeasonNumber(release["show"].asString().orEmpty())
            release_group = "SubsPlease"
        }
    }

    private fun buildEpisodeUrl(showUrl: String, sid: String, releaseTitle: String): String {
        val separator = if ('?' in showUrl) "&" else "?"
        return "$showUrl$separator" +
            "sid=${encodeQueryValue(sid)}&release=${encodeQueryValue(releaseTitle)}"
    }

    private fun parseEpisodeUrl(url: String): Map<String, String> {
        val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            baseUrl + url
        }
        val query = URI(fullUrl).rawQuery.orEmpty()
        if (query.isBlank()) return emptyMap()

        return query.split("&")
            .mapNotNull { part ->
                val keyValue = part.split("=", limit = 2)
                val key = keyValue.getOrNull(0).orEmpty()
                val value = keyValue.getOrNull(1).orEmpty()
                key.takeIf { it.isNotBlank() }?.let { it to value }
            }
            .toMap()
    }

    private fun apiUrl(
        function: String,
        extraParams: Map<String, String> = emptyMap(),
    ): String {
        val params = linkedMapOf("f" to function, "tz" to timezone)
        params.putAll(extraParams)
        return buildString {
            append("$baseUrl/api/?")
            append(
                params.entries.joinToString("&") { (key, value) ->
                    "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
                },
            )
        }
    }

    private fun normalizeShowPath(pathOrSlug: String): String {
        val raw = pathOrSlug.trim()
        val withoutBase = raw.removePrefix(baseUrl)
        val slug = withoutBase
            .removePrefix("/shows/")
            .removePrefix("shows/")
            .trim('/')

        return "/shows/$slug/"
    }

    private fun absoluteUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            baseUrl + url
        }
    }

    private fun parseEpisodeNumber(label: String): Float {
        val firstNumber = episodeRegex.find(label)?.groupValues?.getOrNull(1)
        return firstNumber?.toFloatOrNull() ?: -1f
    }

    private fun parseSeasonNumber(title: String): Int {
        val match = seasonRegex.find(title)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    private fun parseReleaseDate(value: String): Long {
        return runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun extractInfoHash(magnet: String): String? {
        return infoHashRegex.find(magnet)?.groupValues?.getOrNull(1)
    }

    private fun extractMagnetSize(magnet: String): Long? {
        return magnetSizeRegex.find(magnet)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun extractDisplayName(magnet: String): String? {
        val encoded = displayNameRegex.find(magnet)?.groupValues?.getOrNull(1) ?: return null
        return decodeQueryValue(encoded)
    }

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun decodeQueryValue(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    private fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    private fun JsonElement?.asString(): String? {
        return (this as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonElement?.asArray(): JsonArray {
        return this as? JsonArray ?: JsonArray(emptyList())
    }

    companion object {
        private val episodeRegex = Regex("(\\d+(?:\\.\\d+)?)")
        private val seasonRegex = Regex("\\bS(\\d+)\\b", RegexOption.IGNORE_CASE)
        private val infoHashRegex = Regex("xt=urn:btih:([^&]+)")
        private val magnetSizeRegex = Regex("[?&]xl=(\\d+)")
        private val displayNameRegex = Regex("[?&]dn=([^&]+)")
    }
}
