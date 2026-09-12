package at.bernhardberger.tvhplayer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy

internal const val SIDEBAR_SCENE_DESTINATION = "sidebarSceneDestination"

/** One visual handoff for browse destinations; retain only the visited Channels/Guide pair. */
@Composable
internal fun rememberSidebarGuideSceneStrategy(
    drawerActive: Boolean,
    destination: AppNavKey?,
): SceneStrategy<AppNavKey> {
    val inGuidePair = destination == GuideKey || destination == ChannelsKey
    var guideShown by remember(drawerActive, inGuidePair) { mutableStateOf(false) }
    var channelsShown by remember(drawerActive, inGuidePair) { mutableStateOf(false) }
    SideEffect {
        if (drawerActive && destination == GuideKey) guideShown = true
        if (drawerActive && destination == ChannelsKey) channelsShown = true
    }
    val retainGuide = drawerActive && guideShown
    val retainChannels = drawerActive && channelsShown
    return remember(retainGuide, retainChannels) {
        SceneStrategy { entries ->
            val active = entries.last()
            val destinations = entries.filter { it.metadata[SIDEBAR_SCENE_DESTINATION] in browseDestinations }
            if (active in destinations) {
                SidebarGuideScene(
                    destinations, active, retainGuide, retainChannels,
                    entries.dropLast(1),
                )
            } else {
                null
            }
        }
    }
}

private data class SidebarGuideScene(
    val destinations: List<NavEntry<AppNavKey>>,
    val active: NavEntry<AppNavKey>,
    val retainGuide: Boolean,
    val retainChannels: Boolean,
    override val previousEntries: List<NavEntry<AppNavKey>>,
) : Scene<AppNavKey> {
    override val key: Any = ChannelsKey
    override val entries = destinations
    override val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            // Keep the animation slots, not all four screen trees, alive. Even a first
            // visit starts from alpha zero and gets the same handoff as a retained page.
            browseDestinations.forEach { destination ->
                val entry = destinations.lastOrNull { it.metadata[SIDEBAR_SCENE_DESTINATION] == destination }
                val retained = when (destination) {
                    AppDestination.GUIDE -> retainGuide
                    AppDestination.CHANNELS -> retainChannels
                    else -> false
                }
                SidebarVisitDestination(entry, active == entry, retained)
            }
        }
    }
}

private val browseDestinations = listOf(
    AppDestination.CHANNELS, AppDestination.GUIDE, AppDestination.RECORDINGS, AppDestination.SETTINGS,
)

@Composable
@OptIn(ExperimentalAnimationApi::class)
private fun SidebarVisitDestination(entry: NavEntry<AppNavKey>?, visible: Boolean, retained: Boolean) {
    val alpha = animateFloatAsState(
        if (visible) 1f else 0f,
        tween(APP_DESTINATION_CROSSFADE_DURATION_MILLIS, easing = LinearEasing),
        label = "sidebarDestinationAlpha",
    )
    // Release an inactive destination when the visit ends, after its normal exit fade.
    // A destination never visited in this sidebar session is never constructed here.
    if (entry != null && (visible || retained || alpha.value > 0f)) {
        Layout(
            content = {
                // Settings keeps its existing per-category NavEntry/saveable-state owner.
                // A category change fades within this root slot rather than replacing it.
                updateTransition(entry, label = "browseEntry").Crossfade(
                    contentKey = { it.contentKey },
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(APP_DESTINATION_CROSSFADE_DURATION_MILLIS, easing = LinearEasing),
                ) { destinationEntry ->
                    val current = destinationEntry.contentKey == entry.contentKey
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (current) Modifier else Modifier.clearAndSetSemantics { })
                            .focusProperties { onEnter = { if (!current) cancelFocusChange() } }
                            .focusGroup(),
                    ) { destinationEntry.Content() }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value }
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
                .focusProperties { onEnter = { if (!visible) cancelFocusChange() } }
                .focusGroup(),
        ) { measurables, constraints ->
            val child = if (visible || alpha.value > 0f) {
                measurables.firstOrNull()?.measure(constraints)
            } else null
            layout(constraints.maxWidth, constraints.maxHeight) { child?.placeRelative(0, 0) }
        }
    }
}
