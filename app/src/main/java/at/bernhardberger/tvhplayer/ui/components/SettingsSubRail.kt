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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
    showSimpleTv: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val items = rememberSettingsItems(
        showSimpleTv = showSimpleTv,
    )
    val itemFocus = remember(items) { items.associate { it.route to FocusRequester() } }
    val activeItemFocus = itemFocus[currentRoute] ?: itemFocus.getValue(items.first().route)

    // Keep focus on the active category after activation. Content receives focus
    // only when the user presses Right; NavHost recomposition must not leave the
    // screen with no focused target.
    LaunchedEffect(currentRoute, items) {
        (itemFocus[currentRoute] ?: itemFocus[items.firstOrNull()?.route])?.requestFocus()
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
            .focusRestorer(activeItemFocus),
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
private fun rememberSettingsItems(showSimpleTv: Boolean): List<RailItem> {
    val languageLabel = stringResource(R.string.settings_language_nav)
    val optionsLabel = stringResource(R.string.settings_options_nav)
    val channelTagsLabel = stringResource(R.string.settings_channel_tags_nav)
    val connectionLabel = stringResource(R.string.settings_connection_nav)
    val playerLabel = stringResource(R.string.settings_player_nav)
    val applianceLabel = stringResource(R.string.settings_appliance_nav)
    val simpleTvLabel = stringResource(R.string.settings_simple_tv_nav)
    return remember(
        languageLabel,
        optionsLabel,
        channelTagsLabel,
        connectionLabel,
        playerLabel,
        applianceLabel,
        simpleTvLabel,
        showSimpleTv,
    ) {
        buildList {
            add(
                RailItem(SettingsRoutes.GENERAL, languageLabel) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = languageLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsRoutes.OPTIONS, optionsLabel) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = optionsLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsRoutes.CHANNEL_TAGS, channelTagsLabel) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = channelTagsLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsRoutes.CONNECTION, connectionLabel) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = connectionLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsRoutes.PLAYER, playerLabel) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = playerLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsRoutes.APPLIANCE, applianceLabel) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = applianceLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            if (showSimpleTv) {
                add(
                    RailItem(SettingsRoutes.SIMPLE_TV, simpleTvLabel) {
                        Icon(
                            Icons.Filled.AccessibilityNew,
                            contentDescription = simpleTvLabel,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }
        }
    }
}
