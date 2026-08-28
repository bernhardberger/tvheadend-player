package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPlaybackRuntimeTest {
    @Test
    fun selectedProfileUsesTheReleasedSdkIdentifierDirectly() {
        assertEquals(
            "11111111111111111111111111111111",
            selectedStreamProfileId("11111111111111111111111111111111")?.value,
        )
        assertNull(selectedStreamProfileId(""))
    }

    @Test
    fun unavailableSdkTimeshiftStateHasUnavailablePresentation() {
        assertEquals(
            AppTimeshiftState(),
            LiveTimeshiftState.Unavailable.toAppPresentation(),
        )
    }

    @Test
    fun olderTargetCompletionCannotOverwriteANewerFailedTarget() {
        val epoch = PlaybackPresentationEpoch()
        var target: AppPlaybackTarget? = null
        var state: AppPlaybackState = AppPlaybackState.Idle
        val olderLive = epoch.begin()
        val newerRecording = epoch.begin()

        assertFalse(epoch.publishIfCurrent(olderLive) {
            target = AppPlaybackTarget.Live(ChannelId(7))
            state = AppPlaybackState.Playing
        })
        assertTrue(epoch.publishIfCurrent(newerRecording) {
            target = null
            state = AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)
        })

        assertNull(target)
        assertEquals(
            AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED),
            state,
        )
    }

    @Test
    fun olderTargetCompletionCannotSurviveANewerStop() {
        val epoch = PlaybackPresentationEpoch()
        var target: AppPlaybackTarget? = null
        var state: AppPlaybackState = AppPlaybackState.Starting
        val targetCommand = epoch.begin()
        val stopCommand = epoch.begin()

        assertFalse(epoch.publishIfCurrent(targetCommand) {
            target = AppPlaybackTarget.Live(ChannelId(9))
        })
        assertTrue(epoch.publishIfCurrent(stopCommand) {
            target = null
            state = AppPlaybackState.Idle
        })

        assertNull(target)
        assertEquals(AppPlaybackState.Idle, state)
    }

    @Test
    fun onlyTheLatestSuccessfulTargetCanPublishPresentation() {
        val epoch = PlaybackPresentationEpoch()
        var target: AppPlaybackTarget? = null
        val olderRecording = epoch.begin()
        val newerLive = epoch.begin()

        assertTrue(epoch.publishIfCurrent(newerLive) {
            target = AppPlaybackTarget.Live(ChannelId(12))
        })
        assertFalse(epoch.publishIfCurrent(olderRecording) {
            target = AppPlaybackTarget.Recording(DvrEntryId(3))
        })

        assertEquals(AppPlaybackTarget.Live(ChannelId(12)), target)
    }

    @Test
    fun recoveryCannotStartFromATargetSupersededByStop() {
        val epoch = PlaybackPresentationEpoch()
        val activeTarget = epoch.begin()
        epoch.begin()

        assertNull(epoch.beginIfCurrent(activeTarget))
    }
}
