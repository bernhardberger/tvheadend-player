package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.streamProfilePresentation
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSectionTitle
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsPlayer(
    initialFocusRequester: FocusRequester,
    vm: SettingsPlayerViewModel = koinViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    SettingsPlayerContent(
        initialFocusRequester = initialFocusRequester,
        ui = ui,
        onTimeshiftEnabledChanged = vm::onTimeshiftEnabledChanged,
        onRefreshRateMatchingEnabledChanged = vm::onRefreshRateMatchingEnabledChanged,
        onProfileSelected = vm::onProfileSelected,
    )
}

@Composable
internal fun SettingsPlayerContent(
    initialFocusRequester: FocusRequester,
    ui: SettingsPlayerUiState,
    onTimeshiftEnabledChanged: (Boolean) -> Unit,
    onRefreshRateMatchingEnabledChanged: (Boolean) -> Unit,
    onProfileSelected: (StreamProfileId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val directStreamingLabel = stringResource(R.string.profile_direct_streaming)

    SettingsPane(title = stringResource(R.string.settings_player), modifier = modifier) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSwitchRow(
                label = stringResource(R.string.timeshift_setting),
                checked = ui.timeshiftEnabled,
                supportingText = stringResource(R.string.timeshift_setting_description),
                onClick = { onTimeshiftEnabledChanged(!ui.timeshiftEnabled) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(initialFocusRequester),
            )

            SettingsSwitchRow(
                label = stringResource(R.string.refresh_rate_matching_setting),
                checked = ui.refreshRateMatchingEnabled,
                supportingText = stringResource(R.string.refresh_rate_matching_setting_description),
                onClick = {
                    onRefreshRateMatchingEnabledChanged(!ui.refreshRateMatchingEnabled)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SettingsSectionTitle(stringResource(R.string.profile))

            when (val profiles = ui.profiles) {
                StreamProfilesResult.NotReady -> Text(
                    if (ui.connected) stringResource(R.string.loading_wait)
                    else stringResource(R.string.not_connected)
                )
                is StreamProfilesResult.Available -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ListItem(
                            selected = ui.selectedProfileId == null,
                            onClick = { onProfileSelected(null) },
                            headlineContent = { Text(stringResource(R.string.profile_server_default)) },
                            scale = ListItemDefaults.scale(
                                focusedScale = 1f,
                                focusedSelectedScale = 1f,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        profiles.profiles.forEach { profile ->
                            val presentation = streamProfilePresentation(
                                profileName = profile.name,
                                directStreamingLabel = directStreamingLabel,
                            )
                            ListItem(
                                selected = profile.id == ui.selectedProfileId,
                                onClick = { onProfileSelected(profile.id) },
                                headlineContent = { Text(presentation.primaryLabel) },
                                supportingContent = presentation.secondaryLabel?.let { secondary ->
                                    { Text(secondary) }
                                },
                                scale = ListItemDefaults.scale(
                                    focusedScale = 1f,
                                    focusedSelectedScale = 1f,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                StreamProfilesResult.ObservationExpired,
                StreamProfilesResult.ServerRejected,
                StreamProfilesResult.AccessDenied,
                StreamProfilesResult.ConnectionLimit,
                StreamProfilesResult.Timeout,
                StreamProfilesResult.TransportUnavailable,
                StreamProfilesResult.NotSupported -> Text(
                    text = stringResource(R.string.stream_profiles_unavailable),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
