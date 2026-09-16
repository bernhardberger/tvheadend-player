package at.bernhardberger.tvhplayer.ui.components.depth

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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

/** One composed column in the sliding strip. Path is its visual identity. */
private class DepthStripColumn(val path: List<String>, val level: DepthLevel, val frame: DepthFrame)

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
 * slide (`easing_browse`) with a short alpha crossfade, and a 0.6 preview dim. Every
 * column travels the same single step, so the columns keep their spacing while they
 * move and the one leaving the active slot keeps the arriving column's velocity.
 */
internal const val DepthPreviewAlpha = 0.6f
internal const val DepthSlideMillis = 1000
internal const val DepthAlphaMillis = 200
private val DepthBrowseEasing = CubicBezierEasing(0.18f, 1f, 0.22f, 1f)
private fun depthStripSpec() = tween<Float>(DepthSlideMillis, easing = DepthBrowseEasing)

/**
 * Sibling preview replace follows AOSP TvSettings' two-panel preview transaction:
 * TwoPanelSettingsFragment.setCustomAnimations(fade_in_preview_panel,
 * fade_out_preview_panel) on preview replace, with res/animator alpha 0→1/inverse
 * over framework config_longAnimTime (read via android.R.integer.config_longAnimTime,
 * never a guessed constant). Enter uses easing_enter (0.12, 1, 0.40, 1), exit uses
 * easing_exit (0.40, 1, 0.12, 1). This fade is separate from the depth slide
 * (1000ms browse) and the 200ms active emphasis above.
 */
private val SiblingPreviewEnterEasing = CubicBezierEasing(0.12f, 1f, 0.40f, 1f)
private val SiblingPreviewExitEasing = CubicBezierEasing(0.40f, 1f, 0.12f, 1f)

/**
 * Desired columns win their slot. A held column at the same depth as a different
 * desired path is an obsolete sibling and is dropped. Held descendants of a
 * desired path (departing) and held ancestors (returning) are kept.
 */
