package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionRecoveryAction
import at.bernhardberger.tvhplayer.core.EpgColumnDataState
import at.bernhardberger.tvhplayer.core.EpgFocusDirection
import at.bernhardberger.tvhplayer.core.primaryRecoveryAction
import at.bernhardberger.tvhplayer.core.EpgFocusTarget
import at.bernhardberger.tvhplayer.core.epgColumnDataState
import at.bernhardberger.tvhplayer.core.timelineEventSpan
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.ChannelTitle
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.screens.formatDateTime
import at.bernhardberger.tvhplayer.ui.screens.guideEmptyMessageRes
import coil3.ImageLoader

private val CHANNEL_HEADER_WIDTH = 190.dp
private val TIMELINE_ROW_HEIGHT = 76.dp

@Composable
internal fun TimelineTimeRuler(
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val nowSec = nowSecProvider()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .width(CHANNEL_HEADER_WIDTH)
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.small,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Text(
                    text = stringResource(R.string.epg_channels_heading),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha)),
        ) {
            repeat(6) { markerIndex ->
                val markerOffset = maxWidth * (markerIndex / 6f)
                Column(
                    modifier = Modifier
                        .offset(x = markerOffset)
                        .fillMaxHeight(),
                ) {
                    Text(
                        text = formatHm(windowStartSec + markerIndex * 30 * 60L),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTrackAlpha)
                            )
                    )
                }
            }
            if (nowSec in windowStartSec until windowEndSec) {
                val nowFraction = (nowSec - windowStartSec).toFloat() /
                    (windowEndSec - windowStartSec).coerceAtLeast(1L)
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * nowFraction - 5.dp)
                        .width(10.dp)
                        .height(10.dp)
                        .align(Alignment.TopStart)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
                )
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * nowFraction)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
internal fun TimelineChannelRow(
    channel: Channel,
    channelIndex: Int,
    allChannels: List<Channel>,
    selectedTarget: EpgFocusTarget?,
    eventFocusRequesters: MutableMap<EventId, FocusRequester>,
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    events: List<EpgEventEntry>,
    hasCachedEvents: Boolean,
    hasMatchingCachedEvents: Boolean,
    connectionUiState: ConnectionUiState,
    coveragePending: Boolean,
    recordingForEvent: (EventId) -> DvrEntry?,
    onFocused: (EpgEventEntry) -> Unit,
    onOpenDetails: (EpgEventEntry) -> Unit,
    onMoveFocus: (EpgFocusDirection) -> Boolean,
) {
    val nowSec = nowSecProvider()
    val state = epgColumnDataState(
        visibleEvents = events,
        windowStartSec = windowStartSec,
        windowEndSec = windowEndSec,
        connectionState = connectionUiState,
        filterActive = hasCachedEvents != hasMatchingCachedEvents,
        coveragePending = coveragePending,
        hasCachedEvents = hasCachedEvents,
        hasMatchingCachedEvents = hasMatchingCachedEvents,
    )
    val orderedIds = remember(allChannels) { allChannels.map { it.id } }
    val numbers = remember(allChannels) {
        allChannels.associate { it.id to it.number?.toInt() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TIMELINE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineChannelHeader(
            channel = channel,
            number = ChannelNavigation.numberForId(orderedIds, numbers, channel.id),
            imageLoader = imageLoader,
            currentSession = currentSession,
            selected = selectedTarget?.channelIndex == channelIndex,
        )
        Spacer(Modifier.width(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha)),
        ) {
            events.forEach { event ->
                val span = timelineEventSpan(
                    eventStartSec = event.start.epochSeconds,
                    eventEndSec = event.stop.epochSeconds,
                    windowStartSec = windowStartSec,
                    windowEndSec = windowEndSec,
                ) ?: return@forEach
                val start = maxWidth * span.startFraction
                val width = maxWidth * (span.endFraction - span.startFraction)
                val focusRequester = remember(event.id) {
                    eventFocusRequesters.getOrPut(event.id) { FocusRequester() }
                }
                TimelineProgrammeCell(
                    event = event,
                    channel = channel,
                    recording = recordingForEvent(event.id),
                    nowSec = nowSec,
                    selected = selectedTarget?.channelIndex == channelIndex &&
                        selectedTarget.eventId == event.id,
                    focusRequester = focusRequester,
                    onFocused = { onFocused(event) },
                    onOpenDetails = { onOpenDetails(event) },
                    onMoveFocus = onMoveFocus,
                    width = width,
                    modifier = Modifier
                        .offset(x = start)
                        .width(width)
                        .fillMaxHeight(),
                )
            }

            if (events.isEmpty()) {
                TimelineRowState(
                    state = state,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (nowSec in windowStartSec until windowEndSec) {
                val nowFraction = (nowSec - windowStartSec).toFloat() /
                    (windowEndSec - windowStartSec)
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * nowFraction)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
internal fun TimelineChannelHeader(
    channel: Channel,
    number: Int?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
    selected: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .width(CHANNEL_HEADER_WIDTH)
            .fillMaxHeight(),
        colors = SurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PiconBox(
                imageLoader = imageLoader,
                currentSession = currentSession,
                piconPath = channel.icon,
                modifier = Modifier
                    .width(44.dp)
                    .height(30.dp),
            )
            Spacer(Modifier.width(TvSpacing8))
            ChannelTitle(
                number = number,
                name = channel.name.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun TimelineProgrammeCell(
    event: EpgEventEntry,
    channel: Channel,
    recording: DvrEntry?,
    nowSec: Long,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpenDetails: () -> Unit,
    onMoveFocus: (EpgFocusDirection) -> Boolean,
    width: Dp,
    modifier: Modifier,
) {
    val stateText = when {
        event.start.epochSeconds <= nowSec && nowSec < event.stop.epochSeconds ->
            stringResource(R.string.epg_state_now)
        event.start.epochSeconds > nowSec -> stringResource(R.string.epg_state_future)
        else -> stringResource(R.string.epg_state_past)
    }
    val description = stringResource(
        R.string.epg_cell_description,
        channel.name.orEmpty(),
        event.start.epochSeconds.formatDateTime(),
        formatHm(event.stop.epochSeconds),
        event.title.orEmpty(),
        stateText,
    )

    Box(modifier = modifier) {
        ListItem(
            selected = selected,
            onClick = onOpenDetails,
            headlineContent = {
                Text(
                    // Always render a label so no focusable cell is visually blank.
                    text = event.title.orEmpty(),
                    maxLines = if (width >= 140.dp) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            supportingContent = if (width >= 90.dp) {
                {
                    Text(
                        text = "${formatHm(event.start.epochSeconds)}–${formatHm(event.stop.epochSeconds)}",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                null
            },
            scale = ListItemDefaults.scale(
                focusedScale = 1f,
                focusedSelectedScale = 1f,
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 1.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TvPanelDenseAlpha))
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.DirectionUp -> onMoveFocus(EpgFocusDirection.UP)
                        Key.DirectionDown -> onMoveFocus(EpgFocusDirection.DOWN)
                        Key.DirectionLeft -> onMoveFocus(EpgFocusDirection.LEFT)
                        Key.DirectionRight -> onMoveFocus(EpgFocusDirection.RIGHT)
                        else -> false
                    }
                }
                .semantics { contentDescription = description },
        )
        recording?.takeIf {
            it.state == DvrEntryState.RECORDING || it.state == DvrEntryState.SCHEDULED
        }?.let {
            RecordingStatusIndicator(
                state = checkNotNull(it.state),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(2f)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun TimelineRowState(
    state: EpgColumnDataState,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(
        when (state) {
            EpgColumnDataState.READY -> R.string.epg_state_ready
            EpgColumnDataState.LOADING -> R.string.epg_loading
            EpgColumnDataState.NO_DATA -> R.string.epg_no_data
            EpgColumnDataState.EMPTY_DAY -> R.string.epg_empty_day
            EpgColumnDataState.PARTIAL -> R.string.epg_partial
            EpgColumnDataState.STALE -> R.string.epg_stale
            EpgColumnDataState.PERMISSION_DENIED -> R.string.epg_permission_denied
            EpgColumnDataState.RECONNECTING -> R.string.epg_reconnecting
            EpgColumnDataState.SERVER_FAILURE -> R.string.epg_server_failure
            EpgColumnDataState.FILTER_EMPTY -> R.string.epg_filter_empty
        }
    )
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun GuidePassiveNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun GuideConnectionRecovery(
    needsSettings: Boolean,
    permissionDenied: Boolean,
    focusRequester: FocusRequester,
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (permissionDenied) {
                        R.string.epg_permission_denied
                    } else if (needsSettings) {
                        R.string.connection_configuration_required
                    } else {
                        R.string.epg_server_failure
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = if (needsSettings) onOpenConnectionSettings else onRetry,
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                Text(
                    stringResource(
                        if (needsSettings) {
                            R.string.connection_settings_short
                        } else {
                            R.string.retry
                        },
                    ),
                )
            }
        }
    }
}

@Composable
internal fun GuideEmptyState(
    isEmptyTag: Boolean,
    connectionUiState: ConnectionUiState,
    channelCatalogCurrent: Boolean,
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    retryFocusRequester: FocusRequester,
) {
    val permissionDenied = connectionUiState is ConnectionUiState.Error &&
        connectionUiState.kind == ConnectionFailureKind.PERMISSION_DENIED
    val recoveryAction = connectionUiState.primaryRecoveryAction()
    val message = stringResource(
        guideEmptyMessageRes(
            isEmptyTag = isEmptyTag,
            connectionUiState = connectionUiState,
            channelCatalogCurrent = channelCatalogCurrent,
        ),
    )
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("guide-empty-state"),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, style = MaterialTheme.typography.titleLarge)
            if (recoveryAction != ConnectionRecoveryAction.NONE) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = if (recoveryAction == ConnectionRecoveryAction.SETTINGS) {
                        onOpenConnectionSettings
                    } else {
                        onRetry
                    },
                    modifier = Modifier.focusRequester(retryFocusRequester),
                ) {
                    Text(
                        stringResource(
                            if (recoveryAction == ConnectionRecoveryAction.SETTINGS) {
                                R.string.connection_settings_short
                            } else {
                                R.string.retry
                            },
                        ),
                    )
                }
            }
        }
    }
}
