package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemColors
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import androidx.lifecycle.compose.LifecycleResumeEffect
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.ui.common.rememberDetailsEpochSeconds
import at.bernhardberger.tvhplayer.ui.player.NowPlayingPlate
import at.bernhardberger.tvhplayer.ui.player.NowPlayingPlateWidth
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.BrowseShellBackAction
import at.bernhardberger.tvhplayer.core.browseShellBackAction
import at.bernhardberger.tvhplayer.models.RailItem
import at.bernhardberger.tvhplayer.profiling.profileLayout
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.ui.TvGhostFillAlpha
import kotlin.math.roundToInt

// Material for TV drawer padding: 12dp on both edges of the item column.
private val DrawerStartPadding = 12.dp
private val DrawerEndPadding = 12.dp
internal val ClosedDrawerWidth =
    DrawerStartPadding + NavigationDrawerItemDefaults.CollapsedDrawerItemWidth + DrawerEndPadding
internal val OpenDrawerWidth =
    DrawerStartPadding + NavigationDrawerItemDefaults.ExpandedDrawerItemWidth + DrawerEndPadding

/** Drawer top section: the kit's 56dp mark box above the first destination. */
private val BrandHeaderHeight = 56.dp
private val BrandSymbolSize = 32.dp
private val BrandWordmarkHeight = 18.dp

// tv-material's drawer item is a ListItem with 16dp content padding whose leading
// slot is a 32dp minimum box holding the 24dp icon. Collapsed (56dp) only 24dp
// remain for that slot, so the icon sits at 16..40; as the item widens past 64dp
// the slot regains its 32dp and the library nudges the icon to 20..44. The kit
// keeps the icon at x16 in both states, so the icon is pinned there and the mark
// shares that fixed axis: nothing in the rail moves horizontally when it expands.
private val ItemContentPadding = 16.dp
private val ExpandedItemLabelStart = 56.dp
private val ItemIconCenter = ItemContentPadding + NavigationDrawerItemDefaults.IconSize / 2

/**
 * The library owns the collapsed-rail dim. `NavigationDrawerScope.hasFocus` is
 * `drawerState.currentValue == DrawerValue.Open`, and a closed drawer renders
 * unselected items with `inactiveContentColor`; the selected destination keeps
 * its full colour so the rail still reports where you are.
 *
 * That dim cannot reach an icon-only rail as shipped. `ListItem` publishes its
 * leading slot as `LocalContentColor.current.copy(alpha = 0.8f)`, and `copy`
 * replaces the alpha rather than scaling it, so the library's own
 * `onSurface.copy(alpha = 0.4f)` arrives at the icon as `onSurface` at 0.8 --
 * pixel-identical to the active state. Compositing the library's inactive colour
 * against the surface it is drawn on carries the same 0.4 ratio in a form the
 * fixed slot alpha preserves. Every other colour, including the untouched
 * selected pair, stays with the library.
 */
@Composable
private fun drawerItemColors(): NavigationDrawerItemColors {
    val defaults = NavigationDrawerItemDefaults.colors()
    val surface = MaterialTheme.colorScheme.surface
    return NavigationDrawerItemDefaults.colors(
        inactiveContentColor = defaults.inactiveContentColor.compositeOver(surface),
        disabledInactiveContentColor =
            defaults.disabledInactiveContentColor.compositeOver(surface),
    )
}

/** The library's own unselected-to-selected ratio, for the mark that is not an item. */
@Composable
private fun inactiveContentAlpha(): Float {
    val colors = NavigationDrawerItemDefaults.colors()
    return colors.inactiveContentColor.alpha / colors.contentColor.alpha
}

/**
 * Keeps the icon on the kit's fixed axis while the item width animates.
 *
 * tv-material centres its 24dp leading box in a slot that grows from 24dp to 32dp as
 * the item widens, which would carry the icon 4dp right. The icon reads that
 * displacement where the library decides it — from its own parent's placement, in the
 * same layout pass — and cancels it. Nothing is measured into state and read back a
 * frame later, so no frame can show the library's position without this correction.
 */
