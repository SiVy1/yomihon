package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository

class UpdateAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(update: AnimeUpdate): Boolean {
        return animeRepository.update(update)
    }
}
