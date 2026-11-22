@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.guide

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelActionLabel
import at.bernhardberger.tvhplayer.touch.channels.channelRowPrograms
import at.bernhardberger.tvhplayer.touch.ui.DetailsBackButton
import at.bernhardberger.tvhplayer.touch.ui.LocalArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.LocalBrowseMargin
import at.bernhardberger.tvhplayer.touch.ui.PartsLine
import at.bernhardberger.tvhplayer.touch.ui.WatchButton
import at.bernhardberger.tvhplayer.touch.ui.channelLogoSlot
import at.bernhardberger.tvhplayer.touch.ui.rememberStandalonePicture
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * A page or a Channels pane. The host owns a ViewModelStore for each visit, and marks the
 * entry inactive while another screen covers it. A change of channel starts a fresh visit.
 */
@Suppress("LongParameterList")
@Composable
internal fun ChannelPage(
    session: TvheadendSession,
    channels: ChannelsViewModel,
    channelId: String,
    onBack: () -> Unit,
    onDetails: (String) -> Unit,
    onRecord: ((String) -> Unit)?,
    onCancel: ((String) -> Unit)?,
    onStop: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
    initialDay: LocalDate? = null,
    initialProgramId: String? = null,
    playingId: String? = null,
    active: Boolean = true,
    acquisitionContext: CoroutineContext = Dispatchers.Default,
    mappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    behindLive: Boolean = false,
    onWatch: ((String, BrowseContextAction, String?) -> Unit)? = null,
    visitModel: ChannelPageModel? = null,
) {
    val id = ChannelId(channelId.toLong())
    val model = visitModel ?: viewModel(key = "channel-page:$channelId") {
        ChannelPageModel(session, acquisitionContext, mappingDispatcher, id, initialDay)
    }
    val scope by channels.scope.collectAsStateWithLifecycle()
    val observation by session.observation.collectAsStateWithLifecycle()
    val prepared by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val started = lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)
    val activeClock = if (active && started) activeChannelPageClock(id, observation) else null
    val clock = activeClock ?: prepared?.first?.clock
    val input = clock?.let {
        ChannelPageInput(
            scope, observation, it, Configuration(LocalConfiguration.current), AppClock.uses24Hour,
            LocalArtworkModels.current, playingId, behindLive,
        )
    }
    val context = LocalContext.current
    LifecycleStartEffect(model) { onStopOrDispose { model.pause() } }
    DisposableEffect(model, active) {
        if (!active) model.pause()
        onDispose(model::pause)
    }
    if (input != null && activeClock != null) SideEffect {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            model.submit(context, input.copy(scope = channels.scope.value, observation = session.observation.value))
        }
    }
    val shown = prepared?.takeIf { input != null && it.first.sameDays(input) }?.second ?: ChannelPageState()
    val fallbackName = scope.scope.allChannels.firstOrNull { it.id == id }?.name.orEmpty()
    key(channelId, visitModel) {
        ChannelPageContent(
            shown, onBack, onDetails, model::selectDay,
            GuideActions(
                onWatch = null, onRecord = onRecord, onRecordOptions = null, onDay = {}, onJump = {}, onTag = {},
                onCancel = onCancel, onStop = onStop,
            ),
            modifier, inPane, initialProgramId, fallbackName,
            active = active,
            visitScroll = model.scroll,
            onWatch = onWatch?.let { watch ->
                { way -> if (active) watch(channelId, way, shown.watchRecordingId) }
            },
        )
    }
}

