package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.BrowseShellBackAction
import at.bernhardberger.tvhplayer.models.RailItem
import at.bernhardberger.tvhplayer.ui.Routes
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.ui.TvSettingsPanelAlpha
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.browseShellBackAction

private val DrawerStartPadding = 24.dp
private val DrawerEndPadding = 12.dp
private val ClosedDrawerWidth =
    DrawerStartPadding + NavigationDrawerItemDefaults.CollapsedDrawerItemWidth + DrawerEndPadding

@Composable
fun SideRail(
    currentRoute: String?,
    showEpgMenu: Boolean,
    simpleTvProfile: SimpleTvProfile = SimpleTvProfile(SimpleTvSettings(), false),
    rootBackPriority: Boolean = false,
    onRootBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues, drawerActive: Boolean) -> Unit,
) {
    val homeLabel = stringResource(R.string.nav_home)
    val channelsLabel = stringResource(R.string.nav_channels)
    val epgLabel = stringResource(R.string.nav_epg)
    val recordingsLabel = stringResource(R.string.nav_recordings)
    val settingsLabel = stringResource(R.string.nav_settings)
    val unlockLabel = stringResource(R.string.simple_tv_unlock)
    val mainItems = remember(
        homeLabel,
        channelsLabel,
        epgLabel,
        recordingsLabel,
        showEpgMenu,
        simpleTvProfile,
    ) {
        buildList {
            if (simpleTvProfile.allows(SimpleTvCapability.CHANNEL_LIST)) {
                add(RailItem(Routes.HOME, homeLabel) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = homeLabel,
                        modifier = Modifier.size(24.dp),
                    )
                })
                add(RailItem(Routes.CHANNELS, channelsLabel) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = channelsLabel,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
            if (showEpgMenu && simpleTvProfile.allows(SimpleTvCapability.EPG)) {
                add(RailItem(Routes.EPG, epgLabel) {
                    Icon(
                        Icons.Filled.Event,
                        contentDescription = epgLabel,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
            if (simpleTvProfile.allows(SimpleTvCapability.RECORDINGS)) {
                add(RailItem(Routes.RECORDINGS, recordingsLabel) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = recordingsLabel,
                        modifier = Modifier.size(24.dp),
                    )
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
                    Icon(
                        Icons.Filled.LockOpen,
                        contentDescription = unlockLabel,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
            if (simpleTvProfile.allows(SimpleTvCapability.SETTINGS)) {
                add(RailItem(Routes.SETTINGS, settingsLabel) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = settingsLabel,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
        }
    }
    val items = mainItems + footerItems
    val itemFocus = remember(items) { items.associate { it.route to FocusRequester() } }
    val activeItemFocus = itemFocus[currentRoute] ?: itemFocus[items.firstOrNull()?.route]
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val backAction = browseShellBackAction(
        drawerOpen = drawerState.currentValue == DrawerValue.Open,
        currentRoute = currentRoute,
        homeRoute = Routes.HOME,
        rootBackPriority = rootBackPriority,
    )
    val handleBrowseBack: () -> Unit = {
        when (backAction) {
            BrowseShellBackAction.FOCUS_CURRENT_DESTINATION -> {
                (itemFocus[currentRoute] ?: itemFocus[items.firstOrNull()?.route])
                    ?.requestFocus()
            }
            BrowseShellBackAction.FOCUS_HOME_DESTINATION -> {
                itemFocus[Routes.HOME]?.requestFocus()
            }
            BrowseShellBackAction.DELEGATE_TO_ROOT -> onRootBack()
        }
    }
    // Remote key dispatch supplies focus-layer precedence. Keep the dispatcher
    // path for accessibility and system Back actions without a focused key target.
    BackHandler(onBack = handleBrowseBack)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.key != Key.Back || event.type != KeyEventType.KeyUp) {
                    return@onKeyEvent false
                }
                handleBrowseBack()
                true
            },
    ) {
        val browseWidth = (maxWidth - ClosedDrawerWidth).coerceAtLeast(0.dp)
        NavigationDrawer(
            modifier = Modifier.fillMaxSize(),
            drawerState = drawerState,
            drawerContent = { drawerValue ->
                LaunchedEffect(drawerValue, currentRoute) {
                    if (drawerValue == DrawerValue.Open) {
                        (itemFocus[currentRoute] ?: itemFocus[items.firstOrNull()?.route])
                            ?.requestFocus()
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = TvSettingsPanelAlpha)
                        )
                        // The surface reaches the edge; content retains the same safe inset.
                        .padding(
                            start = DrawerStartPadding,
                            end = DrawerEndPadding,
                            top = 32.dp,
                            bottom = 32.dp,
                        )
                        .then(
                            activeItemFocus?.let { Modifier.focusRestorer(it) } ?: Modifier
                        )
                        .focusGroup()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    mainItems.forEach { item ->
                        NavigationDrawerItem(
                            selected = currentRoute == item.route,
                            onClick = { onNavigate(item.route) },
                            leadingContent = item.icon,
                            modifier = Modifier
                                .focusRequester(itemFocus.getValue(item.route))
                                .testTag("nav-${item.route}")
                                .onFocusChanged { focusState ->
                                    if (
                                        drawerValue == DrawerValue.Open &&
                                        focusState.isFocused &&
                                        currentRoute != item.route
                                    ) {
                                        onNavigate(item.route)
                                    }
                                },
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
                            modifier = Modifier
                                .focusRequester(itemFocus.getValue(item.route))
                                .testTag("nav-${item.route}")
                                .onFocusChanged { focusState ->
                                    if (
                                        drawerValue == DrawerValue.Open &&
                                        focusState.isFocused &&
                                        currentRoute != item.route
                                    ) {
                                        onNavigate(item.route)
                                    }
                                },
                        ) {
                            Text(item.label)
                        }
                    }
                }
            },
            content = {
                BrowseViewport(width = browseWidth) {
                    content(
                        TvScreenPadding,
                        drawerState.currentValue == DrawerValue.Open,
                    )
                }
            },
        )
    }
}

@Composable
private fun BrowseViewport(
    width: Dp,
    content: @Composable () -> Unit,
) {
    Layout(
        content = { Box(Modifier.fillMaxSize()) { content() } },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val fixedWidth = width.roundToPx()
        val placeable = measurables.single().measure(
            constraints.copy(minWidth = fixedWidth, maxWidth = fixedWidth),
        )
        // Report Material's available width while preserving the closed browse
        // viewport. Drawer expansion then translates and clips instead of reflowing.
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(0, 0)
        }
    }
}
