package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.graphics.Color
import android.os.Build
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal text-first viewer used for local light novel content.
 */
class TextViewer(
    private val activity: ReaderActivity,
    val isContinuousScrollMode: Boolean,
) : Viewer {

    private val preferences = Injekt.get<ReaderPreferences>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tapDetector: GestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                return handleSingleTap(event)
            }
        },
    )

    private val tapTouchListener: View.OnTouchListener = View.OnTouchListener { _, event ->
        tapDetector.onTouchEvent(event)
        false
    }

    private val textView = TextView(activity).apply {
        textSize = preferences.novelFontSizeSp.get().toFloat()
        setLineSpacing(0f, preferences.novelLineSpacingPercent.get() / 100f)
        setPadding((preferences.novelHorizontalPaddingDp.get() * activity.resources.displayMetrics.density).toInt())
        setOnTouchListener(tapTouchListener)
    }

    private val scrollView = ScrollView(activity).apply {
        isFillViewport = true
        setOnTouchListener(tapTouchListener)
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
    }

    private var chapters: ViewerChapters? = null
    private var currentIndex: Int = 0
    private var currentChapterForProgress: eu.kanade.tachiyomi.ui.reader.model.ReaderChapter? = null
    private var lastReportedProgress: Float = -1f
    private var isRestoringScroll: Boolean = false
    private var preloadRequestedForChapter: Long? = null

    private val scrollProgressListener = ViewTreeObserver.OnScrollChangedListener {
        if (isContinuousScrollMode) {
            updateProgressFromScroll()
        }
    }

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

        preferences.novelDisplayMode.changes()
            .onEach { applyTheme() }
            .launchIn(scope)

        scrollView.viewTreeObserver.addOnScrollChangedListener(scrollProgressListener)

        applyTypographyPrefs()
        applyTheme()
    }

    override fun getView(): View = container

    override fun setChapters(chapters: ViewerChapters) {
        this.chapters = chapters
        val pages = chapters.currChapter.pages.orEmpty()
        if (pages.isEmpty()) return

        if (isContinuousScrollMode) {
            val chapter = chapters.currChapter
            val chapterId = chapter.chapter.id
            val savedProgress = chapterId?.let { preferences.novelScrollProgress(it).get() } ?: 0f
            val fallbackProgress = if (savedProgress <= 0f && pages.lastIndex > 0) {
                chapter.requestedPage.toFloat() / pages.lastIndex.toFloat()
            } else {
                savedProgress
            }
            showContinuousChapter(chapter, fallbackProgress.coerceIn(0f, 1f))
            return
        }

        val initialPage = min(max(chapters.currChapter.requestedPage, 0), pages.lastIndex)
        showPage(initialPage)
    }

    override fun moveToPage(page: ReaderPage) {
        if (isContinuousScrollMode) {
            val chapter = page.chapter
            val pages = chapter.pages.orEmpty()
            if (pages.isEmpty()) return
            val progress = if (pages.lastIndex > 0) {
                page.index.toFloat() / pages.lastIndex.toFloat()
            } else {
                0f
            }
            showContinuousChapter(chapter, progress)
            return
        }

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
        scrollView.viewTreeObserver.removeOnScrollChangedListener(scrollProgressListener)
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

    private fun handleSingleTap(event: MotionEvent): Boolean {
        if (isContinuousScrollMode) {
            activity.toggleMenu()
            return true
        }

        val width = max(scrollView.width, 1)
        when {
            event.x < width * 0.25f -> moveBy(-1)
            event.x > width * 0.75f -> moveBy(1)
            else -> activity.toggleMenu()
        }
        return true
    }

    private fun showPage(index: Int) {
        val pages = chapters?.currChapter?.pages.orEmpty()
        val page = pages.getOrNull(index) ?: return

        currentIndex = index
        currentChapterForProgress = null
        textView.text = page.text.orEmpty()
        scrollView.scrollTo(0, 0)
        activity.onPageSelected(page)

        if (pages.size - page.number < 5) {
            chapters?.nextChapter?.let(activity::requestPreloadChapter)
        }
    }

    private fun showContinuousChapter(chapter: eu.kanade.tachiyomi.ui.reader.model.ReaderChapter, initialProgress: Float) {
        val pages = chapter.pages.orEmpty()
        if (pages.isEmpty()) return

        currentIndex = 0
        currentChapterForProgress = chapter
        lastReportedProgress = -1f
        preloadRequestedForChapter = null

        textView.text = pages
            .mapNotNull { it.text?.takeIf(String::isNotBlank) }
            .joinToString("\n\n")
            .ifBlank { pages.first().text.orEmpty() }

        activity.onPageSelected(pages.first())
        restoreScrollPosition(initialProgress)
    }

    private fun applyTheme() {
        val displayMode = preferences.novelDisplayMode.get()
        val (background, textColor) = when (displayMode) {
            ReaderPreferences.NovelDisplayMode.FOLLOW_APP -> {
                if (activity.isNightMode()) {
                    Color.BLACK to Color.WHITE
                } else {
                    Color.WHITE to 0xFF1D1D1D.toInt()
                }
            }
            ReaderPreferences.NovelDisplayMode.LIGHT -> Color.WHITE to 0xFF1D1D1D.toInt()
            ReaderPreferences.NovelDisplayMode.DARK -> Color.BLACK to Color.WHITE
            ReaderPreferences.NovelDisplayMode.GRAY -> Color.rgb(0x20, 0x21, 0x25) to Color.WHITE
            ReaderPreferences.NovelDisplayMode.SEPIA -> 0xFFF4ECD8.toInt() to 0xFF3D2E1D.toInt()
        }

        container.setBackgroundColor(background)
        textView.setTextColor(textColor)
    }

    private fun updateProgressFromScroll() {
        if (isRestoringScroll) return

        val chapter = currentChapterForProgress ?: return
        val maxScroll = (textView.height - scrollView.height).coerceAtLeast(0)
        val progress = if (maxScroll == 0) 0f else (scrollView.scrollY.toFloat() / maxScroll).coerceIn(0f, 1f)

        if (lastReportedProgress >= 0f && abs(progress - lastReportedProgress) < 0.01f) return
        lastReportedProgress = progress
        activity.onTextProgressChanged(chapter, progress)

        if (progress >= 0.8f && preloadRequestedForChapter != chapter.chapter.id) {
            chapters?.nextChapter?.let(activity::requestPreloadChapter)
            preloadRequestedForChapter = chapter.chapter.id
        }
    }

    private fun restoreScrollPosition(progress: Float) {
        isRestoringScroll = true
        scrollView.post {
            val maxScroll = (textView.height - scrollView.height).coerceAtLeast(0)
            val targetY = (maxScroll * progress.coerceIn(0f, 1f)).toInt()
            scrollView.scrollTo(0, targetY)
            isRestoringScroll = false
            updateProgressFromScroll()
        }
    }

    private fun applyTypographyPrefs() {
        textView.textSize = preferences.novelFontSizeSp.get().toFloat()
        textView.setLineSpacing(0f, preferences.novelLineSpacingPercent.get() / 100f)
        textView.setPadding((preferences.novelHorizontalPaddingDp.get() * activity.resources.displayMetrics.density).toInt())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            textView.justificationMode = if (preferences.novelJustifyText.get()) {
                android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
            } else {
                android.text.Layout.JUSTIFICATION_MODE_NONE
            }
        }
    }
}
