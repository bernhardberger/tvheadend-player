package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.models.RailItem
import at.bernhardberger.tvhplayer.ui.screens.SettingsRoutes
import at.bernhardberger.tvhplayer.ui.TvSettingsPanelAlpha

@Composable
fun SettingsSubRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var railFocused by remember { mutableStateOf(false) }
    val items = rememberSettingsItems()
    val itemFocus = remember(items) { items.associate { it.route to FocusRequester() } }

    var didInit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!didInit) {
            didInit = true
            (itemFocus[currentRoute] ?: itemFocus[items.first().route])?.requestFocus()
        }
    }

    LaunchedEffect(railFocused, currentRoute) {
        if (railFocused) {
            (itemFocus[currentRoute] ?: itemFocus[items.first().route])?.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.medium)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = TvSettingsPanelAlpha)
            )
            .padding(8.dp)
            .onFocusChanged { railFocused = it.hasFocus },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            ListItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                headlineContent = { Text(item.label) },
                leadingContent = { item.icon() },
                scale = ListItemDefaults.scale(
                    focusedScale = 1f,
                    focusedSelectedScale = 1f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(itemFocus.getValue(item.route)),
            )
        }
    }
}

@Composable
private fun rememberSettingsItems(): List<RailItem> {
    val languageLabel = stringResource(R.string.settings_language_nav)
    val optionsLabel = stringResource(R.string.settings_options_nav)
    val connectionLabel = stringResource(R.string.settings_connection_nav)
    val playerLabel = stringResource(R.string.settings_player_nav)
    val applianceLabel = stringResource(R.string.settings_appliance_nav)
    return remember(languageLabel, optionsLabel, connectionLabel, playerLabel, applianceLabel) {
        listOf(
            RailItem(SettingsRoutes.GENERAL, languageLabel) {
                Icon(Icons.Filled.Language, null, Modifier.size(24.dp))
            },
            RailItem(SettingsRoutes.OPTIONS, optionsLabel) {
                Icon(Icons.Filled.Tune, null, Modifier.size(24.dp))
            },
            RailItem(SettingsRoutes.CONNECTION, connectionLabel) {
                Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(24.dp))
            },
            RailItem(SettingsRoutes.PLAYER, playerLabel) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(24.dp))
            },
            RailItem(SettingsRoutes.APPLIANCE, applianceLabel) {
                Icon(Icons.Filled.Home, null, Modifier.size(24.dp))
            },
        )
    }
}
