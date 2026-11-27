package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.ApplianceEntryDestination
import at.bernhardberger.tvhplayer.core.recordingPlayNextTrigger
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.WarmPlaybackMode
import at.bernhardberger.tvhplayer.ui.common.formatClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val chainStart = 59.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingPlaybackRouteTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = NavigationJourneyFixture(compose, ServerSettings(host = javaClass.simpleName))
    @After fun after() = fixture.close()
    private val nearEnd = requireNotNull(recordingPlayNextTrigger(emptyList(), 3_600_000L, false)) + 5_000

    @Test fun naturalEndWithoutOffersStopsAndReturnsToRecordings() {
        openRecording()
        fixture.finishRecording()
        compose.mainClock.advanceTimeUntil(2_000) { !fixture.playerVisible }
        compose.onNodeWithTag("recording-list-entry-7").assertExists()
        compose.onNodeWithTag("recording-end-screen").assertDoesNotExist()
        compose.runOnIdle { assertNull(fixture.runtime.activeTarget.value) }
    }

    @Test fun finishedRouteSwapsToThePresentedOffer() {
        openRecording(9)
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-card-0").assertIsFocused()
        compose.playAndPresentOffer(fixture)
    }

    @Test fun anExpiredWatchLiveCardCannotTune() {
        compose.openRecording(fixture, liveWindow = true)
        fixture.finishRecording()
        val card = compose.onNodeWithTag("recording-end-card-0").assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { fixture.offerNow += 4.minutes }
        card.performSemanticsAction(SemanticsActions.OnClick)
        fixture.settle()
        assertEquals(RecordingsKey, fixture.topRoute())
    }

    @Test fun anEndCardRefreshesItsBookmarkWithoutResettingItsFocus() {
        openRecording(8, autoContinue = true)
        fixture.finishRecording()
        compose.mainClock.autoAdvance = false
        val card = compose.onNodeWithTag("recording-end-card-0").assertIsFocused()
        fixture.updateRecordingBookmark(DvrEntryId(8), 2.minutes)
        fixture.settle()
        card.assertIsFocused()
        val label = ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.recording_end_resume_at, "2:00")
        compose.onNodeWithText(label, useUnmergedTree = true).assertExists()
    }

    @Test fun aPlayNextContinuationLabelFollowsThePlayheadWithinTheSameOffer() {
        openRecording(8, chain = true, programmeEndMs = 58.minutes.inWholeMilliseconds,
            chainFileStart = 58.minutes)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo((58.minutes + 45.seconds).inWholeMilliseconds)
        val nextPosition = nearEnd
        fixture.advanceRecordingTo(nextPosition)
        val expected = NavigationJourneyFixture.NOW - 120.minutes + nextPosition.milliseconds
        val label = ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.recording_end_continue_at, formatClock(expected.epochSeconds))
        compose.onNodeWithText(label, useUnmergedTree = true).assertExists()
    }

    @Test fun theEndScreenNamesTheTransientPlayerItReturnsTo() {
        assertEquals(R.string.player_live_tv_surface, recordingEndOriginTitle(LivePlayerKey(6, "Live")))
        assertEquals(R.string.player_recording_surface, recordingEndOriginTitle(RecordingPlayerKey(7)))
    }

    @Test fun aHeldEndOfferCannotInstallTheSameIdFromAnotherSession() {
        openRecording(8)
        fixture.finishRecording()
        compose.mainClock.autoAdvance = false
        fixture.holdNewPlaybackSettingsReads()
        try {
            compose.onNodeWithTag("recording-end-card-0").performSemanticsAction(SemanticsActions.OnClick)
            compose.mainClock.advanceTimeUntil(2_000) { fixture.playbackSettingsReadHeld }
            fixture.replaceRecordingSession()
        } finally {
            fixture.releasePlaybackSettingsReads()
        }
        fixture.settle()
        compose.runOnIdle { assertEquals(1, fixture.recordingInstalls) }
    }

    @Test fun anExternalGuideRequestRetiresThePendingEndOfferWithoutPoppingGuide() {
        openRecording(8, warmPlaybackMode = WarmPlaybackMode.ON)
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-card-0").performSemanticsAction(SemanticsActions.OnClick)
        fixture.settle()
        compose.runOnIdle {
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(8)), fixture.runtime.activeTarget.value)
            fixture.launchRequests.request(ApplianceEntryDestination.GUIDE)
        }
        fixture.settle()
        assertEquals(GuideKey, fixture.topRoute())
        compose.runOnIdle { assertNull(fixture.runtime.activeTarget.value) }
    }

    @Test fun backDismissesPlayNextAndDisarmsTheRecordingEndCountdown() {
        val mark = nearEnd - 60_000
        openRecording(8, autoContinue = true, programmeEndMs = mark)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(mark)
        compose.onNodeWithTag("recording-play-next-prompt").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.runOnIdle { assertTrue(fixture.recordingPosition > mark) }
        compose.onNodeWithTag("recording-play-next-prompt").assertIsFocused()
        fixture.backKeyCycle()
        compose.onNodeWithTag("recording-play-next-prompt").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
        }
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-screen").assertExists()
        compose.onNodeWithTag("recording-end-countdown").assertDoesNotExist()
    }

    @OptIn(TvheadendTestingApi::class)
    @Test fun aListStartedRecordingOffersPlayNextAtProgrammeEndAfterHiddenSeeks() {
        val fileStart = NavigationJourneyFixture.NOW - 120.minutes
        val programmeLeadIn = 11.minutes + 30.seconds
        val programmeEnd = programmeLeadIn + 45.minutes
        val rule = AutorecRuleId("programme-crossing")
        fun match(stop: Duration) = DvrEntry.create(DvrEntryId(17), channelId = ChannelId(6), autorecRuleId = rule,
            state = DvrEntryState.COMPLETED, title = "Match",
            start = fileStart + programmeLeadIn, stop = fileStart + stop,
            startExtraMinutes = 12.minutes, stopExtraMinutes = 60.minutes,
            files = listOf(TvheadendTestResultFactory.recordingFile(
                serverPath = "match.ts", start = fileStart, stop = fileStart + programmeEnd + 60.minutes)))
        val original = match(programmeEnd + 30.seconds)
        val next = DvrEntry.create(DvrEntryId(18), channelId = ChannelId(6), autorecRuleId = rule,
            state = DvrEntryState.COMPLETED, title = "Post-match",
            start = fileStart + programmeEnd, stop = fileStart + programmeEnd + 45.minutes,
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "post-match.ts",
                start = fileStart + programmeEnd - programmeLeadIn, stop = fileStart + programmeEnd + 45.minutes)))
        fixture.app(withRecording = true, driveRecordingEnd = true, scheduled = listOf(original, next))
        compose.onNodeWithTag("nav-recordings").performSemanticsAction(SemanticsActions.OnClick)
        compose.playFromRecordings(fixture, original.id.value)

        compose.mainClock.autoAdvance = false
        fixture.publishRecordings(fixture.recordings().map { if (it.id == original.id) match(programmeEnd) else it })
        val beforeSeeking = (programmeEnd - 70.seconds).inWholeMilliseconds
        fixture.advanceRecordingTo(beforeSeeking)
        compose.onNodeWithTag("player-stop").assertDoesNotExist()
        repeat(2) { fixture.key(Key.DirectionRight) }
        compose.runOnIdle {
            assertTrue(fixture.recordingPosition in (beforeSeeking + 1) until programmeEnd.inWholeMilliseconds)
        }
        val prompt = compose.onNodeWithTag("recording-play-next-prompt")
        prompt.assertDoesNotExist()
        // The real playhead crosses the scheduled stop, rather than seeking onto the mark.
        fixture.advanceRecordingTo((programmeEnd + 2.seconds).inWholeMilliseconds)
        prompt.assertIsFocused()
        // Left goes below the origin-based mark and re-arms the next crossing.
        fixture.key(Key.DirectionLeft)
        prompt.assertDoesNotExist()
        fixture.advanceRecordingTo((programmeEnd + 2.seconds).inWholeMilliseconds)
        prompt.assertIsFocused()
    }

    @Test fun expiredPlayNextDoesNotPlayAndLeavesTheEndCountdownArmed() {
        openRecording(8, autoContinue = true)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(nearEnd, settleAfter = false)
        val promptTag = "recording-play-next-prompt"
        compose.mainClock.advanceTimeUntil(2_000) {
            compose.onAllNodes(hasTestTag(promptTag)).fetchSemanticsNodes().isNotEmpty()
        }

        // Pin the ten-second contract independently of the implementation's duration.
        compose.mainClock.advanceTimeBy(9_800)
        compose.onNodeWithTag(promptTag).assertExists()
        compose.mainClock.advanceTimeBy(10_200 - 9_800L)
        compose.onNodeWithTag(promptTag).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
        }
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-countdown").assertExists()
    }

    @Test fun rejectedPlayNextKeepsTheOriginalRecordingAndWithdrawsTheOffer() {
        openRecording(8)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(nearEnd)
        compose.onNodeWithTag("recording-play-next-prompt").assertIsFocused()

        fixture.rejectNextRecording()
        fixture.key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-play-next-prompt").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
            assertTrue(fixture.runtime.player.playWhenReady)
            assertEquals(Notice.RecordingPlaybackStartFailed,
                fixture.playbackNotices.state.value.pending.single().notice)
        }
        fixture.key(Key.DirectionUp)
        compose.onNodeWithTag("player-stop").assertExists()
        compose.onNodeWithTag("recording-play-next").assertDoesNotExist()
        fixture.allowRecording()
        val starts = fixture.recordingInstalls
        fixture.retryRecording()
        compose.runOnIdle {
            assertEquals(starts + 1, fixture.recordingInstalls)
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
        }
    }

    @Test fun rejectedPlayNextIsNotRetriedByTheEndScreenCountdown() {
        openRecording(8, 9, autoContinue = true)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(nearEnd)
        fixture.rejectNextRecording()
        fixture.key(Key.DirectionCenter)
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-card-0").assertIsFocused()
        compose.onNodeWithText("Other episode 9").assertExists()
        compose.onNodeWithText("Other episode 8").assertDoesNotExist()
    }

    @Test fun warmReturnUsesOnlyTheRemainingPlayNextTime() {
        openRecording(8, warmPlaybackMode = WarmPlaybackMode.ON)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(nearEnd)
        compose.onNodeWithTag("recording-play-next-prompt").assertIsFocused()
        compose.mainClock.advanceTimeBy(6_000)
        compose.runOnIdle { fixture.launchRequests.request(ApplianceEntryDestination.RECORDINGS) }
        fixture.settle()
        assertEquals(RecordingsKey, fixture.topRoute())
        compose.mainClock.advanceTimeBy(6_000)
        fixture.focus("recording-list-entry-7")
        fixture.key(Key.DirectionCenter)
        fixture.settle()
        compose.onNodeWithTag("recording-play-next-prompt").assertExists()
        compose.mainClock.advanceTimeBy(4_300)
        compose.onNodeWithTag("recording-play-next-prompt").assertDoesNotExist()
    }

    @Test fun backBeforePlayNextPresentsRetiresOnlyTheReplacement() {
        openRecording(8)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(nearEnd)
        fixture.key(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(8)), fixture.runtime.activeTarget.value)
            fixture.back.onBackPressed()
        }
        fixture.settle()
        compose.onNodeWithTag("recording-list-entry-7").assertExists()
        compose.runOnIdle { assertNull(fixture.runtime.activeTarget.value) }
    }

    @Test fun backBeforePlayNextInstallsStopsTheOriginalWhenWarmPlaybackIsOff() {
        openRecording(8, warmPlaybackMode = WarmPlaybackMode.OFF)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeUntil(6_000) {
            compose.onAllNodes(hasTestTag("player-banner") or hasTestTag("player-stop"))
                .fetchSemanticsNodes().isEmpty()
        }
        fixture.advanceRecordingTo(nearEnd)
        fixture.holdPlaybackCommandsBeforeAdmission()
        try {
            fixture.key(Key.DirectionCenter)
            compose.onNodeWithTag("recording-play-next-prompt").assertDoesNotExist()
            compose.onNodeWithTag("player-banner").assertDoesNotExist()
            compose.onNodeWithTag("player-stop").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
                assertEquals(1, fixture.recordingInstalls)
                fixture.back.onBackPressed()
            }
            fixture.settle()
            assertEquals(RecordingsKey, fixture.topRoute())
        } finally {
            fixture.releasePlaybackCommands()
        }
        fixture.settle()
        compose.onNodeWithTag("recording-list-entry-7").assertExists()
        compose.runOnIdle {
            assertNull(fixture.runtime.activeTarget.value)
            assertEquals(1, fixture.recordingInstalls)
        }
    }

    @Test fun steppingPastTheLastChainPartWithdrawsPlayNextWithoutStartingIt() {
        val mark = (chainStart - 10.seconds).inWholeMilliseconds
        openRecording(8, chain = true, programmeEndMs = mark, shortChain = true)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(mark)
        compose.onNodeWithTag("recording-play-next-prompt").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.runOnIdle {
            assertTrue(fixture.recordingPosition > (chainStart + 10.seconds).inWholeMilliseconds)
        }
        compose.onNodeWithTag("recording-play-next-prompt").assertDoesNotExist()
        fixture.key(Key.DirectionUp)
        compose.onNodeWithTag("player-stop").assertExists()
        compose.onNodeWithTag("recording-play-next").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, fixture.recordingInstalls) }
    }

    @Test fun queuedPlayNextDoesNotSwallowSystemStop() {
        openRecording(8)
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(nearEnd)
        val installs = fixture.recordingInstalls
        fixture.queuePlayNextAndSystemStopDuringPause()
        compose.runOnIdle {
            assertEquals(installs, fixture.recordingInstalls)
            assertNull(fixture.runtime.activeTarget.value)
        }
    }

    @Test fun aLostPlayNextTargetShowsFailureWithoutReinstallingOnEntry() {
        openRecording(8, chain = true)
        startHeldPlayNext()
        val installs = fixture.recordingInstalls
        fixture.retirePlayback()
        compose.onNodeWithTag("recording-list-entry-7").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(installs, fixture.recordingInstalls)
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(8)),
                (fixture.runtime.state.value as AppPlaybackState.Failed).target)
        }
        compose.onNodeWithTag("tv-recovery-primary").performSemanticsAction(SemanticsActions.OnClick)
        fixture.settle()
        compose.runOnIdle {
            assertEquals(installs + 1, fixture.recordingInstalls)
            assertEquals(nearEnd.milliseconds - chainStart, fixture.recordingStartOffset)
        }
    }

    @Test fun aHeldPlayNextRetryCannotInstallTheSameIdFromAnotherSession() {
        openRecording(8, chain = true)
        startHeldPlayNext()
        val installs = fixture.recordingInstalls
        fixture.retirePlayback()
        fixture.holdNewPlaybackSettingsReads()
        try {
            compose.onNodeWithTag("tv-recovery-primary").performSemanticsAction(SemanticsActions.OnClick)
            compose.mainClock.advanceTimeUntil(2_000) { fixture.playbackSettingsReadHeld }
            fixture.replaceRecordingSession()
        } finally {
            fixture.releasePlaybackSettingsReads()
        }
        fixture.settle()
        compose.runOnIdle {
            // The screen can restore in the new session, but it must not inherit the old owner's offset.
            assertEquals(installs + 1, fixture.recordingInstalls)
            assertNull(fixture.recordingStartOffset)
        }
    }

    @Test fun playNextWaitsForPresentationThenShowsTheChainAtThePressTime() {
        openRecording(8, chain = true)
        val press = preparePlayNextPress()
        fixture.queuePlayNextDuringPause(press, advanceTo = nearEnd + 5_000)
        compose.runOnIdle { assertEquals(nearEnd.milliseconds - chainStart, fixture.recordingStartOffset) }
        assertEquals(7L, (fixture.topRoute() as RecordingPlayerKey).recordingId)
        fixture.presentFirstFrame()
        assertEquals(8L, (fixture.topRoute() as RecordingPlayerKey).recordingId)
        fixture.key(Key.DirectionUp)
        compose.onNodeWithTag("player-info").performSemanticsAction(SemanticsActions.OnClick)
        fixture.settle()
        compose.onNodeWithText("Other episode 8", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("recording-end-screen").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(8)), fixture.runtime.activeTarget.value)
            assertEquals(null, fixture.runtime.finishedRecording.value)
        }
    }

    @Test fun queuedPlayNextDoesNotSubstituteAnotherOfferWhenTheShownEntryIsRemoved() {
        openRecording(8, 9)
        val press = preparePlayNextPress()
        compose.onNodeWithText("Other episode 8").assertExists()
        fixture.queuePlayNextDuringPause(press, removeOffer = DvrEntryId(8))
        compose.runOnIdle {
            assertEquals(1, fixture.recordingInstalls)
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
            assertTrue(fixture.playbackNotices.state.value.pending.isEmpty())
        }
        fixture.resumeRecordingForPrompt()
        compose.onNodeWithText("Other episode 9").assertExists()
    }

    @Test fun queuedPlayNextDoesNotStartWhenTheSameEntryNowOffersResume() {
        openRecording(8, 9)
        val press = preparePlayNextPress()
        compose.onNodeWithText("Other episode 8").assertExists()
        fixture.queuePlayNextDuringPause(press, bookmarkOffer = DvrEntryId(8) to 2.minutes)
        compose.runOnIdle {
            assertEquals(1, fixture.recordingInstalls)
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
            assertTrue(fixture.playbackNotices.state.value.pending.isEmpty())
        }
        fixture.resumeRecordingForPrompt()
        compose.onNodeWithText("Resume at", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun playNextDoesNotInstallWhenItsBookmarkIsClearedAfterAcceptance() {
        openRecording(8)
        fixture.updateRecordingBookmark(DvrEntryId(8), 12.minutes)
        val press = preparePlayNextPress()
        fixture.holdNewPlaybackSettingsReads()
        try {
            compose.runOnIdle(press)
            compose.mainClock.advanceTimeUntil(2_000) { fixture.playbackSettingsReadHeld }
            fixture.updateRecordingBookmark(DvrEntryId(8), null)
            fixture.finishRecording()
        } finally {
            fixture.releasePlaybackSettingsReads()
        }
        fixture.settle()
        compose.runOnIdle {
            assertEquals(1, fixture.recordingInstalls)
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
            assertTrue(fixture.playbackNotices.state.value.pending.isEmpty())
        }
        compose.onNodeWithTag("recording-end-card-0").assertExists()
        compose.onNodeWithText("Other episode 8", useUnmergedTree = true).assertExists()
    }

    private fun preparePlayNextPress(): () -> Unit {
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(nearEnd)
        val prompt = compose.onNodeWithTag("recording-play-next-prompt").assertIsFocused()
        val press = requireNotNull(prompt.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        return { assertTrue(press()) }
    }

    private fun startHeldPlayNext() {
        compose.mainClock.autoAdvance = false
        fixture.advanceRecordingTo(nearEnd)
        compose.onNodeWithTag("recording-play-next-prompt").assertIsFocused()
        fixture.key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(AppPlaybackTarget.Recording(DvrEntryId(8)), fixture.runtime.activeTarget.value) }
    }

    @Test fun warmReturnDoesNotOfferTheDismissedCrossingAgain() {
        openRecording(8, autoContinue = true)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeUntil(6_000) {
            compose.onAllNodes(hasTestTag("player-banner") or hasTestTag("player-stop"))
                .fetchSemanticsNodes().isEmpty()
        }
        fixture.advanceRecordingTo(nearEnd)
        compose.onNodeWithTag("recording-play-next-prompt").assertIsFocused()
        fixture.backKeyCycle()
        compose.onNodeWithTag("recording-play-next-prompt").assertDoesNotExist()
        compose.onNodeWithTag("player-banner").assertDoesNotExist()
        compose.onNodeWithTag("player-stop").assertDoesNotExist()
        assertTrue(fixture.topRoute() is RecordingPlayerKey)
        fixture.backKeyCycle()
        assertEquals(RecordingsKey, fixture.topRoute())
        compose.onNodeWithTag("recording-list-entry-7").assertExists()
        fixture.focus("recording-list-entry-7")
        fixture.key(Key.DirectionCenter)
        fixture.settle()
        compose.onNodeWithTag("recording-list-entry-7").assertDoesNotExist()
        compose.onNodeWithTag("recording-play-next-prompt").assertDoesNotExist()
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-screen").assertExists()
        compose.onNodeWithTag("recording-end-countdown").assertDoesNotExist()
    }

    @Test fun aFinishedRouteWithoutAnOwnedSnapshotStillStopsAndCloses() {
        openRecording()
        compose.runOnIdle { fixture.runtime.notePlaybackIntent() }
        fixture.finishRecording()
        compose.onNodeWithTag("recording-list-entry-7").assertExists()
        compose.runOnIdle { assertNull(fixture.runtime.activeTarget.value) }
    }

    @Test fun losingTheFinishedSessionsAuthorityStopsAndCloses() {
        openRecording(8)
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-screen").assertExists()
        fixture.loseSession()
        compose.onNodeWithTag("recording-list-entry-7").assertExists()
        compose.onNodeWithTag("recording-end-screen").assertDoesNotExist()
        compose.runOnIdle { assertNull(fixture.runtime.activeTarget.value) }
    }

    @Test fun stopDismissesTheFinishedRouteAndItsOffers() {
        openRecording(8, autoContinue = true)
        // Don't let idle synchronization finish the countdown before the session Stop.
        compose.mainClock.autoAdvance = false
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-screen").assertExists()
        compose.onNodeWithTag("recording-end-countdown").assertExists()
        compose.runOnIdle {
            assertEquals(7L, (fixture.runtime.activeTarget.value as? AppPlaybackTarget.Recording)?.recordingId?.value)
        }
        fixture.holdPlaybackCommands()
        try {
            val stop = compose.runOnIdle { fixture.runtime.stopFromSession { true } }
            compose.mainClock.advanceTimeUntil(2_000) { !fixture.playerVisible }
            compose.onNodeWithTag("recording-end-screen").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(10_001)
            compose.runOnIdle {
                assertFalse("teardown is still held", stop.isCompleted)
                assertEquals(7L, (fixture.runtime.activeTarget.value as? AppPlaybackTarget.Recording)?.recordingId?.value)
            }
        } finally {
            fixture.releasePlaybackCommands()
        }
        fixture.settle()
        compose.runOnIdle { assertNull(fixture.runtime.activeTarget.value) }
    }

    @Test fun backWithPendingOfferDoesNotStopASelectionFromTheOrigin() {
        openRecording(8, 9)
        fixture.finishRecording()
        fixture.holdPlaybackCommands()
        try {
            compose.onNodeWithTag("recording-end-card-0").performSemanticsAction(SemanticsActions.OnClick)
            fixture.settle()
            compose.runOnIdle { fixture.back.onBackPressed() }
            compose.onNodeWithTag("recording-end-screen").assertDoesNotExist()
            fixture.allowRecording()
            compose.playFromRecordings(fixture, 9)
        } finally {
            fixture.releasePlaybackCommands()
        }
        fixture.settle()
        compose.runOnIdle { assertEquals(AppPlaybackTarget.Recording(DvrEntryId(9)), fixture.runtime.activeTarget.value) }
        fixture.presentFirstFrame()
        compose.onNodeWithTag("recording-list-entry-9").assertDoesNotExist()
    }

    @Test fun backDuringEndedOfferTeardownStillClearsPlayback() {
        openRecording(8)
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-card-0").performSemanticsAction(SemanticsActions.OnClick)
        fixture.settle()
        fixture.holdPlaybackCommands()
        try {
            fixture.finishRecording()
            compose.mainClock.advanceTimeUntil(2_000) { fixture.playbackCommandHeld }
            compose.runOnIdle {
                assertEquals(AppPlaybackTarget.Recording(DvrEntryId(8)), fixture.runtime.activeTarget.value)
                fixture.back.onBackPressed()
            }
            compose.onNodeWithTag("recording-list-entry-7").assertExists()
            compose.onNodeWithTag("recording-end-screen").assertDoesNotExist()
        } finally {
            fixture.releasePlaybackCommands()
        }
        fixture.settle()
        compose.runOnIdle {
            assertNull(fixture.runtime.activeTarget.value)
            assertNull(fixture.runtime.finishedRecording.value)
        }
    }

    private fun openRecording(
        vararg offers: Long, autoContinue: Boolean = false, chain: Boolean = false, programmeEndMs: Long? = null,
        shortChain: Boolean = false, warmPlaybackMode: WarmPlaybackMode? = null,
        chainFileStart: Duration = chainStart,
    ) = compose.openRecording(fixture, *offers, autoContinue = autoContinue, chain = chain, programmeEndMs = programmeEndMs,
        shortChain = shortChain, warmPlaybackMode = warmPlaybackMode, chainFileStart = chainFileStart)

    @Test fun warmRecordingRequiresMatchingTargetSelectionAndCurrentSession() {
        val current = requireNotNull(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(CapabilityAccess.ALLOWED, CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create())).currentSession)
        val other = requireNotNull(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(CapabilityAccess.ALLOWED, CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create())).currentSession)
        val id = DvrEntryId(42)
        val target = AppPlaybackTarget.Recording(id)
        val selection = RecordingPlaybackSelection(current, id)
        assertEquals(id, warmRecordingId(target, selection, current))
        assertNull(warmRecordingId(AppPlaybackTarget.Recording(DvrEntryId(43)), selection, current))
        assertNull(warmRecordingId(target, selection, other))
        assertNull(warmRecordingId(null, selection, current))
        assertNull(warmRecordingId(target, null, current))
        assertNull(warmRecordingId(target, selection, null))
    }

    @Test fun browseTreatsATargetAsPlayingOnlyWhileItCanPlay() {
        val target = AppPlaybackTarget.Live(ChannelId(1))
        listOf(AppPlaybackState.Starting, AppPlaybackState.Playing, AppPlaybackState.Buffering,
            AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 1_000L)).forEach {
            assertEquals(target, playingTarget(target, it))
        }
        listOf(AppPlaybackState.Finished, AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)).forEach {
            assertNull(playingTarget(target, it))
        }
        assertNull(playingTarget(null, AppPlaybackState.Playing))
    }

    @Test
    fun keyCarriesOnlyTheStartPolicyWithoutACallerResumePosition() {
        assertEquals(
            RecordingStartMode.RESUME,
            RecordingPlayerKey(recordingId = 42).start,
        )
        assertEquals(
            RecordingStartMode.START_OVER,
            RecordingPlayerKey(recordingId = 42, start = RecordingStartMode.START_OVER).start,
        )
    }

    @Test
    fun playerKeyPreservesTheRequestedPlaybackStart() {
        assertEquals(
            RecordingPlayerKey(recordingId = 42, start = RecordingStartMode.RESUME),
            recordingPlayerKey(DvrEntryId(42), RecordingPlaybackStart.RESUME),
        )
        assertEquals(
            RecordingPlayerKey(recordingId = 42, start = RecordingStartMode.START_OVER),
            recordingPlayerKey(DvrEntryId(42), RecordingPlaybackStart.START_OVER),
        )
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingPlaybackRouteTestOfferFailures(private val failure: Failure) {
    enum class Failure { Rejected, EndedAtInstall, RetiredBeforePresentation }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = Failure.entries.map { arrayOf(it) }
    }

    @get:Rule val compose = createComposeRule()
    private val fixture = NavigationJourneyFixture(compose, ServerSettings(host = javaClass.simpleName))
    @After fun after() = fixture.close()

    @Test fun removesFailedOfferAndPlaysTheRemainingOffer() {
        compose.openRecording(fixture, 8, 9)
        fixture.finishRecording()
        compose.onNodeWithTag("recording-end-card-0").assertIsFocused()
        when (failure) {
            Failure.Rejected -> fixture.rejectNextRecording()
            Failure.EndedAtInstall -> fixture.endNextRecordingAtInstall = true
            Failure.RetiredBeforePresentation -> Unit
        }
        fixture.key(Key.Enter)
        if (failure == Failure.RetiredBeforePresentation) {
            compose.runOnIdle {
                assertEquals(AppPlaybackTarget.Recording(DvrEntryId(8)), fixture.runtime.activeTarget.value)
            }
            fixture.retirePlayback()
        }
        compose.onNodeWithTag("recording-end-screen").assertExists()
        compose.onNodeWithTag("recording-end-card-0").assertIsFocused()
        compose.onNodeWithText("Other episode 8").assertDoesNotExist()
        compose.onNodeWithText("Other episode 9").assertExists()
        if (failure == Failure.EndedAtInstall) {
            compose.runOnIdle { assertNull(fixture.runtime.activeTarget.value) }
        }
        fixture.allowRecording()
        compose.playAndPresentOffer(fixture)
    }
}

