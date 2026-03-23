package eu.kanade.tachiyomi.ui.anime.player

import java.util.concurrent.atomic.AtomicReference

object NativeTorrentLibraryLoader {

    private const val LIBRARY_NAME = "yomihon_torrent"

    private val state = AtomicReference<State>(State.Uninitialized)

    fun isAvailable(): Boolean {
        return when (state.get()) {
            State.Loaded -> true
            State.Failed -> false
            State.Uninitialized -> loadLibrary()
        }
    }

    private fun loadLibrary(): Boolean {
        return try {
            System.loadLibrary(LIBRARY_NAME)
            state.set(State.Loaded)
            true
        } catch (_: UnsatisfiedLinkError) {
            state.set(State.Failed)
            false
        } catch (_: SecurityException) {
            state.set(State.Failed)
            false
        }
    }

    private enum class State {
        Uninitialized,
        Loaded,
        Failed,
    }
}

