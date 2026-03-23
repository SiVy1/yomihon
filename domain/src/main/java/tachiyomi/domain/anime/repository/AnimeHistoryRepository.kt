package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.anime.model.AnimeHistoryUpdate

interface AnimeHistoryRepository {

    suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory>

    fun getHistory(query: String): Flow<List<AnimeHistoryWithRelations>>

    suspend fun getLastHistory(): AnimeHistoryWithRelations?

    suspend fun getTotalWatchDuration(): Long

    suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate)
}