@Composable
private fun PinnedRailIcon(icon: @Composable () -> Unit) {
    Layout(content = icon) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints)
        layout(placeable.width, placeable.height) {
            val slotOffset = coordinates?.parentLayoutCoordinates?.positionInParent()?.x ?: 0f
            // Physical cancellation of the slot's placement; placeRelative mirrors
            // that same offset in RTL so the leading axis stays pinned.
            placeable.placeRelative(-slotOffset.roundToInt(), 0)
        }
    }
}

/** Current measure's visible extent from the browse content's logical leading edge. */
internal val LocalBrowseVisibleWidthPx = compositionLocalOf<Int?> { null }

/**
 * [LocalBrowseVisibleWidthPx] rounded up to [BrowseCullStep]: never narrower than what is visible,
 * and changing only a few times while the drawer moves, so content culled by it does not
 * recompose on every frame of the motion.
 */
internal val LocalBrowseCullWidthPx = compositionLocalOf<Int?> { null }
private val BrowseCullStep = 64.dp

/** Read the widget's current focus ownership at deferred focus-request time. */
internal val LocalBrowseDrawerState = staticCompositionLocalOf<DrawerState?> { null }
internal val LocalBrowseNavigationFocus = staticCompositionLocalOf<FocusRequester?> { null }
private val LocalBrowseFullWidth = staticCompositionLocalOf<Dp?> { null }

/** Only the overlay expands over the closed rail; its parent keeps the browse constraints. */
@Composable
internal fun Modifier.browseDetailsViewport(): Modifier {
    val fullWidth = LocalBrowseFullWidth.current ?: return this
    return layout { measurable, constraints ->
        val width = fullWidth.roundToPx()
        val page = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, page.height) {
            page.placeRelative(constraints.maxWidth - width, 0)
        }
    }
}

/**
 * How far a row reaches past the content's start: under the drawer at its widest, so passed cards
 * run on to the screen edge however far the drawer has moved the content. See [browseUnderlap].
 */
@Composable
internal fun browseDrawerUnderlap(): Dp = if (LocalBrowseFullWidth.current != null) OpenDrawerWidth else 0.dp

/** Widens the content by [underlap] toward the leading edge, beyond its parent's start. */
internal fun Modifier.browseUnderlap(underlap: Dp): Modifier = layout { measurable, constraints ->
    val extra = underlap.roundToPx()
    val page = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, page.height) { page.placeRelative(-extra, 0) }
}

