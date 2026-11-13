package at.bernhardberger.tvhplayer.ui.components.depth

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import at.bernhardberger.tvhplayer.ui.components.backKeyCycleFocus
import at.bernhardberger.tvhplayer.ui.components.rememberBackKeyCycle
import at.bernhardberger.tvhplayer.ui.TvPassiveAlpha
import at.bernhardberger.tvhplayer.ui.components.BrowseContentLayer
import at.bernhardberger.tvhplayer.ui.components.ListBottomReserve
import at.bernhardberger.tvhplayer.ui.components.listEdgeFades
import at.bernhardberger.tvhplayer.ui.components.rememberListEdgeBringIntoView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/** Consumers supply standard rows; the owner knows only stable identity and submenu vs leaf. */
class DepthRow(
    val item: DepthItem,
    val onActivate: () -> Unit = {},
    val leafLevelId: String? = null,
    val previewLevelId: String? = item.childLevelId,
    val content: @Composable (Modifier, () -> Unit) -> Unit,
)

class DepthLevel(
    val id: String,
    val rows: List<DepthRow>,
    // Emphasis is 1 in the active slot and 0 in the preview slot; it follows the strip
    // slide, so a heading can grow while its column moves into the active slot.
    val heading: @Composable (emphasis: () -> Float) -> Unit,
    val initialItemId: String? = null,
    val passiveContent: (@Composable () -> Unit)? = null,
    val emptyContent: (@Composable () -> Unit)? = null,
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
internal const val DepthSlideMillis = 1000
internal const val DepthAlphaMillis = 200

/**
 * The parent column left of the active one dims further than the preview while it slides off,
 * then fades out once the slide has settled: its trailing edge would otherwise rest beside the
 * rail, where its folder arrows read as the rail's own.
 */
internal const val DepthParentAlpha = 0f
internal const val DepthParentSlideAlpha = 0.25f

/** Top content reserve inside a depth list so the first focused row's scale is not clipped. */
internal val DepthListTopReserve = 4.dp

/** How long a restore waits for its row to compose before it lets the pane go. */
private const val RestoreFocusWaitMillis = 1000L

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
    previewAlpha: Float = TvPassiveAlpha,
    parentAlpha: Float = DepthParentAlpha,
    isCurrent: Boolean = true,
    onRootLeft: (() -> Unit)? = onRootBack,
    onFirstRowUp: (() -> Unit)? = null,
    activeFocusRequester: FocusRequester? = null,
    pageKeyDirection: ((Int) -> Int?)? = null,
) {
    val stack = state.stack
    fun resolve(id: String): DepthLevel {
        val candidate = levels[id]
        return candidate?.takeIf {
            it.rows.isNotEmpty() || it.passiveContent != null || it.emptyContent != null
        } ?: fallbackLevel(id)
    }
    val level = resolve(stack.active.levelId)
    val identities = level.rows.map { it.item }
    var previousInitialItemId by remember(stack.visit) { mutableStateOf(level.initialItemId) }
    // A late preference may replace the initial fallback, but not a row the viewer moved to.
    val initialSelectionChanged = previousInitialItemId != level.initialItemId &&
        stack.active.focusedItemId == previousInitialItemId
    val reconciled = (if ((stack.active.focusedItemId == null || initialSelectionChanged) && level.initialItemId != null) {
        stack.focus(level.initialItemId, identities)
    } else stack).reconcile(identities)
    val latestLevels by rememberUpdatedState(levels)
    val latestIsCurrent = rememberUpdatedState(isCurrent)
    val cycles = remember { DepthKeyCycle() }
    val backCycle = rememberBackKeyCycle(isCurrent && backEnabled &&
        (state.stack.canPop || onRootBack != null))
    var pendingRootBack by remember { mutableStateOf<Int?>(null) }
    var pendingRootVisit by remember { mutableLongStateOf(0L) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
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
        listStates.getOrPut(path) {
            val initialLevel = resolve(frame.levelId)
            val initialIndex = initialLevel.rows.indexOfFirst { it.item.id == initialLevel.initialItemId }
            // Mount a choice level's selected row in its preview, before the parent loses focus.
            if (frame.firstVisibleItemId == null && initialIndex >= 0) LazyListState(initialIndex)
            else LazyListState(frame.firstVisibleIndex, frame.scrollOffset)
        }
    fun saveViewport() {
        val list = listStates[state.stack.path] ?: return
        val index = list.firstVisibleItemIndex
        val ids = latestLevels[state.stack.active.levelId]?.rows?.map { it.item } ?: identities
        state.update(state.stack.viewport(ids.getOrNull(index)?.id, index, list.firstVisibleItemScrollOffset))
    }
    fun pop() { saveViewport(); state.pop() }
    // A choice level opens on its current choice; a browse level where it was left.
    fun openingFrame(stack: DepthStack, levelId: String) =
        if (resolve(levelId).initialItemId != null) DepthFrame(levelId) else stack.frameFor(levelId)
    fun activate(row: DepthRow, visit: Long, enterOnly: Boolean = false) {
        if (!latestIsCurrent.value || state.stack.visit != visit) return
        val currentRows = latestLevels[state.stack.active.levelId]?.rows?.takeIf { it.isNotEmpty() } ?: level.rows
        val currentItems = currentRows.map { it.item }
        if (row.item !in currentItems) return
        if (row.item.childLevelId != null) {
            saveViewport()
            state.update(state.stack.enter(row.item, currentItems,
                restore = resolve(row.item.childLevelId).initialItemId == null))
        } else if (!enterOnly) {
            val current = currentRows.first { it.item == row.item }
            if (current.leafLevelId != null) {
                saveViewport()
                state.update(state.stack.copy(frames = state.stack.frames + DepthFrame(current.leafLevelId),
                    visit = state.stack.visit + 1))
            } else current.onActivate()
        }
    }
    SideEffect {
        if (latestIsCurrent.value && state.stack == stack && reconciled != stack) state.update(reconciled)
        previousInitialItemId = level.initialItemId
    }
    BackHandler(isCurrent && backEnabled && initialFocusEnabled && (stack.canPop || onRootBack != null)) {
        if (stack.canPop) pop() else onRootBack?.invoke()
    }

    val desired = buildList {
        reconciled.frames.forEachIndexed { index, frame ->
            val path = reconciled.frames.take(index + 1).map { it.levelId }
            val columnLevel = resolve(frame.levelId)
            add(DepthStripColumn(path, columnLevel, frame))
        }
        val childId = level.rows.firstOrNull { it.item.id == reconciled.active.focusedItemId }?.previewLevelId
        if (childId != null) {
            val previewLevel = resolve(childId)
            add(DepthStripColumn(reconciled.path + childId, previewLevel, openingFrame(reconciled, childId)))
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

    // A control in a level's heading (Recordings' options button) takes its own keys, not the list's.
    var headingFocused by remember { mutableStateOf(false) }
    Box(modifier.backKeyCycleFocus(backCycle).onPreviewKeyEvent { event ->
        if (!isCurrent || headingFocused) {
            backCycle.reset()
            return@onPreviewKeyEvent false
        }
        val native = event.nativeKeyEvent
        val code = native.keyCode
        if (code == KeyEvent.KEYCODE_BACK) {
            return@onPreviewKeyEvent backCycle.handle(native.action == KeyEvent.ACTION_DOWN,
                native.repeatCount, native.isCanceled,
                enabled = backEnabled && (state.stack.canPop || onRootBack != null)) {
                if (state.stack.canPop) pop() else onRootBack?.invoke()
            }
        }
        val back = code == KeyEvent.KEYCODE_DPAD_LEFT
        val right = code == KeyEvent.KEYCODE_DPAD_RIGHT
        val up = code == KeyEvent.KEYCODE_DPAD_UP
        if (!back && !right && !up) return@onPreviewKeyEvent false
        val exit = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> onRootLeft
            KeyEvent.KEYCODE_DPAD_UP -> onFirstRowUp.takeIf {
                state.stack.active.focusedItemId == level.rows.firstOrNull()?.item?.id
            }
            else -> null
        }
        // Held Left walks out level by level; held Right acts once, since Right's
        // repeats outran the entering level on the TV and escaped to the tabs.
        val consumed = cycles.handle(code, native.action == KeyEvent.ACTION_DOWN, native.repeatCount, repeats = code == KeyEvent.KEYCODE_DPAD_LEFT) {
            when {
                back && backEnabled && state.stack.canPop -> { pop(); true }
                (back && backEnabled || up) && exit != null -> {
                    pendingRootBack = code
                    pendingRootVisit = state.stack.visit
                    pendingExit = exit
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
            val action = pendingExit
            pendingExit = null
            if (!native.isCanceled && pendingRootVisit == state.stack.visit) action?.invoke()
        }
        consumed
    }) {
        Box(Modifier.fillMaxSize().padding(start = columnInset)) {
            // Offsets do not enlarge a Box. Include every placed slot in the strip's
            // bounds so accessibility does not clip deeper columns to an empty ancestor.
            val lastSlot = maxOf(activeIndex + 1, displayed.maxOfOrNull { it.path.size - 1 } ?: 0)
            Box(Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(columnWidth + (columnWidth + columnGap) * lastSlot)
                .graphicsLayer { translationX = stripOffset.value }) {
                // Depth push/pop slides the full strip (desired wins the slot in unionStrip).
                // Settled Up/Down sibling preview switches keep the active columns here and
                // crossfade only the preview slot below: intentional overlap is only for
                // DIFFERENT siblings at the same preview slot, never the same level twice
                // during preview→active enter/pop (those dispose via the active-path key).
                val stripColumns = if (moving) displayed else displayed.filter { it.path.size <= activeIndex + 1 }
                stripColumns.forEach { column ->
                    key(column.path) {
                        val columnVisit = state.stack.visit
                        val columnOffset = (column.path.size - 1) * stepPixels
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
                            xOffset = columnOffset,
                            emphasis = { (1f - abs(columnOffset + stripOffset.value) / stepPixels).coerceIn(0f, 1f) },
                            onHeadingFocus = { headingFocused = it },
                            columnWidth = columnWidth,
                            contentPadding = contentPadding,
                            previewAlpha = previewAlpha,
                            parentAlpha = parentAlpha,
                             initialFocusEnabled = initialFocusEnabled,
                             activeFocusRequester = activeFocusRequester,
                             pageKeyDirection = pageKeyDirection,
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
                                    state.stack.path == column.path) {
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
                                    val column = DepthStripColumn(path, previewLevel, openingFrame(state.stack, childId))
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
                                            emphasis = { 0f },
                                            columnWidth = columnWidth,
                                            contentPadding = contentPadding,
                                            previewAlpha = previewAlpha,
                                            parentAlpha = parentAlpha,
                                            initialFocusEnabled = initialFocusEnabled,
                                            activeFocusRequester = null,
                                            pageKeyDirection = null,
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
                                                    state.stack.path == column.path) {
                                                    state.update(state.stack.viewport(items.getOrNull(index)?.id, index, offset))
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize()) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DepthStripPane(
    column: DepthStripColumn,
    activePath: List<String>,
    isCurrent: Boolean,
    visit: Long,
    stillOwning: (Long) -> Boolean,
    stackCanPop: Boolean,
    stepPixels: Int,
    xOffset: Int,
    emphasis: () -> Float,
    columnWidth: Dp,
    onHeadingFocus: (Boolean) -> Unit = {},
    contentPadding: PaddingValues,
    previewAlpha: Float,
    parentAlpha: Float,
    initialFocusEnabled: Boolean,
    activeFocusRequester: FocusRequester?,
    pageKeyDirection: ((Int) -> Int?)?,
    list: LazyListState,
    identities: List<DepthItem>,
    onFocusItem: (String, List<DepthItem>, Long) -> Unit,
    onActivate: (DepthRow, Long) -> Unit,
    focusedItemId: String?,
    viewportIndex: Int,
    viewportOffset: Int,
    onViewport: (Int, Int, Long, List<DepthItem>) -> Unit,
) {
    val visuallyActive = column.path == activePath
    val active = visuallyActive && isCurrent
    val capturedVisit = visit
    val latestActive = rememberUpdatedState(active)
    val latestStillOwning = rememberUpdatedState(stillOwning)
    val latestFocusedId = rememberUpdatedState(focusedItemId)
    val latestFocusEnabled = rememberUpdatedState(initialFocusEnabled)
    val targetAlpha = when {
        active || column.level.passiveContent != null -> 1f
        column.path.size < activePath.size -> parentAlpha
        else -> previewAlpha
    }
    val parentSlideAlpha = maxOf(parentAlpha, DepthParentSlideAlpha)
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = if (targetAlpha == parentAlpha && parentAlpha < parentSlideAlpha) keyframes {
            durationMillis = DepthSlideMillis
            parentSlideAlpha at DepthAlphaMillis
            parentSlideAlpha at DepthSlideMillis - 2 * DepthAlphaMillis
        } else tween(DepthAlphaMillis),
        label = "depth-column-alpha",
    )
    val focus = remember { mutableStateMapOf<String, FocusRequester>() }
    val scope = rememberCoroutineScope()
    val bringIntoViewSpec = rememberListEdgeBringIntoView(list, DepthListTopReserve)
    // Where focus scrolling would place a row, as the item offset from the list's top. For a row
    // the list is about to be scrolled to, [destinationIndex] says whether rows will lie before
    // it, which the list as it stands now may not tell.
    fun pivotOffset(destinationIndex: Int? = null): Int {
        val layout = list.layoutInfo
        val offset = layout.beforeContentPadding.toFloat()
        val size = (layout.visibleItemsInfo.firstOrNull()?.size ?: 0).toFloat()
        val container = layout.viewportSize.height.toFloat()
        return when {
            destinationIndex == null -> bringIntoViewSpec.calculateScrollDistance(offset, size, container)
            destinationIndex == 0 -> 0f
            else -> bringIntoViewSpec.calculateScrollDistance(offset, size, container, Float.POSITIVE_INFINITY)
        }.roundToInt()
    }
    var pageJob by remember { mutableStateOf<Job?>(null) }
    var pageTarget by remember { mutableStateOf<String?>(null) }
    DisposableEffect(active, capturedVisit, initialFocusEnabled, identities) {
        onDispose { pageJob?.cancel(); pageTarget = null }
    }
    // A level left scrolled keeps its list for the next visit, with a row partly under the top
    // fade; as a preview it starts with that row whole under its heading instead, unfaded. Back
    // to the row's start, as a list at its end has no later row to start with.
    val preview = !visuallyActive && column.path.size == activePath.size + 1
    if (preview && list.firstVisibleItemScrollOffset > 0) {
        SideEffect { list.requestScrollToItem(list.firstVisibleItemIndex) }
    }
    val targetId = if (active) focusedItemId else column.level.initialItemId
    // A pane that has just become active owns its restore target until the restore has asked for
    // it, or the viewer presses a key. Focus landing on another row before then is the system
    // re-seating focus, because the level that held it stopped being focusable, not the viewer's
    // choice; it must not replace the stored row, or the restore below gives way to it.
    var restoring by remember(capturedVisit, active, initialFocusEnabled) {
        mutableStateOf(active && initialFocusEnabled && focusedItemId != null)
    }
    // Scrolling from focus is held a little longer than the ownership above: a queued scroll for
    // a row the system re-seated is decided only after the restore has asked for its row. Read
    // live, as the animation that asks is not composition.
    val scrollHeld = remember(capturedVisit, active, initialFocusEnabled) {
        mutableStateOf(restoring && column.level.initialItemId == null)
    }
    // Where the list stands as the level becomes active, before focus the system re-seats can
    // scroll it. A list still settling when the level was left has settled by now, so this, not
    // the position saved when the child opened, is the one to return to.
    val entryViewport = remember(capturedVisit, active, initialFocusEnabled) {
        Snapshot.withoutReadObservation { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
    }
    // Focus the system moves onto a row while this pane is inactive or restoring queues a scroll
    // to that row. A queued scroll outlives the focus: it runs on, as far as the row that holds
    // focus stays in view, after the list has been put back. No row of such a pane scrolls the
    // list; the restore places the one that matters.
    val latestScrollHeld = rememberUpdatedState(scrollHeld)
    val focusScrollSpec = remember(bringIntoViewSpec) {
        object : BringIntoViewSpec by bringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (!latestActive.value || latestScrollHeld.value.value) 0f else bringIntoViewSpec.calculateScrollDistance(offset, size, containerSize)
        }
    }
    LaunchedEffect(capturedVisit, active, initialFocusEnabled, targetId, column.level.rows.map { it.item.id }) {
        try {
            if (targetId == null) return@LaunchedEffect
            val targetIndex = column.level.rows.indexOfFirst { it.item.id == targetId }
            if (targetIndex < 0) return@LaunchedEffect
            if (!active) {
                // Reused choice previews can outlive a preference change. Prepare the selected
                // row while inert, so entering never waits for an off-screen focus target.
                if (isCurrent && column.path.dropLast(1) == activePath &&
                    list.layoutInfo.visibleItemsInfo.none { it.key == targetId }) list.scrollToItem(targetIndex)
                return@LaunchedEffect
            }
            if (!initialFocusEnabled) return@LaunchedEffect
            suspend fun stillThisTarget() =
                latestStillOwning.value(capturedVisit) && latestFocusEnabled.value && latestFocusedId.value == targetId
            // Focus the system re-seats while the level that held it goes away can scroll this list
            // to its own row. A browse level returns to where it stood on becoming active, whatever
            // moved it since, before focus returns to its row; a choice level opens on its selection.
            if (restoring && column.level.initialItemId == null) {
                val (index, offset) = entryViewport
                if (index in column.level.rows.indices &&
                    (list.firstVisibleItemIndex != index || list.firstVisibleItemScrollOffset != offset)) {
                    // No frame to wait for: the scroll remeasures at once, and a pause before
                    // focus is requested lets the system settle it on the rail instead.
                    list.scrollToItem(index, offset)
                }
            }
            if (list.layoutInfo.visibleItemsInfo.none { it.key == targetId }) {
                if (!stillThisTarget()) return@LaunchedEffect
                list.scrollToItem(viewportIndex, viewportOffset)
                withFrameNanos { }
                if (!stillThisTarget()) return@LaunchedEffect
                // Place the row where focus scrolling would, not at the top under the fade.
                if (list.layoutInfo.visibleItemsInfo.none { it.key == targetId }) {
                    list.scrollToItem(targetIndex, pivotOffset(destinationIndex = targetIndex))
                }
            }
            // A row that never composes ends the restore instead of holding the pane.
            withTimeoutOrNull(RestoreFocusWaitMillis) { snapshotFlow { focus[targetId] }.first { it != null } }
                ?: return@LaunchedEffect
            if (stillThisTarget() && initialFocusEnabled) {
                focus[targetId]?.requestFocus()
                // The stored row has focus: what moves it next is the viewer.
                restoring = false
                // Scrolls queued by focus the system re-seated are decided in the frames after.
                withFrameNanos { }
            }
        } finally {
            restoring = false
            scrollHeld.value = false
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
    BrowseContentLayer(departing = !visuallyActive) { layer ->
        Column(
            Modifier
                .offset { IntOffset(xOffset, 0) }
                .width(columnWidth)
                .fillMaxHeight()
                .zIndex(if (visuallyActive) 1f else 0f)
                .graphicsLayer { this.alpha = alpha }
                .then(layer)
                .padding(top = contentPadding.calculateTopPadding())
                .then(
                    when {
                        active -> Modifier.testTag("depth-active")
                        column.path.size == activePath.size + 1 ->
                            Modifier.testTag("depth-preview").clearAndSetSemantics { }
                        else -> Modifier.clearAndSetSemantics { }
                    },
                ),
        ) {
            Box(Modifier.onFocusChanged { onHeadingFocus(it.hasFocus) }) { column.level.heading(emphasis) }
            if (column.level.passiveContent != null) {
                column.level.passiveContent.invoke()
                return@Column
            }
            if (column.level.rows.isEmpty()) {
                column.level.emptyContent?.invoke()
                return@Column
            }
            // The list runs to the bottom of the host and fades out where rows continue.
            CompositionLocalProvider(LocalBringIntoViewSpec provides focusScrollSpec) {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(top = DepthListTopReserve, bottom = ListBottomReserve),
                    modifier = Modifier.fillMaxSize()
                        .listEdgeFades(list, topFade = !preview)
                        .then(if (active && activeFocusRequester != null) Modifier.focusRequester(activeFocusRequester) else Modifier)
                        .onFocusChanged {
                            if (!it.hasFocus) { pageJob?.cancel(); pageTarget = null }
                        }
                        .onPreviewKeyEvent { event ->
                            if (!active) return@onPreviewKeyEvent false
                            val native = event.nativeKeyEvent
                            // A key is the viewer's: whatever moves focus next is theirs to keep.
                            if (native.action == KeyEvent.ACTION_DOWN) { restoring = false; scrollHeld.value = false }
                            val direction = pageKeyDirection?.invoke(native.keyCode)
                            if (direction == null) {
                                if (native.action == KeyEvent.ACTION_DOWN) { pageJob?.cancel(); pageTarget = null }
                                return@onPreviewKeyEvent false
                            }
                            if (native.action == KeyEvent.ACTION_DOWN) {
                                val current = identities.indexOfFirst { it.id == (pageTarget ?: latestFocusedId.value) }
                                val target = (current + direction * (list.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1))
                                    .coerceIn(0, identities.lastIndex)
                                val id = identities[target].id
                                pageJob?.cancel()
                                pageTarget = id
                                pageJob = scope.launch {
                                    list.animateScrollToItem(target, pivotOffset())
                                    snapshotFlow { focus[id] }.first { it != null }
                                    if (latestStillOwning.value(capturedVisit) && latestFocusEnabled.value && pageTarget == id) {
                                        focus[id]?.requestFocus()
                                        pageTarget = null
                                    }
                                }
                            }
                            true
                        }
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
                                        latestStillOwning.value(capturedVisit) &&
                                        !(restoring && row.item.id != latestFocusedId.value)) {
                                        onFocusItem(row.item.id, identities, capturedVisit)
                                    }
                                },
                        ) { if (latestActive.value) onActivate(row, capturedVisit) }
                    }
                }
            }
        }
    }
}
