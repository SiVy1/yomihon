package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.source.getNameForMangaInfo
import tachiyomi.domain.anime.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val LOCAL_SOURCE_ID_ALIAS = "local"
private const val NOVEL_GENRE_TAG = "Light Novel"

enum class LibraryItemType {
    Manga,
    Anime,
}

data class LibraryItem(
    val type: LibraryItemType,
    val libraryManga: LibraryManga? = null,
    val libraryAnime: LibraryAnime? = null,
    val downloadCount: Long = -1,
    val unreadCount: Long = -1,
    val isLocal: Boolean = false,
    val sourceLanguage: String = "",
    private val sourceManager: SourceManager = Injekt.get(),
) {
    init {
        check((libraryManga != null) xor (libraryAnime != null)) {
            "LibraryItem must contain exactly one content payload"
        }
    }

    val id: Long = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).id
        LibraryItemType.Anime -> -requireNotNull(libraryAnime).id
    }

    val title: String = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).manga.title
        LibraryItemType.Anime -> requireNotNull(libraryAnime).anime.title
    }

    val coverData: MangaCover = when (type) {
        LibraryItemType.Manga -> {
            val manga = requireNotNull(libraryManga).manga
            MangaCover(
                mangaId = manga.id,
                sourceId = manga.source,
                isMangaFavorite = manga.favorite,
                url = manga.thumbnailUrl,
                lastModified = manga.coverLastModified,
            )
        }
        LibraryItemType.Anime -> {
            val anime = requireNotNull(libraryAnime).anime
            MangaCover(
                mangaId = anime.id,
                sourceId = anime.source,
                isMangaFavorite = anime.favorite,
                url = anime.thumbnailUrl,
                lastModified = anime.lastModifiedAt,
            )
        }
    }

    val categoryIds: List<Long> = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).categories
        LibraryItemType.Anime -> requireNotNull(libraryAnime).categories
    }

    val hasBookmarks: Boolean = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).hasBookmarks
        LibraryItemType.Anime -> requireNotNull(libraryAnime).hasBookmarks
    }

    val hasStarted: Boolean = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).hasStarted
        LibraryItemType.Anime -> requireNotNull(libraryAnime).hasStarted
    }

    val lastRead: Long = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).lastRead
        LibraryItemType.Anime -> requireNotNull(libraryAnime).lastWatched
    }

    val latestUpload: Long = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).latestUpload
        LibraryItemType.Anime -> requireNotNull(libraryAnime).latestUpload
    }

    val dateAdded: Long = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).manga.dateAdded
        LibraryItemType.Anime -> requireNotNull(libraryAnime).anime.dateAdded
    }

    val completed: Boolean = when (type) {
        LibraryItemType.Manga -> requireNotNull(libraryManga).manga.status.toInt() == eu.kanade.tachiyomi.source.model.SManga.COMPLETED
        LibraryItemType.Anime -> requireNotNull(libraryAnime).anime.status.toInt() == eu.kanade.tachiyomi.source.model.SAnime.COMPLETED
    }

    val isLightNovel: Boolean = libraryManga?.manga?.genre
        ?.any { it.equals(NOVEL_GENRE_TAG, ignoreCase = true) }
        ?: false

    fun matches(constraint: String): Boolean {
        val source = when (type) {
            LibraryItemType.Manga -> sourceManager.getOrStub(requireNotNull(libraryManga).manga.source)
            LibraryItemType.Anime -> sourceManager.getOrStub(requireNotNull(libraryAnime).anime.source)
        }
        val sourceName by lazy { source.getNameForMangaInfo() }
        if (constraint.startsWith("id:", true)) {
            return id == constraint.substringAfter("id:").toLongOrNull()
        } else if (constraint.startsWith("src:", true)) {
            val querySource = constraint.substringAfter("src:")
            return if (querySource.equals(LOCAL_SOURCE_ID_ALIAS, ignoreCase = true)) {
                source.id == LocalSource.ID
            } else {
                source.id == querySource.toLongOrNull()
            }
        } else if (constraint.startsWith("type:", true)) {
            return when (constraint.substringAfter("type:").trim().lowercase()) {
                "anime" -> type == LibraryItemType.Anime
                "novel", "ln", "lightnovel", "light_novel", "light-novel" -> isLightNovel
                "manga", "comic" -> type == LibraryItemType.Manga && !isLightNovel
                else -> false
            }
        }

        val author = when (type) {
            LibraryItemType.Manga -> requireNotNull(libraryManga).manga.author
            LibraryItemType.Anime -> requireNotNull(libraryAnime).anime.author
        }
        val artist = when (type) {
            LibraryItemType.Manga -> requireNotNull(libraryManga).manga.artist
            LibraryItemType.Anime -> requireNotNull(libraryAnime).anime.artist
        }
        val description = when (type) {
            LibraryItemType.Manga -> requireNotNull(libraryManga).manga.description
            LibraryItemType.Anime -> requireNotNull(libraryAnime).anime.description
        }
        val genres = when (type) {
            LibraryItemType.Manga -> requireNotNull(libraryManga).manga.genre
            LibraryItemType.Anime -> requireNotNull(libraryAnime).anime.genre
        }

        return title.contains(constraint, true) ||
            (author?.contains(constraint, true) ?: false) ||
            (artist?.contains(constraint, true) ?: false) ||
            (description?.contains(constraint, true) ?: false) ||
            constraint.split(",").map { it.trim() }.all { subconstraint ->
                checkNegatableConstraint(subconstraint) {
                    sourceName.contains(it, true) ||
                        (genres?.any { genre -> genre.equals(it, true) } ?: false)
                }
            }
    }

    private fun checkNegatableConstraint(
        constraint: String,
        predicate: (String) -> Boolean,
    ): Boolean {
        return if (constraint.startsWith("-")) {
            !predicate(constraint.substringAfter("-").trimStart())
        } else {
            predicate(constraint)
        }
    }
}
