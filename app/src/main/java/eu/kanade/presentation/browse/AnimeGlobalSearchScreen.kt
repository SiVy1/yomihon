package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.components.GlobalSearchToolbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.ui.browse.source.anime.globalsearch.AnimeGlobalSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.anime.globalsearch.AnimeSearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding

@Composable
fun AnimeGlobalSearchScreen(
    state: AnimeGlobalSearchScreenModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    onClickSource: (AnimeCatalogueSource) -> Unit,
    onClickItem: (AnimeCatalogueSource, SAnime) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            GlobalSearchToolbar(
                searchQuery = state.searchQuery,
                progress = state.progress,
                total = state.total,
                navigateUp = navigateUp,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                hideSourceFilter = false,
                sourceFilter = state.sourceFilter,
                onChangeSearchFilter = onChangeSearchFilter,
                onlyShowHasResults = state.onlyShowHasResults,
                onToggleResults = onToggleResults,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(contentPadding = paddingValues) {
            state.filteredItems.forEach { (source, result) ->
                item(key = source.id) {
                    GlobalSearchResultItem(
                        title = source.name,
                        subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                        onClick = { onClickSource(source) },
                        modifier = Modifier.animateItem(),
                    ) {
                        when (result) {
                            AnimeSearchItemResult.Loading -> GlobalSearchLoadingResultItem()
                            is AnimeSearchItemResult.Error -> {
                                GlobalSearchErrorResultItem(message = result.throwable.message)
                            }
                            is AnimeSearchItemResult.Success -> {
                                AnimeSearchResults(
                                    source = source,
                                    animes = result.result,
                                    onClickItem = onClickItem,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeSearchResults(
    source: AnimeCatalogueSource,
    animes: List<SAnime>,
    onClickItem: (AnimeCatalogueSource, SAnime) -> Unit,
) {
    Column {
        animes.forEach { anime ->
            AnimeSearchResultRow(
                anime = anime,
                onClick = { onClickItem(source, anime) },
            )
        }
    }
}

@Composable
private fun AnimeSearchResultRow(
    anime: SAnime,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = anime.thumbnail_url,
            contentDescription = anime.title,
            error = painterResource(R.drawable.cover_error),
            modifier = Modifier
                .size(width = 52.dp, height = 76.dp)
                .clip(MaterialTheme.shapes.small),
        )

        Column(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            anime.genre?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
