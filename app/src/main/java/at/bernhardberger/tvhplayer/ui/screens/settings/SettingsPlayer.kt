package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.streamProfilePresentation
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun settingsPlayerLevel(vm: SettingsPlayerViewModel = koinViewModel()): DepthLevel {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return settingsPlayerLevel(ui, vm::onTimeshiftEnabledChanged, vm::onRefreshRateMatchingEnabledChanged,
        vm::onProfileSelected, vm::onAudioPassthroughEnabledChanged)
}

@Composable
internal fun settingsPlayerLevel(
    ui: SettingsPlayerUiState,
    onTimeshiftEnabledChanged: (Boolean) -> Unit,
    onRefreshRateMatchingEnabledChanged: (Boolean) -> Unit,
    onProfileSelected: (StreamProfileId?) -> Unit,
    onAudioPassthroughEnabledChanged: (Boolean) -> Unit,
): DepthLevel {
    val direct = stringResource(R.string.profile_direct_streaming)
    val profileSection = stringResource(R.string.profile)
    return settingsLevel(SettingsSection.PLAYER.name, stringResource(R.string.settings_player), buildList {
        add(settingsRow("timeshift", stringResource(R.string.timeshift_setting), checked = ui.timeshiftEnabled,
            onClick = { onTimeshiftEnabledChanged(!ui.timeshiftEnabled) }))
        add(settingsRow("refresh-rate", stringResource(R.string.refresh_rate_matching_setting), checked = ui.refreshRateMatchingEnabled,
            onClick = { onRefreshRateMatchingEnabledChanged(!ui.refreshRateMatchingEnabled) }))
        add(settingsRow("audio-passthrough", stringResource(R.string.audio_passthrough_setting),
            supporting = if (ui.audioPassthroughChangeFailed) stringResource(R.string.audio_passthrough_failed)
                else stringResource(R.string.audio_passthrough_description), checked = ui.audioPassthroughEnabled,
            onClick = { onAudioPassthroughEnabledChanged(!ui.audioPassthroughEnabled) }))
        when (val profiles = ui.profiles) {
            StreamProfilesResult.NotReady -> add(settingsRow("profiles-status",
                stringResource(if (ui.connected) R.string.loading_wait else R.string.not_connected), section = profileSection))
            is StreamProfilesResult.Available -> {
                add(settingsRow("profile-default", stringResource(R.string.profile_server_default), section = profileSection,
                    selected = ui.selectedProfileId == null, onClick = { onProfileSelected(null) }))
                profiles.profiles.forEach { profile ->
                    val presentation = streamProfilePresentation(profile.name, direct)
                    add(settingsRow("profile-${profile.id.value}", presentation.primaryLabel, presentation.secondaryLabel,
                        selected = profile.id == ui.selectedProfileId, onClick = { onProfileSelected(profile.id) }))
                }
            }
            else -> add(settingsRow("profiles-status", stringResource(R.string.stream_profiles_unavailable), section = profileSection))
        }
    })
}
