package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.core.EpgEventEntry

data class ProgrammeRecordingTarget(
    val eventId: Int,
    val channelId: Int,
    val start: Long,
    val stop: Long,
    val title: String,
) {
    companion object {
        fun from(event: EpgEventEntry): ProgrammeRecordingTarget =
            ProgrammeRecordingTarget(
                eventId = event.eventId,
                channelId = event.channelId,
                start = event.start,
                stop = event.stop,
                title = event.title,
            )
    }
}
