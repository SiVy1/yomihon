package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Episode
import tachiyomi.domain.anime.model.EpisodeUpdate

interface EpisodeRepository {

    suspend fun getEpisodeById(id: Long): Episode?

    suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Episode?

    suspend fun getEpisodesByAnimeId(animeId: Long): List<Episode>

    fun getEpisodesByAnimeIdAsFlow(animeId: Long): Flow<List<Episode>>

    suspend fun update(episodeUpdate: EpisodeUpdate)

    suspend fun updateAll(episodeUpdates: List<EpisodeUpdate>)

    suspend fun upsertEpisodes(episodes: List<Episode>): List<Episode>
}
