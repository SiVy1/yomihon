package eu.kanade.domain.anime.model

import eu.kanade.tachiyomi.source.model.SAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations

fun Anime.toSAnime(): SAnime = SAnime.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}

fun AnimeHistoryWithRelations.toSAnime(): SAnime = SAnime.create().also {
    it.url = animeUrl
    it.title = title
    it.artist = null
    it.author = null
    it.description = null
    it.genre = null
    it.status = SAnime.UNKNOWN
    it.thumbnail_url = thumbnailUrl
    it.initialized = false
}
