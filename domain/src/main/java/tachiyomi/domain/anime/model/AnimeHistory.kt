package tachiyomi.domain.anime.model

data class AnimeHistory(
    val id: Long,
    val episodeId: Long,
    val watchedAt: Long?,
    val watchDuration: Long,
) {
    companion object {
        fun create() = AnimeHistory(
            id = -1L,
            episodeId = -1L,
            watchedAt = null,
            watchDuration = 0L,
        )
    }
}
