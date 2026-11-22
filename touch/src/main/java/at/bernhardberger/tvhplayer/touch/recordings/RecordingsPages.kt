package at.bernhardberger.tvhplayer.touch.recordings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationBackEnabled
import at.bernhardberger.tvhplayer.touch.ui.OnDestinationReselected
import at.bernhardberger.tvhplayer.touch.ui.ReportFrameBarsWhen
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import at.bernhardberger.tvhplayer.touch.ui.pageMove

private sealed interface RecordingsPageKey {
    data object Browse : RecordingsPageKey
    data class Details(val id: String) : RecordingsPageKey
}

@Composable
internal fun RecordingsPages(
    pageItem: RecordingItem?,
    clock: RecordingClock,
    layout: RecordingsLayout,
    actions: RecordingsActions,
    destinations: List<Destination>,
    onClose: () -> Unit,
    browse: @Composable (active: Boolean, modifier: Modifier) -> Unit,
) {
    val backStack = listOfNotNull(RecordingsPageKey.Browse, pageItem?.let { RecordingsPageKey.Details(it.id) })
    val canHandleBack = LocalDestinationBackEnabled.current
    if (canHandleBack && pageItem != null) {
        OnDestinationReselected(Destination.Recordings) { onClose() }
    }
    val render by rememberUpdatedState<@Composable (RecordingsPageKey, RecordingItem?) -> Unit> { key, initial ->
        val isTop = key == backStack.last()
        val active = isTop && canHandleBack
        ReportFrameBarsWhen(isTop) {
            when (key) {
                RecordingsPageKey.Browse -> {
                    PhoneBrowseViewport(layout.compact && destinations.size > 1, active) { browse(active, it) }
                }
                is RecordingsPageKey.Details -> {
                    // An exiting entry can outlive the recording in the archive.
                    var last by remember { mutableStateOf(checkNotNull(initial)) }
                    val details = if (isTop) pageItem ?: last else last
                    SideEffect { last = details }
                    TouchFrame(
                        initialDestination = Destination.Recordings, destinations = destinations,
                        showTopBar = { false }, showNavigation = { !layout.compact },
                    ) {
                        RecordingPage(details, clock, layout, actions) { if (active) onClose() }
                    }
                }
            }
        }
    }
    val decorators = listOf(rememberSaveableStateHolderNavEntryDecorator<RecordingsPageKey>())
    val entries = rememberDecoratedNavEntries(backStack, decorators) { key ->
        val initial = pageItem
        NavEntry(key) { render(key, initial) }
    }
    val scene = rememberSceneState(entries, sceneStrategies = emptyList(), onBack = onClose)
    val navigation = rememberNavigationEventState(scene)
    val forward = pageMove(forward = true)
    val backward = pageMove(forward = false)
    NavDisplay(
        scene, navigation, modifier = Modifier.fillMaxSize(),
        transitionSpec = { forward }, popTransitionSpec = { backward },
    )
    NavigationBackHandler(navigation, isBackEnabled = canHandleBack && pageItem != null, onBackCompleted = onClose)
}

/** Keep the phone list from growing and clamping its scroll while the shell hides its navigation bar. */
@Composable
private fun PhoneBrowseViewport(enabled: Boolean, active: Boolean, content: @Composable (Modifier) -> Unit) {
    if (!enabled) {
        content(Modifier)
        return
    }
    val density = LocalDensity.current
    val inset = WindowInsets.safeDrawing.getBottom(density)
    var height by rememberSaveable(LocalWindowInfo.current.containerSize, density, inset) { mutableStateOf(0f) }
    BoxWithConstraints {
        if (active && (height == 0f || maxHeight.value < height)) {
            SideEffect { height = maxHeight.value }
        }
        content(Modifier.heightIn(max = if (height > 0f) height.dp else maxHeight))
    }
}
