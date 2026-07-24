package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsOptions(
    settingsStore: UiSettingsStore = koinInject(),
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val scope = rememberCoroutineScope()
    val onClick = {
        scope.launch { settingsStore.setShowEpgMenu(!settings.showEpgMenu) }
        Unit
    }

    SettingsPane(title = stringResource(R.string.settings_options)) {
        ListItem(
            selected = settings.showEpgMenu,
            onClick = onClick,
            headlineContent = { Text(stringResource(R.string.show_epg_menu)) },
            trailingContent = {
                Switch(
                    checked = settings.showEpgMenu,
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
    }
}
