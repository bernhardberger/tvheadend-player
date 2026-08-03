package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.core.Channel
import at.bernhardberger.tvheadend.core.ChannelTag
import at.bernhardberger.tvheadend.core.ClientState
import at.bernhardberger.tvheadend.core.ConnectionFailureKind
import at.bernhardberger.tvheadend.core.DvrActionFailure
import at.bernhardberger.tvheadend.core.DvrActionResult
import at.bernhardberger.tvheadend.core.DvrEntry
import at.bernhardberger.tvheadend.core.DvrFile
import at.bernhardberger.tvheadend.core.DvrState
import at.bernhardberger.tvheadend.core.EpgEventEntry
import at.bernhardberger.tvheadend.core.TimeshiftSeekDecision
import at.bernhardberger.tvheadend.core.TimeshiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOwnedSdkRemovalPolicyTest {
    @Test
    fun channelScopePreservesServerOrderAndNeutralSdkIdentifiers() {
        val channels = listOf(
            channel(3, setOf(10)),
            channel(1, setOf(10, 20)),
            channel(2, emptySet()),
        )

        val scope = resolveChannelScope(
            channels = channels,
            tags = listOf(ChannelTag(10, "News", 1), ChannelTag(20, "Sport", 2)),
            requestedTagId = 10,
        )

        assertEquals(listOf(3, 1), scope.visibleChannels.map(Channel::channelId))
        assertEquals(1, browsingFocusChannelId(scope.visibleChannels, currentFocusId = 1))
        assertEquals(3, browsingFocusChannelId(scope.visibleChannels, currentFocusId = 99))
    }

    @Test
    fun dvrLibraryKeepsArchiveScheduleProblemsAndSafeFoldersSeparate() {
        val completed = entry(1, DvrState.COMPLETED, 400, "Sport/Race.ts")
        val scheduled = entry(2, DvrState.SCHEDULED, 300)
        val recording = entry(3, DvrState.RECORDING, 200)
        val failed = entry(4, DvrState.FAILED, 100)

        val partition = partitionDvrLibrary(listOf(completed, scheduled, recording, failed))

        assertEquals(listOf(1), partition.archive.map(DvrEntry::id))
        assertEquals(listOf(3, 2), partition.schedule.map(DvrEntry::id))
        assertEquals(listOf(4), partition.problems.map(DvrEntry::id))
        assertEquals("Sport", buildDvrArchive(partition.archive).folders.single().name)
        assertEquals(
            "Other",
            buildDvrArchive(listOf(completed.copy(files = listOf(DvrFile(path = "../x.ts")))))
                .folders.single().name,
        )
    }

    @Test
    fun programmePolicyRetainsDetailsFirstActionsCategoriesAndCapturedTarget() {
        val current = event(id = 7, start = 100, stop = 200, contentType = 0x43)
        val future = current.copy(eventId = 8, start = 300, stop = 400)

        assertEquals(
            listOf(ProgrammeAction.WATCH),
            programmeActions(current, nowSec = 150, recording = null),
        )
        assertEquals(
            emptyList<ProgrammeAction>(),
            programmeActions(
                future,
                nowSec = 150,
                recording = null,
                canModifyRecordings = false,
            ),
        )
        assertEquals(ProgrammeCategory.SPORT, programmeCategory(current))
        assertTrue(current.matchesProgrammeCategory(ProgrammeCategory.ALL))
        assertEquals(
            ProgrammeRecordingTarget(7, 9, 100, 200, "Programme 7"),
            current.programmeRecordingTarget(),
        )
    }

    @Test
    fun liveInfoRecordingRevalidatesAndBindsCompletionToCapturedTarget() {
        val event = event(id = 42)
        val target = event.programmeRecordingTarget()
        val confirming = LiveInfoRecordingState.Confirming(target)

        assertEquals(
            LiveInfoRecordingDecision.Dispatch(target),
            liveInfoRecordingDecision(confirming, event, actionEligible = true),
        )
        assertEquals(
            LiveInfoRecordingDecision.Invalidate,
            liveInfoRecordingDecision(confirming, event.copy(title = "Replacement"), true),
        )
        assertEquals(
            LiveInfoRecordingState.Failed(target, DvrActionFailure.CONFLICT),
            liveInfoRecordingCompleted(
                LiveInfoRecordingState.Dispatching(target),
                DvrActionResult.Failed(DvrActionFailure.CONFLICT),
            ),
        )
    }

    @Test
    fun clientStateMappingAddsOnlyApplicationConfigurationStates() {
        assertEquals(ConnectionUiState.Ready, ClientState.Ready.toConnectionUiState())
        assertEquals(
            ConnectionUiState.Error(ConnectionFailureKind.AUTHENTICATION),
            ClientState.Error(ConnectionFailureKind.AUTHENTICATION).toConnectionUiState(),
        )
        assertNotEquals(ConnectionUiState.Ready, ConnectionUiState.NeedsConfiguration)
        assertNotEquals(ConnectionUiState.NeedsConfiguration, ConnectionUiState.CredentialUnavailable)
    }

    @Test
    fun playbackRecoveryPresentationRetainsSurfaceSpecificRetryAndBackPolicy() {
        val live = playbackRecoveryUiModel(
            surface = PlaybackRecoverySurface.LIVE,
            connectionAvailable = false,
            retryTargetAvailable = true,
            simpleTvActive = true,
        )
        val unavailableRecording = playbackRecoveryUiModel(
            surface = PlaybackRecoverySurface.RECORDING,
            connectionAvailable = true,
            retryTargetAvailable = false,
            simpleTvActive = false,
        )

        assertEquals(PlaybackRetryCommand.RECONNECT, live.retryCommand)
        assertEquals(PlaybackRecoverySecondaryAction.EXIT_SIMPLE_TV, live.secondaryAction)
        assertEquals(PlaybackRetryCommand.NONE, unavailableRecording.retryCommand)
        assertEquals(PlaybackRecoveryInitialAction.CLOSE, unavailableRecording.initialAction)
    }

    @Test
    fun timeshiftPresentationAndQueueUseOneInclusiveLiveEdgeTolerance() {
        val state = TimeshiftState(
            available = true,
            bufferStartMs = -120_000,
            positionMs = -TIMESHIFT_LIVE_EDGE_TOLERANCE_MS,
            liveEdgeMs = 0,
        )
        assertTrue(timeshiftPositionPresentation(state).atLiveEdge)
        assertFalse(canSeekTimeshiftForward(state))

        val queued = queueTimeshiftSeek(TimeshiftSeekQueueState(), state, -30_000)
        val dispatch = requireNotNull(beginTimeshiftSeekDispatch(queued))
        assertEquals(-30_000L, dispatch.deltaMs)
        assertEquals(
            -35_000L,
            completeTimeshiftSeekDispatch(
                dispatch.queue,
                TimeshiftSeekDecision(-35_000, -30_000, clamped = false),
            ).projectedPositionMs,
        )
    }

    @Test
    fun recordingPresentationAndRecentChannelRulesRemainStable() {
        assertEquals(
            RecordingFinishedAction.STOP_AND_CLOSE_PLAYER,
            recordingFinishedAction(true, activeRecordingId = 7, recordingPlayerVisible = true),
        )
        assertEquals(
            80_000L,
            recordingStackedSeekTarget(10_000, 50_000, 120_000, 30_000),
        )
        assertEquals(2, LastPlayedChannelPolicy.resolve(listOf(2, 4), persistedId = 99))
        assertEquals(listOf(4, 2, 3), pushRecentChannelId(listOf(2, 3), channelId = 4))
        assertEquals(
            ChannelNowStatus(playingNow = true, recordingNow = true),
            channelNowStatus(4, playingChannelId = 4, recordingChannelIds = setOf(4)),
        )
        assertNull(LastPlayedChannelPolicy.resolve(emptyList(), persistedId = 4))
    }

    private fun channel(id: Int, tags: Set<Int>) = Channel(
        channelId = id,
        name = "Channel $id",
        number = id,
        icon = null,
        tagIds = tags,
    )

    private fun event(
        id: Int,
        start: Long = 1_000,
        stop: Long = 2_000,
        contentType: Int? = null,
    ) = EpgEventEntry(
        eventId = id,
        channelId = 9,
        start = start,
        stop = stop,
        title = "Programme $id",
        contentType = contentType,
    )

    private fun entry(
        id: Int,
        state: DvrState,
        start: Long,
        path: String = "Archive/$id.ts",
    ) = DvrEntry(
        id = id,
        eventId = null,
        channelId = 9,
        start = start,
        stop = start + 60,
        title = "Recording $id",
        state = state,
        files = listOf(DvrFile(path = path)),
    )
}
