package at.bernhardberger.tvhplayer.ui.components.depth

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/** Consumers supply standard rows; the owner knows only stable identity and submenu vs leaf. */
class DepthRow(
    val item: DepthItem,
    val onActivate: () -> Unit = {},
    val leafLevelId: String? = null,
    val content: @Composable (Modifier, () -> Unit) -> Unit,
)

class DepthLevel(
    val id: String,
    val rows: List<DepthRow>,
    val heading: @Composable (Boolean) -> Unit,
    // A leaf editor is mounted only while active. Its rows are its independent safe preview.
    // Editors forward non-editing Left here before a read-only text field consumes it.
    val activeContent: (@Composable (FocusRequester, (KeyEvent) -> Boolean) -> Unit)? = null,
    val initialItemId: String? = null,
)

private class DepthPresentation(var level: DepthLevel, var frame: DepthFrame)

@Stable
class DepthNavigationState(initial: DepthStack) {
    var stack by mutableStateOf(initial)
        private set
    fun update(value: DepthStack) { stack = value }
    fun pop() { stack = stack.pop() }

    companion object {
        val saver = Saver<DepthNavigationState, String>(
            save = { Json.encodeToString(it.stack) },
            restore = { DepthNavigationState(Json.decodeFromString(it)) },
        )
    }
}

@Composable
fun rememberDepthNavigationState(rootId: String, initialItemId: String? = null): DepthNavigationState =
    rememberSaveable(saver = DepthNavigationState.saver) {
        DepthNavigationState(DepthStack(listOf(DepthFrame(rootId, initialItemId))))
    }

/**
 * Depth motion follows AOSP TvSettings' two-panel transition: a long, decelerating
 * slide (`easing_browse`) with a short alpha crossfade, and a 0.6 preview dim.
 */
internal const val DepthPreviewAlpha = 0.6f
internal const val DepthSlideMillis = 1000
internal const val DepthAlphaMillis = 200
private val DepthBrowseEasing = CubicBezierEasing(0.18f, 1f, 0.22f, 1f)
private fun depthSlideSpec() = tween<IntOffset>(DepthSlideMillis, easing = DepthBrowseEasing)

/**
 * One active slot at every depth, plus an inert child preview. Only the current visit
 * can publish focus/scroll or act. Outgoing compositions are presentation snapshots.
 * The shell owns the stationary background and the global navigation layer.
 */
