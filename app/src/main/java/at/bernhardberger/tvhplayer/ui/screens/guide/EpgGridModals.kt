package at.bernhardberger.tvhplayer.ui.screens.guide

import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.floorGuideWindowToHour
import at.bernhardberger.tvhplayer.core.moveGuideWindowByDays
import at.bernhardberger.tvhplayer.core.programmeActions
import at.bernhardberger.tvhplayer.core.programmeHasAired
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsLayout
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsButton
import at.bernhardberger.tvhplayer.ui.components.programmeDetailsFrame
import at.bernhardberger.tvhplayer.ui.player.ProgrammeInformation
import at.bernhardberger.tvhplayer.ui.player.FullDescription
import at.bernhardberger.tvhplayer.ui.player.ProgrammeHero
import at.bernhardberger.tvhplayer.ui.player.programmeReadingFields
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import coil3.ImageLoader
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.withFrameNanos
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.notifications.label
import at.bernhardberger.tvhplayer.ui.screens.formatDateTime
import java.time.ZoneId

@Composable
internal fun JumpToTimeDialog(
    initialSec: Long,
    bounds: GuideWindowBounds,
    zoneId: ZoneId,
    nowSecProvider: () -> Long,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit,
) {
    var targetSec by remember(initialSec, bounds) {
        mutableLongStateOf(bounds.constrain(initialSec))
    }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    val previousDaySec = moveGuideWindowByDays(targetSec, -1, bounds, zoneId)
    val nextDaySec = moveGuideWindowByDays(targetSec, 1, bounds, zoneId)
    val previousHourSec = bounds.constrain(targetSec - 3600L)
    val nextHourSec = bounds.constrain(targetSec + 3600L)
    DialogScrim(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.epg_jump_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = targetSec.formatDateTime(),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { targetSec = previousDaySec },
                enabled = previousDaySec != targetSec,
            ) {
                Text(stringResource(R.string.previous_day))
            }
            OutlinedButton(
                onClick = { targetSec = nextDaySec },
                enabled = nextDaySec != targetSec,
            ) {
                Text(stringResource(R.string.next_day))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { targetSec = previousHourSec },
                enabled = previousHourSec != targetSec,
            ) {
                Text(stringResource(R.string.previous_hour))
            }
            OutlinedButton(
                onClick = { targetSec = nextHourSec },
                enabled = nextHourSec != targetSec,
            ) {
                Text(stringResource(R.string.next_hour))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    onJump(floorGuideWindowToHour(nowSecProvider(), zoneId))
                },
                modifier = Modifier.focusRequester(initialFocus),
            ) {
                Text(stringResource(R.string.now))
            }
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
            Button(onClick = { onJump(floorGuideWindowToHour(targetSec, zoneId)) }) {
                Text(stringResource(R.string.epg_jump_action))
            }
        }
    }
}

