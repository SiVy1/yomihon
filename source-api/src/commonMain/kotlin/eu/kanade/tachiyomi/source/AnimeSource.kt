package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * A source that exposes anime metadata and episode listings.
 */
interface AnimeSource : Source {

    /**
     * Get the updated details for an anime entry.
     *
     * @since extensions-lib 1.5
     * @param anime the anime to update.
     * @return the updated anime.
     */
    @Suppress("DEPRECATION")
    suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return fetchAnimeDetails(anime).awaitSingle()
    }

    /**
     * Get all the available episodes for an anime entry.
     *
     * @since extensions-lib 1.5
     * @param anime the anime to update.
     * @return the episodes for the anime.
     */
    @Suppress("DEPRECATION")
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return fetchEpisodeList(anime).awaitSingle()
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getAnimeDetails"),
    )
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getEpisodeList"),
    )
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        throw IllegalStateException("Not used")
}