@OptIn(TvheadendTestingApi::class)
private fun ComposeContentTestRule.openRecording(
    fixture: NavigationJourneyFixture,
    vararg offers: Long,
    autoContinue: Boolean = false,
    chain: Boolean = false,
    programmeEndMs: Long? = null,
    shortChain: Boolean = false,
    warmPlaybackMode: WarmPlaybackMode? = null,
    liveWindow: Boolean = false,
    chainFileStart: Duration = chainStart,
) {
    val origin = NavigationJourneyFixture.NOW - if (liveWindow) 60.minutes else 120.minutes
    val start = origin + if (chain) chainFileStart else 80.minutes
    fixture.app(withRecording = true, driveRecordingEnd = true, autoContinue = autoContinue, programmeEndMs = programmeEndMs,
        recordingStart = origin.takeIf { chain || liveWindow }, warmPlaybackMode = warmPlaybackMode,
        scheduled = offers.mapIndexed { index, id ->
            DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, title = "Other episode $id",
                channelId = if (chain) ChannelId(6) else null,
                autorecRuleId = if (shortChain) null else AutorecRuleId("journey"), start = start + index.minutes,
                stop = start + (index + if (chain) 60 else 1).minutes,
                files = if (chain) listOf(TvheadendTestResultFactory.recordingFile(
                    serverPath = "part.ts", start = start, stop = start + if (shortChain) 10.seconds else 60.minutes)) else emptyList())
        })
    onNodeWithTag("nav-recordings").performSemanticsAction(SemanticsActions.OnClick)
    playFromRecordings(fixture, 7)
    runOnIdle {
        assertEquals("state=${fixture.runtime.state.value}",
            AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
        assertTrue("recording player route", fixture.playerVisible)
    }
}

private fun ComposeContentTestRule.playFromRecordings(fixture: NavigationJourneyFixture, id: Long) {
    fixture.focus("recording-list-entry-$id")
    fixture.key(Key.Menu)
    onNodeWithTag("browse-context-action-play").performSemanticsAction(SemanticsActions.OnClick)
    fixture.settle()
    fixture.presentFirstFrame()
}

private fun ComposeContentTestRule.playAndPresentOffer(fixture: NavigationJourneyFixture) {
    onNodeWithTag("recording-end-card-0").performSemanticsAction(SemanticsActions.OnClick)
    fixture.settle()
    runOnIdle {
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(9)), fixture.runtime.activeTarget.value)
    }
    onNodeWithTag("recording-end-screen").assertExists()
    fixture.presentFirstFrame()
    onNodeWithTag("recording-end-screen").assertDoesNotExist()
    onNodeWithTag("recording-list-entry-7").assertDoesNotExist()
    runOnIdle { assertNull(fixture.runtime.finishedRecording.value) }
}
