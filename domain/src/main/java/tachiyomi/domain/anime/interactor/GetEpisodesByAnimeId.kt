package tachiyomi.domain.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Episode
import tachiyomi.domain.anime.repository.EpisodeRepository

class GetEpisodesByAnimeId(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(animeId: Long): List<Episode> {
        return episodeRepository.getEpisodesByAnimeId(animeId)
    }

    fun subscribe(animeId: Long): Flow<List<Episode>> {
        return episodeRepository.getEpisodesByAnimeIdAsFlow(animeId)
    }
}
