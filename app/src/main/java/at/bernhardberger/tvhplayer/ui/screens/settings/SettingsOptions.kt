package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsOptions(
    initialFocusRequester: FocusRequester,
    settingsStore: UiSettingsStore = koinInject(),
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val scope = rememberCoroutineScope()

    SettingsPane(title = stringResource(R.string.settings_options)) {
        SettingsSwitchRow(
            label = stringResource(R.string.show_epg_menu),
            checked = settings.showEpgMenu,
            supportingText = stringResource(R.string.show_epg_menu_description),
            onClick = {
                scope.launch { settingsStore.setShowEpgMenu(!settings.showEpgMenu) }
            },
            modifier = Modifier
                .width(480.dp)
                .fillMaxWidth()
                .focusRequester(initialFocusRequester),
        )
    }
}
