package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Pins
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.source.local.isLocal

class GetEnabledSources(
    private val repository: SourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<List<Source>> {
        val allSources = combine(
            repository.getSources(),
            repository.getAnimeSources(),
        ) { mangaSources: List<Source>, animeSources: List<Source> ->
            mangaSources + animeSources
        }

        return combine(
            preferences.pinnedSources.changes(),
            preferences.enabledLanguages.changes(),
            preferences.disabledSources.changes(),
            preferences.lastUsedSource.changes(),
            allSources,
        ) { pinnedSourceIds, enabledLanguages, disabledSources, lastUsedSource, sources ->
            sources
                .filter { source -> source.lang in enabledLanguages || source.isLocal() }
                .filterNot { source -> source.id.toString() in disabledSources }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { source -> source.name })
                .flatMap { source ->
                    val flag = if (source.id.toString() in pinnedSourceIds) Pins.pinned else Pins.unpinned
                    val pinnedSource = source.copy(pin = flag)
                    val toFlatten = mutableListOf(pinnedSource)
                    if (pinnedSource.id == lastUsedSource) {
                        toFlatten.add(
                            pinnedSource.copy(
                                isUsedLast = true,
                                pin = pinnedSource.pin - Pin.Actual,
                            ),
                        )
                    }
                    toFlatten
                }
        }
            .distinctUntilChanged()
    }
}
