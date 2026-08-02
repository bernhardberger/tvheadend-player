package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeRecordingTargetPolicyTest {
    @Test
    fun targetCapturesTheImmutableProgrammeIdentity() {
        val event = event(
            id = 42,
            channelId = 7,
            start = 1_000L,
            stop = 2_000L,
            title = "Captured programme",
        )

        assertEquals(
            ProgrammeRecordingTarget(
                eventId = 42,
                channelId = 7,
                start = 1_000L,
                stop = 2_000L,
                title = "Captured programme",
            ),
            ProgrammeRecordingTarget.from(event),
        )
    }

    private fun event(
        id: Int,
        channelId: Int = 7,
        start: Long = 1_000L,
        stop: Long = 2_000L,
        title: String = "Programme $id",
    ) = EpgEventEntry(
        eventId = id,
        channelId = channelId,
        start = start,
        stop = stop,
        title = title,
    )
}
