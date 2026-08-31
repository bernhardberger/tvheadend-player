package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.models.RailItem
import at.bernhardberger.tvhplayer.ui.SettingsCategoryPaneWidth
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha

@Composable
internal fun SettingsSubRail(
    currentRoute: SettingsSection?,
    categoryFocusRequesters: Map<SettingsSection, FocusRequester>,
    contentFocusRequesters: Map<SettingsSection, FocusRequester>,
    onNavigate: (SettingsSection) -> Unit,
    initialFocusEnabled: Boolean = true,
    showSimpleTv: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val items = rememberSettingsItems(
        showSimpleTv = showSimpleTv,
    )
    val visibleRoutes = items.mapTo(mutableSetOf()) { it.route }
    val activeRoute = currentRoute?.takeIf(visibleRoutes::contains) ?: items.first().route
    val activeItemFocus = categoryFocusRequesters.getValue(activeRoute)

    // Focus the active category when Settings receives focus from the global
    // drawer. Category changes must not pull focus back out of the content pane.
    LaunchedEffect(initialFocusEnabled) {
        if (!initialFocusEnabled) return@LaunchedEffect
        (categoryFocusRequesters[currentRoute]
            ?: categoryFocusRequesters[items.firstOrNull()?.route])?.requestFocus()
    }

    Column(
        modifier = modifier
            .width(SettingsCategoryPaneWidth)
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.medium)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha)
            )
            .padding(TvSpacing8)
            .verticalScroll(rememberScrollState())
            .focusRestorer(activeItemFocus)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(TvSpacing8),
    ) {
        items.forEach { item ->
            val contentFocus = contentFocusRequesters.getValue(item.route)
            ListItem(
                selected = currentRoute == item.route,
                onClick = { contentFocus.requestFocus() },
                headlineContent = {
                    Text(
                        text = item.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = { item.icon() },
                scale = ListItemDefaults.scale(
                    focusedScale = 1f,
                    focusedSelectedScale = 1f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(categoryFocusRequesters.getValue(item.route))
                    .focusProperties { right = contentFocus }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && currentRoute != item.route) {
                            onNavigate(item.route)
                        }
                    },
            )
        }
    }
}

@Composable
private fun rememberSettingsItems(showSimpleTv: Boolean): List<RailItem<SettingsSection>> {
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
                RailItem(SettingsSection.GENERAL, languageLabel) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = languageLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsSection.OPTIONS, optionsLabel) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = optionsLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsSection.CHANNEL_TAGS, channelTagsLabel) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = channelTagsLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsSection.CONNECTION, connectionLabel) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = connectionLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsSection.PLAYER, playerLabel) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = playerLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            add(
                RailItem(SettingsSection.APPLIANCE, applianceLabel) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = applianceLabel,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            if (showSimpleTv) {
                add(
                    RailItem(SettingsSection.SIMPLE_TV, simpleTvLabel) {
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
