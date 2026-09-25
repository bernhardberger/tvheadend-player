package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AudioFormatPreference
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import java.text.Collator
import java.util.Locale

private val audioLanguageCodes = listOf("de", "en", "fr", "it", "es", "pt", "nl", "pl", "cs", "sk", "hu",
    "sl", "hr", "sr", "ro", "bg", "el", "tr", "ru", "uk", "sv", "da", "nb", "fi", "ar", "zh", "ja", "ko")

@Composable
private fun languageName(code: String): String = Locale.forLanguageTag(code).getDisplayLanguage(LocalConfiguration.current.locales[0])

@Composable
private fun audioLanguageTitle(slot: Int): String = stringResource(when (slot) {
    0 -> R.string.audio_language_first
    1 -> R.string.audio_language_second
    else -> R.string.audio_language_third
})

@Composable
private fun formatName(format: AudioFormatPreference): String = stringResource(when (format) {
    AudioFormatPreference.AUTOMATIC -> R.string.audio_automatic
    AudioFormatPreference.PREFER_DOLBY -> R.string.audio_format_dolby
    AudioFormatPreference.PREFER_STEREO -> R.string.audio_format_stereo
})

@Composable
internal fun settingsAudioPreferenceRows(ui: SettingsPlayerUiState, onDescription: (Boolean) -> Unit): List<DepthRow> = buildList {
    for (slot in 0..minOf(ui.audioLanguages.size, 2)) {
        add(settingsRow("audio-language-$slot", audioLanguageTitle(slot),
            supporting = ui.audioLanguages.getOrNull(slot)?.let { languageName(it) }
                ?: stringResource(if (slot == 0) R.string.audio_automatic else R.string.audio_language_none),
            child = "audio-language-$slot", titleMaxLines = 3,
            sectionBottomSpacing = 8.dp,
            section = if (slot == 0) stringResource(R.string.audio_subtitles_section) else null))
    }
    add(settingsRow("audio-format", stringResource(R.string.audio_format), formatName(ui.audioFormat), child = "audio-format", titleMaxLines = 3))
    add(settingsRow("audio-description", stringResource(R.string.audio_description), checked = ui.audioDescription,
        titleMaxLines = 3, onClick = { onDescription(!ui.audioDescription) }))
    add(settingsRow("subtitle-language", stringResource(R.string.subtitle_language),
        ui.subtitleLanguage?.let { languageName(it) } ?: stringResource(R.string.subtitle_follow_system),
        child = "subtitle-language", titleMaxLines = 3))
}

@Composable
internal fun settingsAudioPreferenceLevels(
    ui: SettingsPlayerUiState,
    onAudioLanguageSelected: (Int, String?) -> Unit,
    onAudioFormatSelected: (AudioFormatPreference) -> Unit,
    onSubtitleLanguageSelected: (String?) -> Unit,
): List<DepthLevel> {
    val locale = LocalConfiguration.current.locales[0]
    val collator = Collator.getInstance(locale)
    val languages = audioLanguageCodes.map { it to Locale.forLanguageTag(it).getDisplayLanguage(locale) }
        .sortedWith { a, b -> collator.compare(a.second, b.second) }
    return buildList {
        for (slot in 0..2) {
            val selected = ui.audioLanguages.getOrNull(slot)
            add(settingsLevel("audio-language-$slot", audioLanguageTitle(slot), buildList {
                add(settingsRow("automatic", stringResource(if (slot == 0) R.string.audio_language_automatic else R.string.audio_language_none),
                    selected = selected == null, titleMaxLines = 3, onClick = { onAudioLanguageSelected(slot, null) }))
                languages.forEach { (code, label) ->
                    add(settingsRow(code, label, selected = selected == code, titleMaxLines = 3,
                        onClick = { onAudioLanguageSelected(slot, code) }))
                }
            }, initialItemId = selected?.takeIf(audioLanguageCodes::contains) ?: "automatic"))
        }
        add(settingsLevel("audio-format", stringResource(R.string.audio_format), AudioFormatPreference.entries.map { format ->
            settingsRow(format.name, formatName(format), supporting = stringResource(when (format) {
                AudioFormatPreference.AUTOMATIC -> R.string.audio_format_automatic_help
                AudioFormatPreference.PREFER_DOLBY -> R.string.audio_format_dolby_help
                AudioFormatPreference.PREFER_STEREO -> R.string.audio_format_stereo_help
            }), selected = format == ui.audioFormat, titleMaxLines = 3, onClick = { onAudioFormatSelected(format) })
        }, initialItemId = ui.audioFormat.name))
        add(settingsLevel("subtitle-language", stringResource(R.string.subtitle_language), buildList {
            add(settingsRow("automatic", stringResource(R.string.subtitle_follow_system), selected = ui.subtitleLanguage == null,
                titleMaxLines = 3, onClick = { onSubtitleLanguageSelected(null) }))
            languages.forEach { (code, label) ->
                add(settingsRow(code, label, selected = code == ui.subtitleLanguage, titleMaxLines = 3,
                    onClick = { onSubtitleLanguageSelected(code) }))
            }
        }, initialItemId = ui.subtitleLanguage?.takeIf(audioLanguageCodes::contains) ?: "automatic"))
    }
}
