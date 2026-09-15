package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.profiling.profileLayout
import at.bernhardberger.tvhplayer.profiling.profileTrace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.DrawerState
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.BrowseShellBackAction
import at.bernhardberger.tvhplayer.core.browseShellBackAction
import at.bernhardberger.tvhplayer.models.RailItem
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TvScreenPadding

// Material for TV drawer padding: 12dp on both edges of the item column.
private val DrawerStartPadding = 12.dp
private val DrawerEndPadding = 12.dp
private val ClosedDrawerWidth =
    DrawerStartPadding + NavigationDrawerItemDefaults.CollapsedDrawerItemWidth + DrawerEndPadding

/** Drawer top section: the kit's 56dp mark box above the first destination. */
private val BrandHeaderHeight = 56.dp
private val BrandSymbolSize = 32.dp
private val BrandWordmarkHeight = 18.dp

// tv-material's drawer item is a ListItem with 16dp content padding whose leading
// slot is a 32dp minimum box holding the 24dp icon. Collapsed (56dp) only 24dp
// remain for that slot, so the icon sits at 16..40; as the item widens past 64dp
// the slot regains its 32dp and the icon settles at 20..44, with the label at 56.
// The mark derives its centre from the same animated width so it moves in
// lockstep with the item icons instead of stepping ahead of them.
private val ItemContentPadding = 16.dp
private val ExpandedItemLabelStart = 56.dp

private fun itemIconCenter(itemWidth: Dp): Dp {
    val available = itemWidth - ItemContentPadding * 2
    val leadingSlot = available
        .coerceAtLeast(NavigationDrawerItemDefaults.IconSize)
        .coerceAtMost(ListItemDefaults.IconSize)
    return ItemContentPadding + leadingSlot / 2
}

/** Current measure's visible extent from the browse content's logical leading edge. */
internal val LocalBrowseVisibleWidthPx = compositionLocalOf<Int?> { null }

/** Read the widget's current focus ownership at deferred focus-request time. */
internal val LocalBrowseDrawerState = staticCompositionLocalOf<DrawerState?> { null }
internal val LocalBrowseNavigationFocus = staticCompositionLocalOf<FocusRequester?> { null }

