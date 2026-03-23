package tachiyomi.data.anime

import tachiyomi.data.StringListColumnAdapter
import tachiyomi.domain.anime.model.Anime

object AnimeMapper {
    fun mapAnime(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: String?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        initialized: Boolean,
        dateAdded: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        aniListId: Long?,
    ): Anime = Anime(
        id = id,
        source = source,
        favorite = favorite,
        dateAdded = dateAdded,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre?.let(StringListColumnAdapter::decode),
        status = status,
        thumbnailUrl = thumbnailUrl,
        initialized = initialized,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
        aniListId = aniListId,
    )
}
