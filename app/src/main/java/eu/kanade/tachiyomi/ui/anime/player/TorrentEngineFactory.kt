package eu.kanade.tachiyomi.ui.anime.player

import android.content.Context

class TorrentEngineFactory(
    private val context: Context,
) {

    fun create(): TorrentEngine {
        return runCatching {
            JlibTorrentEngine(context)
        }.getOrElse {
            if (NativeTorrentLibraryLoader.isAvailable()) {
                NativeTorrentEngine(
                    context = context,
                    nativeBridge = NativeTorrentBridge(),
                )
            } else {
                PlaceholderTorrentEngine()
            }
        }
    }
}