@Composable
internal fun SideRail(
    currentRoute: AppDestination?,
    showEpgMenu: Boolean,
    onRootBack: () -> Unit,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    rootRoute: AppDestination = AppDestination.CHANNELS,
    availableDestinations: Set<AppDestination> = setOf(
        AppDestination.CHANNELS,
        AppDestination.GUIDE,
        AppDestination.RECORDINGS,
        AppDestination.SETTINGS,
    ),
    rootBackPriority: Boolean = false,
    onBackHandlerChanged: ((() -> Unit) -> Unit) = {},
    content: @Composable (PaddingValues, drawerActive: Boolean) -> Unit,
) {
    val channelsLabel = stringResource(R.string.nav_channels)
    val epgLabel = stringResource(R.string.nav_epg)
    val recordingsLabel = stringResource(R.string.nav_recordings)
    val settingsLabel = stringResource(R.string.nav_settings)
    val mainItems = remember(
        channelsLabel,
        epgLabel,
        recordingsLabel,
        showEpgMenu,
        availableDestinations,
    ) {
        buildList {
            if (AppDestination.CHANNELS in availableDestinations) {
                add(RailItem(AppDestination.CHANNELS, channelsLabel) {
                    Icon(
                        painter = painterResource(R.drawable.ic_list),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
            if (showEpgMenu && AppDestination.GUIDE in availableDestinations) {
                add(RailItem(AppDestination.GUIDE, epgLabel) {
                    Icon(
                        painter = painterResource(R.drawable.ic_event),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
            if (AppDestination.RECORDINGS in availableDestinations) {
                add(RailItem(AppDestination.RECORDINGS, recordingsLabel) {
                    Icon(
                        painter = painterResource(R.drawable.ic_video_library),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                })
            }
        }
    }
    val footerItems = remember(
        settingsLabel,
        availableDestinations,
    ) {
        buildList {
            if (AppDestination.SETTINGS in availableDestinations) {
                add(RailItem(AppDestination.SETTINGS, settingsLabel) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
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
    var pendingRoute by remember { mutableStateOf<AppDestination?>(null) }
    val requestRoute: (AppDestination) -> Unit = { route ->
        if (requestedRoute != route) {
            profileTrace("P44:sidebarRequest:${route.name}") {
                requestedRoute = route
                pendingRoute = route
                onNavigate(route)
            }
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
    SideEffect { onBackHandlerChanged(handleBrowseBack) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val browseWidth = (maxWidth - ClosedDrawerWidth).coerceAtLeast(0.dp)
        // The standard push drawer owns no scrim of its own: readability over warm
        // playback belongs to the single global WarmPlaybackScrimAlpha layer.
        NavigationDrawer(
            modifier = Modifier
                .fillMaxSize()
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
                        .profileLayout("sidebar")
                        .fillMaxHeight()
                        .testTag("global-drawer-surface")
                        // The surface reaches the edge; content retains the same safe inset.
                        // Kit drawer: 12dp on every edge; header and footer are 56dp
                        // sections and the destination block is centred between them.
                        .padding(DrawerStartPadding)
                        .then(
                            activeItemFocus?.let { Modifier.focusRestorer(it) } ?: Modifier
                        )
                        .focusGroup()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DrawerBrandHeader(drawerValue)

                    Spacer(Modifier.weight(1f))

                    mainItems.forEach { item ->
                        key(item.route) {
                            NavigationDrawerItem(
                                selected = selectedRoute == item.route,
                                onClick = { requestRoute(item.route) },
                                leadingContent = item.icon,
                                modifier = Modifier
                                    .focusRequester(itemFocus.getValue(item.route))
                                    .semantics { contentDescription = item.label }
                                    .testTag(item.route.testTag)
                                    .onFocusChanged { focusState ->
                                        if (BuildConfig.PROFILE_TRACE && focusState.isFocused) {
                                            profileTrace("P48:sidebarFocus:${item.route.name}") { }
                                        }
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
                                    .testTag(item.route.testTag)
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
                    CompositionLocalProvider(
                        LocalBrowseDrawerState provides drawerState,
                        LocalBrowseNavigationFocus provides activeItemFocus,
                    ) {
                        content(
                            TvScreenPadding,
                            drawerState.currentValue == DrawerValue.Open,
                        )
                    }
                }
            },
        )
    }
}

/**
 * Drawer top section carrying the product mark. It is not a focus target, has no
 * click action, and never changes first focus or Back; the app name is announced
 * once by the wordmark. Its width animates between the same two drawer item
 * widths on `animateDpAsState`'s default spring, as the library item does, so the
 * mark reveals with the sheet instead of widening it ahead of the items.
 */
@Composable
private fun DrawerBrandHeader(drawerValue: DrawerValue) {
    val expanded = drawerValue == DrawerValue.Open
    val headerWidth by animateDpAsState(
        targetValue = if (expanded) {
            NavigationDrawerItemDefaults.ExpandedDrawerItemWidth
        } else {
            NavigationDrawerItemDefaults.CollapsedDrawerItemWidth
        },
        label = "drawerBrandHeaderWidth",
    )
    val symbolCenter = itemIconCenter(headerWidth)
    Box(
        modifier = Modifier
            .width(headerWidth)
            .height(BrandHeaderHeight)
            .clipToBounds()
            .testTag("global-drawer-brand"),
    ) {
        Image(
            painter = painterResource(R.drawable.startup_brand_symbol),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = symbolCenter - BrandSymbolSize / 2)
                .size(BrandSymbolSize),
        )
        // The wordmark reveals and hides with the same transitions the library
        // applies to the drawer items' labels.
        AnimatedVisibility(
            visible = expanded,
            enter = NavigationDrawerItemDefaults.ContentAnimationEnter,
            exit = NavigationDrawerItemDefaults.ContentAnimationExit,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = ExpandedItemLabelStart),
        ) {
            Image(
                painter = painterResource(R.drawable.brand_wordmark),
                contentDescription = stringResource(R.string.app_name),
                // A logo keeps its drawn proportions instead of following font scale.
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .requiredHeight(BrandWordmarkHeight)
                    .testTag("global-drawer-wordmark"),
            )
        }
    }
}

private val AppDestination.testTag: String
    get() = when (this) {
        AppDestination.CHANNELS -> "nav-channels"
        AppDestination.GUIDE -> "nav-epg"
        AppDestination.RECORDINGS -> "nav-recordings"
        AppDestination.SETTINGS -> "nav-settings"
        AppDestination.LIVE_PLAYER -> "nav-player"
        AppDestination.RECORDING_PLAYER -> "nav-recording-player"
    }

@Composable
private fun BrowseViewport(
    width: Dp,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(
        modifier = Modifier.fillMaxSize(),
    ) { constraints ->
        val fixedWidth = width.roundToPx()
        val placeable = subcompose(Unit) {
            CompositionLocalProvider(
                LocalBrowseVisibleWidthPx provides constraints.maxWidth,
            ) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }.single().measure(
            constraints.copy(minWidth = fixedWidth, maxWidth = fixedWidth),
        )
        // Report Material's available width while preserving the closed browse
        // viewport. Drawer expansion then translates and clips instead of reflowing.
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(0, 0)
        }
    }
}
