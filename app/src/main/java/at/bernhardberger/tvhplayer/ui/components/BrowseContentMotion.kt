package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy

/** Presentation history only: selection and readiness remain with the screen. */
@Stable
internal class BrowseContentMotion(initialKey: Any?) {
    internal class Change(val destination: Any?, val direction: Int)

    internal var change by mutableStateOf<Change?>(null)
        private set
    internal var externalGeneration by mutableIntStateOf(0)
        private set
    private var requestedKey by mutableStateOf(initialKey)
    private var renderedKey = initialKey

    fun select(destination: Any?, orderedKeys: List<Any?>) {
        if (destination == requestedKey) return
        val from = orderedKeys.indexOf(requestedKey)
        val to = orderedKeys.indexOf(destination)
        requestedKey = destination
        change = if (from >= 0 && to >= 0) Change(destination, to.compareTo(from)) else null
    }

    internal fun isRequested(destination: Any?) = requestedKey == destination

    internal fun acceptRendered(destination: Any?) {
        if (destination == renderedKey && destination == requestedKey) return
        renderedKey = destination
        requestedKey = destination
        if (change?.destination != destination) {
            change = null
            externalGeneration++
        }
    }
}

@Composable
internal fun rememberBrowseContentMotion(
    selectedKey: Any?,
    currentKey: () -> Any? = { selectedKey },
): BrowseContentMotion {
    val motion = remember { BrowseContentMotion(selectedKey) }
    // Consult the owner, not a possibly superseded rendered scope.
    SideEffect { motion.acceptRendered(currentKey()) }
    return motion
}

/** A rendered visit, not just a tab ID: old A cannot act after A → B → A. */
@Stable
internal class BrowseTabOwner(private val current: () -> Boolean) {
    private var attached = true
    val isCurrent: Boolean get() = attached && current()
    internal fun release() { attached = false }
}

internal val LocalBrowseTabOwner = staticCompositionLocalOf<BrowseTabOwner?> { null }

/** Keep native styling while making outgoing actionable leaves unfocusable. */
@Composable
internal fun Modifier.browseTabFocus(): Modifier {
    val owner = LocalBrowseTabOwner.current ?: return this
    return focusProperties { canFocus = owner.isCurrent }
}

/** Never measure two lists against the controller's one authoritative viewport. */
@Composable
internal fun rememberBrowseTabListState(current: LazyListState): LazyListState {
    val owner = LocalBrowseTabOwner.current
    return if (owner?.isCurrent != false) current else remember {
        LazyListState(current.firstVisibleItemIndex, current.firstVisibleItemScrollOffset)
    }
}

private data class TabBody<T>(val destination: Any?, val owner: BrowseTabOwner, val state: T)
private class DisplayedTabState<T>(var value: T)

private class TabValueReader<T>(private val owner: BrowseTabOwner, private val source: () -> T) {
    private var lastRead: DisplayedTabState<T>? = null

    fun read(): T {
        val previous = lastRead
        if (previous != null && !owner.isCurrent) return previous.value
        val value = source()
        if (previous == null) lastRead = DisplayedTabState(value) else previous.value = value
        return value
    }
}

/** Observe at the consuming leaf/draw phase; retain its last value on departure.
 * The retained value is not observable state, so drawing never invalidates composition.
 */
@Composable
internal fun <T> rememberBrowseTabReader(source: () -> T): () -> T {
    val owner = LocalBrowseTabOwner.current ?: return source
    val latest = rememberUpdatedState(source)
    return remember(owner) { TabValueReader(owner) { latest.value() }::read }
}

/** Animate immutable presentation values. Only the newest rendered visit may act. */
@Composable
internal fun <T> BrowseTabContent(
    motion: BrowseContentMotion,
    selectedKey: Any?,
    state: () -> T,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(T, BrowseTabOwner) -> Unit,
) {
    val generation = motion.externalGeneration
    val visit = remember(selectedKey, generation) { Any() }
    val currentVisit = rememberUpdatedState(visit)
    val owner = remember(visit) {
        BrowseTabOwner { currentVisit.value === visit && motion.isRequested(selectedKey) }
    }
    val displayed = remember(visit) { DisplayedTabState(state()) }
    // Selection callbacks may clear live focus before the new scope is rendered.
    // Keep that departing page's last committed presentation in the meantime.
    val presentation = if (owner.isCurrent) state() else displayed.value
    SideEffect { if (owner.isCurrent) displayed.value = presentation }
    val target = TabBody(selectedKey, owner, presentation)
    val change = motion.change
    val rtlSign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    // An external replacement must not resurrect a still-exiting old slot.
    key(generation) {
        AnimatedContent(
            targetState = target,
            contentKey = { it.destination },
            modifier = modifier.clipToBounds(),
            transitionSpec = {
                val direction = change?.takeIf { it.destination == targetState.destination }?.direction
                val sign = direction?.takeUnless { initialState.destination == targetState.destination }?.times(rtlSign)
                val transform = BrowseMotionPolicy.tabTransform(sign)
                transform.using(null)
            },
            label = "browseTabBody",
        ) { body ->
            // A returning tab gets fresh interactive locals, not an old paging job.
            key(body.owner) {
                DisposableEffect(body.owner) { onDispose(body.owner::release) }
                CompositionLocalProvider(LocalBrowseTabOwner provides body.owner) {
                    Column(
                        Modifier.fillMaxSize()
                            .then(if (body.owner.isCurrent) Modifier else Modifier.clearAndSetSemantics { })
                            .onPreviewKeyEvent { !body.owner.isCurrent }
                            .focusProperties { onEnter = { if (!body.owner.isCurrent) cancelFocusChange() } }
                            .focusGroup(),
                    ) { content(body.state, body.owner) }
                }
            }
        }
    }
}
