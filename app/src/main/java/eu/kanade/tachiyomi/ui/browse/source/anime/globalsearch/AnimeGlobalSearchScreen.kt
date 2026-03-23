package eu.kanade.tachiyomi.ui.browse.source.anime.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.AnimeGlobalSearchScreen as AnimeGlobalSearchContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.anime.AnimeDetailsScreen
import eu.kanade.tachiyomi.ui.browse.source.anime.BrowseAnimeSourceScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeGlobalSearchScreen(
    private val searchQuery: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            AnimeGlobalSearchScreenModel(initialQuery = searchQuery)
        }
        val state by screenModel.state.collectAsState()

        AnimeGlobalSearchContent(
            state = state,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = { source ->
                navigator.push(BrowseAnimeSourceScreen(source.id, state.searchQuery))
            },
            onClickItem = { source, anime ->
                navigator.push(AnimeDetailsScreen(source.id, anime))
            },
        )
    }
}
