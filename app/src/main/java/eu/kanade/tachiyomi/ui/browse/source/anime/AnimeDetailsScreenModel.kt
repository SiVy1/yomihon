package eu.kanade.tachiyomi.ui.browse.source.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.AnimeSource
import eu.kanade.tachiyomi.source.TorrentAnimeSource
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.TorrentDescriptor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import mihon.domain.anime.model.toDomainAnime
import mihon.domain.anime.model.toDomainEpisode
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.EpisodeRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDetailsScreenModel(
    sourceId: Long,
    initialAnime: SAnime,
    sourceManager: SourceManager = Injekt.get(),
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val episodeRepository: EpisodeRepository = Injekt.get(),
) : StateScreenModel<AnimeDetailsScreenModel.State>(
    State(
        anime = initialAnime,
        isLoading = true,
    ),
) {

    val source = sourceManager.getOrStub(sourceId)

    init {
        screenModelScope.launchIO {
            getAnimeByUrlAndSourceId.subscribe(initialAnime.url, sourceId).collectLatest { localAnime ->
                mutableState.update { it.copy(localAnime = localAnime) }
            }
        }

        screenModelScope.launchIO {
            val animeSource = source as? AnimeSource
            if (animeSource == null) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Missing anime source.",
                    )
                }
                return@launchIO
            }

            runCatching {
                val anime = animeSource.getAnimeDetails(initialAnime)
                val episodes = animeSource.getEpisodeList(anime)
                val savedAnime = networkToLocalAnime(anime.toDomainAnime(sourceId))
                val fetchedAt = System.currentTimeMillis()
                episodeRepository.upsertEpisodes(
                    episodes.mapIndexed { index, episode ->
                        episode.toDomainEpisode(
                            animeId = savedAnime.id,
                            sourceOrder = index.toLong(),
                            fetchedAt = fetchedAt,
                        )
                    },
                )
                Triple(anime, episodes, savedAnime)
            }.onSuccess { (anime, episodes, localAnime) ->
                mutableState.update {
                    it.copy(
                        anime = anime,
                        episodes = episodes,
                        localAnime = localAnime,
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load anime details.",
                    )
                }
            }
        }
    }

    fun toggleLibrary() {
        val localAnime = state.value.localAnime ?: return
        val shouldFavorite = !localAnime.favorite

        screenModelScope.launchIO {
            val success = updateAnime.await(
                AnimeUpdate(
                    id = localAnime.id,
                    favorite = shouldFavorite,
                    dateAdded = when {
                        shouldFavorite && localAnime.dateAdded == 0L -> System.currentTimeMillis()
                        else -> localAnime.dateAdded
                    },
                ),
            )

            if (!success) {
                mutableState.update {
                    it.copy(errorMessage = "Failed to update library state.")
                }
            }
        }
    }

    fun selectEpisode(episode: SEpisode) {
        val torrentSource = source as? TorrentAnimeSource ?: return
        mutableState.update { it.copy(resolvingEpisodeUrl = episode.url) }

        screenModelScope.launchIO {
            runCatching {
                torrentSource.getTorrentDescriptors(episode)
            }.onSuccess { descriptors ->
                mutableState.update {
                    it.copy(
                        resolvingEpisodeUrl = null,
                        dialog = Dialog.TorrentOptions(episode, descriptors),
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        resolvingEpisodeUrl = null,
                        errorMessage = error.message ?: "Failed to resolve torrent options.",
                    )
                }
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    sealed interface Dialog {
        data class TorrentOptions(
            val episode: SEpisode,
            val descriptors: List<TorrentDescriptor>,
        ) : Dialog
    }

    @Immutable
    data class State(
        val anime: SAnime,
        val episodes: List<SEpisode> = emptyList(),
        val isLoading: Boolean,
        val localAnime: Anime? = null,
        val resolvingEpisodeUrl: String? = null,
        val errorMessage: String? = null,
        val dialog: Dialog? = null,
    )
}
