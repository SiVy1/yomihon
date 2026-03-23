package eu.kanade.tachiyomi.ui.anime.player

class TorrentFileSelector {

    fun select(
        discoveredFiles: List<TorrentDiscoveredFile>,
        fileNameHint: String?,
        subtitleHint: String?,
    ): SelectionResult {
        val videoFiles = discoveredFiles
            .filter { it.path.isLikelyVideoFile() }
            .sortedWith(
                compareByDescending<TorrentDiscoveredFile> { file ->
                    fileNameHint != null && file.path.contains(fileNameHint, ignoreCase = true)
                }.thenByDescending { it.sizeBytes ?: 0L }
                    .thenBy { it.path },
            )
            .map {
                TorrentPlayableFile(
                    id = it.id,
                    name = it.path.substringAfterLast('/'),
                    sizeBytes = it.sizeBytes,
                    isVideo = true,
                )
            }

        val subtitleTracks = discoveredFiles
            .filter { it.path.isLikelySubtitleFile() }
            .sortedWith(
                compareByDescending<TorrentDiscoveredFile> { file ->
                    subtitleHint != null && file.path.contains(subtitleHint, ignoreCase = true)
                }.thenBy { it.path },
            )
            .map {
                SubtitleTrack(
                    id = it.id,
                    label = it.path.substringAfterLast('/'),
                    language = it.path.substringAfterLast('.', "").takeIf { lang -> lang.length in 2..5 },
                )
            }

        val selectedVideoFileId = when {
            videoFiles.size == 1 -> videoFiles.first().id
            else -> null
        }
        val selectedSubtitleTrackId = when {
            subtitleTracks.size == 1 && subtitleHint.isNullOrBlank() -> subtitleTracks.first().id
            subtitleHint != null -> subtitleTracks.firstOrNull {
                it.label.contains(subtitleHint, ignoreCase = true)
            }?.id
            else -> null
        }

        return SelectionResult(
            availableVideoFiles = videoFiles,
            availableSubtitleTracks = subtitleTracks,
            selectedVideoFileId = selectedVideoFileId,
            selectedSubtitleTrackId = selectedSubtitleTrackId,
        )
    }

    data class SelectionResult(
        val availableVideoFiles: List<TorrentPlayableFile>,
        val availableSubtitleTracks: List<SubtitleTrack>,
        val selectedVideoFileId: String?,
        val selectedSubtitleTrackId: String?,
    )

    private fun String.isLikelyVideoFile(): Boolean {
        val extension = substringAfterLast('.', "").lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    private fun String.isLikelySubtitleFile(): Boolean {
        val extension = substringAfterLast('.', "").lowercase()
        return extension in SUBTITLE_EXTENSIONS
    }

    private companion object {
        val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "webm", "m4v", "ts")
        val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")
    }
}

