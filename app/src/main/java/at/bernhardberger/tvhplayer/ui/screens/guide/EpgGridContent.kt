package at.bernhardberger.tvhplayer.ui.screens.guide

import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.profiling.ProfileCompositionLifetime
import at.bernhardberger.tvhplayer.profiling.profileViewportItem

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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import at.bernhardberger.tvhplayer.core.shouldComposeTimelineCell
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
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
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionRecoveryAction
import at.bernhardberger.tvhplayer.core.EpgColumnDataState
import at.bernhardberger.tvhplayer.core.primaryRecoveryAction
import at.bernhardberger.tvhplayer.core.epgColumnDataState
import at.bernhardberger.tvhplayer.core.timelineEventSpan
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.screens.formatDateTime
import at.bernhardberger.tvhplayer.ui.screens.guideEmptyMessageRes
import coil3.ImageLoader

private val GuideChannelLogoWidth = 64.dp
private val GuideChannelLogoHeight = 36.dp
private val GuideChannelHeaderVerticalPadding = 6.dp
private val GuideChannelHeaderLineGap = 4.dp

@Composable
internal fun guideTimelineRowHeight(): Dp = with(LocalDensity.current) {
    val titleLine = MaterialTheme.typography.titleSmall.lineHeight.toDp()
    val numberLine = MaterialTheme.typography.labelMedium.lineHeight.toDp()
    val headerContent = maxOf(
        GuideChannelLogoHeight,
        numberLine,
        numberLine + GuideChannelHeaderLineGap + titleLine,
    ) + GuideChannelHeaderVerticalPadding * 2
    // One programme title and time line, including native ListItem content padding;
    // headers either show number/logo or number/name, never all three.
    maxOf(
        64.dp * fontScale,
        headerContent,
        titleLine + MaterialTheme.typography.bodySmall.lineHeight.toDp() + 24.dp,
    )
}

