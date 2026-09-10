package at.bernhardberger.tvhplayer.ui

import androidx.compose.animation.AnimatedVisibility
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

/** Retain only a Guide already shown during this expanded-sidebar visit. */
@Composable
internal fun rememberSidebarGuideSceneStrategy(
    drawerActive: Boolean,
    destination: AppNavKey?,
): SceneStrategy<AppNavKey> {
    val inGuidePair = destination == GuideKey || destination == ChannelsKey
    var guideShown by remember(drawerActive, inGuidePair) { mutableStateOf(false) }
    SideEffect { if (drawerActive && destination == GuideKey) guideShown = true }
    val retainGuide = drawerActive && guideShown
    return remember(retainGuide) {
        SceneStrategy { entries ->
            val active = entries.last()
            val guide = entries.firstOrNull { it.metadata[SIDEBAR_SCENE_DESTINATION] == AppDestination.GUIDE }
            if (guide != null && (active == guide ||
                    (retainGuide && active.metadata[SIDEBAR_SCENE_DESTINATION] == AppDestination.CHANNELS))) {
                SidebarGuideScene(
                    guide, active, entries.firstOrNull {
                        it.metadata[SIDEBAR_SCENE_DESTINATION] == AppDestination.CHANNELS
                    },
                    entries.dropLast(1),
                )
            } else {
                null
            }
        }
    }
}

private data class SidebarGuideScene(
    val guide: NavEntry<AppNavKey>,
    val active: NavEntry<AppNavKey>,
    val channels: NavEntry<AppNavKey>?,
    override val previousEntries: List<NavEntry<AppNavKey>>,
) : Scene<AppNavKey> {
    override val key: Any = GuideKey
    override val entries = listOfNotNull(guide, channels)
    override val content: @Composable () -> Unit = {
        val guideVisible = active == guide
        val guideAlpha = animateFloatAsState(
            if (guideVisible) 1f else 0f,
            tween(APP_DESTINATION_CROSSFADE_DURATION_MILLIS, easing = LinearEasing),
            label = "sidebarGuideAlpha",
        )
        Box(Modifier.fillMaxSize()) {
            Layout(
                content = { guide.Content() },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = guideAlpha.value }
                    .then(if (guideVisible) Modifier else Modifier.clearAndSetSemantics { })
                    .focusProperties { onEnter = { if (!guideVisible) cancelFocusChange() } }
                    .focusGroup(),
            ) { measurables, constraints ->
                // Keep the composition, but do no hidden Guide measure/place work after fade.
                val child = if (guideVisible || guideAlpha.value > 0f) {
                    measurables.firstOrNull()?.measure(constraints)
                } else null
                layout(constraints.maxWidth, constraints.maxHeight) {
                    child?.placeRelative(0, 0)
                }
            }
            AnimatedVisibility(
                visible = !guideVisible,
                enter = appDestinationEnterTransition(),
                exit = appDestinationExitTransition(),
            ) {
                channels?.Content()
            }
        }
    }
}