/** A screen backdrop covers the whole screen and stays put while the opening drawer moves the browse content. */
@Composable
internal fun Modifier.browseScreenBackdrop(): Modifier {
    val fullWidth = LocalBrowseFullWidth.current ?: return this
    val layering = LocalBrowseContentLayering.current ?: return browseDetailsViewport()
    return then(BrowseScreenBackdropElement(layering, fullWidth, LocalBrowseVisibleWidthPx.current))
}

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
    connectionDegraded: Boolean = false,
    onBackHandlerChanged: ((() -> Unit) -> Unit) = {},
    /** What plays behind browse; the controls below it act on that target. */
    nowPlaying: RailNowPlaying? = null,
    programmeAt: (ChannelId, Long) -> EpgEvent? = { _, _ -> null },
    onOpenNowPlaying: () -> Unit = {},
    livePauseAvailable: Boolean = false,
    nowPlayingPaused: Boolean = false,
    onToggleNowPlayingPause: () -> Unit = {},
    onStopNowPlaying: () -> Unit = {},
    animateNowPlaying: Boolean? = null,
    nowSecProvider: () -> Long = { System.currentTimeMillis() / 1000L },
    content: @Composable (PaddingValues, drawerActive: Boolean) -> Unit,
) {
    var systemAnimationsEnabled by remember { mutableStateOf(android.animation.ValueAnimator.areAnimatorsEnabled()) }
    LifecycleResumeEffect(Unit) {
        systemAnimationsEnabled = android.animation.ValueAnimator.areAnimatorsEnabled()
        onPauseOrDispose { }
    }
    val channelsLabel = stringResource(R.string.nav_channels)
    val epgLabel = stringResource(R.string.nav_epg)
    val recordingsLabel = stringResource(R.string.nav_recordings)
    val settingsLabel = stringResource(R.string.nav_settings)
    val settingsDescription = if (connectionDegraded) stringResource(R.string.connection_problem_label) else settingsLabel
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
    val layering = remember { BrowseContentLayering() }
    val detailsOpen = layering.details.isNotEmpty()
    val modalOpen = layering.modalScrims.isNotEmpty()
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
    val fullScreenFocus = remember { FocusRequester() }
    val pauseAvailable = nowPlaying?.target is AppPlaybackTarget.Recording || livePauseAvailable
    var focusedControl by remember { mutableStateOf<String?>(null) }
    // Read while composing the slot's removal, before it detaches and drops its focus.
    val nowPlayingFocusLost = remember(nowPlaying == null, pauseAvailable) {
        Snapshot.withoutReadObservation {
            focusedControl != null && (nowPlaying == null ||
                !pauseAvailable && focusedControl == "pause")
        }
    }
    LaunchedEffect(nowPlayingFocusLost) {
        if (nowPlayingFocusLost) {
            focusedControl = null
            val fallback = AppDestination.CHANNELS.takeIf(itemFocus::containsKey) ?: items.firstOrNull()?.route
            if (fallback != null) {
                requestRoute(fallback)
                itemFocus[fallback]?.requestFocus()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val browseWidth = (maxWidth - ClosedDrawerWidth).coerceAtLeast(0.dp)
        val fullWidth = maxWidth
        // What the drawer sheet leaves the browse content, as the content is measured each frame.
        val visibleWidthPx = remember { mutableIntStateOf(Int.MAX_VALUE) }
        // Clip at the screen edge, not at the moving content's leading edge.
        NavigationDrawer(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
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
                val drawerColors = drawerItemColors()
                Column(
                    modifier = Modifier
                        .profileLayout("sidebar")
                        .fillMaxHeight()
                        .graphicsLayer {
                            alpha = 1f - layering.detailsAlpha()
                            compositingStrategy = if (modalOpen) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                else androidx.compose.ui.graphics.CompositingStrategy.Auto
                        }
                        .drawWithContent {
                            drawContent()
                            // NavigationDrawer paints the rail above content. Dim only its ink;
                            // the full-screen modal already covers the backdrop beneath it.
                            if (modalOpen) drawRect(androidx.compose.ui.graphics.Color.Black,
                                alpha = layering.modalScrimAlpha(), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                        }
                        .then(if (detailsOpen || modalOpen) Modifier.clearAndSetSemantics { } else Modifier)
                        .testTag("global-drawer-surface")
                        // The surface reaches the edge; content retains the same safe inset.
                        // Kit drawer: 12dp on every edge; header and footer are 56dp
                        // sections and the destination block is centred between them.
                        .padding(DrawerStartPadding)
                        // Entry is owned by the destination effect above, not a remembered control.
                        .focusGroup()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (nowPlaying == null) {
                        DrawerBrandHeader(drawerValue)
                    } else {
                        DrawerNowPlaying(
                            nowPlaying = nowPlaying,
                            expanded = drawerValue == DrawerValue.Open,
                            controlsEnabled = !detailsOpen && !modalOpen,
                            pauseAvailable = pauseAvailable,
                            paused = nowPlayingPaused,
                            fullScreenFocus = fullScreenFocus,
                            channelsFocus = itemFocus[AppDestination.CHANNELS] ?: activeItemFocus,
                            programmeAt = programmeAt,
                            nowSecProvider = nowSecProvider,
                            onOpen = onOpenNowPlaying,
                            onTogglePause = onToggleNowPlayingPause,
                            onStop = onStopNowPlaying,
                            onControlFocused = { control, focused ->
                                if (focused) focusedControl = control
                                else if (focusedControl == control) focusedControl = null
                            },
                            animated = animateNowPlaying ?: systemAnimationsEnabled,
                            fullWidth = fullWidth,
                            visibleWidthPx = visibleWidthPx,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    mainItems.forEach { item ->
                        key(item.route) {
                            NavigationDrawerItem(
                                selected = selectedRoute == item.route,
                                onClick = { requestRoute(item.route) },
                                leadingContent = { PinnedRailIcon(item.icon) },
                                colors = drawerColors,
                                modifier = Modifier
                                    .withinDrawerSheet(fullWidth, visibleWidthPx)
                                    .focusProperties {
                                        canFocus = !detailsOpen && !modalOpen
                                        if (item.route == AppDestination.CHANNELS && nowPlaying != null && drawerValue == DrawerValue.Open) {
                                            up = fullScreenFocus
                                        }
                                    }
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
                            val showConnectionBadge = item.route == AppDestination.SETTINGS && connectionDegraded
                            var itemFocused by remember { mutableStateOf(false) }
                            NavigationDrawerItem(
                                selected = selectedRoute == item.route,
                                onClick = { requestRoute(item.route) },
                                leadingContent = {
                                    PinnedRailIcon {
                                        Box {
                                            item.icon()
                                            if (showConnectionBadge && drawerValue == DrawerValue.Closed) {
                                                Box(
                                                    Modifier
                                                        .align(Alignment.TopEnd)
                                                        .offset(x = 2.dp, y = (-2).dp)
                                                        .size(8.dp)
                                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                                        .testTag("connection-problem-badge")
                                                        .clearAndSetSemantics {},
                                                )
                                            }
                                        }
                                    }
                                },
                                trailingContent = if (showConnectionBadge) {
                                    {
                                        // The pink error pill vanishes on the light focused item; use the deep container there.
                                        NavigationDrawerItemDefaults.TrailingBadge(
                                            text = stringResource(R.string.connection_offline_badge),
                                            containerColor = if (itemFocused) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                            contentColor = if (itemFocused) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onError
                                            },
                                        )
                                    }
                                } else null,
                                colors = drawerColors,
                                modifier = Modifier
                                    .withinDrawerSheet(fullWidth, visibleWidthPx)
                                    .focusProperties { canFocus = !detailsOpen && !modalOpen }
                                    .focusRequester(itemFocus.getValue(item.route))
                                    .semantics {
                                        contentDescription = if (item.route == AppDestination.SETTINGS) settingsDescription else item.label
                                    }
                                    .testTag(item.route.testTag)
                                    .onFocusChanged { focusState ->
                                        itemFocused = focusState.isFocused
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
                BrowseViewport(width = browseWidth, layering = layering, visibleWidthPx = visibleWidthPx) {
                    CompositionLocalProvider(
                        LocalBrowseDrawerState provides drawerState,
                        LocalBrowseNavigationFocus provides activeItemFocus.takeUnless { detailsOpen || modalOpen },
                        LocalBrowseFullWidth provides maxWidth,
                    ) {
                        content(
                            TvScreenPadding,
                            !detailsOpen && drawerState.currentValue == DrawerValue.Open,
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
 * mark reveals with the sheet instead of widening it ahead of the items. The mark
 * itself never moves; while the drawer is inactive it is dimmed like the items.
 * While something plays behind browse, [DrawerNowPlaying] replaces it with the header and controls.
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
    val inactiveAlpha = inactiveContentAlpha()
    Box(
        modifier = Modifier
            .width(headerWidth)
            .height(BrandHeaderHeight)
            .clipToBounds()
            .graphicsLayer { alpha = if (expanded) 1f else inactiveAlpha }
            .testTag("global-drawer-brand"),
    ) {
        Image(
            painter = painterResource(R.drawable.startup_brand_symbol),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = ItemIconCenter - BrandSymbolSize / 2)
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

private val NowPlayingPlateGap = 12.dp

/** The labels start [NowPlayingPlateGap] after the plate, which reaches past the destinations' icons. */
private val NowPlayingTextStart = ItemIconCenter + NowPlayingPlateWidth / 2 + NowPlayingPlateGap

/**
 * The top section while something plays behind browse: the playing channel's plate on the mark's
 * axis, and with the drawer open what plays and where the viewer is. Only the controls take focus.
 * Their height stays reserved when closed, so the destinations do not move.
 *
 * Positions are read where they are drawn: the strip and the behind-live line follow the viewer each
 * second, while the item itself recomposes only when the programme or the state changes.
 */
@Composable
private fun NavigationDrawerScope.DrawerNowPlaying(
    nowPlaying: RailNowPlaying,
    expanded: Boolean,
    controlsEnabled: Boolean,
    pauseAvailable: Boolean,
    paused: Boolean,
    fullScreenFocus: FocusRequester,
    channelsFocus: FocusRequester?,
    programmeAt: (ChannelId, Long) -> EpgEvent?,
    nowSecProvider: () -> Long,
    onOpen: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onControlFocused: (String, Boolean) -> Unit,
    animated: Boolean,
    fullWidth: Dp,
    visibleWidthPx: IntState,
) = HoldWatching(nowPlaying.reconnecting) {
    // A reconnect keeps the strip and title where the viewer was until playback resumes or ends.
    val reconnecting = nowPlaying.reconnecting
    // A held mark belongs to what played when it was taken; another target's pause or tune is not this one's.
    val watchingSlot = LocalWatchingTarget.current == nowPlaying.target
    val indicator = if (watchingSlot) LocalWatchingIndicator.current else ChannelPlaybackIndicator.NONE
    val liveChannel = (nowPlaying.target as? AppPlaybackTarget.Live)?.channelId
    val watchingLive = liveChannel != null && indicator.isPlayingItem
    val livePosition = LocalLivePosition.current
    // liveViewerPosition's contract, held as state so its per-second samples stay out of this scope.
    val viewer = remember(livePosition, liveChannel, watchingLive) {
        derivedStateOf { livePosition?.value?.takeIf { watchingLive && it.channelId == liveChannel } }
    }
    // Without a position sample the viewer is at the clock, so a reconnect stops the clock too.
    val nowSec = heldWhile(reconnecting, rememberDetailsEpochSeconds(nowSecProvider))
    val currentProgrammeAt by rememberUpdatedState(programmeAt)
    val lookedUp by remember(viewer, liveChannel, nowSec) {
        derivedStateOf { liveChannel?.let { currentProgrammeAt(it, viewer.value?.epochSec ?: nowSec) } }
    }
    val programme = heldWhile(reconnecting, lookedUp)
    val behindLive by remember(viewer) { derivedStateOf { viewer.value?.behindLive == true } }
    val status = railPlaybackStatus(nowPlaying, indicator, behindLive)
    val channel = channelTitleText(nowPlaying.channelNumber, nowPlaying.channelName)
    val title = nowPlaying.recordingTitle ?: programme?.title
    // A recording whose channel the server no longer names has only its title to show.
    val lineChannel = channel.takeIf { title != null && it.isNotBlank() }
    val statusLabel = railStatusLabel(status, behindLiveMinutes = null, LocalResources.current)
    val description = if (title != null && lineChannel != null) {
        stringResource(R.string.rail_now_playing_description, title, lineChannel, statusLabel)
    } else {
        stringResource(R.string.rail_now_playing_description_channel, title ?: channel, statusLabel)
    }
    val headerHeight = if (LocalDensity.current.fontScale >= 1.3f) 64.dp else BrandHeaderHeight
    Column(Modifier.testTag("global-drawer-now-playing")) {
        Box(Modifier.height(headerHeight).width(if (expanded) NavigationDrawerItemDefaults.ExpandedDrawerItemWidth
            else NavigationDrawerItemDefaults.CollapsedDrawerItemWidth)
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
                testTag = "global-drawer-now-playing-item"
            }) {
            NowPlayingPlate(
                picon = nowPlaying.picon,
                channelNumber = nowPlaying.channelNumber?.toString(),
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset(x = ItemIconCenter - NowPlayingPlateWidth / 2)
                    .testTag("global-drawer-now-playing-plate"),
                contentAlpha = if (status == RailPlaybackStatus.TUNING) inactiveContentAlpha() else 1f,
            ) {
                NowPlayingRailStrip(nowPlaying, programme, status, nowSec)
            }
            // The kit's enter slides in from the left; clipped, the labels emerge from the plate's edge.
            androidx.compose.animation.AnimatedVisibility(expanded,
                enter = NavigationDrawerItemDefaults.ContentAnimationEnter,
                exit = NavigationDrawerItemDefaults.ContentAnimationExit,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = NowPlayingTextStart).clipToBounds()) {
                Column(Modifier.testTag("global-drawer-now-playing-text")) {
                    Text(title ?: channel, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    NowPlayingStatusLine(status, viewer, nowSecProvider, lineChannel, animated)
                }
            }
        }
        Box(Modifier.height(36.dp)) {
            if (expanded) DrawerNowPlayingControls(
                paused = paused,
                pauseAvailable = pauseAvailable,
                enabled = controlsEnabled,
                fullScreenFocus = fullScreenFocus,
                channelsFocus = channelsFocus,
                onOpen = onOpen,
                onTogglePause = onTogglePause,
                onStop = onStop,
                onControlFocused = onControlFocused,
                fullWidth = fullWidth,
                visibleWidthPx = visibleWidthPx,
            )
        }
    }
}

/**
 * The kit has no grouped icon row: the shared pill is passive; each button keeps kit focus. The pill
 * spans a destination's extent and grows with the sheet as their pills do; the buttons share it evenly.
 */
@Composable
private fun DrawerNowPlayingControls(
    paused: Boolean,
    pauseAvailable: Boolean,
    enabled: Boolean,
    fullScreenFocus: FocusRequester,
    channelsFocus: FocusRequester?,
    onOpen: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onControlFocused: (String, Boolean) -> Unit,
    fullWidth: Dp,
    visibleWidthPx: IntState,
) {
    val pauseFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val controls = listOf("fullscreen", "pause", "stop").filter { it != "pause" || pauseAvailable }
    val requesters = listOfNotNull(fullScreenFocus, pauseFocus.takeIf { pauseAvailable }, stopFocus)
    Row(Modifier.padding(top = 4.dp)
        .withinDrawerSheet(fullWidth, visibleWidthPx)
        .width(NavigationDrawerItemDefaults.ExpandedDrawerItemWidth)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TvGhostFillAlpha), CircleShape)
        .padding(vertical = 2.dp).testTag("global-drawer-now-playing-controls"),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        controls.forEachIndexed { index, control ->
            key(control) {
                val label = when (control) {
                    "fullscreen" -> stringResource(R.string.player_full_screen)
                    "pause" -> stringResource(if (paused) R.string.play else R.string.pause)
                    else -> stringResource(R.string.stop)
                }
                val icon = when (control) {
                    "fullscreen" -> R.drawable.ic_fullscreen
                    "pause" -> if (paused) R.drawable.ic_play_arrow else R.drawable.ic_pause
                    else -> R.drawable.ic_stop
                }
                IconButton(
                    onClick = when (control) { "fullscreen" -> onOpen; "pause" -> onTogglePause; else -> onStop },
                    modifier = Modifier.size(IconButtonDefaults.SmallButtonSize)
                        .focusRequester(requesters[index])
                        .focusProperties {
                            canFocus = enabled
                            up = FocusRequester.Cancel
                            down = channelsFocus ?: FocusRequester.Default
                            left = requesters.getOrNull(index - 1) ?: FocusRequester.Cancel
                            right = requesters.getOrNull(index + 1) ?: FocusRequester.Default
                        }
                        .onFocusChanged { onControlFocused(control, it.isFocused) }
                        .testTag("global-drawer-now-playing-$control")
                        .semantics { contentDescription = label },
                    colors = IconButtonDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                ) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(IconButtonDefaults.SmallIconSize))
                }
            }
        }
    }
}

/** The plate's strip at the viewer's position; a tune has no position yet. */
@Composable
private fun BoxScope.NowPlayingRailStrip(
    nowPlaying: RailNowPlaying,
    programme: EpgEvent?,
    status: RailPlaybackStatus,
    nowSec: Long,
) {
    if (status == RailPlaybackStatus.TUNING) return
    val progress = when (val target = nowPlaying.target) {
        is AppPlaybackTarget.Recording -> recordingViewerPosition(target.recordingId)?.fraction
        is AppPlaybackTarget.Live -> programmeProgress(programme, nowSec)
    } ?: return
    ProgressStrip(
        progress = progress,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        height = 2.dp,
        color = progressColor(watching = true),
    )
}

/** Line two: the playback mark, then the state it leaves unsaid and the channel when the title is a programme or recording. */
@Composable
private fun NowPlayingStatusLine(
    status: RailPlaybackStatus,
    viewer: State<LiveViewerPosition?>,
    nowSecProvider: () -> Long,
    channel: String?,
    animated: Boolean,
) {
    val minutes = if (status == RailPlaybackStatus.BEHIND_LIVE) {
        railBehindLiveMinutes(viewer.value?.epochSec, nowSecProvider())
    } else null
    val mark = when (status) {
        RailPlaybackStatus.PAUSED -> ChannelPlaybackIndicator.PAUSED
        RailPlaybackStatus.TUNING, RailPlaybackStatus.RECONNECTING -> ChannelPlaybackIndicator.TUNING
        else -> ChannelPlaybackIndicator.PLAYING
    }
    Row(Modifier.testTag("global-drawer-now-playing-line"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            ChannelPlaybackMarker(mark, size = 16.dp, announceState = false, animated = animated,
                modifier = Modifier.testTag("global-drawer-now-playing-glyph-${mark.name.lowercase()}"))
        }
        Text(
            railStatusLine(status, minutes, channel, LocalResources.current),
            Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** [value], or while [held] the value it had when the hold began. */
@Composable
private fun <T> heldWhile(held: Boolean, value: T): T {
    var last by remember { mutableStateOf(value) }
    SideEffect { if (!held) last = value }
    return if (held) last else value
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

/**
 * tv-material's drawer sheet clips to its `animateContentSize` spring, which is slower than the
 * width spring of its items. Opening, an item outgrows the sheet and its pill ends flat at the
 * clip; closing, it shrinks inside it. Ending the item round, inset from the sheet's edge like the
 * open drawer's items, makes the pill grow with the drawer. Content within the sheet is unchanged.
 */
private fun Modifier.withinDrawerSheet(fullWidth: Dp, visibleWidthPx: IntState): Modifier = drawWithContent {
    val sheet = fullWidth.toPx() - visibleWidthPx.intValue
    val visible = sheet - (DrawerStartPadding + DrawerEndPadding).toPx()
    if (visible >= size.width) {
        drawContent()
        return@drawWithContent
    }
    val start = if (layoutDirection == LayoutDirection.Rtl) size.width - visible else 0f
    val radius = CornerRadius(size.height / 2)
    clipPath(Path().apply { addRoundRect(RoundRect(start, 0f, start + visible, size.height, radius)) }) {
        this@drawWithContent.drawContent()
    }
}

@Composable
private fun BrowseViewport(
    width: Dp,
    layering: BrowseContentLayering,
    visibleWidthPx: MutableIntState,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(-1f)
            .browseShellLayer(layering, width + ClosedDrawerWidth),
    ) { constraints ->
        // Read only while the drawer items draw, after this frame's layout.
        visibleWidthPx.intValue = constraints.maxWidth
        val fixedWidth = width.roundToPx()
        val cullStep = BrowseCullStep.roundToPx()
        val cullWidth = (constraints.maxWidth + cullStep - 1) / cullStep * cullStep
        val placeable = subcompose(Unit) {
            CompositionLocalProvider(
                LocalBrowseVisibleWidthPx provides constraints.maxWidth,
                LocalBrowseCullWidthPx provides cullWidth,
                LocalBrowseContentLayering provides layering,
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
