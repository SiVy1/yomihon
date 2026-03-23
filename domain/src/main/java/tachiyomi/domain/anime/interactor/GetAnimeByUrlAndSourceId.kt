package tachiyomi.domain.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository

class GetAnimeByUrlAndSourceId(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(url: String, sourceId: Long): Anime? {
        return animeRepository.getAnimeByUrlAndSourceId(url, sourceId)
    }

    fun subscribe(url: String, sourceId: Long): Flow<Anime?> {
        return animeRepository.getAnimeByUrlAndSourceIdAsFlow(url, sourceId)
    }
}