@Composable
internal fun TimelineTimeRuler(
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    modifier: Modifier = Modifier,
    earlierContent: Boolean = false,
    laterContent: Boolean = false,
) {
    val nowSec = nowSecProvider()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) {
                maxOf(28.dp, MaterialTheme.typography.labelMedium.lineHeight.toDp() + 12.dp)
            }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .width(GuideChannelWidth)
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.small,
            colors = SurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Text(
                    text = stringResource(R.string.epg_channels_heading),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Spacer(Modifier.width(GuideChannelGap))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .testTag("epg-time-ruler"),
        ) {
            val density = LocalDensity.current
            val trackWidthPx = with(density) { maxWidth.toPx() }
            Box(Modifier.fillMaxSize().guideViewportFades(0f, trackWidthPx, earlierContent, laterContent)) {
            val firstTick = Math.floorDiv(windowStartSec + 1799L, 1800L) * 1800L
            for (tick in firstTick until windowEndSec step 1800L) {
                val markerOffset = with(density) {
                    guideTimePositionPx(tick, windowStartSec, windowEndSec, trackWidthPx).toDp()
                }
                Column(
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(x = markerOffset)
                        .fillMaxHeight(),
                    horizontalAlignment = AbsoluteAlignment.Left,
                ) {
                    Text(
                        text = formatHm(tick),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
                val nowOffset = with(density) {
                    guideTimePositionPx(nowSec, windowStartSec, windowEndSec, trackWidthPx).toDp()
                }
                Box(
                    modifier = Modifier
                        .align(AbsoluteAlignment.BottomLeft)
                        .absoluteOffset(x = nowOffset - 4.dp)
                        .width(8.dp)
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
            }
            }
        }
    }
}

@Composable
internal fun TimelineChannelRow(
    channel: Channel,
    channelIndex: Int,
    number: Long?,
    selectedEventId: EventId?,
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
    visibleRowWidthPx: Int? = null,
    focusStartInset: Dp = 0.dp,
    focusEndInset: Dp = 0.dp,
) {
    val nowSec = nowSecProvider()
    // Share one zone lookup across the row's cells, refreshed on the existing clock.
    val owner = LocalTabOwner.current
    val active = owner?.isCurrent != false
    val formattingZone = remember(nowSec) { java.time.ZoneId.systemDefault() }
    if (BuildConfig.PROFILE_TRACE) ProfileCompositionLifetime("guideRow:$channelIndex")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(guideTimelineRowHeight())
            .testTag("epg-channel-row-${channel.id.value}")
            .profileViewportItem { "guideRow:$channelIndex:$windowStartSec:${events.size}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineChannelHeader(
            channel = channel,
            number = number,
            imageLoader = imageLoader,
            currentSession = currentSession,
            selected = selectedEventId != null,
        )
        Spacer(Modifier.width(GuideChannelGap))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Drawn, not clipped: a clip at the track edge would cut the focused
                // cell's library scale for the first and last programme of the row.
                .background(TvSurfaceColors.container.copy(alpha = TvPanelDenseAlpha), MaterialTheme.shapes.small),
        ) {
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val visibleTrackWidthPx = visibleRowWidthPx?.minus(
                with(density) { GuideChannelWidth.roundToPx() + GuideChannelGap.roundToPx() },
            )
            events.forEach { event ->
                val span = timelineEventSpan(
                    eventStartSec = event.start.epochSeconds,
                    eventEndSec = event.stop.epochSeconds,
                    windowStartSec = windowStartSec,
                    windowEndSec = windowEndSec,
                ) ?: return@forEach
                val isFocusTarget = selectedEventId == event.id
                val fullStart = maxWidth * span.startFraction
                val fullEnd = maxWidth * span.endFraction
                val readableWidth = (maxWidth - focusStartInset - focusEndInset).coerceAtLeast(0.dp)
                val oversized = isFocusTarget && fullEnd - fullStart > readableWidth
                // Only oversized programmes use a readable fragment. Normal cells retain
                // their full time allocation; the shared terminal viewport reserves scale.
                val start = if (oversized) maxOf(fullStart, focusStartInset) else fullStart
                val end = if (oversized) minOf(fullEnd, maxWidth - focusEndInset) else fullEnd
                val width = (end - start).coerceAtLeast(0.dp)
                if (!shouldComposeTimelineCell(
                        // Shell clipping is logical-leading; the time axis is physical.
                        startPx = with(density) {
                            (if (layoutDirection == LayoutDirection.Rtl) maxWidth - end else start).roundToPx()
                        },
                        widthPx = with(density) { width.roundToPx() },
                        visibleWidthPx = visibleTrackWidthPx,
                        isFocusTarget = isFocusTarget,
                    )
                ) return@forEach
                key(event.id) {
                    val focusRequester = remember(event.id) { FocusRequester() }
                    DisposableEffect(event.id, focusRequester, eventFocusRequesters, active) {
                        // Synchronous input must only find requesters for committed cells.
                        if (active) eventFocusRequesters[event.id] = focusRequester
                        onDispose {
                            if (eventFocusRequesters[event.id] === focusRequester) {
                                eventFocusRequesters.remove(event.id)
                            }
                        }
                    }
                    TimelineProgrammeCell(
                        event = event,
                        channel = channel,
                        recording = recordingForEvent(event.id),
                        nowSec = nowSec,
                        formattingZone = formattingZone,
                        selected = isFocusTarget,
                        focusRequester = focusRequester,
                        onFocused = { onFocused(event) },
                        onOpenDetails = { onOpenDetails(event) },
                        width = width,
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)
                            .absoluteOffset(x = start)
                            .width(width)
                            .fillMaxHeight()
                            .profileViewportItem { "guideCell:$channelIndex:${event.id.value}" },
                    )
                }
            }

            if (events.isEmpty()) {
                TimelineRowState(
                    state = epgColumnDataState(
                        visibleEvents = events,
                        windowStartSec = windowStartSec,
                        windowEndSec = windowEndSec,
                        connectionState = connectionUiState,
                        filterActive = hasCachedEvents != hasMatchingCachedEvents,
                        coveragePending = coveragePending,
                        hasCachedEvents = hasCachedEvents,
                        hasMatchingCachedEvents = hasMatchingCachedEvents,
                    ),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

        }
    }
}

