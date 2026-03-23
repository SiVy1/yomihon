package tachiyomi.domain.anime.model

data class LibraryAnime(
    val anime: Anime,
    val totalEpisodes: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    val latestUpload: Long,
    val episodeFetchedAt: Long,
    val lastWatched: Long,
) {
    val id: Long = anime.id

    val unseenCount: Long
        get() = totalEpisodes - seenCount

    val hasBookmarks: Boolean
        get() = bookmarkCount > 0

    val hasStarted: Boolean
        get() = seenCount > 0 || lastWatched > 0

    val isContinuing: Boolean
        get() = lastWatched > 0 && unseenCount > 0
}
