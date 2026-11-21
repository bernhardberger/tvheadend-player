package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import kotlin.time.Instant

data class ChannelNowStatus(
    val playingNow: Boolean,
    val channelRecording: Boolean,
)

/** Running entries in their unpadded window, including entries without a complete window. */
fun activeRecordingChannelIds(entries: List<DvrEntry>, nowSec: Long): Set<ChannelId> {
    val now = Instant.fromEpochSeconds(nowSec)
    return entries.asSequence()
        .filter { entry ->
            val start = entry.start
            val stop = entry.stop
            recordingIsRunning(entry) && (start == null || stop == null || (now >= start && now < stop))
        }
        .mapNotNull { it.channelId }
        .toSet()
}

fun showChannelRecordingMark(channelRecording: Boolean, programmeMarked: Boolean): Boolean =
    channelRecording && !programmeMarked

/** Reject stale links without changing the SDK's selection order or choosing a replacement. */
@OptIn(ExperimentalTvheadendApi::class)
fun SessionObservation.linkedRecordingForEvent(event: EpgEvent?): DvrEntry? {
    if (event == null) return null
    return dvrEntryForEvent(event)?.takeIf { entry ->
        (entry.eventId?.let { it.value == event.id.value } != false) &&
            (entry.channelId == null || entry.channelId == event.channelId)
    }
}

/** Padding follows the entry selected for this event, not the channel's airtime. */
fun SessionObservation.runningRecordingForEvent(event: EpgEvent?): DvrEntry? =
    linkedRecordingForEvent(event)?.takeIf(::recordingIsRunning)

fun channelNowStatus(
    channelId: ChannelId,
    playingChannelId: ChannelId?,
    recordingChannelIds: Set<ChannelId>,
): ChannelNowStatus = ChannelNowStatus(
    playingNow = channelId == playingChannelId,
    channelRecording = channelId in recordingChannelIds,
)
