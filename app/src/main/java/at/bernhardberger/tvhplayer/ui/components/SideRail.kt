package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.models.RailItem
import at.bernhardberger.tvhplayer.ui.Routes
import at.bernhardberger.tvhplayer.ui.TvSettingsPanelAlpha

@Composable
fun SideRail(
    currentRoute: String?,
    showEpgMenu: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val channelsLabel = stringResource(R.string.nav_channels)
    val epgLabel = stringResource(R.string.nav_epg)
    val settingsLabel = stringResource(R.string.nav_settings)
    val items = remember(channelsLabel, epgLabel, settingsLabel, showEpgMenu) {
        buildList {
            add(RailItem(Routes.CHANNELS, channelsLabel) {
                Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(24.dp))
            })
            if (showEpgMenu) {
                add(RailItem(Routes.EPG, epgLabel) {
                    Icon(Icons.Filled.Event, null, Modifier.size(24.dp))
                })
            }
            add(RailItem(Routes.SETTINGS, settingsLabel) {
                Icon(Icons.Filled.Settings, null, Modifier.size(24.dp))
            })
        }
    }
    val itemFocus = remember(items) { items.associate { it.route to FocusRequester() } }

    NavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerContent = { drawerValue ->
            LaunchedEffect(drawerValue, currentRoute) {
                if (drawerValue == DrawerValue.Open) {
                    (itemFocus[currentRoute] ?: itemFocus[items.first().route])?.requestFocus()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = TvSettingsPanelAlpha)
                    )
                    .padding(horizontal = 12.dp, vertical = 32.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.dropLast(1).forEach { item ->
                    NavigationDrawerItem(
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        leadingContent = item.icon,
                        modifier = Modifier.focusRequester(itemFocus.getValue(item.route)),
                    ) {
                        Text(item.label)
                    }
                }

                Spacer(Modifier.weight(1f))

                val settings = items.last()
                NavigationDrawerItem(
                    selected = currentRoute == settings.route,
                    onClick = { onNavigate(settings.route) },
                    leadingContent = settings.icon,
                    modifier = Modifier.focusRequester(itemFocus.getValue(settings.route)),
                ) {
                    Text(settings.label)
                }
            }
        },
        content = content,
    )
}
