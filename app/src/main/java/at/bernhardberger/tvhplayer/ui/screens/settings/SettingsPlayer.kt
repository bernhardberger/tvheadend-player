package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.viewmodels.ProfilesUiState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsPlayer(
    vm: SettingsPlayerViewModel = koinViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    SettingsPane(title = stringResource(R.string.settings_player)) {
        ListItem(
            selected = ui.timeshiftEnabled,
            onClick = { vm.onTimeshiftEnabledChanged(!ui.timeshiftEnabled) },
            headlineContent = { Text(stringResource(R.string.timeshift_setting)) },
            supportingContent = { Text(stringResource(R.string.timeshift_setting_description)) },
            trailingContent = {
                Switch(
                    checked = ui.timeshiftEnabled,
                    onCheckedChange = null,
                )
            },
            scale = ListItemDefaults.scale(
                focusedScale = 1f,
                focusedSelectedScale = 1f,
            ),
            modifier = Modifier
                .width(480.dp)
                .fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.profile),
            style = MaterialTheme.typography.titleMedium,
        )

        when (val profiles = ui.profiles) {
            ProfilesUiState.Idle -> Text(
                if (ui.connected) stringResource(R.string.loading_wait)
                else stringResource(R.string.not_connected)
            )
            ProfilesUiState.Loading -> Text(stringResource(R.string.loading))
            is ProfilesUiState.Error -> Text(
                text = stringResource(R.string.error_prefix) + " " + profiles.message,
                color = MaterialTheme.colorScheme.error,
            )
            is ProfilesUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .width(480.dp)
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profiles.items.forEach { profile ->
                        ListItem(
                            selected = profile.id == ui.selectedProfileUuid,
                            onClick = { vm.onProfileSelected(profile) },
                            headlineContent = { Text(profile.name) },
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
