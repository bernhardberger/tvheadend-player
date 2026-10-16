package at.bernhardberger.tvhplayer.ui.screens.guide

import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.dialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.floorGuideWindowToHour
import at.bernhardberger.tvhplayer.core.moveGuideWindowByDays
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.player.*
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.notifications.label
import at.bernhardberger.tvhplayer.ui.screens.formatDateTime
import java.time.ZoneId
import androidx.compose.foundation.lazy.rememberLazyListState

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
            text = targetSec.formatDateTime(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
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
                Text(stringResource(R.string.previous_hour, at.bernhardberger.tvhplayer.ui.common.formatWholeHours(1, androidx.compose.ui.platform.LocalResources.current)))
            }
            OutlinedButton(
                onClick = { targetSec = nextHourSec },
                enabled = nextHourSec != targetSec,
            ) {
                Text(stringResource(R.string.next_hour, at.bernhardberger.tvhplayer.ui.common.formatWholeHours(1, androidx.compose.ui.platform.LocalResources.current)))
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
    val resultsState = rememberLazyListState()
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
        LaunchedEffect(restoreFocusTo, events) {
            val target = restoreFocusTo ?: return@LaunchedEffect
            val index = events.indexOfFirst { it.id == target }
            if (index < 0) return@LaunchedEffect
            resultsState.scrollToItem(index)
            // Restore inside the new dialog window, after its lazy result is placed.
            withFrameNanos { }
            if (resultFocusRequesters.getValue(target).requestFocus()) onFocusRestored()
        }
        LaunchedEffect(Unit) {
            if (restoreFocusTo == null) queryFocus.requestFocus()
        }
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
                state = resultsState,
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
                        formatTimeInterval(event.start.epochSeconds, event.stop.epochSeconds, withDate = true,
                            locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
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
    result !is EpgSearchResult.Failed -> R.string.epg_search_unavailable
    result.failure == TvheadendFailure.SERVER_REJECTED -> R.string.epg_search_invalid
    result.failure == TvheadendFailure.ACCESS_DENIED -> R.string.epg_search_denied
    result.failure == TvheadendFailure.CONNECTION_LIMIT -> R.string.epg_search_connection_limit
    result.failure == TvheadendFailure.NOT_SUPPORTED -> R.string.epg_search_not_supported
    else -> R.string.epg_search_unavailable
}

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
internal fun ConfirmProgrammeActionDialog(
    action: ProgrammeAction,
    programmeTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    channelName: String = "",
    dateAndTime: String = "",
    entries: at.bernhardberger.tvheadend.sdk.core.DvrEventEntries? = null,
    configurations: List<DvrConfiguration> = emptyList(),
    visibility: androidx.compose.animation.core.MutableTransitionState<Boolean> = remember {
        androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
    },
) {
    val target = programmeConfirmationTarget(entries, configurations,
        stringResource(R.string.recording_profile_default))
    val message = stringResource(if (action == ProgrammeAction.STOP_RECORDING) R.string.stop_recording_confirm_message else R.string.cancel_recording_confirm_message,
        programmeTitle, channelName, dateAndTime)
    at.bernhardberger.tvhplayer.ui.components.CentredDialog(
        visibility = visibility,
        title = stringResource(if (action == ProgrammeAction.STOP_RECORDING) R.string.stop_recording_confirm_title else R.string.cancel_recording_confirm_title),
        message = if (target == null) message else "$message ${stringResource(target.first, target.second)}",
        firstLabel = stringResource(R.string.keep_recording),
        secondLabel = programmeActionLabel(action),
        onFirst = onDismiss,
        onSecond = onConfirm,
        onDismiss = onDismiss,
        onPreviewKeyEvent = onPreviewKeyEvent,
    )
}

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun programmeConfirmationTarget(
    entries: at.bernhardberger.tvheadend.sdk.core.DvrEventEntries?,
    configurations: List<DvrConfiguration>,
    defaultProfileLabel: String,
): Pair<Int, String>? {
    if (entries == null || entries.otherActive.isEmpty()) return null
    val selected = entries.selected
    val profileId = selected.configId?.value?.takeIf(String::isNotBlank)
    if (entries.otherActive.any { it.configId?.value?.takeIf(String::isNotBlank) != profileId }) {
        val name = configurations.firstOrNull { it.id == selected.configId }?.name?.takeIf(String::isNotBlank)
            ?: defaultProfileLabel
        return R.string.recording_confirmation_profile to name
    }
    val owner = selected.owner?.takeIf(String::isNotBlank) ?: selected.creator?.takeIf(String::isNotBlank)
        ?: return null
    return R.string.recording_confirmation_owner to owner
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
