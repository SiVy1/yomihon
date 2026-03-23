package tachiyomi.domain.anime.model

data class Episode(
    val id: Long,
    val animeId: Long,
    val seen: Boolean,
    val bookmark: Boolean,
    val lastSecondsWatched: Long,
    val totalSeconds: Long,
    val dateFetch: Long,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Long,
    val episodeNumber: Double,
    val seasonNumber: Long,
    val releaseGroup: String?,
    val lastModifiedAt: Long,
    val version: Long,
) {
    companion object {
        fun create() = Episode(
            id = -1L,
            animeId = -1L,
            seen = false,
            bookmark = false,
            lastSecondsWatched = 0L,
            totalSeconds = 0L,
            dateFetch = 0L,
            sourceOrder = 0L,
            url = "",
            name = "",
            dateUpload = -1L,
            episodeNumber = -1.0,
            seasonNumber = -1L,
            releaseGroup = null,
            lastModifiedAt = 0L,
            version = 1L,
        )
    }
}
