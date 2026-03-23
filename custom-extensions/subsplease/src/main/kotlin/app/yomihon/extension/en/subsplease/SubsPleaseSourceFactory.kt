package app.yomihon.extension.en.subsplease

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class SubsPleaseSourceFactory : SourceFactory {
    override fun createSources(): List<Source> {
        return listOf(SubsPleaseAnimeSource())
    }
}
