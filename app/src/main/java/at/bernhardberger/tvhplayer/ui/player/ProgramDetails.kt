package at.bernhardberger.tvhplayer.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CompactCard
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import androidx.tv.material3.WideCardContainer
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.programmeDetailsBody
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.programmeActions
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.programmeMetadata
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.AppTabRow
import at.bernhardberger.tvhplayer.ui.components.AppTabStyle
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.embeddedProgressCardBorder
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.guide.programmeActionLabel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry

/**
 * Trial: the program details layer's own navigation. A program opened from the schedule and the
 * full description are steps Back undoes before returning to the first tab's content.
 */
internal class ProgramDetailsState {
    var tab by mutableIntStateOf(0)
    var opened by mutableStateOf<EpgEventEntry?>(null)
        private set
    var readMore by mutableStateOf(false)
    /** The schedule row a program was opened from, focused again when it closes. */
    var returnRow by mutableStateOf<EventId?>(null)
    var lastAction by mutableStateOf<String?>(null)
    var tabFocused by mutableStateOf(false)
    var focusRequest by mutableIntStateOf(0)
        private set
    var selectionVersion by mutableIntStateOf(0)
        private set
    var scheduleEntry by mutableStateOf(false)
        private set

    fun focusContent() {
        tabFocused = false
        focusRequest++
    }

    fun open(event: EpgEventEntry) {
        selectionVersion++
        returnRow = event.id
        opened = event
        lastAction = null
        focusContent()
    }

    /** Undoes one inner step; false when Back should close the layer. */
    fun back(): Boolean = when {
        readMore -> { readMore = false; focusContent(); true }
        opened != null -> { selectionVersion++; opened = null; focusContent(); true }
        scheduleEntry -> false
        tab != 0 || tabFocused -> {
            tab = 0
            lastAction = null
            focusContent()
            true
        }
        else -> false
    }

    fun reset(startOnSchedule: Boolean = false) {
        selectionVersion++
        scheduleEntry = startOnSchedule
        tab = if (startOnSchedule) 1 else 0
        opened = null
        readMore = false
        returnRow = null
        lastAction = null
        tabFocused = false
        focusRequest++
    }
}

/** What a details action does, and whether it exists yet. Stubs are planned entries that do nothing. */
private class DetailsAction(
    val tag: String,
    @param:DrawableRes val icon: Int,
    val title: String,
    val onClick: () -> Unit = {},
    val record: Boolean = false,
    val busy: Boolean = false,
)

/**
 * Trial: program details over the dimmed live video. Bar tabs separate the program's Details from
 * the channel's Schedule. Details reads on the left (tile, facts, title, episode, description) and
 * acts on the right, in one column of wide buttons.
 */
