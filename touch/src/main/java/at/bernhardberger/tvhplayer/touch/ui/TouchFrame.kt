@file:Suppress("MatchingDeclarationName") // Destination identifies this frame's slots.

package at.bernhardberger.tvhplayer.touch.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import at.bernhardberger.tvhplayer.touch.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

internal enum class Destination(
    @param:StringRes val label: Int,
    @param:DrawableRes val outline: Int,
    @param:DrawableRes val filled: Int,
) {
    Channels(R.string.nav_channels, R.drawable.play_tv_outline, R.drawable.play_tv_filled),
    Guide(R.string.nav_epg, R.drawable.calendar_month_outline, R.drawable.calendar_month_filled),
    Recordings(R.string.nav_recordings, R.drawable.video_library_outline, R.drawable.video_library_filled),
}

private data class FrameBars(val top: Boolean = true, val navigation: Boolean = true)

private val FrameBarsSaver = listSaver<FrameBars, Boolean>(
    save = { listOf(it.top, it.navigation) },
    restore = { FrameBars(top = it[0], navigation = it[1]) },
)

// The rendered destination owns its page chrome; the shell owns the navigation and notice host.
private val LocalReportFrameBars = staticCompositionLocalOf<((FrameBars) -> Unit)?> { null }

/** Keep a composed entry's own chrome, but let only the top entry change the destination shell. */
internal val ReportFrameBarsWhen: @Composable (isTop: Boolean, content: @Composable () -> Unit) -> Unit =
    { isTop, content ->
        val report = LocalReportFrameBars.current
        CompositionLocalProvider(
            LocalReportFrameBars provides if (isTop || report == null) report else { _ -> },
            content = content,
        )
    }

/** Only the top page and its selected destination may handle system Back. */
internal val LocalDestinationBackEnabled = compositionLocalOf { true }

/** The shown page may collect taps on its already selected destination; events are not replayed. */
internal val LocalDestinationReselections = staticCompositionLocalOf<Flow<Destination>> { emptyFlow() }

/** The outer horizontal margin for browsing pages. */
internal val CompactBrowseMargin = 16.dp
internal val WideBrowseMargin = 24.dp

internal val LocalBrowseMargin = staticCompositionLocalOf { WideBrowseMargin }

private val WindowSizeClass.hasNavigationRail: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ||
        (isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
            !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND))

@Composable
internal fun OnDestinationReselected(destination: Destination, action: suspend () -> Unit) {
    val taps = LocalDestinationReselections.current
    val latestAction by rememberUpdatedState(action)
    LaunchedEffect(taps, destination) {
        taps.filter { it == destination }.collectLatest { latestAction() }
    }
}

@Composable
internal fun LazyListState.ScrollToStartOnReselect(destination: Destination) {
    OnDestinationReselected(destination) {
        if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) {
            if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) scrollToItem(0)
            else animateScrollToItem(0)
        }
    }
}

// AnimatedContent's default fade timings; the theme's spring effects have no fixed delay.
private const val DESTINATION_EXIT_MILLIS = 90
private const val DESTINATION_ENTER_MILLIS = 220

