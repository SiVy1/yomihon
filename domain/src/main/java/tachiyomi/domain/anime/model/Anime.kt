package tachiyomi.domain.anime.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

@Immutable
data class Anime(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val dateAdded: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val initialized: Boolean,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val aniListId: Long?,
) : Serializable {
    companion object {
        fun create() = Anime(
            id = -1L,
            source = -1L,
            favorite = false,
            dateAdded = 0L,
            url = "",
            title = "",
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = 0L,
            thumbnailUrl = null,
            initialized = false,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 0L,
            aniListId = null,
        )
    }
}
