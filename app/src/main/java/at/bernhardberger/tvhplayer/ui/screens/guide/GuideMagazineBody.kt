package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.TimelineEpgEventIndex
import at.bernhardberger.tvhplayer.core.recordingWasRemoved
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession

/** Uses exactly the Guide's prepared window; empty columns retain their channel head. */
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
internal fun GuideMagazineBody(
    channels: List<Channel>,
    index: TimelineEpgEventIndex,
    windowStart: Long,
    windowEnd: Long,
    observation: SessionObservation,
    scopeObservation: SessionObservation?,
    nowSec: Long,
    modifier: Modifier,
    gridFocus: FocusRequester,
    headerFocus: FocusRequester,
    navigationFocus: FocusRequester,
    focusRequesters: MutableMap<EventId, FocusRequester>,
    autoFocus: Boolean,
    onFocused: (Int, EpgEvent) -> Unit,
    onOpen: (Int, EpgEvent, Boolean) -> Unit,
    onVisible: (List<Int>) -> Unit,
) {
    // Timeline preparation includes historical lead-in; a current window starts at the on-air entry.
    val entryStart = if (nowSec in windowStart until windowEnd) nowSec else windowStart
    val columns = remember(channels, index, entryStart, observation) {
        channels.map { channel ->
            MagazineColumn(channel, index.visibleEventsByChannel[channel.id].orEmpty()
                .filter { it.stop.epochSeconds > entryStart }
                .sortedBy { it.start }
                .map { event ->
                    MagazineEntry(event, observation.dvrEntryForEvent(event)?.takeUnless(::recordingWasRemoved)?.state)
                })
        }
    }
    ProvideArtworkSession(observation.currentSession, scopeObservation) {
        key(windowStart, channels.map { it.id }) {
            GuideMagazine(
                columns, nowSec,
                onOpenProgram = { event -> onOpen(channels.indexOfFirst { it.id == event.channelId }, event, false) },
                modifier = modifier.testTag("epg-magazine-content"), initialFocus = gridFocus,
                headerFocus = headerFocus, navigationFocus = navigationFocus,
                eventFocusRequesters = focusRequesters, autoFocus = autoFocus,
                onProgramFocused = onFocused,
                onContextMenu = { c, event -> onOpen(c, event, true) },
                onColumnsVisible = onVisible,
            )
        }
    }
}
