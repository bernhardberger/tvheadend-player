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

@Composable
internal fun settingsRecordingLevels(
    profiles: List<DvrConfiguration>,
    preferredId: DvrConfigId?,
    onSelect: (DvrConfigId) -> Unit,
): List<DepthLevel> {
    if (profiles.size <= 1) return emptyList()
    val selected = chooseDvrConfig(profiles, preferredId)
    val title = stringResource(R.string.details_recording_profile)
    val default = stringResource(R.string.recording_profile_default)
    return listOf(
        settingsLevel(RECORDINGS_LEVEL, stringResource(R.string.nav_recordings), listOf(
            settingsRow(RECORDING_PROFILE_LEVEL, title,
                profiles.firstOrNull { it.id == selected }?.name.orEmpty().ifBlank { default },
                child = RECORDING_PROFILE_LEVEL),
        )),
        settingsLevel(RECORDING_PROFILE_LEVEL, title, profiles.map { profile ->
            settingsRow(profile.id.value, profile.name.ifBlank { default }, selected = profile.id == selected,
                onClick = { onSelect(profile.id) })
        }, initialItemId = selected?.value),
    )
}
