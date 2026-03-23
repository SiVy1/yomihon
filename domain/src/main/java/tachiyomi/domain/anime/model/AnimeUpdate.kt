package tachiyomi.domain.anime.model

data class AnimeUpdate(
    val id: Long,
    val source: Long? = null,
    val favorite: Boolean? = null,
    val dateAdded: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long? = null,
    val thumbnailUrl: String? = null,
    val initialized: Boolean? = null,
    val version: Long? = null,
    val aniListId: Long? = null,
)

fun Anime.toAnimeUpdate(): AnimeUpdate {
    return AnimeUpdate(
        id = id,
        source = source,
        favorite = favorite,
        dateAdded = dateAdded,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
        initialized = initialized,
        version = version,
        aniListId = aniListId,
    )
}
