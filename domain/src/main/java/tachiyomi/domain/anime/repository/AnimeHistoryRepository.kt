package tachiyomi.domain.anime.repository

import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryUpdate

interface AnimeHistoryRepository {

    suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory>

    suspend fun getTotalWatchDuration(): Long

    suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate)
}