@Composable
internal fun ProgramDetails(
    state: ProgramDetailsState,
    /** The program the player is on; [ProgramDetailsState.opened] replaces it while shown. */
    current: EpgEventEntry,
    schedule: List<EpgEventEntry>,
    nowSec: Long,
    channelIdentity: String,
    tile: @Composable (event: EpgEventEntry, modifier: Modifier) -> Unit,
    recordingFor: (EpgEventEntry) -> DvrEntry?,
    canModifyRecordings: Boolean,
    recordFocus: FocusRequester,
    firstFocus: FocusRequester,
    onAction: (ProgrammeAction) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onClose: () -> Unit = {},
    showWatch: Boolean = false,
) {
    val event = state.opened ?: current
    val isCurrent = event.id == current.id
    val tabs = schedule.size > 1 && !state.scheduleEntry && state.opened == null
    val tabFocus = remember { FocusRequester() }
    val tabMotion = rememberTabContentMotion(state.tab) { state.tab }
    val recording = recordingFor(event)
    // The viewport belongs to this channel's schedule visit, not its opened programme.
    val scheduleList = key(current.channelId) { rememberLazyListState() }
    // Only the rail's schedule shares its keyline; programme details retain columns 2–11.
    val railSchedule = LocalPlayerPageRailHeader.current && state.scheduleEntry && state.opened == null
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        if (!LocalPlayerPageRailHeader.current) Row(Modifier.fillMaxWidth().height(48.dp)
            .pageMotion(120..420, dy = 120.dp).testTag("details-top-row"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
        if (tabs) {
            AppTabRow(
                selectedTabIndex = state.tab,
                style = AppTabStyle.Section,
                selectedTabFocus = tabFocus,
                modifier = Modifier.testTag("details-tabs"),
                onUp = onClose,
                onMoveToContent = {
                    state.lastAction = null
                    state.returnRow = schedule.firstOrNull { airingProgress(it, nowSec) != null }?.id
                    state.focusContent()
                    true
                },
            ) {
                listOf(R.string.details_tab_details, R.string.details_tab_schedule).forEachIndexed { index, label ->
                    AppTab(
                        selected = index == state.tab,
                        label = stringResource(label),
                        onFocus = {
                            tabMotion.select(index, listOf(0, 1))
                            state.tabFocused = true
                            state.tab = index
                        },
                        canFocus = !state.readMore,
                        first = index == 0,
                        last = index == 1,
                        modifier = Modifier.testTag("details-tab-$index"),
                    )
                }
            }
        } else {
            Text(if (state.opened == null && state.scheduleEntry)
                "$channelIdentity · ${stringResource(R.string.details_tab_schedule)}"
                else stringResource(R.string.details_tab_details),
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 20.dp).testTag("details-heading").semantics { heading() })
        }
        }
        Box(Modifier.weight(1f).fillMaxWidth()
            .padding(horizontal = if (railSchedule) 0.dp else DetailsInset).pageMotion(150..480, dy = 160.dp)) {
            val detailsContent: @Composable () -> Unit = {
                if (state.readMore) FullDescription(event, nowSec, channelIdentity, recording?.state, tile)
                else DetailsTab(
                    event = event,
                    nowSec = nowSec,
                    channelIdentity = channelIdentity,
                    tile = tile,
                    recording = recording?.state,
                    actions = detailsActions(
                        event = event, nowSec = nowSec, isCurrent = isCurrent,
                        recording = recording, busy = busy,
                        canModifyRecordings = canModifyRecordings, onAction = onAction, showWatch = showWatch,
                    ),
                    recordFocus = recordFocus,
                    firstFocus = firstFocus,
                    state = state,
                    tabFocus = if (tabs) tabFocus else FocusRequester.Cancel,
                    onReadMore = { state.readMore = true },
                    onUp = if (tabs) null else onClose,
                )
            }
            when {
                state.opened != null -> detailsContent()
                state.scheduleEntry -> Schedule(state, scheduleList, schedule, nowSec, tile, recordingFor, FocusRequester.Cancel, onClose)
                else -> TabContent(tabMotion, state.tab, state = { state.tab }) { tab, _ ->
                    if (tab == 1) Schedule(state, scheduleList, schedule, nowSec, tile, recordingFor, tabFocus)
                    else detailsContent()
                }
            }
        }
    }
}

@Composable
private fun detailsActions(
    event: EpgEventEntry,
    nowSec: Long,
    isCurrent: Boolean,
    recording: DvrEntry?,
    busy: Boolean,
    canModifyRecordings: Boolean,
    onAction: (ProgrammeAction) -> Unit,
    showWatch: Boolean,
): List<DetailsAction> = buildList {
    val future = event.start.epochSeconds > nowSec
    programmeActions(event, nowSec, recording, canModifyRecordings = canModifyRecordings).forEach { action ->
        if (action == ProgrammeAction.WATCH && !showWatch || action == ProgrammeAction.WATCH_FROM_START) return@forEach
        val record = action != ProgrammeAction.WATCH
        add(DetailsAction(
            tag = if (!record) "details-watch" else if (isCurrent) "live-info-record" else "details-record",
            icon = when (action) {
                ProgrammeAction.RECORD -> R.drawable.ic_fiber_manual_record
                ProgrammeAction.STOP_RECORDING, ProgrammeAction.CANCEL_RECORDING -> R.drawable.ic_stop
                else -> R.drawable.ic_play_arrow
            },
            title = if (record && busy) stringResource(R.string.recording_request_busy_action) else programmeActionLabel(action),
            onClick = { if (!busy) onAction(action) }, record = record, busy = record && busy,
        ))
    }
    if (future) add(DetailsAction("details-remind", R.drawable.ic_event, stringResource(R.string.details_action_remind)))
    add(DetailsAction("details-record-series", R.drawable.ic_video_library, stringResource(R.string.details_action_record_series)))
    add(DetailsAction("details-other-airings", R.drawable.ic_schedule, stringResource(R.string.details_action_other_airings)))
}

