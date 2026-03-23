package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.graphics.Color
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.util.system.isNightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal text-first viewer used for local light novel content.
 */
class TextViewer(
    private val activity: ReaderActivity,
) : Viewer {

    private val preferences = Injekt.get<ReaderPreferences>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val textView = TextView(activity).apply {
        textSize = preferences.novelFontSizeSp.get().toFloat()
        setLineSpacing(0f, preferences.novelLineSpacingPercent.get() / 100f)
        setPadding((preferences.novelHorizontalPaddingDp.get() * activity.resources.displayMetrics.density).toInt())
    }

    private val scrollView = ScrollView(activity).apply {
        isFillViewport = true
        addView(
            textView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private val container = FrameLayout(activity).apply {
        setBackgroundColor(Color.TRANSPARENT)
        addView(
            scrollView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        setOnClickListener {
            activity.toggleMenu()
        }
        setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener false
            val width = max(this.width, 1)
            when {
                event.x < width * 0.25f -> moveBy(-1)
                event.x > width * 0.75f -> moveBy(1)
                else -> activity.toggleMenu()
            }
            true
        }
    }

    private var chapters: ViewerChapters? = null
    private var currentIndex: Int = 0

    init {
        preferences.novelFontSizeSp.changes()
            .onEach { textView.textSize = it.toFloat() }
            .launchIn(scope)

        preferences.novelLineSpacingPercent.changes()
            .onEach { textView.setLineSpacing(0f, it / 100f) }
            .launchIn(scope)

        preferences.novelHorizontalPaddingDp.changes()
            .onEach {
                textView.setPadding((it * activity.resources.displayMetrics.density).toInt())
            }
            .launchIn(scope)

        preferences.novelJustifyText.changes()
            .onEach {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    textView.justificationMode = if (it) {
                        android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
                    } else {
                        android.text.Layout.JUSTIFICATION_MODE_NONE
                    }
                }
            }
            .launchIn(scope)

        preferences.novelSepiaBackground.changes()
            .onEach { applyTheme() }
            .launchIn(scope)

        preferences.readerTheme.changes()
            .onEach { applyTheme() }
            .launchIn(scope)

        applyTheme()
    }

    override fun getView(): View = container

    override fun setChapters(chapters: ViewerChapters) {
        this.chapters = chapters
        val pages = chapters.currChapter.pages.orEmpty()
        if (pages.isEmpty()) return

        val initialPage = min(max(chapters.currChapter.requestedPage, 0), pages.lastIndex)
        showPage(initialPage)
    }

    override fun moveToPage(page: ReaderPage) {
        val pages = chapters?.currChapter?.pages.orEmpty()
        if (pages.isEmpty()) return
        val targetIndex = pages.indexOf(page).takeIf { it >= 0 } ?: page.index.coerceIn(0, pages.lastIndex)
        showPage(targetIndex)
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                preferences.novelFontSizeSp.set((preferences.novelFontSizeSp.get() + 1).coerceAtMost(32))
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                preferences.novelFontSizeSp.set((preferences.novelFontSizeSp.get() - 1).coerceAtLeast(14))
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_SPACE,
            -> {
                moveBy(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_PAGE_UP,
            -> {
                moveBy(-1)
                true
            }
            else -> false
        }
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }

    override fun destroy() {
        scope.cancel()
    }

    private fun moveBy(delta: Int) {
        val pages = chapters?.currChapter?.pages.orEmpty()
        if (pages.isEmpty()) return

        val next = currentIndex + delta
        if (next in pages.indices) {
            showPage(next)
        } else {
            activity.showMenu()
        }
    }

    private fun showPage(index: Int) {
        val pages = chapters?.currChapter?.pages.orEmpty()
        val page = pages.getOrNull(index) ?: return

        currentIndex = index
        textView.text = page.text.orEmpty()
        scrollView.scrollTo(0, 0)
        activity.onPageSelected(page)

        if (pages.size - page.number < 5) {
            chapters?.nextChapter?.let(activity::requestPreloadChapter)
        }
    }

    private fun applyTheme() {
        val themeValue = preferences.readerTheme.get()
        val useSepia = preferences.novelSepiaBackground.get()

        val background = if (useSepia) {
            0xFFF4ECD8.toInt()
        } else {
            when (themeValue) {
                0 -> Color.WHITE
                2 -> Color.rgb(0x20, 0x21, 0x25)
                3 -> if (activity.isNightMode()) Color.rgb(0x20, 0x21, 0x25) else Color.WHITE
                else -> Color.BLACK
            }
        }

        val textColor = when {
            useSepia -> 0xFF3D2E1D.toInt()
            background == Color.WHITE -> 0xFF1D1D1D.toInt()
            else -> Color.WHITE
        }

        container.setBackgroundColor(background)
        textView.setTextColor(textColor)
    }
}
