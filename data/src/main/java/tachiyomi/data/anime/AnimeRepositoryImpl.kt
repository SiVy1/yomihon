package tachiyomi.data.anime

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.LibraryAnime
import tachiyomi.domain.anime.repository.AnimeRepository

class AnimeRepositoryImpl(
    private val handler: DatabaseHandler,
) : AnimeRepository {

    override suspend fun getAnimeById(id: Long): Anime? {
        return handler.awaitOneOrNull { animesQueries.getAnimeById(id, AnimeMapper::mapAnime) }
    }

    override fun getAnimeByIdAsFlow(id: Long): Flow<Anime?> {
        return handler.subscribeToOneOrNull { animesQueries.getAnimeById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime? {
        return handler.awaitOneOrNull {
            animesQueries.getAnimeByUrlAndSource(
                url = url,
                source = sourceId,
                mapper = AnimeMapper::mapAnime,
            )
        }
    }

    override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?> {
        return handler.subscribeToOneOrNull {
            animesQueries.getAnimeByUrlAndSource(
                url = url,
                source = sourceId,
                mapper = AnimeMapper::mapAnime,
            )
        }
    }

    override suspend fun getLibraryAnime(): List<LibraryAnime> {
        return handler.awaitList {
            animeLibraryViewQueries.library(AnimeMapper::mapLibraryAnime)
        }
    }

    override fun getLibraryAnimeAsFlow(): Flow<List<LibraryAnime>> {
        return handler.subscribeToList {
            animeLibraryViewQueries.library(AnimeMapper::mapLibraryAnime)
        }
    }

    override suspend fun update(update: AnimeUpdate): Boolean {
        return try {
            handler.await {
                animesQueries.update(
                    source = update.source,
                    url = update.url,
                    artist = update.artist,
                    author = update.author,
                    description = update.description,
                    genre = update.genre?.let(StringListColumnAdapter::encode),
                    title = update.title,
                    status = update.status,
                    thumbnailUrl = update.thumbnailUrl,
                    favorite = update.favorite,
                    initialized = update.initialized,
                    dateAdded = update.dateAdded,
                    version = update.version,
                    aniListId = update.aniListId,
                    animeId = update.id,
                )
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun insertNetworkAnime(anime: List<Anime>): List<Anime> {
        return handler.await(inTransaction = true) {
            anime.map { item ->
                animesQueries.insertNetworkAnime(
                    source = item.source,
                    url = item.url,
                    artist = item.artist,
                    author = item.author,
                    description = item.description,
                    genre = item.genre?.let(StringListColumnAdapter::encode),
                    title = item.title,
                    status = item.status,
                    thumbnailUrl = item.thumbnailUrl,
                    favorite = item.favorite,
                    initialized = item.initialized,
                    dateAdded = item.dateAdded,
                    version = item.version,
                    aniListId = item.aniListId,
                    updateTitle = item.title.isNotBlank(),
                    updateCover = !item.thumbnailUrl.isNullOrBlank(),
                    updateDetails = item.initialized,
                    updateAniListId = item.aniListId != null,
                    mapper = AnimeMapper::mapAnime,
                ).executeAsOne()
            }
        }
    }
}
