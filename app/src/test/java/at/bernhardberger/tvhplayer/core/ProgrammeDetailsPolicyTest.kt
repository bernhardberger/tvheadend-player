package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.BrowseContextAction.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
class ProgrammeDetailsPolicyTest {
    private val event = EpgEvent.create(EventId(1), ChannelId(1), Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(200))
    private fun recording(state: DvrEntryState, position: Long = 0, files: List<DvrRecordingFile>? = null) = DvrEntry.create(
        DvrEntryId(1), state = state, start = event.start, stop = event.stop, playPosition = position.seconds, files = files)

    private fun file(path: String = "/rec/a.ts", start: Instant? = null, stop: Instant? = null) =
        TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = path, start = start, stop = stop)

    private fun admission(entry: DvrEntry, capability: RecordingProgressCapability = RecordingProgressCapability.SUPPORTED) =
        SessionObservation.create(recordingProgressCapability = capability).recordingPlaybackAdmission(entry)

    private fun actions(entry: DvrEntry, canWatchChannel: Boolean = true) =
        recordingDetailsActions(entry, admission(entry), canModifyRecordings = true, canWatchChannel = canWatchChannel)

    private fun facts(entry: DvrEntry, capability: RecordingProgressCapability = RecordingProgressCapability.SUPPORTED) =
        recordingDetailsFacts(entry, admission(entry, capability), nowSec = 300)

    @Test fun programmeActionsPreservePermissionAndOrigin() {
        assertEquals(listOf(WATCH, RECORD), programmeDetailsActions(event, 150, null, null, true, true, true))
        assertEquals(listOf(WATCH, RECORD, SHOW_GUIDE), programmeDetailsActions(event, 50, null, null, true, true, false))
        assertEquals(listOf(WATCH, SHOW_GUIDE), programmeDetailsActions(event, 150, null, null, false, true, false))
        assertEquals(listOf(WATCH), programmeDetailsActions(event, 200, null, null, true, true, true))
        assertEquals(emptyList<BrowseContextAction>(), programmeDetailsActions(event, 150, null, null, false, false, true))
        assertFalse(programmeDetailsActions(event, 150, null, null, true, true, true, liveProgramme = false).contains(RECORD))
    }

    @Test fun livePlayerReplacesWatchWithContinueAndKeepsGuardedRecordingAndGuide() {
        assertEquals(listOf(CONTINUE_WATCHING, RECORD, SHOW_GUIDE),
            programmeDetailsActions(event, 150, null, null, true, true, false, fromPlayer = true))
        assertEquals(listOf(CONTINUE_WATCHING, SHOW_GUIDE),
            programmeDetailsActions(event, 150, null, null, false, true, false, fromPlayer = true))
        assertEquals(listOf(CONTINUE_WATCHING, CANCEL, SHOW_GUIDE),
            programmeDetailsActions(event, 150, recording(DvrEntryState.SCHEDULED), null,
                true, true, false, fromPlayer = true))
    }

    @Test fun endedPlayerProgrammeDropsRecordStopAndCancelButKeepsContinueWatching() {
        for (entry in listOf(null, recording(DvrEntryState.SCHEDULED), recording(DvrEntryState.RECORDING),
            recording(DvrEntryState.RECORDING_ERROR))) {
            for (observed in listOf(true, false)) {
                assertEquals(listOf(CONTINUE_WATCHING, SHOW_GUIDE),
                    programmeDetailsActions(event, event.stop.epochSeconds, entry, null, true, true, false,
                        liveProgramme = observed, fromPlayer = true))
            }
        }
    }

    @Test fun recordingPlayerNeverOffersPlaybackOrMutationActions() {
        for (state in listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING,
            DvrEntryState.COMPLETED, DvrEntryState.MISSED, DvrEntryState.COMPLETED_ERROR)) {
            val entry = recording(state)
            assertEquals("$state", listOf(CONTINUE_WATCHING),
                recordingDetailsActions(entry, admission(entry), true, true, fromPlayer = true))
        }
    }

    @Test fun browseDetailsAndContextMenusNeverOfferContinueWatching() {
        for (canModify in listOf(false, true)) {
            assertFalse(CONTINUE_WATCHING in channelContextActions(null, 150, null, null, canModify))
            val entries = listOf<DvrEntry?>(null) + listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING,
                DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED, DvrEntryState.COMPLETED_ERROR,
                DvrEntryState.MISSED, DvrEntryState.INVALID, DvrEntryState.FILE_MISSING).map { recording(it) }
            for (entry in entries) {
                val playback = entry?.let { admission(it) }
                for (now in listOf(50L, 150L, 250L)) {
                    assertFalse(CONTINUE_WATCHING in channelContextActions(event, now, entry, playback, canModify))
                    for (fromGuide in listOf(false, true)) for (stacked in listOf(false, true)) {
                        assertFalse(CONTINUE_WATCHING in programmeDetailsActions(event, now, entry, playback,
                            canModify, true, fromGuide, stacked = stacked))
                    }
                }
                if (entry != null) {
                    assertFalse(CONTINUE_WATCHING in recordingContextActions(entry, playback, canModify))
                    assertFalse(CONTINUE_WATCHING in recordingDetailsActions(entry, playback, canModify, true))
                }
            }
        }
    }

    @Test fun recordingActionsFollowLifecycleAndPlaybackAuthority() {
        assertEquals(listOf(PLAY, MARK_WATCHED, DELETE), actions(recording(DvrEntryState.COMPLETED)))
        assertEquals(listOf(RESUME, START_OVER, MARK_WATCHED, DELETE), actions(recording(DvrEntryState.COMPLETED, 20)))
        assertEquals(listOf(WATCH, STOP), actions(recording(DvrEntryState.RECORDING, files = listOf(file()))))
        assertEquals(listOf(STOP), actions(recording(DvrEntryState.RECORDING)))
        assertEquals(listOf(WATCH, CANCEL), actions(recording(DvrEntryState.SCHEDULED)))
        assertEquals(listOf(CANCEL), actions(recording(DvrEntryState.SCHEDULED), canWatchChannel = false))
        assertEquals(listOf(DELETE), actions(recording(DvrEntryState.MISSED)))
        assertEquals(emptyList<BrowseContextAction>(),
            recordingDetailsActions(recording(DvrEntryState.COMPLETED), null, false, false))
    }

    @Test fun showGuideIsOmittedOnlyOnTheGuideRootPage() {
        for (fromGuide in listOf(false, true)) for (stacked in listOf(false, true)) {
            assertEquals(!fromGuide || stacked,
                SHOW_GUIDE in programmeDetailsActions(event, 50, null, null, true, true, fromGuide, stacked = stacked))
        }
        assertFalse(SHOW_GUIDE in programmeDetailsActions(event, 50, null, null, true, false, true, stacked = true))
    }

    @Test fun factsFollowProgrammeTimeAndPartialRecordingProgress() {
        assertEquals(listOf(DetailsFact(DetailsFactKind.STARTS, 100), DetailsFact(DetailsFactKind.LENGTH, 100)), programmeDetailsFacts(event, 50))
        assertEquals(DetailsFact(DetailsFactKind.LEFT, 50), programmeDetailsFacts(event, 150).last())
        assertEquals(DetailsFactKind.STARTED, programmeDetailsFacts(event, 200).first().kind)
        assertEquals(listOf(DetailsFact(DetailsFactKind.RECORDED, 100), DetailsFact(DetailsFactKind.LENGTH, 100)),
            facts(recording(DvrEntryState.COMPLETED)))
        assertEquals(DetailsFact(DetailsFactKind.LEFT, 80), facts(recording(DvrEntryState.COMPLETED, 20)).last())
        assertFalse(facts(recording(DvrEntryState.COMPLETED, 100)).any { it.kind == DetailsFactKind.LEFT })
        // The length is what the files hold, not the schedule.
        assertEquals(DetailsFact(DetailsFactKind.LENGTH, 30), facts(recording(DvrEntryState.COMPLETED, files = listOf(
            DvrRecordingFile.create(start = Instant.fromEpochSeconds(100), stop = Instant.fromEpochSeconds(130))))).last())
        // A missed recording never ran: when it was due, but no length.
        assertEquals(listOf(DetailsFact(DetailsFactKind.RECORDED, 100)),
            recordingDetailsFacts(recording(DvrEntryState.MISSED), null, nowSec = 300))
    }

    @Test fun progressIsShownOnlyWhereTheAdmissionOffersResume() {
        val partial = recording(DvrEntryState.COMPLETED, 20)
        listOf(RecordingProgressCapability.UNKNOWN, RecordingProgressCapability.UNSUPPORTED).forEach {
            assertFalse("$it", facts(partial, it).any { fact -> fact.kind == DetailsFactKind.LEFT })
        }
        assertFalse(recordingDetailsFacts(partial, null, nowSec = 300).any { it.kind == DetailsFactKind.LEFT })
        assertFalse(facts(recording(DvrEntryState.RECORDING, 20, listOf(file("/rec/a.mkv"))))
            .any { it.kind == DetailsFactKind.LEFT })
        // A running recording's length is what its files hold so far; without file times it is unknown.
        assertEquals(DetailsFact(DetailsFactKind.LEFT, 180),
            facts(recording(DvrEntryState.RECORDING, 20, listOf(file(start = event.start)))).last())
        assertFalse(facts(recording(DvrEntryState.RECORDING, 20, listOf(file())))
            .any { it.kind == DetailsFactKind.LENGTH || it.kind == DetailsFactKind.LEFT })
    }

    @Test fun failedRecordingProgressFollowsTheSdkResumeOffer() {
        assertEquals(DetailsFact(DetailsFactKind.LEFT, 80),
            facts(recording(DvrEntryState.COMPLETED_ERROR, 20, listOf(file(start = event.start, stop = event.stop)))).last())
        // A failed file without times has no known length, so nothing is left to count down.
        assertFalse(facts(recording(DvrEntryState.COMPLETED_ERROR, 20, listOf(file()))).any { it.kind == DetailsFactKind.LEFT })
        assertFalse(facts(recording(DvrEntryState.MISSED, 20, listOf(file()))).any { it.kind == DetailsFactKind.LEFT })
        assertFalse(facts(recording(DvrEntryState.COMPLETED_ERROR, 20)).any { it.kind == DetailsFactKind.LEFT })
        assertEquals(listOf(RESUME, START_OVER, MARK_WATCHED, DELETE), actions(recording(DvrEntryState.COMPLETED_ERROR, 20, listOf(file()))))
        assertEquals(listOf(PLAY, DELETE), actions(recording(DvrEntryState.MISSED, 20, listOf(file()))))
        assertEquals(listOf(DELETE), actions(recording(DvrEntryState.MISSED, 20)))
    }

    @Test fun statusPrioritizesRecordingLifecycleOverAiring() {
        assertNull(detailsStatus(event, null, 99))
        assertEquals(DetailsStatus.ON_NOW, detailsStatus(event, null, 100))
        assertNull(detailsStatus(event, null, 200))
        assertEquals(DetailsStatus.RECORDING, detailsStatus(event, recording(DvrEntryState.RECORDING), 150))
        assertEquals(DetailsStatus.SCHEDULED, detailsStatus(event, recording(DvrEntryState.SCHEDULED), 50))
        assertEquals(DetailsStatus.RECORDING_ERRORS, detailsStatus(event, recording(DvrEntryState.RECORDING_ERROR), 150))
        assertEquals(DetailsStatus.MISSED, detailsStatus(null, recording(DvrEntryState.MISSED), 150))
        // Canceled is neutral, as in the list, the pane and Problems; only real failures are red.
        assertEquals(DetailsStatus.CANCELLED, detailsStatus(null, recording(DvrEntryState.INVALID), 150))
        listOf(DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING).forEach {
            assertEquals(DetailsStatus.FAILED, detailsStatus(null, recording(it), 150))
        }
        assertNull(detailsStatus(null, recording(DvrEntryState.COMPLETED), 150))
    }
}
