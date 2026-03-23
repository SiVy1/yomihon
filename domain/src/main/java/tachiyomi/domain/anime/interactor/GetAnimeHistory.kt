package tachiyomi.domain.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.anime.repository.AnimeHistoryRepository

class GetAnimeHistory(
    private val repository: AnimeHistoryRepository,
) {

    fun subscribe(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return repository.getHistory(query)
    }

    suspend fun getLastHistory(): AnimeHistoryWithRelations? {
        return repository.getLastHistory()
    }
}
