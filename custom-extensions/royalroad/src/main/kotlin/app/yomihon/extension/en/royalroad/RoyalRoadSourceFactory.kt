package app.yomihon.extension.en.royalroad

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class RoyalRoadSourceFactory : SourceFactory {
    override fun createSources(): List<Source> {
        return listOf(RoyalRoadNovelSource())
    }
}