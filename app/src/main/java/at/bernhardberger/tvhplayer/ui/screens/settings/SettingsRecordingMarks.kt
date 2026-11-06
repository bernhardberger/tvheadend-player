package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.SkipLanding
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState

internal const val AD_BREAKS_LEVEL = "ad-breaks"

internal const val SKIP_LANDING_LEVEL = "skip-landing"

@Composable
internal fun settingsAdBreaksLevel(mode: AdBreakMode, onSelect: (AdBreakMode) -> Unit): DepthLevel =
    settingsLevel(AD_BREAKS_LEVEL, stringResource(R.string.ad_breaks_setting), AdBreakMode.entries.map { value ->
        settingsRow(value.name, adBreakModeLabel(value), supporting = stringResource(when (value) {
            AdBreakMode.ASK -> R.string.ad_breaks_ask_help
            AdBreakMode.AUTOMATIC -> R.string.ad_breaks_automatic_help
            AdBreakMode.OFF -> R.string.ad_breaks_off_help
        }), selected = mode == value, onClick = { onSelect(value) })
    }, initialItemId = mode.name)

@Composable
internal fun settingsSkipLandingLevel(landing: SkipLanding, onSelect: (SkipLanding) -> Unit): DepthLevel =
    settingsLevel(SKIP_LANDING_LEVEL, stringResource(R.string.skip_landing_setting), SkipLanding.entries.map { value ->
        settingsRow(value.name, skipLandingLabel(value), selected = landing == value, onClick = { onSelect(value) })
    }, initialItemId = landing.name, description = stringResource(R.string.skip_landing_description))

/** The Playback page's "Recording marks" group; its first row carries the section header. */
@Composable
internal fun settingsRecordingMarksRows(ui: SettingsPlayerUiState, onSkipCutsChanged: (Boolean) -> Unit) = listOf(
    settingsRow(AD_BREAKS_LEVEL, stringResource(R.string.ad_breaks_setting), adBreakModeLabel(ui.adBreakMode),
        child = AD_BREAKS_LEVEL, section = stringResource(R.string.settings_section_recording_marks)),
    settingsRow("skip-cuts", stringResource(R.string.skip_cuts_setting),
        supporting = stringResource(R.string.skip_cuts_setting_description), checked = ui.skipCuts,
        onClick = { onSkipCutsChanged(!ui.skipCuts) }),
    settingsRow(SKIP_LANDING_LEVEL, stringResource(R.string.skip_landing_setting), skipLandingLabel(ui.skipLanding),
        child = SKIP_LANDING_LEVEL),
)

@Composable
private fun adBreakModeLabel(mode: AdBreakMode): String = stringResource(when (mode) {
    AdBreakMode.ASK -> R.string.ad_breaks_ask
    AdBreakMode.AUTOMATIC -> R.string.ad_breaks_automatic
    AdBreakMode.OFF -> R.string.ad_breaks_off
})

@Composable
private fun skipLandingLabel(landing: SkipLanding): String = stringResource(when (landing) {
    SkipLanding.END -> R.string.skip_landing_end
    SkipLanding.TWO_SECONDS_BEFORE -> R.string.skip_landing_two_seconds
    SkipLanding.FIVE_SECONDS_BEFORE -> R.string.skip_landing_five_seconds
})
