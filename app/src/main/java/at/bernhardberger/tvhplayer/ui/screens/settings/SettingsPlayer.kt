package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.streamProfilePresentation
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSectionTitle
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import at.bernhardberger.tvhplayer.viewmodels.ProfilesUiState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsPlayer(
    initialFocusRequester: FocusRequester,
    vm: SettingsPlayerViewModel = koinViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val directStreamingLabel = stringResource(R.string.profile_direct_streaming)

    SettingsPane(title = stringResource(R.string.settings_player)) {
        SettingsSwitchRow(
            label = stringResource(R.string.timeshift_setting),
            checked = ui.timeshiftEnabled,
            supportingText = stringResource(R.string.timeshift_setting_description),
            onClick = { vm.onTimeshiftEnabledChanged(!ui.timeshiftEnabled) },
            modifier = Modifier
                .width(480.dp)
                .fillMaxWidth()
                .focusRequester(initialFocusRequester),
        )

        SettingsSwitchRow(
            label = stringResource(R.string.refresh_rate_matching_setting),
            checked = ui.refreshRateMatchingEnabled,
            supportingText = stringResource(R.string.refresh_rate_matching_setting_description),
            onClick = {
                vm.onRefreshRateMatchingEnabledChanged(!ui.refreshRateMatchingEnabled)
            },
            modifier = Modifier
                .width(480.dp)
                .fillMaxWidth(),
        )

        SettingsSectionTitle(stringResource(R.string.profile))

        when (val profiles = ui.profiles) {
            ProfilesUiState.Idle -> Text(
                if (ui.connected) stringResource(R.string.loading_wait)
                else stringResource(R.string.not_connected)
            )
            ProfilesUiState.Loading -> Text(stringResource(R.string.loading))
            ProfilesUiState.Error -> Text(
                text = stringResource(R.string.stream_profiles_unavailable),
                color = MaterialTheme.colorScheme.error,
            )
            is ProfilesUiState.Ready -> {
                Column(
                    modifier = Modifier.width(480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profiles.items.forEach { profile ->
                        val presentation = streamProfilePresentation(
                            profileName = profile.name,
                            directStreamingLabel = directStreamingLabel,
                        )
                        ListItem(
                            selected = profile.id == ui.selectedProfileUuid,
                            onClick = { vm.onProfileSelected(profile) },
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
        }
    }
}
