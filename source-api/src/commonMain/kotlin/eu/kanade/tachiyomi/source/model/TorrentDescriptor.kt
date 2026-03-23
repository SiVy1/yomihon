package eu.kanade.tachiyomi.source.model

import java.io.Serializable

data class TorrentDescriptor(
    val releaseTitle: String,
    val magnetUri: String? = null,
    val torrentUrl: String? = null,
    val infoHash: String? = null,
    val sizeBytes: Long? = null,
    val quality: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val fileNameHint: String? = null,
    val publishedAt: Long? = null,
    val subtitleHint: String? = null,
) : Serializable
