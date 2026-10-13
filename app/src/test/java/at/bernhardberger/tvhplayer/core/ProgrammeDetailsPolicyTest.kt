package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.BrowseContextAction.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ProgrammeDetailsPolicyTest {
    private val event = EpgEvent.create(EventId(1), ChannelId(1), Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(200))
    private fun recording(state: DvrEntryState, position: Long = 0) = DvrEntry.create(
        DvrEntryId(1), state = state, start = event.start, stop = event.stop, playPosition = position.seconds)

    @Test fun programmeActionsPreservePermissionAndOrigin() {
        assertEquals(listOf(WATCH, RECORD), programmeDetailsActions(event, 150, null, true, true, true))
        assertEquals(listOf(WATCH, RECORD, SHOW_GUIDE), programmeDetailsActions(event, 50, null, true, true, false))
        assertEquals(listOf(WATCH, SHOW_GUIDE), programmeDetailsActions(event, 150, null, false, true, false))
        assertEquals(listOf(WATCH), programmeDetailsActions(event, 200, null, true, true, true))
        assertEquals(emptyList<BrowseContextAction>(), programmeDetailsActions(event, 150, null, false, false, true))
        assertFalse(programmeDetailsActions(event, 150, null, true, true, true, liveProgramme = false).contains(RECORD))
    }

    @Test fun recordingActionsFollowLifecycleAndPlaybackAuthority() {
        assertEquals(listOf(PLAY), recordingDetailsActions(recording(DvrEntryState.COMPLETED), true, true, true))
        assertEquals(listOf(RESUME, START_OVER), recordingDetailsActions(recording(DvrEntryState.COMPLETED, 20), true, true, true))
        assertEquals(listOf(WATCH, STOP), recordingDetailsActions(recording(DvrEntryState.RECORDING), true, true, true))
        assertEquals(listOf(WATCH, CANCEL), recordingDetailsActions(recording(DvrEntryState.SCHEDULED), false, true, true))
        assertEquals(listOf(CANCEL), recordingDetailsActions(recording(DvrEntryState.SCHEDULED), false, true, false))
        assertEquals(listOf(DELETE), recordingDetailsActions(recording(DvrEntryState.MISSED), false, true, true))
        assertEquals(emptyList<BrowseContextAction>(), recordingDetailsActions(recording(DvrEntryState.COMPLETED), false, false, false))
    }

    @Test fun factsFollowProgrammeTimeAndPartialRecordingProgress() {
        assertEquals(listOf(DetailsFact(DetailsFactKind.STARTS, 100), DetailsFact(DetailsFactKind.LENGTH, 100)), programmeDetailsFacts(event, 50))
        assertEquals(DetailsFact(DetailsFactKind.LEFT, 50), programmeDetailsFacts(event, 150).last())
        assertEquals(DetailsFactKind.STARTED, programmeDetailsFacts(event, 200).first().kind)
        assertEquals(listOf(DetailsFact(DetailsFactKind.RECORDED, 100), DetailsFact(DetailsFactKind.LENGTH, 100)),
            recordingDetailsFacts(recording(DvrEntryState.COMPLETED)))
        assertEquals(DetailsFact(DetailsFactKind.LEFT, 80), recordingDetailsFacts(recording(DvrEntryState.COMPLETED, 20)).last())
        assertFalse(recordingDetailsFacts(recording(DvrEntryState.COMPLETED, 100)).any { it.kind == DetailsFactKind.LEFT })
    }

    @Test fun statusPrioritizesRecordingLifecycleOverAiring() {
        assertNull(detailsStatus(event, null, 99))
        assertEquals(DetailsStatus.ON_NOW, detailsStatus(event, null, 100))
        assertNull(detailsStatus(event, null, 200))
        assertEquals(DetailsStatus.RECORDING, detailsStatus(event, recording(DvrEntryState.RECORDING), 150))
        assertEquals(DetailsStatus.SCHEDULED, detailsStatus(event, recording(DvrEntryState.SCHEDULED), 50))
        listOf(DvrEntryState.MISSED, DvrEntryState.INVALID, DvrEntryState.RECORDING_ERROR,
            DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING).forEach {
            assertEquals(DetailsStatus.FAILED, detailsStatus(null, recording(it), 150))
        }
        assertNull(detailsStatus(null, recording(DvrEntryState.COMPLETED), 150))
    }
}
