package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel

internal const val RECORDINGS_LEVEL = "recordings"
internal const val RECORDING_PROFILE_LEVEL = "recording-profile"
internal const val RECORDING_SPACE_ROW = "recording-space"

@Composable
internal fun settingsRecordingLevels(
    profiles: List<DvrConfiguration>,
    preferredId: DvrConfigId?,
    spaceValue: String? = null,
    onSelect: (DvrConfigId) -> Unit,
): List<DepthLevel> {
    val showProfiles = profiles.size > 1
    if (!showProfiles && spaceValue == null) return emptyList()
    val selected = chooseDvrConfig(profiles, preferredId)
    val title = stringResource(R.string.details_recording_profile)
    val default = stringResource(R.string.recording_profile_default)
    // The profile choice comes first so the section opens on something to change; recording
    // space is a read-only row that still takes focus, so a one-profile server has a section too.
    val rows = listOfNotNull(
        if (showProfiles) settingsRow(RECORDING_PROFILE_LEVEL, title,
            profiles.firstOrNull { it.id == selected }?.name.orEmpty().ifBlank { default },
            child = RECORDING_PROFILE_LEVEL) else null,
        spaceValue?.let { settingsRow(RECORDING_SPACE_ROW, stringResource(R.string.recording_space_title), it) },
    )
    return listOf(settingsLevel(RECORDINGS_LEVEL, stringResource(R.string.nav_recordings), rows)) +
        if (showProfiles) listOf(settingsLevel(RECORDING_PROFILE_LEVEL, title, profiles.map { profile ->
            settingsRow(profile.id.value, profile.name.ifBlank { default }, selected = profile.id == selected,
                onClick = { onSelect(profile.id) })
        }, initialItemId = selected?.value)) else emptyList()
}
