package tachiyomi.data.anime

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.anime.model.Episode
import tachiyomi.domain.anime.model.EpisodeUpdate
import tachiyomi.domain.anime.repository.EpisodeRepository

class EpisodeRepositoryImpl(
    private val handler: DatabaseHandler,
) : EpisodeRepository {

    override suspend fun getEpisodeById(id: Long): Episode? {
        return handler.awaitOneOrNull { animeEpisodesQueries.getEpisodeById(id, ::mapEpisode) }
    }

    override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Episode? {
        return handler.awaitOneOrNull {
            animeEpisodesQueries.getEpisodeByUrlAndAnimeId(
                episodeUrl = url,
                animeId = animeId,
                mapper = ::mapEpisode,
            )
        }
    }

    override suspend fun getEpisodesByAnimeId(animeId: Long): List<Episode> {
        return handler.awaitList {
            animeEpisodesQueries.getEpisodesByAnimeId(animeId, ::mapEpisode)
        }
    }

    override fun getEpisodesByAnimeIdAsFlow(animeId: Long): Flow<List<Episode>> {
        return handler.subscribeToList {
            animeEpisodesQueries.getEpisodesByAnimeId(animeId, ::mapEpisode)
        }
    }

    override suspend fun update(episodeUpdate: EpisodeUpdate) {
        partialUpdate(episodeUpdate)
    }

    override suspend fun updateAll(episodeUpdates: List<EpisodeUpdate>) {
        partialUpdate(*episodeUpdates.toTypedArray())
    }

    override suspend fun upsertEpisodes(episodes: List<Episode>): List<Episode> {
        return try {
            handler.await(inTransaction = true) {
                episodes.map { episode ->
                    val existing = animeEpisodesQueries.getEpisodeByUrlAndAnimeId(
                        episodeUrl = episode.url,
                        animeId = episode.animeId,
                        mapper = ::mapEpisode,
                    ).executeAsOneOrNull()

                    if (existing == null) {
                        animeEpisodesQueries.insert(
                            animeId = episode.animeId,
                            url = episode.url,
                            name = episode.name,
                            releaseGroup = episode.releaseGroup,
                            seen = episode.seen,
                            bookmark = episode.bookmark,
                            lastSecondsWatched = episode.lastSecondsWatched,
                            totalSeconds = episode.totalSeconds,
                            episodeNumber = episode.episodeNumber,
                            seasonNumber = episode.seasonNumber,
                            sourceOrder = episode.sourceOrder,
                            dateFetch = episode.dateFetch,
                            dateUpload = episode.dateUpload,
                            version = episode.version,
                        )
                        episode.copy(id = animeEpisodesQueries.selectLastInsertedRowId().executeAsOne())
                    } else {
                        animeEpisodesQueries.update(
                            animeId = episode.animeId,
                            url = episode.url,
                            name = episode.name,
                            releaseGroup = episode.releaseGroup,
                            seen = existing.seen,
                            bookmark = existing.bookmark,
                            lastSecondsWatched = existing.lastSecondsWatched,
                            totalSeconds = existing.totalSeconds,
                            episodeNumber = episode.episodeNumber,
                            seasonNumber = episode.seasonNumber,
                            sourceOrder = episode.sourceOrder,
                            dateFetch = episode.dateFetch,
                            dateUpload = episode.dateUpload,
                            version = existing.version,
                            episodeId = existing.id,
                        )
                        existing.copy(
                            url = episode.url,
                            name = episode.name,
                            releaseGroup = episode.releaseGroup,
                            episodeNumber = episode.episodeNumber,
                            seasonNumber = episode.seasonNumber,
                            sourceOrder = episode.sourceOrder,
                            dateFetch = episode.dateFetch,
                            dateUpload = episode.dateUpload,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    private suspend fun partialUpdate(vararg episodeUpdates: EpisodeUpdate) {
        handler.await(inTransaction = true) {
            episodeUpdates.forEach { episodeUpdate ->
                animeEpisodesQueries.update(
                    animeId = episodeUpdate.animeId,
                    url = episodeUpdate.url,
                    name = episodeUpdate.name,
                    releaseGroup = episodeUpdate.releaseGroup,
                    seen = episodeUpdate.seen,
                    bookmark = episodeUpdate.bookmark,
                    lastSecondsWatched = episodeUpdate.lastSecondsWatched,
                    totalSeconds = episodeUpdate.totalSeconds,
                    episodeNumber = episodeUpdate.episodeNumber,
                    seasonNumber = episodeUpdate.seasonNumber,
                    sourceOrder = episodeUpdate.sourceOrder,
                    dateFetch = episodeUpdate.dateFetch,
                    dateUpload = episodeUpdate.dateUpload,
                    version = episodeUpdate.version,
                    episodeId = episodeUpdate.id,
                )
            }
        }
    }

    private fun mapEpisode(
        id: Long,
        animeId: Long,
        url: String,
        name: String,
        releaseGroup: String?,
        seen: Boolean,
        bookmark: Boolean,
        lastSecondsWatched: Long,
        totalSeconds: Long,
        episodeNumber: Double,
        seasonNumber: Long,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
    ): Episode = Episode(
        id = id,
        animeId = animeId,
        seen = seen,
        bookmark = bookmark,
        lastSecondsWatched = lastSecondsWatched,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        releaseGroup = releaseGroup,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}