@Composable
internal fun EpgSearchDialog(
    contentPadding: PaddingValues,
    query: String,
    result: EpgSearchResult?,
    searching: Boolean,
    searchEnabled: Boolean,
    channelName: (ChannelId?) -> String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetails: (EpgEventEntry) -> Unit,
    onDismiss: () -> Unit,
    restoreFocusTo: EventId? = null,
    onFocusRestored: () -> Unit = {},
) {
    val queryFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var editingId by remember { mutableStateOf<String?>(null) }
    var searchWasLastFocused by remember { mutableStateOf(false) }
    val events = (result as? EpgSearchResult.Available)?.events.orEmpty()
    val resultFocusRequesters = remember(events) {
        events.associate { event -> event.id to FocusRequester() }
    }
    val canSearch = searchEnabled && query.isNotBlank() && !searching

    LaunchedEffect(Unit) {
        if (restoreFocusTo == null) queryFocus.requestFocus()
    }
    LaunchedEffect(restoreFocusTo, events) {
        val requester = restoreFocusTo?.let(resultFocusRequesters::get) ?: return@LaunchedEffect
        if (requester.requestFocus()) onFocusRestored()
    }
    LaunchedEffect(canSearch) {
        if (!canSearch && searchWasLastFocused) closeFocus.requestFocus()
    }
    BackHandler(enabled = editingId == null, onBack = onDismiss)
    DialogScrim(
        onDismissRequest = {
            if (editingId != null) editingId = null else onDismiss()
        },
        wide = true,
        contentPadding = contentPadding,
    ) {
        Text(
            text = stringResource(R.string.epg_search_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        TvOutlinedTextField(
            id = "epg-search-query",
            editingId = editingId,
            setEditingId = { editingId = it },
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.epg_search_query)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(queryFocus)
                .focusProperties { down = if (canSearch) searchFocus else closeFocus }
                .onFocusChanged { if (it.isFocused) searchWasLastFocused = false }
                .testTag("epg-search-field"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSearch,
                enabled = canSearch,
                modifier = Modifier
                    .focusRequester(searchFocus)
                    .onFocusChanged { if (it.isFocused) searchWasLastFocused = true }
                    .focusProperties {
                        up = queryFocus
                        right = closeFocus
                        resultFocusRequesters[events.firstOrNull()?.id]?.let { down = it }
                    }
                    .testTag("epg-search-submit"),
            ) {
                Text(stringResource(R.string.epg_search_action))
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(closeFocus)
                    .onFocusChanged { if (it.isFocused) searchWasLastFocused = false }
                    .focusProperties {
                        up = queryFocus
                        left = if (canSearch) searchFocus else queryFocus
                        resultFocusRequesters[events.firstOrNull()?.id]?.let { down = it }
                    }
                    .testTag("epg-search-close"),
            ) {
                Text(stringResource(R.string.close))
            }
        }

        val messageRes = epgSearchMessageRes(
            result = result,
            searching = searching,
            searchEnabled = searchEnabled,
        )
        if (messageRes != null) {
            Text(
                text = stringResource(messageRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (events.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusGroup()
                    .testTag("epg-search-results"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { event -> event.id.value }) { event ->
                    val title = event.title?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.epg_search_untitled)
                    val channel = channelName(event.channelId)
                        ?: stringResource(R.string.epg_search_unknown_channel)
                    val schedule = stringResource(
                        R.string.epg_search_result_schedule,
                        channel,
                        event.start.epochSeconds.formatDateTime(),
                        formatHm(event.stop.epochSeconds),
                    )
                    val description = stringResource(
                        R.string.epg_search_result_description,
                        title,
                        schedule,
                    )
                    ListItem(
                        selected = false,
                        onClick = { onOpenDetails(event) },
                        headlineContent = {
                            Text(
                                text = title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = schedule,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(resultFocusRequesters.getValue(event.id))
                            .onFocusChanged {
                                if (it.isFocused) searchWasLastFocused = false
                            }
                            .focusProperties {
                                if (event.id == events.first().id) {
                                    up = if (canSearch) searchFocus else closeFocus
                                }
                            }
                            .testTag("epg-search-result-${event.id.value}")
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

private fun epgSearchMessageRes(
    result: EpgSearchResult?,
    searching: Boolean,
    searchEnabled: Boolean,
): Int? = when {
    searching -> R.string.epg_search_searching
    !searchEnabled -> R.string.epg_search_connection_unavailable
    result == null -> R.string.epg_search_hint
    result is EpgSearchResult.Available -> if (result.events.isEmpty()) {
        R.string.epg_search_empty
    } else {
        null
    }
    result === EpgSearchResult.InvalidQuery -> R.string.epg_search_invalid
    result === EpgSearchResult.AccessDenied -> R.string.epg_search_denied
    result === EpgSearchResult.ConnectionLimit -> R.string.epg_search_connection_limit
    result === EpgSearchResult.NotSupported -> R.string.epg_search_not_supported
    result === EpgSearchResult.ObservationExpired ||
        result === EpgSearchResult.Timeout ||
        result === EpgSearchResult.TransportUnavailable ||
        result === EpgSearchResult.ConnectionChanged -> R.string.epg_search_unavailable
    else -> R.string.epg_search_unavailable
}

@Composable
internal fun ProgrammeDetailsPanel(
    event: EpgEventEntry,
    channel: Channel?,
    recording: DvrEntry?,
    nowSecProvider: () -> Long,
    canModifyRecordings: Boolean,
    onAction: (ProgrammeAction) -> Unit,
    onClose: () -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    liveProgrammeActions: Boolean = true,
    notices: @Composable () -> Unit = {},
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
) {
    val nowSec = nowSecProvider()
    val actions = programmeActions(
        event,
        nowSec,
        recording,
        // Stream-coordinate history cannot establish a programme-time target.
        serverTimeshiftCoversEvent = false,
        canModifyRecordings = canModifyRecordings,
    ).filter { liveProgrammeActions || it !in setOf(ProgrammeAction.WATCH, ProgrammeAction.RECORD) }
    val actionFocus = remember { ProgrammeAction.entries.associateWith { FocusRequester() } }
    val panelFocus = remember { FocusRequester() }
    val moreFocus = remember { FocusRequester() }
    var readMore by remember(event.id) { mutableStateOf(false) }
    val hasMore = programmeReadingFields(event, LocalConfiguration.current.locales[0]).isNotEmpty() || event.isNew != null
    var focusedAction by remember(event.id) { mutableStateOf<ProgrammeAction?>(null) }
    var focusedMore by remember(event.id) { mutableStateOf(false) }
    var panelFocused by remember(event.id) { mutableStateOf(false) }
    val noButtons = actions.isEmpty() && !hasMore
    val primaryFocus = actions.firstOrNull()?.let(actionFocus::get) ?: if (hasMore) moreFocus else panelFocus
    // Capture before apply can automatically move focus away from a removed action.
    val removedFocusTarget = if (
        focusedAction != null && focusedAction !in actions || focusedMore && !hasMore || panelFocused && !noButtons
    ) {
        primaryFocus
    } else null
    Dialog(
        onDismissRequest = { if (readMore) readMore = false else onClose() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = TvScrimModalAlpha))
            .onPreviewKeyEvent(onPreviewKeyEvent)
            .testTag("programme-details-panel")
            .then(if (noButtons && !readMore) Modifier
                .focusRequester(panelFocus)
                .onFocusChanged { panelFocused = it.isFocused }
                .focusProperties {
                    up = FocusRequester.Cancel; down = FocusRequester.Cancel
                    left = FocusRequester.Cancel; right = FocusRequester.Cancel
                }
                .focusable() else Modifier.focusGroup())) {
            val tile: @Composable (EpgEventEntry, Modifier) -> Unit = { shown, modifier ->
                ProgrammeHero(shown.image, shown.channelId ?: ChannelId(0), channel?.number?.toString().orEmpty(), channel?.icon,
                    imageLoader, currentSession, modifier)
            }
            val status: @Composable () -> Unit = {
                recording?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecordingStatusIndicator(state = it.state ?: DvrEntryState.UNKNOWN)
                        Text(
                            text = stringResource(
                                R.string.recording_status,
                                dvrStateLabel(it.state),
                            ),
                            color = when (it.state) {
                                DvrEntryState.SCHEDULED,
                                DvrEntryState.RECORDING -> TvRecordingColor
                                DvrEntryState.MISSED,
                                DvrEntryState.INVALID,
                                DvrEntryState.RECORDING_ERROR,
                                DvrEntryState.COMPLETED_ERROR,
                                DvrEntryState.FILE_MISSING -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    it.subscriptionError?.label()?.let { reason ->
                        Text(
                            text = stringResource(R.string.recording_failure_reason, reason),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (actions.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (programmeHasAired(event, nowSec)) {
                                R.string.epg_already_aired
                            } else {
                                R.string.epg_no_actions
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.fillMaxSize().programmeDetailsFrame()) {
                if (readMore) FullDescription(event, nowSec, channel?.name.orEmpty(), recording?.state, tile, status)
                else {
                    LaunchedEffect(event.id) {
                        withFrameNanos { }
                        (if (focusedMore && hasMore) moreFocus else focusedAction?.takeIf { it in actions }?.let(actionFocus::get)
                            ?: primaryFocus).requestFocus()
                    }
                    LaunchedEffect(actions, hasMore) {
                        if (noButtons) panelFocus.requestFocus() else removedFocusTarget?.requestFocus()
                    }
                    ProgrammeDetailsLayout(reading = {
                        ProgrammeInformation(event, nowSec, channel?.name.orEmpty(), recording?.state, tile, true, status)
                    }) {
                        actions.forEachIndexed { index, action ->
                            key(action) {
                                ProgrammeDetailsButton(
                                    title = programmeActionLabel(action),
                                    onClick = { onAction(action) },
                                    modifier = Modifier.focusRequester(actionFocus.getValue(action))
                                        .onFocusChanged {
                                            if (it.isFocused) { focusedAction = action; focusedMore = false; panelFocused = false }
                                        }
                                        .focusProperties {
                                            up = if (index == 0) FocusRequester.Cancel else actionFocus.getValue(actions[index - 1])
                                            down = if (index == actions.lastIndex) {
                                                if (hasMore) moreFocus else FocusRequester.Cancel
                                            } else actionFocus.getValue(actions[index + 1])
                                        },
                                    icon = { Icon(painterResource(when (action) {
                                        ProgrammeAction.RECORD -> R.drawable.ic_fiber_manual_record
                                        ProgrammeAction.CANCEL_RECORDING, ProgrammeAction.STOP_RECORDING -> R.drawable.ic_stop
                                        else -> R.drawable.ic_play_arrow
                                    }), null) },
                                )
                            }
                        }
                        if (hasMore) ProgrammeDetailsButton(
                            title = stringResource(R.string.details_read_more),
                            onClick = { readMore = true },
                            modifier = Modifier.focusRequester(moreFocus)
                                .onFocusChanged { if (it.isFocused) { focusedAction = null; focusedMore = true; panelFocused = false } }
                                .focusProperties {
                                    up = actions.lastOrNull()?.let(actionFocus::get) ?: FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                },
                            icon = { Icon(painterResource(R.drawable.ic_info), null) },
                        )
                    }
                }
            }
            notices()
        }
    }
}

@Composable
internal fun DvrConfigDialog(
    configs: List<DvrConfiguration>,
    onDismiss: () -> Unit,
    onSelect: (DvrConfiguration) -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(configs) { initialFocus.requestFocus() }
    DialogScrim(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.recording_config_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.recording_config_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        configs.forEachIndexed { index, config ->
            OutlinedButton(
                onClick = { onSelect(config) },
                modifier = if (index == 0) {
                    Modifier.focusRequester(initialFocus)
                } else {
                    Modifier
                },
            ) {
                Column {
                    Text(config.name)
                    config.comment.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        OutlinedButton(onClick = onDismiss) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
internal fun ConfirmProgrammeActionDialog(
    action: ProgrammeAction,
    programmeTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
) {
    val safeFocus = remember { FocusRequester() }
    LaunchedEffect(action) { safeFocus.requestFocus() }
    DialogScrim(onDismissRequest = onDismiss, onPreviewKeyEvent = onPreviewKeyEvent) {
        Text(
            text = stringResource(
                when (action) {
                    ProgrammeAction.RECORD -> R.string.record_confirm_title
                    ProgrammeAction.STOP_RECORDING -> R.string.stop_recording_confirm_title
                    else -> R.string.cancel_recording_confirm_title
                },
                programmeTitle,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(safeFocus),
            ) {
                Text(stringResource(R.string.back))
            }
            Button(onClick = onConfirm) {
                Icon(
                    painter = painterResource(
                        if (action == ProgrammeAction.RECORD) {
                            R.drawable.ic_fiber_manual_record
                        } else {
                            R.drawable.ic_stop
                        },
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        when (action) {
                            ProgrammeAction.RECORD -> R.string.record
                            ProgrammeAction.STOP_RECORDING -> R.string.stop_recording
                            else -> R.string.cancel_recording
                        }
                    )
                )
            }
        }
    }
}

@Composable
internal fun programmeActionLabel(action: ProgrammeAction): String = stringResource(
    when (action) {
        ProgrammeAction.WATCH -> R.string.watch
        ProgrammeAction.RECORD -> R.string.record
        ProgrammeAction.CANCEL_RECORDING -> R.string.cancel_recording
        ProgrammeAction.STOP_RECORDING -> R.string.stop_recording
        ProgrammeAction.WATCH_FROM_START -> R.string.watch_from_start
    }
)

@Composable
private fun dvrStateLabel(state: DvrEntryState?): String = stringResource(
    when (state) {
        DvrEntryState.SCHEDULED -> R.string.recording_state_scheduled
        DvrEntryState.RECORDING -> R.string.recording_state_recording
        DvrEntryState.COMPLETED -> R.string.recording_state_completed
        DvrEntryState.MISSED,
        DvrEntryState.INVALID -> R.string.recording_state_cancelled
        DvrEntryState.RECORDING_ERROR,
        DvrEntryState.COMPLETED_ERROR,
        DvrEntryState.FILE_MISSING -> R.string.recording_state_failed
        DvrEntryState.UNKNOWN,
        null -> R.string.recording_state_unknown
    }
)

@Composable
private fun DialogScrim(
    onDismissRequest: () -> Unit,
    wide: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = TvScrimModalAlpha))
                .onPreviewKeyEvent(onPreviewKeyEvent)
                .focusGroup()
                .then(if (wide) Modifier.padding(contentPadding) else Modifier),
            contentAlignment = if (wide) Alignment.CenterEnd else Alignment.Center,
        ) {
            Surface(
                modifier = if (wide) {
                    Modifier.width(680.dp).fillMaxHeight()
                } else {
                    Modifier.width(720.dp)
                },
                shape = MaterialTheme.shapes.large,
                colors = SurfaceDefaults.colors(
                    containerColor = TvSurfaceColors.containerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
            overlay()
        }
    }
}