@Composable
private fun DetailsTab(
    event: EpgEventEntry,
    nowSec: Long,
    channelIdentity: String,
    tile: @Composable (event: EpgEventEntry, modifier: Modifier) -> Unit,
    recording: DvrEntryState?,
    actions: List<DetailsAction>,
    recordFocus: FocusRequester,
    firstFocus: FocusRequester,
    state: ProgramDetailsState,
    tabFocus: FocusRequester,
    onReadMore: () -> Unit,
    onUp: (() -> Unit)?,
) {
    val shownActions = actions + DetailsAction("details-more-info", R.drawable.ic_info,
        stringResource(R.string.details_read_more), onClick = onReadMore)
    val tags = shownActions.map { it.tag }
    val focusTargets = remember(tags) { tags.associateWith { FocusRequester() } }
    val owner = LocalTabOwner.current
    val pageActive = LocalPlayerPageActive.current
    LaunchedEffect(event.id, state.focusRequest, tags, state.tab, pageActive) {
        if (!pageActive) return@LaunchedEffect
        if (owner?.isCurrent == false) return@LaunchedEffect
        if (state.tabFocused || state.opened == null && state.tab != 0) return@LaunchedEffect
        withFrameNanos { }
        if (owner?.isCurrent == false) return@LaunchedEffect
        focusTargets[state.lastAction]?.requestFocus() ?: firstFocus.requestFocus()
    }
    // On the 12-column grid: reading in columns 2–5 (text as wide as the tile), actions in columns 8–11.
    Row(Modifier.fillMaxSize().padding(bottom = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ProgrammeInformation(event, nowSec, channelIdentity, recording, tile, showDescription = true)
        Column(Modifier.width(DetailsActionsWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            shownActions.forEachIndexed { index, action ->
                WideButton(
                    onClick = { if (owner?.isCurrent != false) action.onClick() },
                    // TV Material 1.1's stock 240dp background and 1.1 focus scale fit this slot.
                    modifier = Modifier.tabFocus().width(240.dp).testTag(action.tag)
                        .focusRequester(focusTargets.getValue(action.tag))
                        .then(if (owner?.isCurrent != false && index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .then(if (owner?.isCurrent != false && action.record) Modifier.focusRequester(recordFocus) else Modifier)
                        .onFocusChanged { if (owner?.isCurrent != false && it.isFocused) { state.lastAction = action.tag; state.tabFocused = false } }
                         .focusProperties {
                            canFocus = state.opened != null || state.tab == 0
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            up = if (index == 0) tabFocus else focusTargets.getValue(shownActions[index - 1].tag)
                            down = if (index == shownActions.lastIndex) FocusRequester.Cancel else focusTargets.getValue(shownActions[index + 1].tag)
                        }
                        .onPreviewKeyEvent {
                            if (index != 0 || onUp == null || it.key != Key.DirectionUp) false
                            else {
                                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onUp()
                                true
                            }
                        },
                    icon = {
                        if (action.busy) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                        else Icon(painterResource(action.icon), null)
                    },
                    title = { Text(action.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    subtitle = null,
                )
            }
        }
    }
}

@Composable
private fun ProgrammeInformation(
    event: EpgEventEntry,
    nowSec: Long,
    channelIdentity: String,
    recording: DvrEntryState?,
    tile: @Composable (EpgEventEntry, Modifier) -> Unit,
    showDescription: Boolean,
) {
        Column(Modifier.width(DetailsColumnWidth).fillMaxHeight().testTag("details-information"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgrammeTile(event, nowSec, tile, Modifier.size(DetailsColumnWidth, DetailsTileHeight))
            Spacer(Modifier.height(10.dp))
            event.title?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("details-title"))
            }
            detailsSubtitle(event)?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
             Text(factsLine(event, channelIdentity), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                 modifier = Modifier.padding(top = 4.dp).testTag("details-facts"))
            when (recording) {
                DvrEntryState.RECORDING -> PlayerRecBadge(Modifier.testTag("details-recording-badge"))
                DvrEntryState.SCHEDULED -> Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("details-recording-badge")) {
                    RecordingStatusIndicator(recording, announceState = false)
                    Text(stringResource(R.string.recording_state_scheduled), style = MaterialTheme.typography.labelLarge)
                }
                else -> Unit
            }
            if (showDescription) programmeDetailsBody(event)?.let { body ->
                Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f, fill = false).padding(top = 6.dp))
            }
        }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Schedule(
    state: ProgramDetailsState,
    list: LazyListState,
    schedule: List<EpgEventEntry>,
    nowSec: Long,
    tile: @Composable (event: EpgEventEntry, modifier: Modifier) -> Unit,
    recordingFor: (EpgEventEntry) -> DvrEntry?,
    tabFocus: FocusRequester,
    onUp: (() -> Unit)? = null,
) {
    val owner = LocalTabOwner.current
    val rows = remember(schedule.map { it.id }) { schedule.associate { it.id to FocusRequester() } }
    // Restoring focus must not restart even an interrupted scroll. Native traversal enables it.
    var navigating by remember { mutableStateOf(false) }
    val bringIntoView = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (navigating) offset - (containerSize * 0.3f).coerceAtMost(containerSize - size) else 0f
        }
    }
    val typography = MaterialTheme.typography
    val rowHeight = maxOf(ScheduleTileHeight, 12.dp + with(LocalDensity.current) {
        typography.titleMedium.lineHeight.toDp() + typography.bodyMedium.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp()
    })
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bottomRoom = (maxHeight * 0.7f - rowHeight)
            .coerceAtLeast(ScheduleFocusPadding)
        val pageActive = LocalPlayerPageActive.current
        LaunchedEffect(state.focusRequest, state.tab, pageActive) {
            if (!pageActive || state.tabFocused || state.tab != 1 || owner?.isCurrent == false) return@LaunchedEffect
            val index = schedule.indexOfFirst { it.id == state.returnRow }.coerceAtLeast(0)
            // Tab entry selects Now; Back restores the already visible row without re-anchoring.
            if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
            withFrameNanos { }
            if (owner?.isCurrent != false) rows.getValue(schedule[index].id).requestFocus()
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(ScheduleGap),
            contentPadding = PaddingValues(start = ScheduleFocusPadding, end = ScheduleFocusPadding,
                top = ScheduleFocusPadding, bottom = bottomRoom),
            modifier = Modifier
                .fillMaxHeight().width(ScheduleWidth + ScheduleFocusPadding * 2)
                .offset(x = -ScheduleFocusPadding)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 32.dp.toPx()
                    if (list.canScrollBackward) drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = fade), blendMode = BlendMode.DstIn)
                    if (list.canScrollForward) drawRect(
                        Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                        blendMode = BlendMode.DstIn)
                }
                .testTag("details-schedule")) {
        itemsIndexed(schedule, key = { _, it -> it.id.value }) { index, event ->
            val airing = airingProgress(event, nowSec)
            val time = if (airing != null) stringResource(R.string.details_on_now) else formatClock(event.start.epochSeconds)
            val subtitle = detailsSubtitle(event)
            val meta = buildList {
                val minutes = ((event.stop.epochSeconds - event.start.epochSeconds) / 60).toInt()
                if (minutes > 0) add(stringResource(R.string.details_minutes, minutes))
                event.genre?.takeIf(String::isNotBlank)?.let(::add)
            }.joinToString(" · ")
            WideCardContainer(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                imageCard = { interaction ->
                    CompactCard(
                        onClick = { if (owner?.isCurrent != false) state.open(event) },
                        interactionSource = interaction,
                        border = embeddedProgressCardBorder(cardRadius = 12.dp),
                        shape = CardDefaults.shape(PlayerChromeTokens.heroShape),
                        colors = CardDefaults.compactCardColors(containerColor = Color.Transparent),
                        scale = CardDefaults.scale(focusedScale = PlayerChromeTokens.cardFocusedScale),
                        modifier = Modifier.tabFocus().size(ScheduleTileWidth, ScheduleTileHeight)
                            .focusRequester(rows.getValue(event.id)).testTag("details-schedule-${event.id.value}")
                            .onFocusChanged { if (owner?.isCurrent != false && it.isFocused) { state.returnRow = event.id; state.tabFocused = false } }
                            .focusProperties {
                                canFocus = state.tab == 1
                                up = if (index == 0) tabFocus else FocusRequester.Default
                                down = if (index == schedule.lastIndex) FocusRequester.Cancel else FocusRequester.Default
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .semantics { contentDescription = listOfNotNull(time, event.title, subtitle, meta.takeIf(String::isNotBlank)).joinToString(", ") }
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionUp || it.key == Key.DirectionDown)) navigating = true
                                if (index != 0 || it.key != Key.DirectionUp) false
                                else {
                                    if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) {
                                        if (onUp != null) onUp() else tabFocus.requestFocus()
                                    }
                                    true
                                }
                            },
                        image = {
                            ProgrammeTile(event, nowSec, tile,
                                Modifier.size(ScheduleTileWidth, ScheduleTileHeight), embeddedProgress = false, clipShape = false)
                            when (recordingFor(event)?.state) {
                                DvrEntryState.RECORDING -> PlayerRecBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
                                DvrEntryState.SCHEDULED -> RecordingStatusIndicator(DvrEntryState.SCHEDULED,
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).clearAndSetSemantics {})
                                else -> Unit
                            }
                        },
                        // 1.1's title slot has no built-in padding. Use the card label's 12dp inset.
                        title = {
                            Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                                Text(time, style = MaterialTheme.typography.titleMedium, color = Color.White,
                                    maxLines = 1, modifier = Modifier.padding(12.dp).testTag("details-schedule-time"))
                                ProgrammeProgress(event, nowSec, Modifier.align(Alignment.BottomStart).fillMaxWidth())
                            }
                        },
                    )
                },
                title = {
                    Spacer(Modifier.height(12.dp))
                    event.title?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
                    }
                },
                subtitle = {
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
                    }
                },
                description = {
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp))
                    }
                },
            )
        }
        }
        }
    }
}

