package tachiyomi.data.anime

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
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

    override fun getHistory(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return handler.subscribeToList {
            animeHistoryViewQueries.history(query, ::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getLastHistory(): AnimeHistoryWithRelations? {
        return handler.awaitOneOrNull {
            animeHistoryViewQueries.getLatestHistory(::mapAnimeHistoryWithRelations)
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

    private fun mapAnimeHistoryWithRelations(
        id: Long,
        animeId: Long,
        episodeId: Long,
        sourceId: Long,
        animeUrl: String,
        title: String,
        thumbnailUrl: String?,
        episodeName: String,
        episodeNumber: Double,
        seen: Boolean,
        bookmark: Boolean,
        lastSecondsWatched: Long,
        totalSeconds: Long,
        watchedAt: Long?,
        watchDuration: Long,
    ): AnimeHistoryWithRelations = AnimeHistoryWithRelations(
        id = id,
        animeId = animeId,
        episodeId = episodeId,
        sourceId = sourceId,
        animeUrl = animeUrl,
        title = title,
        thumbnailUrl = thumbnailUrl,
        episodeName = episodeName,
        episodeNumber = episodeNumber,
        seen = seen,
        bookmark = bookmark,
        lastSecondsWatched = lastSecondsWatched,
        totalSeconds = totalSeconds,
        watchedAt = watchedAt,
        watchDuration = watchDuration,
    )
}
