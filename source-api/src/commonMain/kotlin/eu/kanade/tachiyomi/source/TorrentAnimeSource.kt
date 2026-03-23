package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.TorrentDescriptor
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * An anime source that resolves episodes into torrent metadata descriptors.
 */
interface TorrentAnimeSource : AnimeCatalogueSource {

    /**
     * Resolve an episode into one or more torrent descriptors.
     *
     * @since extensions-lib 1.5
     * @param episode the episode to resolve.
     * @return available torrent descriptors for the episode.
     */
    @Suppress("DEPRECATION")
    suspend fun getTorrentDescriptors(episode: SEpisode): List<TorrentDescriptor> {
        return fetchTorrentDescriptors(episode).awaitSingle()
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getTorrentDescriptors"),
    )
    fun fetchTorrentDescriptors(episode: SEpisode): Observable<List<TorrentDescriptor>> =
        throw IllegalStateException("Not used")
}
