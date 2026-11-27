package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.settings.ServerSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingPlaybackRouteTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = NavigationJourneyFixture(compose, ServerSettings(host = javaClass.simpleName))
    @After fun after() = fixture.close()

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
                org.junit.Assert.assertFalse("teardown is still held", stop.isCompleted)
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

    private fun openRecording(vararg offers: Long, autoContinue: Boolean = false) =
        compose.openRecording(fixture, *offers, autoContinue = autoContinue)

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

private fun ComposeContentTestRule.openRecording(
    fixture: NavigationJourneyFixture,
    vararg offers: Long,
    autoContinue: Boolean = false,
) {
    val start = Clock.System.now() - 40.minutes
    fixture.app(withRecording = true, driveRecordingEnd = true, autoContinue = autoContinue,
        scheduled = offers.mapIndexed { index, id ->
            DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, title = "Other episode $id",
                autorecRuleId = AutorecRuleId("journey"), start = start + index.minutes,
                stop = start + (index + 1).minutes)
        })
    onNodeWithTag("nav-recordings").performSemanticsAction(SemanticsActions.OnClick)
    playFromRecordings(fixture, 7)
    runOnIdle {
        assertEquals("state=${fixture.runtime.state.value}",
            AppPlaybackTarget.Recording(DvrEntryId(7)), fixture.runtime.activeTarget.value)
        org.junit.Assert.assertTrue("recording player route", fixture.playerVisible)
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
