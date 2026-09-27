package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStatusPresentationTest {

    @Test
    fun ordinaryChannelStart_isCompactAndNonBlocking() {
        assertEquals(
            PlaybackStatusPresentation.COMPACT_TUNING,
            playbackStatusPresentation(
                connectionAvailable = true,
                playbackStarting = true,
                playbackRecovering = false,
                playbackPlaying = false,
            )
        )
    }

    @Test
    fun genuineRecovery_orConnectionLoss_isFullScreen() {
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(true, false, true, false),
        )
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(false, true, false, false),
        )
        assertEquals(
            PlaybackStatusPresentation.CHANNEL_UNAVAILABLE,
            playbackStatusPresentation(
                connectionAvailable = true,
                playbackStarting = false,
                playbackRecovering = false,
                playbackPlaying = false,
                playbackFailed = true,
            ),
        )
        assertEquals(
            CompactTuningVisibilityAction.HIDE_IMMEDIATELY,
            compactTuningVisibilityAction(true, PlaybackStatusPresentation.CHANNEL_UNAVAILABLE, true),
        )
    }

    @Test
    fun playingSession_hasNoWaitingPresentation() {
        assertEquals(
            PlaybackStatusPresentation.NONE,
            playbackStatusPresentation(true, false, false, true),
        )
    }

    @Test
    fun compactTuning_waitsBeforeAppearingAndThenKeepsAnOpaqueInterval() {
        assertEquals(
            CompactTuningVisibilityAction.SHOW_AFTER_DELAY,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.COMPACT_TUNING,
                currentlyVisible = false,
            ),
        )
        assertEquals(500L, COMPACT_TUNING_DELAY_MS)

        assertEquals(
            CompactTuningVisibilityAction.KEEP_VISIBLE,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.COMPACT_TUNING,
                currentlyVisible = true,
            ),
        )
        assertEquals(
            CompactTuningVisibilityAction.HIDE_AFTER_MINIMUM,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.NONE,
                currentlyVisible = true,
            ),
        )
        assertEquals(150L, COMPACT_TUNING_FADE_IN_MS)
        assertEquals(600L, COMPACT_TUNING_MINIMUM_OPAQUE_MS)
    }

    @Test
    fun recoveryAndInactiveScreen_hideCompactTuningImmediately() {
        assertEquals(
            CompactTuningVisibilityAction.HIDE_IMMEDIATELY,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.FULL_RECOVERY,
                currentlyVisible = true,
            ),
        )
        assertEquals(
            CompactTuningVisibilityAction.HIDE_IMMEDIATELY,
            compactTuningVisibilityAction(
                screenActive = false,
                presentation = PlaybackStatusPresentation.COMPACT_TUNING,
                currentlyVisible = true,
            ),
        )
        assertEquals(
            CompactTuningVisibilityAction.KEEP_HIDDEN,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.NONE,
                currentlyVisible = false,
            ),
        )
    }

    @Test
    fun serverStop_presentsTheUnavailableCardInEveryPlayerState() {
        // READY (presented), BUFFERING or starting, and paused (presented, no play intent) alike.
        for ((starting, presented) in listOf(false to true, true to false, false to false)) {
            for (issue in listOf(false, true)) {
                val presentation = playbackStatusPresentation(
                    connectionAvailable = true,
                    playbackStarting = starting,
                    playbackRecovering = false,
                    playbackPlaying = presented,
                    playbackFailed = issue,
                    liveInterrupted = true,
                )
                assertEquals(PlaybackStatusPresentation.CHANNEL_UNAVAILABLE, presentation)
                // The compact tuning and buffering spinners yield to the card.
                assertEquals(
                    CompactTuningVisibilityAction.HIDE_IMMEDIATELY,
                    compactTuningVisibilityAction(true, presentation, currentlyVisible = true),
                )
                assertTrue(presentation != PlaybackStatusPresentation.NONE)
            }
        }
    }

    @Test
    fun serverStart_clearsTheInterruption() {
        assertEquals(
            PlaybackStatusPresentation.NONE,
            playbackStatusPresentation(true, false, false, true, liveInterrupted = false),
        )
        assertFalse(liveInterrupted(serverStopped = false, playingLiveChannelId = CHANNEL, shownChannelId = CHANNEL))
    }

    @Test
    fun connectionLossAndPlayerRecovery_takePrecedenceOverTheInterruption() {
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(false, false, false, true, liveInterrupted = true),
        )
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(true, false, true, false, liveInterrupted = true),
        )
    }

    @Test
    fun interruption_belongsOnlyToTheLiveChannelOnScreen() {
        assertTrue(liveInterrupted(serverStopped = true, playingLiveChannelId = CHANNEL, shownChannelId = CHANNEL))
        // Another channel is being tuned, or a recording (no live channel) is playing.
        assertFalse(liveInterrupted(serverStopped = true, playingLiveChannelId = ChannelId(2), shownChannelId = CHANNEL))
        assertFalse(liveInterrupted(serverStopped = true, playingLiveChannelId = null, shownChannelId = CHANNEL))
    }

    @Test
    fun interruptionMessage_neverDuplicatesAnIssueOrMasksAFailure() {
        assertTrue(liveInterruptionMessageShown(liveInterrupted = true, subscriptionIssuePresent = false, playbackFailed = false))
        assertFalse(liveInterruptionMessageShown(liveInterrupted = true, subscriptionIssuePresent = true, playbackFailed = false))
        assertFalse(liveInterruptionMessageShown(liveInterrupted = true, subscriptionIssuePresent = false, playbackFailed = true))
        assertFalse(liveInterruptionMessageShown(liveInterrupted = false, subscriptionIssuePresent = false, playbackFailed = false))
    }

    private companion object {
        val CHANNEL = ChannelId(1)
    }
}