@Composable
private fun FullDescription(
    event: EpgEventEntry,
    nowSec: Long,
    channelIdentity: String,
    recording: DvrEntryState?,
    tile: @Composable (EpgEventEntry, Modifier) -> Unit,
) {
    val reading = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val scrollStep = with(LocalDensity.current) { 60.dp.roundToPx() }
    val paneTitle = stringResource(R.string.details_read_more)
    val locale = LocalConfiguration.current.locales[0]
    val fields = programmeReadingFields(event, locale)
    LaunchedEffect(Unit) { withFrameNanos { }; reading.requestFocus() }
    Row(Modifier.fillMaxSize().padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(PlayerChromeTokens.gridGutter)) {
        ProgrammeInformation(event, nowSec, channelIdentity, recording, tile, showDescription = false)
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("details-full-description"),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 0.06f else 0.03f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = Border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 0.3f else 0.1f))),
        ) {
            Box(Modifier.fillMaxSize().testTag("player-info-reading")
                .semantics { this.paneTitle = paneTitle }
                .focusRequester(reading)
                .onFocusChanged { focused = it.isFocused }
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                    val delta = when (event.key) {
                        Key.DirectionDown -> scrollStep
                        Key.DirectionUp -> -scrollStep
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown) scope.launch {
                        scroll.scrollTo((scroll.value + delta).coerceIn(0, scroll.maxValue))
                    }
                    true
                }
                .focusable()
                .padding(20.dp)) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = 32.dp.toPx().coerceAtMost(size.height)
                        if (scroll.canScrollBackward) drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = fade), blendMode = BlendMode.DstIn)
                        if (scroll.canScrollForward) drawRect(
                            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                            blendMode = BlendMode.DstIn)
                    }
                    .verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    fields.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(label), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            Text(value, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    event.isNew?.let {
                        Text(stringResource(if (it) R.string.details_new_yes else R.string.details_new_no),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private val XmltvRating = Regex("""XMLTV:(.*):([^:]*)""")

/** Viewer-facing SDK metadata only: identifiers and artwork/series URIs are not programme facts. */
internal fun programmeReadingFields(event: EpgEventEntry, locale: Locale): List<Pair<Int, String>> = buildList {
    fun text(label: Int, value: String?) {
        value?.replace("\\r\\n", "\n")?.replace("\\n", "\n")?.trim()?.takeIf(String::isNotEmpty)?.let { add(label to it) }
    }
    fun number(label: Int, value: Long?) = text(label, value?.toString())
    text(R.string.details_description, event.description)
    if (event.summary?.trim() != event.description?.trim()) text(R.string.details_summary, event.summary)
    text(R.string.details_genre, event.genre)
    text(R.string.details_categories, event.categories?.filter(String::isNotBlank)?.distinct()?.joinToString(" · "))
    text(R.string.details_keywords, event.keywords?.filter(String::isNotBlank)?.distinct()?.joinToString(" · "))
    event.episode?.let {
        text(R.string.details_episode, it.onscreen)
        number(R.string.details_season, it.seasonNumber)
        number(R.string.details_seasons, it.seasonCount)
        number(R.string.details_episode_number, it.episodeNumber)
        number(R.string.details_episodes, it.episodeCount)
        number(R.string.details_part, it.partNumber)
        number(R.string.details_parts, it.partCount)
    }
    event.rating?.let {
        // TVHeadend passes XMLTV ratings through as "XMLTV:<authority>:<value>".
        val xmltv = it.label?.trim()?.let(XmltvRating::matchEntire)
        val label = xmltv?.groupValues?.get(2)?.trim() ?: it.label
        number(R.string.details_age_rating, it.age)
        if (label?.trim() != it.age?.toString()) text(R.string.details_rating, label)
        text(R.string.details_rating_authority, it.authority ?: xmltv?.groupValues?.get(1))
        text(R.string.details_rating_country, it.country)
        number(R.string.details_stars, it.stars)
    }
    number(R.string.details_copyright_year, event.copyrightYear)
    event.firstAired?.let {
        text(R.string.details_first_aired, DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(it.toEpochMilliseconds())))
    }
}

internal fun detailsRecordingResultIsCurrent(
    request: DvrMutationAction,
    state: DvrMutationAction?,
    requestedTarget: ProgrammeRecordingTarget,
    shownTarget: ProgrammeRecordingTarget?,
    infoOpen: Boolean,
    sameSelection: Boolean,
): Boolean = infoOpen && sameSelection && state === request && shownTarget == requestedTarget

@Composable
private fun ProgrammeTile(
    event: EpgEventEntry,
    nowSec: Long,
    face: @Composable (EpgEventEntry, Modifier) -> Unit,
    modifier: Modifier,
    embeddedProgress: Boolean = true,
    clipShape: Boolean = true,
) {
    Box(modifier.then(if (clipShape) Modifier.clip(PlayerChromeTokens.heroShape) else Modifier).clearAndSetSemantics {}) {
        face(event, Modifier.fillMaxSize())
        if (embeddedProgress) ProgrammeProgress(event, nowSec, Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}

@Composable
private fun ProgrammeProgress(event: EpgEventEntry, nowSec: Long, modifier: Modifier) {
    airingProgress(event, nowSec)?.let { ProgressStrip(it, height = 3.dp, modifier = modifier.testTag("details-tile-progress")) }
}

private fun airingProgress(event: EpgEventEntry, nowSec: Long): Float? {
    val start = event.start.epochSeconds
    val stop = event.stop.epochSeconds
    if (nowSec < start || nowSec >= stop || stop <= start) return null
    return ((nowSec - start).toDouble() / (stop - start)).toFloat()
}

internal fun detailsSubtitle(event: EpgEventEntry): String? =
    event.subtitle?.takeIf(String::isNotBlank)
        ?: event.episode?.onscreen?.takeIf(String::isNotBlank)
        ?: buildList {
            event.episode?.seasonNumber?.let { add("S$it") }
            event.episode?.episodeNumber?.let { add("E$it") }
        }.takeIf { it.isNotEmpty() }?.joinToString(" ")

@Composable
private fun factsLine(event: EpgEventEntry, channelIdentity: String): String = programmeFactsSegments(buildList {
    add(channelIdentity)
    add("${formatClock(event.start.epochSeconds)}–${formatClock(event.stop.epochSeconds)}")
    val minutes = ((event.stop.epochSeconds - event.start.epochSeconds) / 60).toInt()
    if (minutes > 0) add(stringResource(R.string.details_minutes, minutes))
    // The subtitle line may already be the on-screen episode; don't repeat it in the facts.
    val subtitle = detailsSubtitle(event)?.trim()
    programmeMetadata(event)?.takeIf(String::isNotBlank)?.split(" • ")
        ?.filterNot { it.trim() == subtitle }?.let(::addAll)
})

internal fun programmeFactsSegments(segments: List<String>): String =
    segments.joinToString(" · ") { it.replace(' ', '\u00a0').replace("–", "\u2060–\u2060") }

/** The next programs on a channel from the one airing now, as far as the guide knows them. */
internal fun channelSchedule(
    now: EpgEventEntry?,
    next: (after: kotlin.time.Instant) -> EpgEventEntry?,
    limit: Int = ScheduleLimit,
): List<EpgEventEntry> = buildList {
    var event = now
    while (event != null && size < limit) {
        val shown: EpgEventEntry = event
        add(shown)
        event = next(shown.start)?.takeIf { it.id != shown.id && it.start > shown.start }
    }
}

/** One grid column and its gutter. */
private val DetailsInset = 72.dp
/** Four grid columns. */
private val DetailsColumnWidth = 268.dp
private val DetailsActionsWidth = 268.dp
private val DetailsTileHeight = 151.dp
private val ScheduleWidth = 620.dp
private val ScheduleTileWidth = 124.dp
private val ScheduleTileHeight = ScheduleTileWidth * 9 / 16
private val ScheduleFocusPadding = 12.dp
private val ScheduleGap = PlayerChromeTokens.gridGutter
private const val ScheduleLimit = 16
