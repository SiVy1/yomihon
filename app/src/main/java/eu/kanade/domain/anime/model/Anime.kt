package eu.kanade.domain.anime.model

import eu.kanade.tachiyomi.source.model.SAnime
import tachiyomi.domain.anime.model.Anime

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