@Composable
internal fun DestinationFrame(
    initialDestination: Destination = Destination.Channels,
    content: @Composable (Destination) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initialDestination) }
    var bars by rememberSaveable(stateSaver = FrameBarsSaver) { mutableStateOf(FrameBars()) }
    val saved = rememberSaveableStateHolder()
    val taps = remember { MutableSharedFlow<Destination>() }
    val reselections = remember(taps) { taps.asSharedFlow() }
    val scope = rememberCoroutineScope()
    val select: (Destination) -> Unit = {
        if (it == selected) scope.launch { taps.emit(it) } else selected = it
    }
    val canHandleBack = LocalDestinationBackEnabled.current
    BackHandler(enabled = canHandleBack && selected != Destination.Channels) { selected = Destination.Channels }
    FrameShell(Modifier, Destination.entries, selected, select, bars) { padding ->
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            transitionSpec = {
                val enter = fadeIn(tween(DESTINATION_ENTER_MILLIS, delayMillis = DESTINATION_EXIT_MILLIS))
                (enter togetherWith fadeOut(tween(DESTINATION_EXIT_MILLIS))) using null
            },
            label = "Browse destination",
        ) { destination ->
            val report = remember(destination) {
                { next: FrameBars -> if (destination == selected) bars = next }
            }
            CompositionLocalProvider(
                LocalReportFrameBars provides report,
                LocalDestinationBackEnabled provides (canHandleBack && destination == selected),
                LocalDestinationReselections provides reselections,
            ) {
                saved.SaveableStateProvider(destination) { content(destination) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TouchFrame(
    modifier: Modifier = Modifier,
    initialDestination: Destination = Destination.Channels,
    /**
     * The destinations offered. With only one the phone's navigation bar is left out, as Material
     * asks for three to five; the tablet's rail keeps its single item beside the content.
     */
    destinations: List<Destination> = Destination.entries,
    // A destination's page can draw its own top bar, or take the whole window (then it handles the insets).
    showTopBar: (Destination) -> Boolean = { true },
    showNavigation: (Destination) -> Boolean = { true },
    lastActionIsIconOnly: Boolean = false,
    actions: @Composable RowScope.(Destination) -> Unit = {},
    belowTopBar: @Composable (Destination) -> Unit = {},
    content: @Composable (Destination) -> Unit = {},
) {
    val report = LocalReportFrameBars.current
    if (report != null) {
        val bars = FrameBars(showTopBar(initialDestination), showNavigation(initialDestination))
        SideEffect { report(bars) }
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = { FrameTop(bars.top, initialDestination, lastActionIsIconOnly, actions, belowTopBar) },
            contentWindowInsets = frameInsets(bars.top),
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) { content(initialDestination) }
        }
        return
    }
    var selected by rememberSaveable { mutableStateOf(initialDestination) }
    BackHandler(enabled = selected != Destination.Channels) {
        selected = Destination.Channels
    }
    val bars = FrameBars(showTopBar(selected), showNavigation(selected))
    FrameShell(
        modifier, destinations, selected, { selected = it }, bars,
        top = { FrameTop(bars.top, selected, lastActionIsIconOnly, actions, belowTopBar) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) { content(selected) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FrameShell(
    modifier: Modifier,
    destinations: List<Destination>,
    selected: Destination,
    select: (Destination) -> Unit,
    bars: FrameBars,
    top: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val window = currentWindowAdaptiveInfoV2().windowSizeClass
    val mediumOrWider = window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val hasRail = window.hasNavigationRail

    val hasBottomBar = !hasRail && destinations.size > 1
    val bottomBar = hasBottomBar && bars.navigation
    val navigation = if (hasBottomBar) updateTransition(bottomBar, label = "Bottom navigation") else null
    val barOccupiesSlot = bottomBar || navigation?.currentState == true || navigation?.isRunning == true
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val notices = LocalFrameNotices.current
    val browseMargin = if (mediumOrWider) WideBrowseMargin else CompactBrowseMargin
    Row(modifier.fillMaxSize()) {
        if (hasRail && bars.navigation) DestinationRail(destinations, selected, select)
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                CompositionLocalProvider(LocalBrowseMargin provides browseMargin) { top?.invoke() }
            },
            bottomBar = {
                // Reserve the full bar on entry so a returning list doesn't lose its scroll position.
                navigation?.AnimatedVisibility(
                    visible = { it },
                    enter = fadeIn(effects),
                    exit = fadeOut(effects),
                ) {
                    DestinationBar(destinations, selected, mediumOrWider, bottomBar, select)
                }
            },
            snackbarHost = {
                // Above the bar, or above the system bar when a page takes the whole window; at the
                // start beside the rail on wider windows, across on the phone.
                val insets = if (!bars.top && !barOccupiesSlot) {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                } else {
                    Modifier
                }
                Box(
                    Modifier.fillMaxWidth().then(insets),
                    contentAlignment = if (mediumOrWider) Alignment.BottomStart else Alignment.BottomCenter,
                ) {
                    notices(if (mediumOrWider) Modifier else Modifier.fillMaxWidth())
                }
            },
            contentWindowInsets = frameInsets(top != null && bars.top),
            content = { padding ->
                CompositionLocalProvider(LocalBrowseMargin provides browseMargin) { content(padding) }
            },
        )
    }
}

@Composable
private fun FrameTop(
    visible: Boolean,
    destination: Destination,
    lastActionIsIconOnly: Boolean,
    actions: @Composable RowScope.(Destination) -> Unit,
    belowTopBar: @Composable (Destination) -> Unit,
) {
    if (visible) {
        val window = currentWindowAdaptiveInfoV2().windowSizeClass
        val expanded = window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
        val medium = window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
        Column {
            DestinationTopBar(destination, medium, expanded, lastActionIsIconOnly, actions)
            belowTopBar(destination)
        }
    }
}

@Composable
private fun frameInsets(top: Boolean) =
    if (top) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DestinationTopBar(
    selected: Destination,
    mediumOrWider: Boolean,
    expanded: Boolean,
    lastActionIsIconOnly: Boolean,
    actions: @Composable RowScope.(Destination) -> Unit,
) {
    val keyline = LocalBrowseMargin.current
    val titleInset = if (mediumOrWider && (!expanded || selected == Destination.Channels)) keyline - 16.dp else 0.dp
    // The stock bar leaves 4 dp at the end; a 24 dp symbol in its 48 dp target adds another 12 dp.
    val stockInset = if (lastActionIsIconOnly) 16.dp else 4.dp
    TopAppBar(
        title = {
            Text(
                text = stringResource(selected.label),
                modifier = Modifier
                    .padding(start = titleInset)
                    .semantics { heading() },
                style = if (mediumOrWider) {
                    MaterialTheme.typography.headlineMediumEmphasized
                } else {
                    MaterialTheme.typography.headlineSmallEmphasized
                },
            )
        },
        actions = {
            actions(selected)
            Spacer(Modifier.width(keyline - stockInset))
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DestinationRail(
    destinations: List<Destination>,
    selected: Destination,
    onSelect: (Destination) -> Unit,
) {
    WideNavigationRail(
        header = {
            Box(Modifier.size(width = 96.dp, height = 64.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.startup_brand_symbol),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).testTag("browse-app-mark"),
                )
            }
        },
        arrangement = Arrangement.Center,
        contentPadding = PaddingValues(0.dp),
    ) {
        destinations.forEach { destination ->
            WideNavigationRailItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { DestinationIcon(destination, selected) },
                label = { Text(stringResource(destination.label)) },
                railExpanded = false,
                iconPosition = NavigationItemIconPosition.Top,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DestinationBar(
    destinations: List<Destination>,
    selected: Destination,
    medium: Boolean,
    enabled: Boolean,
    onSelect: (Destination) -> Unit,
) {
    ShortNavigationBar(
        arrangement = if (medium) ShortNavigationBarArrangement.Centered else ShortNavigationBarArrangement.EqualWeight,
    ) {
        destinations.forEach { destination ->
            ShortNavigationBarItem(
                selected = destination == selected,
                enabled = enabled,
                onClick = { onSelect(destination) },
                icon = { DestinationIcon(destination, selected) },
                label = { Text(stringResource(destination.label)) },
                iconPosition = if (medium) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top,
            )
        }
    }
}

@Composable
private fun DestinationIcon(destination: Destination, selected: Destination) {
    Icon(
        painter = painterResource(if (destination == selected) destination.filled else destination.outline),
        contentDescription = null,
    )
}
