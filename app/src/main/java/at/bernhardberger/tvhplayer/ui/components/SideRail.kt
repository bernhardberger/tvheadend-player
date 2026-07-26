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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.LockOpen
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
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvSettings

@Composable
fun SideRail(
    currentRoute: String?,
    showEpgMenu: Boolean,
    simpleTvProfile: SimpleTvProfile = SimpleTvProfile(SimpleTvSettings(), false),
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val channelsLabel = stringResource(R.string.nav_channels)
    val epgLabel = stringResource(R.string.nav_epg)
    val recordingsLabel = stringResource(R.string.nav_recordings)
    val settingsLabel = stringResource(R.string.nav_settings)
    val unlockLabel = stringResource(R.string.simple_tv_unlock)
    val mainItems = remember(
        channelsLabel,
        epgLabel,
        recordingsLabel,
        showEpgMenu,
        simpleTvProfile,
    ) {
        buildList {
            if (simpleTvProfile.allows(SimpleTvCapability.CHANNEL_LIST)) {
                add(RailItem(Routes.CHANNELS, channelsLabel) {
                    Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(24.dp))
                })
            }
            if (showEpgMenu && simpleTvProfile.allows(SimpleTvCapability.EPG)) {
                add(RailItem(Routes.EPG, epgLabel) {
                    Icon(Icons.Filled.Event, null, Modifier.size(24.dp))
                })
            }
            if (simpleTvProfile.allows(SimpleTvCapability.RECORDINGS)) {
                add(RailItem(Routes.RECORDINGS, recordingsLabel) {
                    Icon(Icons.Filled.VideoLibrary, null, Modifier.size(24.dp))
                })
            }
        }
    }
    val footerItems = remember(
        unlockLabel,
        settingsLabel,
        simpleTvProfile,
    ) {
        buildList {
            if (simpleTvProfile.active) {
                add(RailItem(Routes.UNLOCK, unlockLabel) {
                    Icon(Icons.Filled.LockOpen, null, Modifier.size(24.dp))
                })
            }
            if (simpleTvProfile.allows(SimpleTvCapability.SETTINGS)) {
                add(RailItem(Routes.SETTINGS, settingsLabel) {
                    Icon(Icons.Filled.Settings, null, Modifier.size(24.dp))
                })
            }
        }
    }
    val items = mainItems + footerItems
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
                mainItems.forEach { item ->
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

                footerItems.forEach { item ->
                    NavigationDrawerItem(
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        leadingContent = item.icon,
                        modifier = Modifier.focusRequester(itemFocus.getValue(item.route)),
                    ) {
                        Text(item.label)
                    }
                }
            }
        },
        content = content,
    )
}
