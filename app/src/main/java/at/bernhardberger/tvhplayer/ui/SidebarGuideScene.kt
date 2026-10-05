package at.bernhardberger.tvhplayer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import at.bernhardberger.tvhplayer.ui.components.BrowseContentLayer

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
        val transition = updateTransition(
            browseDestinations.indexOf(active.metadata[SIDEBAR_SCENE_DESTINATION]),
            label = "browseDestination",
        )
        // The shell clips at the screen edge. Clipping this narrower scene would
        // cut departing content off at the drawer boundary before its backing.
        Box(Modifier.fillMaxSize()) {
            // The slots exist before a first visit, so incoming and outgoing pages
            // follow the drawer's vertical order without constructing hidden screens.
            browseDestinations.forEachIndexed { index, destination ->
                val entry = destinations.lastOrNull { it.metadata[SIDEBAR_SCENE_DESTINATION] == destination }
                val retained = when (destination) {
                    AppDestination.GUIDE -> retainGuide
                    AppDestination.CHANNELS -> retainChannels
                    else -> false
                }
                SidebarVisitDestination(entry, index, transition, retained)
            }
        }
    }
}

private val browseDestinations = listOf(
    AppDestination.CHANNELS, AppDestination.GUIDE, AppDestination.RECORDINGS, AppDestination.SETTINGS,
)

@Composable
private fun SidebarVisitDestination(
    entry: NavEntry<AppNavKey>?,
    index: Int,
    transition: Transition<Int>,
    retained: Boolean,
) {
    val visible = transition.targetState == index
    val alpha = transition.animateFloat(
        transitionSpec = { BrowseMotionPolicy.animation() },
        label = "sidebarDestinationAlpha",
    ) { target -> if (target == index) 1f else 0f }
    val position = transition.animateFloat(
        transitionSpec = { BrowseMotionPolicy.animation() },
        label = "sidebarDestinationPosition",
    ) { target -> (index - target).coerceIn(-1, 1).toFloat() }
    val showing = remember(alpha) { derivedStateOf { alpha.value > 0f } }
    // Release an inactive destination when its exit motion finishes.
    // Intermediate destinations can still be fading after a rapid retarget, so
    // transition.currentState alone is not sufficient to retain their content.
    // A destination never visited in this sidebar session is never constructed here.
    if (entry != null && (visible || retained || showing.value)) {
        BrowseContentLayer(departing = !visible) { layer ->
            Layout(
                content = {
                    // Settings keeps its existing per-category NavEntry/saveable-state owner.
                    SidebarEntryContent(entry)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (visible) 1f else 0f)
                    .graphicsLayer {
                        this.alpha = alpha.value
                        translationY = position.value * size.height * BrowseMotionPolicy.slideFraction
                    }
                    .then(layer)
                    .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
                    .focusProperties { onEnter = { if (!visible) cancelFocusChange() } }
                    .focusGroup(),
            ) { measurables, constraints ->
                // Measuring depends on visibility, not every alpha tick. Translation
                // and opacity updates stay in the graphics layer above.
                val child = if (visible || showing.value) {
                    measurables.firstOrNull()?.measure(constraints)
                } else null
                layout(constraints.maxWidth, constraints.maxHeight) { child?.placeRelative(0, 0) }
            }
        }
    }
}

/** A scene-local crossfade whose actual sibling wrappers can put a returning key on top. */
@Composable
private fun SidebarEntryContent(entry: NavEntry<AppNavKey>) {
    val transition = updateTransition(entry, label = "browseEntry")
    val entries = remember { mutableStateListOf(transition.currentState) }
    if (transition.currentState == transition.targetState && !transition.isRunning) {
        entries.removeAll { it != transition.targetState }
    }
    val replacement = entries.indexOfFirst { it.contentKey == entry.contentKey }
    if (replacement < 0) entries.add(entry) else if (entries[replacement] != entry) entries[replacement] = entry

    Box(Modifier.fillMaxSize()) {
        entries.forEach { destinationEntry ->
            key(destinationEntry.contentKey) {
                val current = destinationEntry.contentKey == entry.contentKey
                val alpha = transition.animateFloat(
                    transitionSpec = { tween(APP_DESTINATION_CROSSFADE_DURATION_MILLIS, easing = LinearEasing) },
                    label = "sidebarEntryAlpha",
                ) { if (it == destinationEntry) 1f else 0f }
                BrowseContentLayer(departing = !current) { layer ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (current) 1f else 0f)
                            .graphicsLayer { this.alpha = alpha.value }
                            .then(layer)
                            .then(if (current) Modifier else Modifier.clearAndSetSemantics { })
                            .focusProperties { onEnter = { if (!current) cancelFocusChange() } }
                            .focusGroup(),
                    ) { destinationEntry.Content() }
                }
            }
        }
    }
}
