package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.anime.repository.AnimeHistoryRepository

class UpsertAnimeHistory(
    private val animeHistoryRepository: AnimeHistoryRepository,
) {

    suspend fun await(update: AnimeHistoryUpdate) {
        animeHistoryRepository.upsertHistory(update)
    }
}
