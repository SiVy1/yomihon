package eu.kanade.tachiyomi.source

/**
 * Base class for torrent-backed anime extensions.
 *
 * Using a host-provided base class avoids direct interface implementation from the
 * extension APK, which can otherwise trip classloader ABI issues on suspend bridges.
 */
abstract class TorrentAnimeSourceBase : TorrentAnimeSource
