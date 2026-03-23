package tachiyomi.domain.anime.model

data class AnimeHistoryWithRelations(
    val id: Long,
    val animeId: Long,
    val episodeId: Long,
    val sourceId: Long,
    val animeUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val episodeName: String,
    val episodeNumber: Double,
    val seen: Boolean,
    val bookmark: Boolean,
    val lastSecondsWatched: Long,
    val totalSeconds: Long,
    val watchedAt: Long?,
    val watchDuration: Long,
)
