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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.BrowseShellBackAction
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.browseShellBackAction
import at.bernhardberger.tvhplayer.models.RailItem
import at.bernhardberger.tvhplayer.ui.Routes
import at.bernhardberger.tvhplayer.ui.TvNavigationDrawerGradientEarlyAlpha
import at.bernhardberger.tvhplayer.ui.TvNavigationDrawerGradientLateAlpha
import at.bernhardberger.tvhplayer.ui.TvNavigationDrawerGradientMiddleAlpha
import at.bernhardberger.tvhplayer.ui.TvNavigationDrawerGradientStartAlpha
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientLateAlpha
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientMiddleAlpha
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientQuarterAlpha
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientRunout
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientStartAlpha
import at.bernhardberger.tvhplayer.ui.TvScreenPadding

private val DrawerStartPadding = 24.dp
private val DrawerEndPadding = 12.dp
private val ClosedDrawerWidth =
    DrawerStartPadding + NavigationDrawerItemDefaults.CollapsedDrawerItemWidth + DrawerEndPadding

@Composable
fun SideRail(
    currentRoute: String?,
    rootRoute: String = Routes.CHANNELS,
    showEpgMenu: Boolean,
    simpleTvProfile: SimpleTvProfile = SimpleTvProfile(SimpleTvSettings(), false),
    rootBackPriority: Boolean = false,
    onRootBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues, drawerActive: Boolean) -> Unit,
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
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
            if (showEpgMenu && simpleTvProfile.allows(SimpleTvCapability.EPG)) {
                add(RailItem(Routes.EPG, epgLabel) {
                    Icon(
                        Icons.Filled.Event,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
            if (simpleTvProfile.allows(SimpleTvCapability.RECORDINGS)) {
                add(RailItem(Routes.RECORDINGS, recordingsLabel) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = null,
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
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
            if (simpleTvProfile.allows(SimpleTvCapability.SETTINGS)) {
                add(RailItem(Routes.SETTINGS, settingsLabel) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
        }
    }
    val items = mainItems + footerItems
    val itemFocus = remember(items) { items.associate { it.route to FocusRequester() } }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var requestedRoute by remember { mutableStateOf(currentRoute) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    val requestRoute: (String) -> Unit = { route ->
        if (requestedRoute != route) {
            requestedRoute = route
            pendingRoute = route
            onNavigate(route)
        }
    }
    LaunchedEffect(currentRoute, pendingRoute) {
        if (currentRoute == pendingRoute) pendingRoute = null
    }
    val pendingDrawerRoute = pendingRoute?.takeIf(itemFocus::containsKey)
    val drawerRoute = requestedRoute?.takeIf(itemFocus::containsKey)
        ?: currentRoute?.takeIf(itemFocus::containsKey)
        ?: items.firstOrNull()?.route
    val activeItemFocus = itemFocus[
        if (drawerState.currentValue == DrawerValue.Open) {
            drawerRoute
        } else {
            pendingDrawerRoute ?: currentRoute
        }
    ] ?: itemFocus[items.firstOrNull()?.route]
    val backAction = browseShellBackAction(
        drawerOpen = drawerState.currentValue == DrawerValue.Open,
        currentRoute = currentRoute,
        drawerRoute = drawerRoute,
        rootRoute = rootRoute,
        rootBackPriority = rootBackPriority,
    )
    val handleBrowseBack: () -> Unit = {
        when (backAction) {
            BrowseShellBackAction.FOCUS_CURRENT_DESTINATION -> {
                (itemFocus[pendingDrawerRoute ?: currentRoute]
                    ?: itemFocus[items.firstOrNull()?.route])
                    ?.requestFocus()
            }
            BrowseShellBackAction.FOCUS_ROOT_DESTINATION -> {
                requestRoute(rootRoute)
                itemFocus[rootRoute]?.requestFocus()
            }
            BrowseShellBackAction.AWAIT_ROOT_DESTINATION -> {
                requestRoute(rootRoute)
                itemFocus[rootRoute]?.requestFocus()
            }
            BrowseShellBackAction.DELEGATE_TO_ROOT -> onRootBack()
        }
    }
    BackHandler(onBack = handleBrowseBack)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val browseWidth = (maxWidth - ClosedDrawerWidth).coerceAtLeast(0.dp)
        val railGradientWidth = ClosedDrawerWidth + TvNavigationRailGradientRunout
        val railGradientEndPx = with(LocalDensity.current) { railGradientWidth.toPx() }
        val collapsedRailBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = TvNavigationRailGradientStartAlpha),
                0.25f to Color.Black.copy(alpha = TvNavigationRailGradientQuarterAlpha),
                0.55f to Color.Black.copy(alpha = TvNavigationRailGradientMiddleAlpha),
                0.78f to Color.Black.copy(alpha = TvNavigationRailGradientLateAlpha),
                1f to Color.Transparent,
            ),
            endX = railGradientEndPx,
        )
        val expandedDrawerBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = TvNavigationDrawerGradientStartAlpha),
                0.35f to Color.Black.copy(alpha = TvNavigationDrawerGradientEarlyAlpha),
                0.70f to Color.Black.copy(alpha = TvNavigationDrawerGradientMiddleAlpha),
                0.90f to Color.Black.copy(alpha = TvNavigationDrawerGradientLateAlpha),
                1f to Color.Transparent,
            ),
        )
        NavigationDrawer(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (drawerState.currentValue == DrawerValue.Closed) {
                        Modifier.background(collapsedRailBrush)
                    } else {
                        Modifier
                    }
                )
                .testTag("global-navigation-shell"),
            drawerState = drawerState,
            drawerContent = { drawerValue ->
                val selectedRoute = if (drawerValue == DrawerValue.Open) {
                    drawerRoute
                } else {
                    currentRoute
                }
                // Preserve an unacknowledged focus intent across drawer re-entry.
                LaunchedEffect(drawerValue) {
                    if (drawerValue == DrawerValue.Open) {
                        val targetRoute = pendingDrawerRoute
                            ?: currentRoute?.takeIf(itemFocus::containsKey)
                            ?: items.firstOrNull()?.route
                        if (pendingDrawerRoute != null) {
                            requestedRoute = pendingDrawerRoute
                        } else if (targetRoute != null && targetRoute != currentRoute) {
                            requestRoute(targetRoute)
                        } else {
                            requestedRoute = targetRoute
                        }
                        itemFocus[targetRoute]?.requestFocus()
                    }
                }
                // An item-set change preserves a still-valid D-pad intent. Only
                // removal of that target falls back to reported route or root.
                LaunchedEffect(itemFocus) {
                    val targetRoute = requestedRoute?.takeIf(itemFocus::containsKey)
                        ?: currentRoute?.takeIf(itemFocus::containsKey)
                        ?: items.firstOrNull()?.route
                    val targetChanged = targetRoute != requestedRoute
                    val pendingTargetRemoved = pendingRoute
                        ?.let { !itemFocus.containsKey(it) } == true
                    if (pendingTargetRemoved) pendingRoute = null
                    if (targetChanged && targetRoute != null && targetRoute != currentRoute) {
                        requestRoute(targetRoute)
                    } else {
                        requestedRoute = targetRoute
                    }
                    if (drawerValue == DrawerValue.Open) {
                        itemFocus[targetRoute]?.requestFocus()
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(
                            if (drawerValue == DrawerValue.Open) {
                                Modifier.background(expandedDrawerBrush)
                            } else {
                                Modifier
                            }
                        )
                        .testTag("global-drawer-surface")
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
                        key(item.route) {
                            NavigationDrawerItem(
                                selected = selectedRoute == item.route,
                                onClick = { requestRoute(item.route) },
                                leadingContent = item.icon,
                                modifier = Modifier
                                    .focusRequester(itemFocus.getValue(item.route))
                                    .semantics { contentDescription = item.label }
                                    .testTag("nav-${item.route}")
                                    .onFocusChanged { focusState ->
                                        if (
                                            drawerValue == DrawerValue.Open &&
                                            focusState.isFocused
                                        ) {
                                            requestRoute(item.route)
                                        }
                                    },
                            ) {
                                Text(item.label)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    footerItems.forEach { item ->
                        key(item.route) {
                            NavigationDrawerItem(
                                selected = selectedRoute == item.route,
                                onClick = { requestRoute(item.route) },
                                leadingContent = item.icon,
                                modifier = Modifier
                                    .focusRequester(itemFocus.getValue(item.route))
                                    .semantics { contentDescription = item.label }
                                    .testTag("nav-${item.route}")
                                    .onFocusChanged { focusState ->
                                        if (
                                            drawerValue == DrawerValue.Open &&
                                            focusState.isFocused
                                        ) {
                                            requestRoute(item.route)
                                        }
                                    },
                            ) {
                                Text(item.label)
                            }
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
