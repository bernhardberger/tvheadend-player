package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSeekBackRestorationTest {
    @Test fun liveAncestorBackClearsPendingAndDispatchedPreviewWithoutReappearance() = runTest {
        for (dispatched in listOf(false, true)) {
            val fixture = TimeshiftTestFixture(600.seconds).apply { updateHistory(0.seconds, 600.seconds) }
            val state = fixture.state.value.toAppPresentation(fixture.playbackPosition(540.seconds)).copy(paused = true)
            val owner = LiveTimelinePresentationState(this, { 0L }, { testScheduler.currentTime })
            var dispatches = 0
            owner.queueRelativeSeek(state, -30_000L, "unavailable", "expired", "replaced", "uncertain") {
                dispatches++
                fixture.completed(readerReached = null)
            }
            if (dispatched) { advanceTimeBy(400L); runCurrent() }
            val action = playerBackAction(PlayerSurface.LIVE, PlayerForegroundLayer.CONTROLS, owner.seekPreviewPhase(true))
            assertEquals(if (dispatched) PlayerBackAction.DISMISS_SEEK_FEEDBACK else PlayerBackAction.CANCEL_PENDING_SEEK, action)
            // The exact callbacks used by VideoPlayerScreen's ancestor Back handler.
            if (action == PlayerBackAction.CANCEL_PENDING_SEEK) owner.cancelPendingSeek() else owner.dismissDispatchedFeedback()
            assertNull(owner.previewForTimeline(state.timeline))
            assertEquals(PlayerSeekPreviewPhase.NONE, owner.seekPreviewPhase(true))
            assertEquals(0.55f, playerChromeAlpha(timelineFocused = true, previewing = owner.preview != null), 0f)
            advanceTimeBy(2_000L); runCurrent()
            owner.sampleTimeshiftPresentation { state }
            assertNull(owner.preview)
            assertEquals(if (dispatched) 1 else 0, dispatches)
            owner.dispose()
        }
    }

    @Test fun recordingAncestorBackClearsPendingAndDispatchedPreviewWithoutRedispatch() = runTest {
        for (dispatched in listOf(false, true)) {
            val seeks = mutableListOf<Long>()
            val owner = RecordingTimelinePresentationState(
                scope = this, currentPositionMs = { 60_000L }, currentDurationMs = { 120_000L },
                currentIsPlaying = { false }, currentCanSeek = { true }, currentEpochMillis = { 0L },
                seekAbsolute = seeks::add, feedbackSettled = { false },
            )
            owner.queueSeek(-30_000L)
            if (dispatched) owner.commitPendingSeek()
            val action = playerBackAction(PlayerSurface.RECORDING, PlayerForegroundLayer.CONTROLS, owner.seekPreviewPhase(true))
            assertEquals(if (dispatched) PlayerBackAction.DISMISS_SEEK_FEEDBACK else PlayerBackAction.CANCEL_PENDING_SEEK, action)
            // The exact callbacks used by RecordingPlayerScreen's ancestor Back handler.
            if (action == PlayerBackAction.CANCEL_PENDING_SEEK) owner.cancelPendingSeek() else owner.dismissDispatchedFeedback()
            assertNull(owner.pendingTargetMs)
            assertNull(owner.pendingOriginMs)
            assertEquals(PlayerSeekPreviewPhase.NONE, owner.seekPreviewPhase(true))
            assertEquals(0.55f, playerChromeAlpha(timelineFocused = true, previewing = owner.pendingTargetMs != null), 0f)
            advanceTimeBy(2_000L); runCurrent()
            assertNull(owner.pendingTargetMs)
            assertEquals(if (dispatched) listOf(30_000L) else emptyList<Long>(), seeks)
            owner.dispose()
        }
    }
}
