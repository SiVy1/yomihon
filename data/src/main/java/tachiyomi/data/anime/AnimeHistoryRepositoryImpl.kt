package tachiyomi.data.anime

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.anime.repository.AnimeHistoryRepository

class AnimeHistoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : AnimeHistoryRepository {

    override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> {
        return handler.awaitList {
            animeHistoryQueries.getHistoryByAnimeId(animeId, ::mapAnimeHistory)
        }
    }

    override suspend fun getTotalWatchDuration(): Long {
        return handler.awaitOne { animeHistoryQueries.getWatchDuration() }
    }

    override suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate) {
        try {
            handler.await {
                animeHistoryQueries.upsert(
                    episodeId = historyUpdate.episodeId,
                    watchedAt = historyUpdate.watchedAt,
                    watchDuration = historyUpdate.sessionWatchDuration,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    private fun mapAnimeHistory(
        id: Long,
        episodeId: Long,
        watchedAt: Long?,
        watchDuration: Long,
    ): AnimeHistory = AnimeHistory(
        id = id,
        episodeId = episodeId,
        watchedAt = watchedAt,
        watchDuration = watchDuration,
    )
}