private fun unionStrip(held: List<DepthStripColumn>, desired: List<DepthStripColumn>): List<DepthStripColumn> {
    val desiredBySlot = desired.associateBy { it.path.size }
    val desiredPaths = desired.map { it.path }
    fun onBranch(path: List<String>) = desiredPaths.any { candidate ->
        candidate.size >= path.size && candidate.take(path.size) == path ||
            path.size > candidate.size && path.take(candidate.size) == candidate
    }
    val byPath = LinkedHashMap<List<String>, DepthStripColumn>()
    desired.forEach { byPath[it.path] = it }
    held.forEach { column ->
        if (column.path in byPath) return@forEach
        val occupant = desiredBySlot[column.path.size]
        if (occupant != null && occupant.path != column.path) return@forEach
        if (!onBranch(column.path)) return@forEach
        byPath[column.path] = column
    }
    return byPath.values.sortedBy { it.path.size }
}

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
    fun resolve(id: String): DepthLevel {
        val candidate = levels[id]
        return candidate?.takeIf { it.rows.isNotEmpty() || it.activeContent != null } ?: fallbackLevel(id)
    }
    val level = resolve(stack.active.levelId)
    val identities = level.rows.map { it.item }
    val reconciled = (if (stack.active.focusedItemId == null && level.initialItemId != null) {
        stack.focus(level.initialItemId, identities)
    } else stack).reconcile(identities)
    val latestLevels by rememberUpdatedState(levels)
    val latestIsCurrent = rememberUpdatedState(isCurrent)
    val cycles = remember { DepthKeyCycle() }
    val focusManager = LocalFocusManager.current
    var editorLeftExit by remember { mutableStateOf(false) }
    var pendingRootBack by remember { mutableStateOf<Int?>(null) }
    var pendingRootVisit by remember { mutableLongStateOf(0L) }
    val stepPixels = with(LocalDensity.current) { (columnWidth + columnGap).roundToPx() }
    // Sibling preview fade duration is the framework long animation time, not a guessed
    // constant. AOSP preview replace uses config_longAnimTime for both fade_in and fade_out.
    val previewFadeContext = LocalContext.current
    val previewFadeMillis = remember(previewFadeContext) {
        previewFadeContext.resources.getInteger(android.R.integer.config_longAnimTime)
    }
    // The columns are inset inside the moving area, not outside it, so a column that
    // slides out of the active slot disappears at the edge of the host's own area
    // instead of crossing whatever the host placed beside it.
    val columnInset = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    val listStates = remember { mutableMapOf<List<String>, LazyListState>() }
    fun listState(path: List<String>, frame: DepthFrame) =
        listStates.getOrPut(path) { LazyListState(frame.firstVisibleIndex, frame.scrollOffset) }
    fun saveViewport() {
        val list = listStates[state.stack.path] ?: return
        val index = list.firstVisibleItemIndex
        val ids = latestLevels[state.stack.active.levelId]?.rows?.map { it.item } ?: identities
        state.update(state.stack.viewport(ids.getOrNull(index)?.id, index, list.firstVisibleItemScrollOffset))
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
        if (!latestIsCurrent.value || state.stack.visit != visit) return
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
    BackHandler(isCurrent && backEnabled && initialFocusEnabled && stack.canPop) { pop() }

    val desired = buildList {
        reconciled.frames.forEachIndexed { index, frame ->
            val path = reconciled.frames.take(index + 1).map { it.levelId }
            val columnLevel = resolve(frame.levelId)
            if (columnLevel.activeContent == null) add(DepthStripColumn(path, columnLevel, frame))
        }
        if (level.activeContent == null) {
            val previewKey = reconciled.previewKey(identities)
            val childId = previewKey?.childLevelId
            if (childId != null && reconciled.acceptsPreview(previewKey, identities)) {
                val previewLevel = resolve(childId)
                if (previewLevel.activeContent == null && previewLevel.rows.isNotEmpty()) {
                    add(DepthStripColumn(reconciled.path + childId, previewLevel, DepthFrame(childId)))
                }
            }
        }
    }
    val activeIndex = reconciled.frames.lastIndex
    val latestDesired by rememberUpdatedState(desired)
    var held by remember { mutableStateOf(desired) }
    var inMotion by remember { mutableStateOf(false) }
    var anchoredIndex by remember { mutableIntStateOf(activeIndex) }
    val stripOffset = remember { Animatable((-activeIndex * stepPixels).toFloat()) }
    val moving = inMotion || anchoredIndex != activeIndex
    LaunchedEffect(activeIndex) {
        held = unionStrip(held, latestDesired)
        inMotion = true
        stripOffset.animateTo((-activeIndex * stepPixels).toFloat(), depthStripSpec())
        held = latestDesired
        anchoredIndex = activeIndex
        inMotion = false
    }
    val displayed = if (moving) unionStrip(held, desired) else desired

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
        Box(Modifier.fillMaxSize().padding(start = columnInset)) {
            Box(Modifier.graphicsLayer { translationX = stripOffset.value }) {
                // Depth push/pop slides the full strip (desired wins the slot in unionStrip).
                // Settled Up/Down sibling preview switches keep the active columns here and
                // crossfade only the preview slot below: intentional overlap is only for
                // DIFFERENT siblings at the same preview slot, never the same level twice
                // during preview→active enter/pop (those dispose via the active-path key).
                val stripColumns = if (moving) displayed else displayed.filter { it.path.size <= activeIndex + 1 }
                stripColumns.forEach { column ->
                    key(column.path) {
                        val columnVisit = state.stack.visit
                        DepthStripPane(
                            column = column,
                            activePath = state.stack.path,
                            isCurrent = isCurrent,
                            visit = columnVisit,
                            stillOwning = { owned ->
                                latestIsCurrent.value &&
                                    column.path == state.stack.path &&
                                    state.stack.visit == owned
                            },
                            stackCanPop = stack.canPop,
                            stepPixels = stepPixels,
                            xOffset = (column.path.size - 1) * stepPixels,
                            columnWidth = columnWidth,
                            contentPadding = contentPadding,
                            previewAlpha = previewAlpha,
                            initialFocusEnabled = initialFocusEnabled,
                            list = listState(column.path, column.frame),
                            identities = if (column.path == state.stack.path) identities else column.level.rows.map { it.item },
                            onFocusItem = { itemId, items, owned ->
                                if (latestIsCurrent.value && state.stack.visit == owned) {
                                    state.update(state.stack.focus(itemId, items))
                                }
                            },
                            onActivate = { row, owned -> activate(row, owned) },
                            focusedItemId = if (column.path == state.stack.path) reconciled.active.focusedItemId else column.frame.focusedItemId,
                            viewportIndex = if (column.path == state.stack.path) reconciled.active.firstVisibleIndex else column.frame.firstVisibleIndex,
                            viewportOffset = if (column.path == state.stack.path) reconciled.active.scrollOffset else column.frame.scrollOffset,
                            onViewport = { index, offset, owned, items ->
                                if (latestIsCurrent.value && state.stack.visit == owned &&
                                    state.stack.path == column.path && level.activeContent == null) {
                                    state.update(state.stack.viewport(items.getOrNull(index)?.id, index, offset))
                                }
                            },
                        )
                    }
                }
                if (!moving) {
                    val previewPath = displayed.firstOrNull { it.path.size > activeIndex + 1 }?.path
                    val previewSlot = activeIndex + 1
                    key(state.stack.path) {
                        // Position the fade container at the preview slot so its children fill it
                        // with no extra offset (no overflow, no clipping during the crossfade).
                        // Intentional overlap is only DIFFERENT siblings here; push/pop disposes
                        // via the active-path key and never draws the same level twice.
                        Box(Modifier.offset { IntOffset(previewSlot * stepPixels, 0) }
                            .width(columnWidth).fillMaxHeight()) {
                            AnimatedContent(
                                targetState = previewPath,
                                contentKey = { it },
                                transitionSpec = {
                                    fadeIn(tween(previewFadeMillis, easing = SiblingPreviewEnterEasing)) togetherWith
                                        fadeOut(tween(previewFadeMillis, easing = SiblingPreviewExitEasing))
                                },
                                label = "sibling-preview-fade",
                                modifier = Modifier.fillMaxSize(),
                            ) { path ->
                                if (path != null) {
                                    val childId = path.last()
                                    val previewLevel = resolve(childId)
                                    if (previewLevel.activeContent == null && previewLevel.rows.isNotEmpty()) {
                                        val column = DepthStripColumn(path, previewLevel, DepthFrame(childId))
                                        key(column.path) {
                                            val columnVisit = state.stack.visit
                                            DepthStripPane(
                                                column = column,
                                                activePath = state.stack.path,
                                                isCurrent = isCurrent,
                                                visit = columnVisit,
                                                stillOwning = { owned ->
                                                    latestIsCurrent.value &&
                                                        column.path == state.stack.path &&
                                                        state.stack.visit == owned
                                                },
                                                stackCanPop = stack.canPop,
                                                stepPixels = stepPixels,
                                                xOffset = 0,
                                                columnWidth = columnWidth,
                                                contentPadding = contentPadding,
                                                previewAlpha = previewAlpha,
                                                initialFocusEnabled = initialFocusEnabled,
                                                list = listState(column.path, column.frame),
                                                identities = column.level.rows.map { it.item },
                                                onFocusItem = { itemId, items, owned ->
                                                    if (latestIsCurrent.value && state.stack.visit == owned) {
                                                        state.update(state.stack.focus(itemId, items))
                                                    }
                                                },
                                                onActivate = { row, owned -> activate(row, owned) },
                                                focusedItemId = column.frame.focusedItemId,
                                                viewportIndex = column.frame.firstVisibleIndex,
                                                viewportOffset = column.frame.scrollOffset,
                                                onViewport = { index, offset, owned, items ->
                                                    if (latestIsCurrent.value && state.stack.visit == owned &&
                                                        state.stack.path == column.path && level.activeContent == null) {
                                                        state.update(state.stack.viewport(items.getOrNull(index)?.id, index, offset))
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        Box(Modifier.fillMaxSize()) {}
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize()) {}
                                }
                            }
                        }
                    }
                }
            }
            if (isCurrent && level.activeContent != null) {
                val editorFocus = remember { FocusRequester() }
                val editorVisit = stack.visit
                LaunchedEffect(stack.visit, initialFocusEnabled) {
                    if (!initialFocusEnabled) return@LaunchedEffect
                    editorFocus.requestFocus()
                }
                Box(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding())
                    .focusProperties {
                        onExit = {
                            if (requestedFocusDirection == FocusDirection.Left) {
                                editorLeftExit = true
                                cancelFocusChange()
                            }
                        }
                    }.focusGroup()) {
                    if (state.stack.visit == editorVisit) {
                        level.activeContent.invoke(editorFocus, ::editorLeft)
                    }
                }
            }
        }
    }
}

@Composable
private fun DepthStripPane(
    column: DepthStripColumn,
    activePath: List<String>,
    isCurrent: Boolean,
    visit: Long,
    stillOwning: (Long) -> Boolean,
    stackCanPop: Boolean,
    stepPixels: Int,
    xOffset: Int,
    columnWidth: Dp,
    contentPadding: PaddingValues,
    previewAlpha: Float,
    initialFocusEnabled: Boolean,
    list: LazyListState,
    identities: List<DepthItem>,
    onFocusItem: (String, List<DepthItem>, Long) -> Unit,
    onActivate: (DepthRow, Long) -> Unit,
    focusedItemId: String?,
    viewportIndex: Int,
    viewportOffset: Int,
    onViewport: (Int, Int, Long, List<DepthItem>) -> Unit,
) {
    val active = column.path == activePath && isCurrent
    val capturedVisit = visit
    val latestActive = rememberUpdatedState(active)
    val latestStillOwning = rememberUpdatedState(stillOwning)
    val latestFocusedId = rememberUpdatedState(focusedItemId)
    val targetAlpha = if (active) 1f else previewAlpha
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(DepthAlphaMillis),
        label = "depth-column-alpha",
    )
    val focus = remember { mutableStateMapOf<String, FocusRequester>() }
    val targetId = focusedItemId
    LaunchedEffect(capturedVisit, active, initialFocusEnabled, column.level.rows.map { it.item.id }) {
        if (!active || !initialFocusEnabled) return@LaunchedEffect
        if (targetId == null) return@LaunchedEffect
        val targetIndex = column.level.rows.indexOfFirst { it.item.id == targetId }
        if (targetIndex < 0) return@LaunchedEffect
        suspend fun stillThisTarget() =
            latestStillOwning.value(capturedVisit) && latestFocusedId.value == targetId
        if (list.layoutInfo.visibleItemsInfo.none { it.key == targetId }) {
            if (!stillThisTarget()) return@LaunchedEffect
            list.scrollToItem(viewportIndex, viewportOffset)
            withFrameNanos { }
            if (!stillThisTarget()) return@LaunchedEffect
            if (list.layoutInfo.visibleItemsInfo.none { it.key == targetId }) list.scrollToItem(targetIndex)
        }
        snapshotFlow { focus[targetId] }.first { it != null }
        if (stillThisTarget() && initialFocusEnabled) {
            focus[targetId]?.requestFocus()
        }
    }
    if (active) {
        LaunchedEffect(list, capturedVisit) {
            snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (latestStillOwning.value(capturedVisit)) {
                        onViewport(index, offset, capturedVisit, identities)
                    }
                }
        }
    }
    Column(
        Modifier
            .offset { IntOffset(xOffset, 0) }
            .width(columnWidth)
            .fillMaxHeight()
            .graphicsLayer { this.alpha = alpha }
            .padding(top = contentPadding.calculateTopPadding())
            .then(
                when {
                    active -> Modifier.testTag("depth-active")
                    column.path.size == activePath.size + 1 ->
                        Modifier.clearAndSetSemantics { }.testTag("depth-preview")
                    else -> Modifier.clearAndSetSemantics { }
                },
            ),
    ) {
        column.level.heading(column.path.size > 1)
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            modifier = Modifier.fillMaxSize()
                .focusProperties {
                    onEnter = {
                        if (latestActive.value) focus[latestFocusedId.value]?.requestFocus()
                    }
                }.focusGroup(),
        ) {
            itemsIndexed(column.level.rows, key = { _, row -> row.item.id }) { _, row ->
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
                            if (stackCanPop) left = FocusRequester.Cancel
                        }
                                    .onFocusChanged {
                                        if (it.isFocused && latestActive.value &&
                                            latestStillOwning.value(capturedVisit)) {
                                            onFocusItem(row.item.id, identities, capturedVisit)
                                        }
                                    },
                            ) { if (latestActive.value) onActivate(row, capturedVisit) }
            }
        }
    }
}
