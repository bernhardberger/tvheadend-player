package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EventId

data class ProgrammeRecordingTarget(
    val eventId: EventId,
    val channelId: ChannelId?,
    val start: Long,
    val stop: Long,
    val title: String,
    val currentSession: CurrentSessionObservation,
) {
    companion object {
        fun from(
            event: EpgEventEntry,
            currentSession: CurrentSessionObservation,
        ): ProgrammeRecordingTarget =
            ProgrammeRecordingTarget(
                eventId = event.id,
                channelId = event.channelId,
                start = event.start.epochSeconds,
                stop = event.stop.epochSeconds,
                title = event.title.orEmpty(),
                currentSession = currentSession,
            )
    }
}