@Composable
fun DepthNavigation(
    state: DepthNavigationState,
    levels: Map<String, DepthLevel>,
    fallbackLevel: (String) -> DepthLevel,
    columnWidth: Dp,
    columnGap: Dp,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    initialFocusEnabled: Boolean = true,
    backEnabled: Boolean = true,
    onRootBack: (() -> Unit)? = null,
    previewAlpha: Float = DepthPreviewAlpha,
    isCurrent: Boolean = true,
) {
    val stack = state.stack
    val candidate = levels[stack.active.levelId]
    val level = candidate?.takeIf { it.rows.isNotEmpty() || it.activeContent != null } ?: fallbackLevel(stack.active.levelId)
    val identities = level.rows.map { it.item }
    val reconciled = (if (stack.active.focusedItemId == null && level.initialItemId != null) {
        stack.focus(level.initialItemId, identities)
    } else stack).reconcile(identities)
    val latestLevels by rememberUpdatedState(levels)
    val cycles = remember { DepthKeyCycle() }
    val focusManager = LocalFocusManager.current
    var editorLeftExit by remember { mutableStateOf(false) }
    var pendingRootBack by remember { mutableStateOf<Int?>(null) }
    var pendingRootVisit by remember { mutableLongStateOf(0L) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val stepPixels = with(LocalDensity.current) { (columnWidth + columnGap).roundToPx() }
    val currentList = remember(stack.visit) {
        LazyListState(reconciled.active.firstVisibleIndex, reconciled.active.scrollOffset)
    }
    fun saveViewport() {
        val index = currentList.firstVisibleItemIndex
        state.update(state.stack.viewport(identities.getOrNull(index)?.id, index, currentList.firstVisibleItemScrollOffset))
    }
    fun pop() { saveViewport(); state.pop() }
    fun editorLeft(native: KeyEvent): Boolean {
        if (!isCurrent || state.stack.visit != stack.visit || level.activeContent == null) return false
        return cycles.handle(native.keyCode, native.action == KeyEvent.ACTION_DOWN, native.repeatCount) {
            editorLeftExit = false
            val moved = focusManager.moveFocus(FocusDirection.Left)
            // Compose also reports a cancelled focus search as handled.
            if ((editorLeftExit || !moved) && backEnabled && state.stack.canPop) pop()
            true
        }
    }
    fun activate(row: DepthRow, visit: Long, enterOnly: Boolean = false) {
        if (state.stack.visit != visit) return
        val currentRows = latestLevels[state.stack.active.levelId]?.rows?.takeIf { it.isNotEmpty() } ?: level.rows
        val currentItems = currentRows.map { it.item }
        if (row.item !in currentItems) return
        if (row.item.childLevelId != null) {
            saveViewport()
            state.update(state.stack.enter(row.item, currentItems))
        } else if (!enterOnly) {
            val current = currentRows.first { it.item == row.item }
            if (current.leafLevelId != null) {
                saveViewport()
                state.update(state.stack.copy(frames = state.stack.frames + DepthFrame(current.leafLevelId),
                    visit = state.stack.visit + 1))
            } else current.onActivate()
        }
    }
    SideEffect { if (state.stack == stack && reconciled != stack) state.update(reconciled) }
    LaunchedEffect(stack.visit, currentList) {
        snapshotFlow { currentList.firstVisibleItemIndex to currentList.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (state.stack.visit == stack.visit && level.activeContent == null) {
                    val currentItems = latestLevels[state.stack.active.levelId]?.rows?.map { it.item } ?: identities
                    state.update(state.stack.viewport(currentItems.getOrNull(index)?.id, index, offset))
                }
            }
    }
    BackHandler(isCurrent && backEnabled && initialFocusEnabled && stack.canPop) { pop() }

    Box(modifier.onPreviewKeyEvent { event ->
        if (!isCurrent) return@onPreviewKeyEvent false
        val native = event.nativeKeyEvent
        val code = native.keyCode
        val back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_LEFT
        val right = code == KeyEvent.KEYCODE_DPAD_RIGHT
        if (!back && !right) return@onPreviewKeyEvent false
        val consumed = cycles.handle(code, native.action == KeyEvent.ACTION_DOWN, native.repeatCount) {
            when {
                level.activeContent != null -> false // The editor/IME owns input before local Back.
                back && backEnabled && state.stack.canPop -> { pop(); true }
                back && backEnabled && onRootBack != null -> {
                    pendingRootBack = code
                    pendingRootVisit = state.stack.visit
                    true
                }
                code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    level.rows.firstOrNull { it.item.id == state.stack.active.focusedItemId }
                        ?.let { activate(it, stack.visit, enterOnly = true) }
                    true // Right on a leaf never commits and cannot reach the preview.
                }
                else -> false
            }
        }
        // Root handoff leaves this key owner; finish its cycle before moving to the drawer.
        if (native.action == KeyEvent.ACTION_UP && pendingRootBack == code) {
            pendingRootBack = null
            if (!native.isCanceled && !state.stack.canPop && pendingRootVisit == state.stack.visit) onRootBack?.invoke()
        }
        consumed
    }.onKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (!isCurrent || level.activeContent == null ||
            (native.keyCode != KeyEvent.KEYCODE_BACK && native.keyCode != KeyEvent.KEYCODE_DPAD_LEFT)) return@onKeyEvent false
        if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return@onKeyEvent editorLeft(native)
        cycles.handle(native.keyCode, native.action == KeyEvent.ACTION_DOWN, native.repeatCount) {
            if (backEnabled && state.stack.canPop) { pop(); true } else false
        }
    }) {
        // Key by visit, not by data/locale. Metadata and selection updates do not replay motion.
        AnimatedContent(
            targetState = stack.frames.size to stack.visit,
            transitionSpec = {
                val entering = targetState.first >= initialState.first
                (slideInHorizontally(depthSlideSpec()) {
                    if (entering) stepPixels * direction else -it * direction
                }).togetherWith(slideOutHorizontally(depthSlideSpec()) {
                    // Child enters from the preview slot; parent exits wholly offscreen.
                    if (entering) -it * direction else stepPixels * direction
                })
                    .using(null)
            },
            label = "depth-navigation",
        ) { (depth, visit) ->
            val active = visit == state.stack.visit && isCurrent
            // The column entering the active slot brightens from the preview dim to full;
            // the outgoing one dims on the same short fade while the slide carries it away.
            val columnAlpha by transition.animateFloat(
                transitionSpec = { tween(DepthAlphaMillis) },
                label = "depth-column-alpha",
            ) { if (it == EnterExitState.Visible) 1f else previewAlpha }
            val retained = remember(visit) { DepthPresentation(level, reconciled.active) }
            SideEffect {
                if (active && state.stack.visit == visit) {
                    retained.level = level
                    retained.frame = reconciled.active
                }
            }
            val frame = retained.frame
            val rendered = if (active) level else retained.level
            val list = if (active) currentList else remember(visit) {
                LazyListState(frame.firstVisibleIndex, frame.scrollOffset)
            }
            val focus = remember(visit) { mutableStateMapOf<String, FocusRequester>() }
            val editorFocus = remember(visit) { FocusRequester() }
            val targetId = if (active) reconciled.active.focusedItemId else frame.focusedItemId
            LaunchedEffect(visit, active, initialFocusEnabled, rendered.rows.map { it.item.id }) {
                if (!active || !initialFocusEnabled) return@LaunchedEffect
                if (rendered.activeContent != null) {
                    editorFocus.requestFocus()
                    return@LaunchedEffect
                }
                if (targetId == null) return@LaunchedEffect
                val targetIndex = rendered.rows.indexOfFirst { it.item.id == targetId }
                if (targetIndex < 0) return@LaunchedEffect
                // Preserve the exact viewport unless a removed/replaced item made it invisible.
                if (list.layoutInfo.visibleItemsInfo.none { it.key == targetId }) {
                    list.scrollToItem(reconciled.active.firstVisibleIndex, reconciled.active.scrollOffset)
                    withFrameNanos { }
                    if (list.layoutInfo.visibleItemsInfo.none { it.key == targetId }) list.scrollToItem(targetIndex)
                }
                snapshotFlow { focus[targetId] }.first { it != null }
                if (state.stack.visit == visit && initialFocusEnabled && state.stack.active.focusedItemId == targetId) {
                    focus[targetId]?.requestFocus()
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (active && rendered.activeContent != null) {
                    // Dedicated leaf surface: no narrow-column constraint, and never retained on exit.
                    Box(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding())
                        .focusProperties {
                            onExit = {
                                // A failed local Left search is a depth pop, never a geometric drawer jump.
                                if (requestedFocusDirection == FocusDirection.Left) {
                                    editorLeftExit = true
                                    cancelFocusChange()
                                }
                            }
                        }.focusGroup()) {
                        rendered.activeContent.invoke(editorFocus, ::editorLeft)
                    }
                } else {
                Column(Modifier.width(columnWidth).fillMaxHeight()
                    .graphicsLayer { alpha = columnAlpha }
                    .padding(top = contentPadding.calculateTopPadding())) {
                    rendered.heading(depth > 1)
                    LazyColumn(
                        state = list,
                        // Reserve the ListItem focus-scale overflow at the viewport's scroll
                        // edges instead of letting the container clip the focused row.
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                        modifier = Modifier.fillMaxSize().testTag("depth-active")
                            .focusProperties {
                                onEnter = {
                                    if (active) focus[state.stack.active.focusedItemId]?.requestFocus()
                                }
                            }.focusGroup(),
                    ) {
                        itemsIndexed(rendered.rows, key = { _, row -> row.item.id }) { _, row ->
                            val requester = remember(row.item.id) { FocusRequester() }
                            DisposableEffect(row.item.id, active) {
                                if (active) focus[row.item.id] = requester
                                onDispose { if (focus[row.item.id] === requester) focus.remove(row.item.id) }
                            }
                            row.content(
                                Modifier.focusRequester(requester)
                                    .focusProperties {
                                        canFocus = active
                                        right = FocusRequester.Cancel
                                        if (stack.canPop) left = FocusRequester.Cancel
                                    }
                                    .onFocusChanged {
                                        if (it.isFocused && active && state.stack.visit == visit) {
                                            state.update(state.stack.focus(row.item.id, identities))
                                        }
                                    },
                            ) { if (active) activate(row, visit) }
                        }
                    }
                }
                if (active) {
                    val previewKey = reconciled.previewKey(identities)
                    val preview = previewKey?.let { levels[it.childLevelId] ?: fallbackLevel(it.childLevelId) }
                    if (preview != null && state.stack.acceptsPreview(previewKey, identities)) {
                        // No clipping to the active column; only the outer shell/screen clips overflow.
                        Column(Modifier.offset(x = columnWidth + columnGap).width(columnWidth).fillMaxHeight()
                            .graphicsLayer { alpha = previewAlpha }
                            .clearAndSetSemantics { }
                            .padding(top = contentPadding.calculateTopPadding()).testTag("depth-preview")) {
                            preview.heading(false)
                            LazyColumn(
                                // Same top reservation as the active column so preview rows
                                // stay aligned with the rows they mirror.
                                contentPadding = PaddingValues(
                                    top = 4.dp,
                                    bottom = contentPadding.calculateBottomPadding(),
                                ),
                            ) {
                                itemsIndexed(preview.rows, key = { _, row -> row.item.id }) { _, row ->
                                    row.content(Modifier.focusProperties { canFocus = false }) { }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
