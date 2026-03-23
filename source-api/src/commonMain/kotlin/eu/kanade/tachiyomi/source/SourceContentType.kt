package eu.kanade.tachiyomi.source

enum class SourceContentType {
    MANGA,
    ANIME,
}

val Source.contentType: SourceContentType
    get() = when (this) {
        is AnimeSource -> SourceContentType.ANIME
        else -> SourceContentType.MANGA
    }