@Composable
private fun activeChannelPageClock(id: ChannelId, observation: SessionObservation): GuideClock {
    val snapshot = observation.epgSnapshotForDisplay
    var nowSec by remember(id, snapshot) { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(id, snapshot) {
        while (isActive) {
            val now = Instant.fromEpochSeconds(System.currentTimeMillis() / 1000L)
            nowSec = now.epochSeconds
            val boundary = observation.channelRowPrograms(id, now, includeDetails = false).nextBoundary(now)
            val nextMinute = channelPageMinute(nowSec) + CHANNEL_PAGE_MINUTE_SEC
            delay((minOf(boundary, nextMinute) * 1000L - System.currentTimeMillis()).coerceAtLeast(1L))
        }
    }
    return GuideClock(nowSec, ZoneId.systemDefault())
}

@Suppress("LongParameterList")
@Composable
internal fun ChannelPageContent(
    state: ChannelPageState,
    onBack: () -> Unit,
    onDetails: (String) -> Unit,
    onDay: (LocalDate) -> Unit,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
    initialProgramId: String? = null,
    fallbackName: String = "",
    active: Boolean = true,
    onWatch: ((BrowseContextAction) -> Unit)? = null,
    visitScroll: ChannelScheduleScroll? = null,
) {
    val watchWays = if (onWatch != null) state.watchWays else emptyList()
    val colors = MaterialTheme.colorScheme
    val ground = if (inPane) colors.surfaceContainerLow else colors.surface
    Surface(modifier.fillMaxSize(), color = if (inPane) Color.Transparent else ground) {
        Column {
            ChannelPageHeading(state.channel, fallbackName, inPane, onBack)
            val capped = !inPane && guideLayout() == GuideLayout.Wide
            val pageWidth = if (capped) Modifier.widthIn(max = 680.dp) else Modifier
            val navigationInset = if (inPane) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            Box(Modifier.weight(1f).fillMaxWidth().then(navigationInset)) {
                Box(pageWidth.fillMaxSize()) {
                    when {
                        state.loading -> DayLoading()
                        state.days.isEmpty() -> NoSchedule(onWatch = null)
                        else -> ChannelSchedule(
                            state, onDetails, onDay, actions, initialProgramId, watchWays.isNotEmpty(), ground,
                            visitScroll,
                        )
                    }
                }
                if (onWatch != null) {
                    val inset = if (watchWays.size == 1) Modifier.padding(end = 16.dp, bottom = 16.dp) else Modifier
                    WatchButton(watchWays, state.channel?.id, Modifier.align(Alignment.BottomEnd).then(inset),
                        active = active, onWatch = onWatch)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelPageHeading(
    channel: GuideChannel?, fallbackName: String, inPane: Boolean, onBack: () -> Unit,
) {
    val heading: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            channel?.let {
                val logo = channelLogoSlot(
                    it.logo, it.logoTint, Modifier.size(56.dp, 32.dp), RoundedCornerShape(8.dp), it.logoModel,
                )
                logo?.invoke()
            }
            Text(
                channel?.name ?: fallbackName, Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (inPane) {
        val margin = LocalBrowseMargin.current
        // The stock bar already reserves 16 dp before a title without navigation and 8 dp after it.
        TopAppBar(
            title = heading,
            expandedHeight = 64.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(start = margin - 16.dp, end = margin - 8.dp),
        )
    } else {
        TopAppBar(title = heading, navigationIcon = {
            DetailsBackButton(stringResource(R.string.recordings_back), onBack)
        })
    }
}

/** Stock lazy states kept in memory for an open Channels visit shared by its two drawings. */
internal class ChannelScheduleScroll {
    val outer = LazyListState()
    val lists = mutableMapOf<LocalDate, LazyListState>()
    val tabsHeight = mutableIntStateOf(0)
}

@Composable
private fun rememberDayLists(
    dates: List<LocalDate>,
    visitScroll: ChannelScheduleScroll?,
): Map<LocalDate, LazyListState> {
    // Keep each day's list outside the pager's disposable items.
    if (visitScroll == null) return dates.associateWith { date -> key(date) { rememberLazyListState() } }
    SideEffect { visitScroll.lists.keys.retainAll(dates) }
    return dates.associateWith { date -> visitScroll.lists.getOrPut(date) { LazyListState() } }
}

@Composable
private fun ChannelSchedule(
    state: ChannelPageState,
    onDetails: (String) -> Unit,
    onDay: (LocalDate) -> Unit,
    actions: GuideActions,
    initialProgramId: String?,
    hasWatchButton: Boolean,
    ground: Color,
    visitScroll: ChannelScheduleScroll?,
) {
    val dates = state.days.keys.toList()
    val selected = dates.indexOf(state.selectedDay).coerceAtLeast(0)
    val pager = rememberGuidePager(dates, selected) { dates.getOrNull(it)?.let(onDay) }
    val outer = visitScroll?.outer ?: rememberLazyListState()
    val lists = rememberDayLists(dates, visitScroll)
    val margin = LocalBrowseMargin.current
    // A temporary full-height pager would clamp a restored list position near the end.
    var tabsHeight by visitScroll?.tabsHeight ?: rememberSaveable { mutableIntStateOf(0) }
    var revealed by rememberSaveable(initialProgramId) { mutableStateOf(false) }
    val onNow = state.onNow
    val collapse = rememberHeaderCollapse(outer)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = (maxHeight - with(LocalDensity.current) { tabsHeight.toDp() }).coerceAtLeast(0.dp)
        LazyColumn(Modifier.fillMaxSize().testTag("channel-page-scroll"), state = outer) {
            if (onNow != null) item(key = "on-now") {
                val padding = Modifier.padding(start = margin, end = margin, top = 12.dp, bottom = 16.dp)
                OnNow(onNow, actions, onDetails, padding)
            }
            stickyHeader(key = "days") {
                ChannelDayTabs(state.days, pager.tabIndex(selected), onDay, ground) { tabsHeight = it }
            }
            item(key = "schedule") {
                HorizontalPager(
                    pager, Modifier.fillMaxWidth().height(pageHeight).nestedScroll(collapse),
                    key = { dates.getOrNull(it)?.toString() ?: it },
                ) { page ->
                    val date = dates.getOrNull(page)
                    val programs = state.retained[date]
                    val list = lists[date]
                    if (programs == null || list == null) {
                        DayLoading()
                    } else {
                        LaunchedEffect(initialProgramId, programs, page == selected, state.coveragePending) {
                            if (!revealed && initialProgramId != null && page == selected) {
                                val index = programs.indexOfFirst { it.id == initialProgramId }
                                if (index >= 0) {
                                    if (onNow != null) outer.scrollToItem(1)
                                    list.scrollToItem(index)
                                    revealed = true
                                } else if (!state.coveragePending) {
                                    revealed = true
                                }
                            }
                        }
                        ChannelProgramRows(programs, list, onDetails, hasWatchButton)
                    }
                }
            }
        }
    }
}

/** Let the On now section leave before the day's own list starts moving. */
@Composable
private fun rememberHeaderCollapse(outer: LazyListState): NestedScrollConnection =
    remember(outer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f) Offset(0f, -outer.dispatchRawDelta(-available.y)) else Offset.Zero
        }
    }

@Composable
private fun ChannelDayTabs(
    days: Map<LocalDate, String>, selected: Int, onDay: (LocalDate) -> Unit,
    ground: Color, onHeight: (Int) -> Unit,
) {
    val margin = LocalBrowseMargin.current
    // The stock text tab's own inset counts toward the browse margin.
    val labelInset = 16.dp
    val minTarget = 48.dp
    PrimaryScrollableTabRow(
        selectedTabIndex = selected,
        edgePadding = margin - labelInset,
        minTabWidth = minTarget,
        containerColor = ground,
        modifier = Modifier.onSizeChanged { onHeight(it.height) },
    ) {
        days.entries.forEachIndexed { index, (date, label) ->
            Tab(
                selected = index == selected, onClick = { onDay(date) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(label, Modifier.widthIn(min = minTarget - labelInset * 2)) },
            )
        }
    }
}

@Composable
private fun OnNow(
    program: GuideProgram,
    actions: GuideActions,
    onDetails: (String) -> Unit,
    modifier: Modifier,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.channels_on_now), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.labelLarge, color = secondary,
                )
                Text(program.title, style = MaterialTheme.typography.headlineSmall)
                program.episode?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = secondary) }
                program.timeRange?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = secondary) }
            }
            rememberStandalonePicture(program.still, program.stillModel)?.let {
                ProgramPicture(it, Modifier.width(120.dp), recording = program.recording, strip = false)
            }
        }
        program.progress?.let {
            ProgramProgress(it, program.timeLeft, program.recording, Modifier.padding(top = 4.dp), program.watching)
        }
        program.synopsis?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgramRecordAction(program.record, program.id, actions, ruleSchedule = program.ruleSchedule)
            TextButton(onClick = { onDetails(program.id) }) {
                ChannelActionLabel(R.drawable.channels_info, stringResource(R.string.channels_details))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelProgramRows(
    programs: List<GuideProgram>, list: LazyListState, onDetails: (String) -> Unit, hasWatchButton: Boolean,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val timeStyle = MaterialTheme.typography.bodyMedium
    val measure = rememberTextMeasurer()
    val timePixels = remember(programs, measure, timeStyle) {
        programs.map { it.start }.distinct().maxOfOrNull {
            measure.measure(it, style = timeStyle, softWrap = false, maxLines = 1).size.width
        } ?: 0
    }
    val timeWidth = with(LocalDensity.current) { timePixels.toDp() }
    LazyColumn(
        Modifier.fillMaxSize().testTag("channel-program-rows"), state = list,
        contentPadding = PaddingValues(
            start = LocalBrowseMargin.current, end = LocalBrowseMargin.current,
            top = 8.dp, bottom = if (hasWatchButton) 80.dp else 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(programs, key = { _, program -> program.id }) { index, program ->
            ListItem(
                onClick = { onDetails(program.id) },
                shapes = ListItemDefaults.segmentedShapes(index, programs.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                leadingContent = {
                    Text(program.start, Modifier.width(timeWidth), style = timeStyle, color = secondary, maxLines = 1)
                },
                supportingContent = {
                    PartsLine(listOfNotNull(program.number, program.episodeName, program.length, program.genre))
                },
            ) {
                ChannelProgramTitle(program)
            }
        }
    }
}

@Composable
private fun ChannelProgramTitle(program: GuideProgram) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(program.title, Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (program.recording) {
            val recording = stringResource(SharedR.string.recordings_recording_now)
            Box(Modifier.size(8.dp).background(TvRecordingColor, CircleShape)
                .semantics { contentDescription = recording })
        }
        if (program.scheduled) ScheduledIcon(program.ruleSchedule)
    }
}
