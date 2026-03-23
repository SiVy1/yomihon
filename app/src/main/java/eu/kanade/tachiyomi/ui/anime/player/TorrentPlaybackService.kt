package eu.kanade.tachiyomi.ui.anime.player

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TorrentPlaybackService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): TorrentPlaybackService = this@TorrentPlaybackService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backend: TorrentPlaybackBackend by lazy {
        NoopTorrentPlaybackBackend(
            engine = TorrentEngineFactory(this).create(),
        )
    }
    private var currentRequest: AnimePlaybackRequest? = null

    private val _snapshot = MutableStateFlow(
        TorrentPlaybackSnapshot(
            phase = TorrentPlaybackPhase.Idle,
            statusMessage = "Idle",
        ),
    )
    val snapshot: StateFlow<TorrentPlaybackSnapshot> = _snapshot.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.ID_ANIME_PLAYER, buildNotification().build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        readPlaybackRequest(intent)?.let(::prepare)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun prepare(request: AnimePlaybackRequest) {
        serviceScope.launch {
            currentRequest = request
            _snapshot.value = TorrentPlaybackSnapshot(
                phase = TorrentPlaybackPhase.Buffering,
                statusMessage = "Preparing playback session for ${request.episodeName}",
            )
            _snapshot.value = backend.prepare(request)
            refreshNotification(request, _snapshot.value)
        }
    }

    fun selectVideoFile(fileId: String) {
        serviceScope.launch {
            _snapshot.value = backend.selectVideoFile(fileId)
            currentRequest?.let { refreshNotification(it, _snapshot.value) }
        }
    }

    fun selectSubtitleTrack(trackId: String?) {
        serviceScope.launch {
            _snapshot.value = backend.selectSubtitleTrack(trackId)
            currentRequest?.let { refreshNotification(it, _snapshot.value) }
        }
    }

    fun stopPlaybackSession() {
        serviceScope.launch {
            backend.stopSession()
            currentRequest = null
            _snapshot.value = TorrentPlaybackSnapshot(
                phase = TorrentPlaybackPhase.Idle,
                statusMessage = "Playback stopped",
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(
        title: String = "Anime player",
        text: String = snapshot.value.statusMessage ?: "Preparing playback",
    ): NotificationCompat.Builder {
        return notificationBuilder(Notifications.CHANNEL_COMMON) {
            setSmallIcon(R.drawable.ic_mihon)
            setOngoing(true)
            setAutoCancel(false)
            setShowWhen(false)
            setContentTitle(title)
            setContentText(text)
            priority = NotificationCompat.PRIORITY_LOW
        }
    }

    private fun refreshNotification(
        request: AnimePlaybackRequest,
        snapshot: TorrentPlaybackSnapshot,
    ) {
        startForeground(
            Notifications.ID_ANIME_PLAYER,
            buildNotification(
                title = request.animeTitle,
                text = snapshot.statusMessage ?: request.episodeName,
            ).build(),
        )
    }

    private fun readPlaybackRequest(intent: Intent?): AnimePlaybackRequest? {
        return intent?.let {
            AnimePlayerActivity.readPlaybackRequestFromIntent(it)
        }
    }
}
