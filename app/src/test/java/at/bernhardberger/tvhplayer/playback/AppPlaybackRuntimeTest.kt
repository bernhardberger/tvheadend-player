package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPlaybackRuntimeTest {
    private val repositoryRoot = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }.first { File(it, ".git").exists() }

    @Test
    fun liveTargetSetsAppOwnedPlayIntentBeforeCoordinatorInstall() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/at/bernhardberger/tvhplayer/playback/AppPlaybackRuntime.kt",
        ).readText()
        val playIndex = source.indexOf("player.play()")
        val installIndex = source.indexOf("coordinator.setLiveTarget(")

        assertTrue(playIndex >= 0)
        assertTrue(playIndex < installIndex)
    }

    @Test
    fun unavailableSdkTimeshiftStateHasUnavailablePresentation() {
        assertEquals(
            AppTimeshiftState(),
            LiveTimeshiftState.Unavailable.toAppPresentation(),
        )
    }

    @Test
    fun nullMeasuredTimeshiftDoesNotInventSeekableHistory() {
        assertEquals(
            AppTimeshiftState(available = true),
            measuredTimeshiftPresentation(
                bufferedDuration = null,
                positionBehindLive = null,
                serverPaused = false,
            ),
        )
    }

    @Test
    fun measuredTimeshiftPreservesObservedBufferPositionAndPause() {
        assertEquals(
            AppTimeshiftState(
                available = true,
                paused = true,
                bufferStartMs = -120_000L,
                positionMs = -30_000L,
            ),
            measuredTimeshiftPresentation(
                bufferedDuration = 2.minutes,
                positionBehindLive = 30.seconds,
                serverPaused = true,
            ),
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

    @Test
    fun liveRecoveryFenceRejectsARecordingReplacement() {
        val fence = LiveRecoveryFence(
            target = AppPlaybackTarget.Live(ChannelId(7)),
            targetEpoch = 20L,
        )

        assertFalse(
            fence.matches(
                activeTarget = AppPlaybackTarget.Recording(DvrEntryId(9)),
                activeTargetEpoch = 21L,
                selectionChannelId = ChannelId(7),
            ),
        )
    }

    @Test
    fun liveRecoveryFenceRejectsANewerLiveTarget() {
        val fence = LiveRecoveryFence(
            target = AppPlaybackTarget.Live(ChannelId(11)),
            targetEpoch = 22L,
        )

        assertFalse(
            fence.matches(
                activeTarget = AppPlaybackTarget.Live(ChannelId(13)),
                activeTargetEpoch = 23L,
                selectionChannelId = ChannelId(13),
            ),
        )
    }

    @Test
    fun liveRecoveryFenceAcceptsOnlyTheSameLiveTargetEpochAndSelection() {
        val target = AppPlaybackTarget.Live(ChannelId(17))
        val fence = LiveRecoveryFence(target = target, targetEpoch = 24L)

        assertTrue(
            fence.matches(
                activeTarget = target,
                activeTargetEpoch = 24L,
                selectionChannelId = ChannelId(17),
            ),
        )
        assertFalse(
            fence.matches(
                activeTarget = target,
                activeTargetEpoch = 24L,
                selectionChannelId = ChannelId(19),
            ),
        )
    }

    @Test
    fun replacementConcealsThePreviousTargetsFrame() {
        val previous = AppVideoPresentation(epoch = 1L, visible = true)

        assertEquals(
            AppVideoPresentation(epoch = 2L, visible = false),
            previous.beginTarget(epoch = 2L),
        )
    }

    @Test
    fun staleFirstFrameCannotRevealTheCurrentTarget() {
        val current = AppVideoPresentation(epoch = 2L, visible = false)

        assertEquals(
            current,
            current.onFirstFrame(frameEpoch = 1L, activeTargetEpoch = 2L),
        )
    }

    @Test
    fun currentTargetsFirstFrameRevealsVideoOnlyAfterTargetActivation() {
        val current = AppVideoPresentation(epoch = 2L, visible = false)

        assertEquals(
            current,
            current.onFirstFrame(frameEpoch = 2L, activeTargetEpoch = null),
        )
        assertEquals(
            AppVideoPresentation(epoch = 2L, visible = true),
            current.onFirstFrame(frameEpoch = 2L, activeTargetEpoch = 2L),
        )
    }

    @Test
    fun liveBackgroundStopsAndForegroundRetunesExactlyOnce() {
        val lifecycle = ForegroundPlaybackLifecycle()
        val target = AppPlaybackTarget.Live(ChannelId(7))

        assertEquals(
            ForegroundPlaybackAction.StopLive,
            lifecycle.onBackgrounded(
                activeTarget = target,
                activeTargetEpoch = 11L,
                recordingPlayWhenReady = true,
            ),
        )
        assertEquals(
            ForegroundPlaybackAction.ResumeLive(ChannelId(7)),
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
    }

    @Test
    fun recordingBackgroundPausesAndResumesTheSameTargetInPlace() {
        val lifecycle = ForegroundPlaybackLifecycle()
        val target = AppPlaybackTarget.Recording(DvrEntryId(19))

        assertEquals(
            ForegroundPlaybackAction.PauseRecording,
            lifecycle.onBackgrounded(
                activeTarget = target,
                activeTargetEpoch = 12L,
                recordingPlayWhenReady = true,
            ),
        )
        assertEquals(
            ForegroundPlaybackAction.ResumeRecording,
            lifecycle.onForegrounded(activeTarget = target, activeTargetEpoch = 12L),
        )
    }

    @Test
    fun foregroundDoesNotRestartARecordingThatWasAlreadyPaused() {
        val lifecycle = ForegroundPlaybackLifecycle()
        val target = AppPlaybackTarget.Recording(DvrEntryId(23))

        lifecycle.onBackgrounded(
            activeTarget = target,
            activeTargetEpoch = 13L,
            recordingPlayWhenReady = false,
        )

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(activeTarget = target, activeTargetEpoch = 13L),
        )
    }

    @Test
    fun targetReplacementInvalidatesAPendingRecordingResume() {
        val lifecycle = ForegroundPlaybackLifecycle()
        val previousTarget = AppPlaybackTarget.Recording(DvrEntryId(29))

        lifecycle.onBackgrounded(
            activeTarget = previousTarget,
            activeTargetEpoch = 14L,
            recordingPlayWhenReady = true,
        )
        lifecycle.onTargetCommand()

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(
                activeTarget = AppPlaybackTarget.Recording(DvrEntryId(31)),
                activeTargetEpoch = 15L,
            ),
        )
    }

    @Test
    fun staleRecordingIdentityCannotResumeAfterBackground() {
        val lifecycle = ForegroundPlaybackLifecycle()

        lifecycle.onBackgrounded(
            activeTarget = AppPlaybackTarget.Recording(DvrEntryId(37)),
            activeTargetEpoch = 16L,
            recordingPlayWhenReady = true,
        )

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(
                activeTarget = AppPlaybackTarget.Recording(DvrEntryId(37)),
                activeTargetEpoch = 17L,
            ),
        )
    }

    @Test
    fun liveTargetThatFinishesStartingInBackgroundIsStoppedThenRetunedOnce() {
        val lifecycle = ForegroundPlaybackLifecycle()

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onBackgrounded(
                activeTarget = null,
                activeTargetEpoch = null,
                recordingPlayWhenReady = true,
            ),
        )
        assertEquals(
            ForegroundPlaybackAction.StopLive,
            lifecycle.onTargetStarted(
                activeTarget = AppPlaybackTarget.Live(ChannelId(41)),
                activeTargetEpoch = 18L,
                recordingPlayWhenReady = true,
            ),
        )
        assertEquals(
            ForegroundPlaybackAction.ResumeLive(ChannelId(41)),
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
    }

    @Test
    fun explicitStopInvalidatesAPendingLiveRetune() {
        val lifecycle = ForegroundPlaybackLifecycle()

        lifecycle.onBackgrounded(
            activeTarget = AppPlaybackTarget.Live(ChannelId(43)),
            activeTargetEpoch = 19L,
            recordingPlayWhenReady = true,
        )
        lifecycle.onTargetCommand()

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
    }

    @Test
    fun recordingRetryReadsItsRequestInsideTargetCommandSerialization() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/at/bernhardberger/tvhplayer/playback/AppPlaybackRuntime.kt",
        ).readText()
        val retryRecording = source.substring(
            startIndex = source.indexOf("suspend fun retryRecording()"),
            endIndex = source.indexOf("suspend fun pauseTimeshift()"),
        )

        assertTrue(
            retryRecording.indexOf("targetCommandMutex.withLock") in
                0 until retryRecording.indexOf("lastRecordingRequest"),
        )
    }

    @Test
    fun recordingForegroundResumeUsesOnlyTheExistingPlayerTarget() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/at/bernhardberger/tvhplayer/playback/AppPlaybackRuntime.kt",
        ).readText()
        val foregroundAction = source.substring(
            startIndex = source.indexOf("private suspend fun applyForegroundPlaybackAction"),
            endIndex = source.indexOf("private fun beginTargetPresentation"),
        )

        assertTrue("ResumeRecording -> player.play()" in foregroundAction)
        assertFalse("setRecordingTarget" in foregroundAction)
        assertFalse("playRecording" in foregroundAction)
        assertFalse("seekTo" in foregroundAction)
    }
}
