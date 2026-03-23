package tachiyomi.domain.anime.model

data class AnimeHistoryUpdate(
    val episodeId: Long,
    val watchedAt: Long?,
    val sessionWatchDuration: Long,
)
