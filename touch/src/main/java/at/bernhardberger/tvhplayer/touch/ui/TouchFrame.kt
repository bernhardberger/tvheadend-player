@file:Suppress("MatchingDeclarationName") // Destination identifies this frame's slots.

package at.bernhardberger.tvhplayer.touch.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import at.bernhardberger.tvhplayer.touch.R

internal enum class Destination(
    @param:StringRes val label: Int,
    @param:DrawableRes val outline: Int,
    @param:DrawableRes val filled: Int,
) {
    Channels(R.string.nav_channels, R.drawable.play_tv_outline, R.drawable.play_tv_filled),
    Guide(R.string.nav_epg, R.drawable.calendar_month_outline, R.drawable.calendar_month_filled),
    Recordings(R.string.nav_recordings, R.drawable.video_library_outline, R.drawable.video_library_filled),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TouchFrame(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.(Destination) -> Unit = {},
    belowTopBar: @Composable (Destination) -> Unit = {},
    content: @Composable (Destination) -> Unit = {},
) {
    var selected by rememberSaveable { mutableStateOf(Destination.Channels) }
    val select: (Destination) -> Unit = { selected = it }
    BackHandler(enabled = selected != Destination.Channels) {
        selected = Destination.Channels
    }

    val window = currentWindowAdaptiveInfoV2().windowSizeClass
    val expanded = window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val mediumOrWider = window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    Row(modifier.fillMaxSize()) {
        if (expanded) DestinationRail(selected, select)
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                Column {
                    DestinationTopBar(selected, mediumOrWider, expanded, actions)
                    belowTopBar(selected)
                }
            },
            bottomBar = {
                if (!expanded) DestinationBar(selected, mediumOrWider, select)
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) { content(selected) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DestinationTopBar(
    selected: Destination,
    mediumOrWider: Boolean,
    expanded: Boolean,
    actions: @Composable RowScope.(Destination) -> Unit,
) {
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
        actions = { actions(selected) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DestinationRail(selected: Destination, onSelect: (Destination) -> Unit) {
    WideNavigationRail(
        header = {
            Box(Modifier.size(width = 96.dp, height = 64.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.startup_brand_symbol),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
        },
        arrangement = Arrangement.Center,
        contentPadding = PaddingValues(0.dp),
    ) {
        Destination.entries.forEach { destination ->
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
private fun DestinationBar(selected: Destination, medium: Boolean, onSelect: (Destination) -> Unit) {
    ShortNavigationBar(
        arrangement = if (medium) ShortNavigationBarArrangement.Centered else ShortNavigationBarArrangement.EqualWeight,
    ) {
        Destination.entries.forEach { destination ->
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
