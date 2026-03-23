package mihon.domain.anime.model

import eu.kanade.tachiyomi.source.model.SEpisode
import tachiyomi.domain.anime.model.Episode

fun SEpisode.toDomainEpisode(animeId: Long, sourceOrder: Long, fetchedAt: Long): Episode {
    return Episode.create().copy(
        animeId = animeId,
        url = url,
        name = name,
        dateUpload = date_upload,
        episodeNumber = episode_number.toDouble(),
        seasonNumber = season_number.toLong(),
        releaseGroup = release_group,
        dateFetch = fetchedAt,
        sourceOrder = sourceOrder,
    )
}
