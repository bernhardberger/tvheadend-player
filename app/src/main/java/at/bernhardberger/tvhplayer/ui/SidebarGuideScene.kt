package at.bernhardberger.tvhplayer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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

/** Retain only destinations already shown during this expanded Channels/Guide visit. */
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
            val guide = entries.firstOrNull { it.metadata[SIDEBAR_SCENE_DESTINATION] == AppDestination.GUIDE }
            val channels = entries.firstOrNull {
                it.metadata[SIDEBAR_SCENE_DESTINATION] == AppDestination.CHANNELS
            }
            if (active == guide || active == channels) {
                SidebarGuideScene(
                    guide, active, channels, retainGuide, retainChannels,
                    entries.dropLast(1),
                )
            } else {
                null
            }
        }
    }
}

private data class SidebarGuideScene(
    val guide: NavEntry<AppNavKey>?,
    val active: NavEntry<AppNavKey>,
    val channels: NavEntry<AppNavKey>?,
    val retainGuide: Boolean,
    val retainChannels: Boolean,
    override val previousEntries: List<NavEntry<AppNavKey>>,
) : Scene<AppNavKey> {
    override val key: Any = ChannelsKey
    override val entries = listOfNotNull(guide, channels)
    override val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            SidebarVisitDestination(guide, active == guide, retainGuide)
            SidebarVisitDestination(channels, active == channels, retainChannels)
        }
    }
}

@Composable
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
            content = { entry.Content() },
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
