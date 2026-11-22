@file:Suppress("MatchingDeclarationName") // Destination identifies this frame's slots.

package at.bernhardberger.tvhplayer.touch.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import at.bernhardberger.tvhplayer.touch.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

// The rendered destination owns its page chrome; the shell owns the navigation and notice host.
private val LocalReportFrameBars = staticCompositionLocalOf<((FrameBars) -> Unit)?> { null }

/** The shown page may collect taps on its already selected destination; events are not replayed. */
internal val LocalDestinationReselections = staticCompositionLocalOf<Flow<Destination>> { emptyFlow() }

// AnimatedContent's default fade timings; the theme's spring effects have no fixed delay.
private const val DESTINATION_EXIT_MILLIS = 90
private const val DESTINATION_ENTER_MILLIS = 220

@Composable
internal fun DestinationFrame(
    initialDestination: Destination = Destination.Channels,
    content: @Composable (Destination) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initialDestination) }
    var bars by remember { mutableStateOf(FrameBars()) }
    val saved = rememberSaveableStateHolder()
    val taps = remember { MutableSharedFlow<Destination>() }
    val reselections = remember(taps) { taps.asSharedFlow() }
    val scope = rememberCoroutineScope()
    val select: (Destination) -> Unit = {
        if (it == selected) scope.launch { taps.emit(it) } else selected = it
    }
    BackHandler(enabled = selected != Destination.Channels) { selected = Destination.Channels }
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
    val expanded = window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val mediumOrWider = window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val bottomBar = !expanded && bars.navigation && destinations.size > 1
    val notices = LocalFrameNotices.current
    Row(modifier.fillMaxSize()) {
        if (expanded && bars.navigation) DestinationRail(destinations, selected, select)
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = { top?.invoke() },
            bottomBar = {
                if (bottomBar) {
                    DestinationBar(destinations, selected, mediumOrWider, select)
                }
            },
            snackbarHost = {
                // Above the bar, or above the system bar when a page takes the whole window; at the
                // start beside the rail on wider windows, across on the phone.
                val insets = if (!bars.top && !bottomBar) {
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
            content = content,
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
    val keyline = if (mediumOrWider) 24.dp else 16.dp
    // The stock bar leaves 4 dp at the end; a 24 dp symbol in its 48 dp target adds another 12 dp.
    val stockInset = if (lastActionIsIconOnly) 16.dp else 4.dp
    TopAppBar(
        title = {
            Text(
                text = stringResource(selected.label),
                modifier = Modifier
                    .padding(start = if (mediumOrWider && !expanded) 8.dp else 0.dp)
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
    onSelect: (Destination) -> Unit,
) {
    ShortNavigationBar(
        arrangement = if (medium) ShortNavigationBarArrangement.Centered else ShortNavigationBarArrangement.EqualWeight,
    ) {
        destinations.forEach { destination ->
            ShortNavigationBarItem(
                selected = destination == selected,
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