@Composable
internal fun TimelineChannelHeader(
    channel: Channel,
    number: Long?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
    selected: Boolean = false,
) {
    var logoFailed by remember(channel.icon, currentSession) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .width(GuideChannelWidth)
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(number?.toString(), channel.name?.takeIf { it.isNotBlank() }).joinToString(" ")
            }
            .testTag("epg-channel-header-${channel.id.value}"),
        colors = SurfaceDefaults.colors(
            containerColor = if (selected) {
                TvSurfaceColors.containerHighest
            } else {
                TvSurfaceColors.container.copy(alpha = TvPanelDenseAlpha)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = GuideChannelHeaderVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(GuideChannelHeaderLineGap, Alignment.CenterVertically),
        ) {
            if (channel.icon != null && currentSession != null && !logoFailed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
                ) {
                    if (number != null) {
                        Text(
                            text = number.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    PiconBox(
                        imageLoader = imageLoader,
                        currentSession = currentSession,
                        piconPath = channel.icon,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = GuideChannelLogoWidth)
                            .height(GuideChannelLogoHeight)
                            .testTag("epg-channel-picon-${channel.id.value}"),
                        onError = { logoFailed = true },
                    )
                }
            } else {
                if (number != null) {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = channel.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun TimelineProgrammeCell(
    event: EpgEventEntry,
    channel: Channel,
    recording: DvrEntry?,
    nowSec: Long,
    formattingZone: java.time.ZoneId,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpenDetails: () -> Unit,
    width: Dp,
    modifier: Modifier,
) {
    val stateText = when {
        event.start.epochSeconds <= nowSec && nowSec < event.stop.epochSeconds ->
            stringResource(R.string.epg_state_now)
        event.start.epochSeconds > nowSec -> stringResource(R.string.epg_state_future)
        else -> stringResource(R.string.epg_state_past)
    }
    // Locale is observable; the row refreshes the shared zone on its normal clock.
    // No standalone time-zone listener or per-cell system lookup is needed here.
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val startSec = event.start.epochSeconds
    val stopSec = event.stop.epochSeconds
    val startDate = remember(startSec, locale, formattingZone) { startSec.formatDateTime() }
    val startTime = remember(startSec, locale, formattingZone) { formatHm(startSec) }
    val stopTime = remember(stopSec, locale, formattingZone) { formatHm(stopSec) }
    var focused by remember { mutableStateOf(false) }
    val description = stringResource(
        R.string.epg_cell_description,
        channel.name.orEmpty(),
        startDate,
        stopTime,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            supportingContent = if (width >= 90.dp) {
                {
                    Text(
                        text = "$startTime–$stopTime",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .tabFocus()
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                // The panel shape is drawn, not clipped: a clip here would cancel the
                // library focus scale the surface applies outside this modifier.
                .background(TvSurfaceColors.containerHigh.copy(alpha = TvPanelDenseAlpha), MaterialTheme.shapes.small)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .semantics { contentDescription = description },
        )
        recording?.takeIf {
            it.state == DvrEntryState.RECORDING || it.state == DvrEntryState.SCHEDULED
        }?.let {
            // The overlaid mark follows the cell's content colour, which inverts on focus.
            val contentColor = if (focused) {
                MaterialTheme.colorScheme.inverseOnSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            CompositionLocalProvider(LocalContentColor provides contentColor) { RecordingStatusIndicator(
                state = checkNotNull(it.state),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(2f)
                    .padding(6.dp),
            ) }
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
            containerColor = TvSurfaceColors.containerHigh,
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
            containerColor = TvSurfaceColors.containerHigh,
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
                modifier = Modifier.tabFocus().focusRequester(focusRequester),
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
            containerColor = TvSurfaceColors.container.copy(alpha = TvPanelDenseAlpha),
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
                    modifier = Modifier.tabFocus().focusRequester(retryFocusRequester),
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
