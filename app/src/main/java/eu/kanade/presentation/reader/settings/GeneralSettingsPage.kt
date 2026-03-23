package eu.kanade.presentation.reader.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.viewer.text.TextViewer
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

private val themes = listOf(
    MR.strings.black_background to 1,
    MR.strings.gray_background to 2,
    MR.strings.white_background to 0,
    MR.strings.automatic_background to 3,
)

private val flashColors = listOf(
    MR.strings.pref_flash_style_black to ReaderPreferences.FlashColor.BLACK,
    MR.strings.pref_flash_style_white to ReaderPreferences.FlashColor.WHITE,
    MR.strings.pref_flash_style_white_black to ReaderPreferences.FlashColor.WHITE_BLACK,
)

private val novelDisplayModes = listOf(
    "Follow app theme" to ReaderPreferences.NovelDisplayMode.FOLLOW_APP,
    "Light" to ReaderPreferences.NovelDisplayMode.LIGHT,
    "Dark" to ReaderPreferences.NovelDisplayMode.DARK,
    "Gray" to ReaderPreferences.NovelDisplayMode.GRAY,
    "Sepia" to ReaderPreferences.NovelDisplayMode.SEPIA,
)

@Composable
internal fun ColumnScope.GeneralPage(screenModel: ReaderSettingsScreenModel) {
    val readerTheme by screenModel.preferences.readerTheme.collectAsState()

    val flashPageState by screenModel.preferences.flashOnPageChange.collectAsState()

    val flashMillisPref = screenModel.preferences.flashDurationMillis
    val flashMillis by flashMillisPref.collectAsState()

    val flashIntervalPref = screenModel.preferences.flashPageInterval
    val flashInterval by flashIntervalPref.collectAsState()

    val flashColorPref = screenModel.preferences.flashColor
    val flashColor by flashColorPref.collectAsState()

    val viewer by screenModel.viewerFlow.collectAsState()

    val novelFontSizeSpPref = screenModel.preferences.novelFontSizeSp
    val novelFontSizeSp by novelFontSizeSpPref.collectAsState()

    val novelLineSpacingPercentPref = screenModel.preferences.novelLineSpacingPercent
    val novelLineSpacingPercent by novelLineSpacingPercentPref.collectAsState()

    val novelHorizontalPaddingDpPref = screenModel.preferences.novelHorizontalPaddingDp
    val novelHorizontalPaddingDp by novelHorizontalPaddingDpPref.collectAsState()

    val novelDisplayModePref = screenModel.preferences.novelDisplayMode
    val novelDisplayMode by novelDisplayModePref.collectAsState()

    SettingsChipRow(MR.strings.pref_reader_theme) {
        themes.map { (labelRes, value) ->
            FilterChip(
                selected = readerTheme == value,
                onClick = { screenModel.preferences.readerTheme.set(value) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_show_page_number),
        pref = screenModel.preferences.showPageNumber,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_fullscreen),
        pref = screenModel.preferences.fullscreen,
    )

    val isFullscreen by screenModel.preferences.fullscreen.collectAsState()
    if (LocalActivity.current?.hasDisplayCutout() == true && isFullscreen) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_cutout_short),
            pref = screenModel.preferences.drawUnderCutout,
        )
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_keep_screen_on),
        pref = screenModel.preferences.keepScreenOn,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_read_with_long_tap),
        pref = screenModel.preferences.readWithLongTap,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_always_show_chapter_transition),
        pref = screenModel.preferences.alwaysShowChapterTransition,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_page_transitions),
        pref = screenModel.preferences.pageTransitions,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_flash_page),
        pref = screenModel.preferences.flashOnPageChange,
    )
    if (flashPageState) {
        SliderItem(
            value = flashMillis / ReaderPreferences.MILLI_CONVERSION,
            valueRange = 1..15,
            label = stringResource(MR.strings.pref_flash_duration),
            valueString = stringResource(MR.strings.pref_flash_duration_summary, flashMillis),
            onChange = { flashMillisPref.set(it * ReaderPreferences.MILLI_CONVERSION) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = flashInterval,
            valueRange = 1..10,
            label = stringResource(MR.strings.pref_flash_page_interval),
            valueString = pluralStringResource(MR.plurals.pref_pages, flashInterval, flashInterval),
            onChange = {
                flashIntervalPref.set(it)
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SettingsChipRow(MR.strings.pref_flash_with) {
            flashColors.map { (labelRes, value) ->
                FilterChip(
                    selected = flashColor == value,
                    onClick = { flashColorPref.set(value) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }

    if (viewer is TextViewer) {
        HeadingItem("Novel display mode")
        novelDisplayModes.forEach { (label, mode) ->
            RadioItem(
                label = label,
                selected = novelDisplayMode == mode,
                onClick = { novelDisplayModePref.set(mode) },
            )
        }

        SliderItem(
            value = novelFontSizeSp,
            valueRange = 14..32,
            label = "Novel font size",
            valueString = "$novelFontSizeSp sp",
            onChange = novelFontSizeSpPref::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        SliderItem(
            value = novelLineSpacingPercent,
            valueRange = 110..220,
            label = "Novel line spacing",
            valueString = "$novelLineSpacingPercent%",
            onChange = novelLineSpacingPercentPref::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        SliderItem(
            value = novelHorizontalPaddingDp,
            valueRange = 8..36,
            label = "Novel side margin",
            valueString = "$novelHorizontalPaddingDp dp",
            onChange = novelHorizontalPaddingDpPref::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        CheckboxItem(
            label = "Novel justify text",
            pref = screenModel.preferences.novelJustifyText,
        )
    }

}
