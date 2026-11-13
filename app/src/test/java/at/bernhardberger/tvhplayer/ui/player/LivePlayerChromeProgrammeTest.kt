@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)


package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.notices.NoticeSeverity
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class LivePlayerChromeProgrammeTest : LivePlayerChromeFixture() {
    @Test fun programmeInfoRetainsItsLastSnapshotAndWatchFocusAcrossBoundaryAndEviction() {
        screen()
        key(Key.Info)
        val now = System.currentTimeMillis() / 1_000L
        val ended = EpgEvent.create(EventId(11L), ChannelId(1L), Instant.fromEpochSeconds(now - 1_800L),
            Instant.fromEpochSeconds(now - 1L), title = "Ended programme", summary = "Last known summary")
        val nextProgramme = EpgEvent.create(EventId(21L), ChannelId(1L), Instant.fromEpochSeconds(now - 60L),
            Instant.fromEpochSeconds(now + 3_600L), title = "Next up", summary = "Summary")
        session.publish(observation(now, listOf(ended, nextProgramme)))
        repeat(2) { settle() }
        assertEquals("Ended programme", texts("details-title"))
        assertFalse(exists("details-record"))
        // The ended programme is not the one at the viewer position: it offers Watch, which returns to the picture.
        assertEquals(listOf("details-watch"), focused())
        session.publish(observation(now, listOf(nextProgramme)))
        repeat(2) { settle() }
        assertEquals("Ended programme", texts("details-title"))
        assertEquals(listOf("details-watch"), focused())
        session.publish(observation(now, listOf(nextProgramme), recordings = listOf(
            DvrEntry.create(DvrEntryId(2L), eventId = nextProgramme.id, state = DvrEntryState.RECORDING),
        )))
        repeat(2) { settle() }
        assertEquals(listOf("details-watch"), focused())
        assertFalse(exists("details-stop"))
        assertEquals("Ended programme", texts("details-title"))
        session.publish(observation(now, listOf(nextProgramme), missingRequestedChannel = true))
        repeat(2) { settle() }
        assertFalse(exists("player-actions"))
        assertFalse(exists("programme-details-page"))
    }

    @Test fun withoutEpgInfoKeepsContinueWatchingFocusedAndBackReturnsToInfo() {
        screen(epg = false)
        key(Key.DirectionDown)
        key(Key.Info)
        assertTrue(exists("programme-details-page"))
        assertEquals(listOf("details-continue_watching"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("details-continue_watching"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("details-continue_watching"), focused())
        assertFalse(exists("live-info-stream-signal"))
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
        assertTrue(exists("player-actions"))
        assertEquals(listOf("player-info"), focused())
    }

    @Test fun playerInfoOffersCancelAfterContinueWatchingWhenAlreadyScheduled() {
        screen(recordingScheduled = true)
        key(Key.Info)
        assertEquals(listOf("details-continue_watching"), focused())
        compose.onNodeWithTag("details-cancel").assertExists()
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
    }

    @Test fun playerInfoDoesNotInventMissingProgrammeTitleOrSubtitle() {
        screen()
        val now = System.currentTimeMillis() / 1_000L
        session.publish(observation(now, listOf(EpgEvent.create(EventId(31), ChannelId(1),
            Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600)))))
        settle()
        key(Key.Info)
        assertEquals(listOf("details-continue_watching"), focused())
        assertEquals("", texts("details-title"))
        assertFalse(exists("details-subtitle"))
    }

    @Test fun playerInfoConsumesRemoteCommandsAndReturnsToInfo() {
        screen()
        key(Key.DirectionDown)
        assertEquals(listOf("player-pause"), focused())
        key(Key.Info)
        assertEquals(listOf("details-continue_watching"), focused())
        val playing = player.playWhenReady
        listOf(Key.ChannelUp, Key.ChannelDown, Key.MediaStop, Key.MediaPause, Key.Menu, Key.Info).forEach(::key)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(playing, player.playWhenReady)
        assertEquals(listOf("details-continue_watching"), focused())
        key(Key.Back)
        assertEquals(listOf("player-info"), focused())
        assertFalse(exists("programme-details-page"))
        assertTrue(exists("player-actions"))
        assertFalse("controls rest has no now-playing strip", exists("player-now-playing"))
        key(Key.Info)
        assertEquals("a new visit resets Details and its action", listOf("details-continue_watching"), focused())
    }

    @Test fun recordKeySchedulesDirectlyAndReopensInfoOnItsConfirmedStopAction() {
        screen(recordingProfile = DvrConfigId("archive"))
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.MediaRecord)
        assertFalse(exists("programme-details-page"))
        assertFalse(exists("two-column-first"))
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertEquals(DvrConfigId("archive"), scheduleRequests.single().configId)
        publishInfoRecording(DvrEntryState.RECORDING)
        key(Key.MediaRecord)
        assertEquals(listOf("details-stop"), focused())
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        key(Key.DirectionCenter)
        assertTrue(exists("two-column-first"))
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        key(Key.Back)
        assertEquals(listOf("details-stop"), focused())
    }

    @Test fun playerInfoRecordsDirectlyThenOffersStopFromObservedState() {
        screen(recordingProfile = DvrConfigId("archive"))
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertEquals(DvrConfigId("archive"), scheduleRequests.single().configId)
        publishInfoRecording(DvrEntryState.RECORDING)
        assertEquals("Stop recording", texts("details-stop"))
        assertEquals(listOf("details-stop"), focused())
        assertTrue("command success never posts a notice", notices.state.value.pending.isEmpty())
        assertFalse("the shared queue replaces the local notice", exists("programme-recording-notice"))
        session.dvrRepository.scriptStopEntry(DvrMutationResult.Confirmed(Unit))
        key(Key.DirectionCenter)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        confirmInfoAction(destructive = true)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        assertEquals(listOf("details-stop"), focused())
    }

    @Test fun playerInfoConfirmationBackIsSafeAndDoesNotCloseDetails() {
        screen(recordingScheduled = true)
        key(Key.MediaRecord)
        assertEquals(listOf("details-cancel"), focused())
        key(Key.DirectionCenter)
        compose.onNodeWithTag("two-column-first").assertIsFocused().assertTextEquals("Keep recording")
        compose.onNode(isDialog()).performKeyInput { pressKey(Key.Back) }
        settle()
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        assertTrue(exists("programme-details-page"))
        assertEquals(listOf("details-cancel"), focused())
        assertTrue(notices.state.value.pending.isEmpty())
    }

    @Test fun playerInfoRecordIgnoresASecondPressBeforeRecomposition() {
        screen()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        val click = compose.onNodeWithTag("details-record").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { click(); click() }
        settle()
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertEquals(listOf("details-record"), focused())
    }

    @Test fun playerInfoStopKeepReturnsToTheInvokingButtonWithoutSending() {
        screen()
        publishInfoRecording(DvrEntryState.RECORDING)
        key(Key.Info)
        compose.onNodeWithTag("details-stop").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("two-column-first").assertIsFocused().assertTextEquals("Keep recording")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        assertEquals(listOf("details-stop"), focused())
    }

    @Test fun playerInfoRecordingFailureKeepsRecordAndShowsANonFocusableNotice() {
        screen()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        assertEquals("Record", texts("details-record"))
        assertEquals(listOf("details-record"), focused())
        val notice = notices.state.value.pending.single().notice
        assertEquals(NoticeSeverity.FAILURE, notice.severity)
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED), notice)
        assertEquals("dvr-action", notice.key)
        assertFalse(exists("programme-recording-notice"))
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
    }

    @Test fun playerInfoRecordFailureAfterContinueWatchingPostsNotice() {
        val completion = CompletableDeferred<Unit>()
        screen(scheduleCompletion = completion)
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        compose.onNodeWithTag("details-continue_watching").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.runOnIdle { completion.complete(Unit) }
        settle()
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED),
            notices.state.value.pending.single().notice)
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        assertFalse(exists("player-actions"))
    }

    @Test fun playerInfoRecordResultAfterCloseDoesNotAffectReopenedProgramme() {
        val completion = CompletableDeferred<Unit>()
        screen(scheduleCompletion = completion)
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        key(Key.Back)
        val now = System.currentTimeMillis() / 1_000L
        session.publish(observation(now, listOf(EpgEvent.create(EventId(21), ChannelId(1),
            Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = "New programme"))))
        settle()
        key(Key.Info)
        assertEquals("New programme", texts("details-title"))
        compose.runOnIdle { completion.complete(Unit) }
        settle()
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED),
            notices.state.value.pending.single().notice)
        assertEquals(listOf("details-continue_watching"), focused())
        compose.onNodeWithTag("details-record").assertExists()
        compose.onNodeWithTag("details-stop").assertDoesNotExist()
    }

    @Test fun playerInfoRecordResultAfterSelectingAnotherAiringDoesNotAffectThatPage() {
        val completion = CompletableDeferred<Unit>()
        screen(scheduleCompletion = completion)
        publishMatchingFutureProgramme()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        key(Key.DirectionDown)
        compose.onNodeWithTag("details-card-12").assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.runOnIdle { completion.complete(Unit) }
        settle()
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED),
            notices.state.value.pending.single().notice)
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onNodeWithTag("details-record").assertExists()
    }

    @Test fun playerInfoFutureAiringRecordsThenAutomaticallyFocusesCancel() {
        screen()
        publishMatchingFutureProgramme()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.Info)
        key(Key.DirectionDown)
        compose.onNodeWithTag("details-card-12").assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        publishInfoRecording(DvrEntryState.SCHEDULED, EventId(12))
        compose.onNodeWithTag("details-cancel").assertIsFocused()
        session.dvrRepository.scriptCancelEntry(DvrMutationResult.Confirmed(Unit))
        key(Key.DirectionCenter)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        confirmInfoAction(destructive = true)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        compose.onNodeWithTag("details-cancel").assertIsFocused()
    }

    @Test fun playerInfoUsesSharedDescriptionReaderAndReturnsToItsPanel() {
        screen()
        val now = System.currentTimeMillis() / 1_000L
        val description = "A full description with its own ending.\n".repeat(24)
        session.publish(observation(now, listOf(EpgEvent.create(EventId(11), ChannelId(1),
            Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = "Now showing",
            description = description, summary = "A separate summary", genre = "Drama",
            categories = listOf("Documentary"), keywords = listOf("Mountains"), copyrightYear = 2024,
            episode = at.bernhardberger.tvheadend.sdk.core.EpgEpisode(null, null, 2, null, 3, 6, null, null, null)))))
        settle()
        key(Key.Info)
        compose.onNodeWithTag("details-about-description").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertTrue(texts("details-reading-heading").contains("Now showing"))
        assertTrue(texts("details-reading-text").contains(description.trim()))
        key(Key.Back)
        assertEquals(listOf("details-about-description"), focused())
    }
}
