package eu.kanade.tachiyomi.ui.anime.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.view.setComposeContent

class AnimePlayerActivity : BaseActivity() {

    companion object {
        private const val PLAYBACK_REQUEST_KEY = "playback_request"

        fun newIntent(
            context: Context,
            request: AnimePlaybackRequest,
        ): Intent {
            return Intent(context, AnimePlayerActivity::class.java).apply {
                putExtra(PLAYBACK_REQUEST_KEY, request)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    private var controller: AnimePlayerController? = null

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val request = readPlaybackRequest() ?: run {
            finish()
            return
        }

        controller = AnimePlayerController(this, request)

        setComposeContent {
            val controller = controller ?: return@setComposeContent
            val state by controller.state.collectAsState()
            AnimePlayerScreen(
                state = state,
                player = controller.player,
                onBack = ::finish,
            )
        }
    }

    override fun onPause() {
        controller?.persistProgress()
        super.onPause()
    }

    override fun onStop() {
        controller?.persistProgress()
        super.onStop()
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }

    private fun readPlaybackRequest(): AnimePlaybackRequest? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(PLAYBACK_REQUEST_KEY, AnimePlaybackRequest::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(PLAYBACK_REQUEST_KEY) as? AnimePlaybackRequest
        }
    }
}
