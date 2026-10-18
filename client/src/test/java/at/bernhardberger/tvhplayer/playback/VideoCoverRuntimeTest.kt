@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure

import android.app.Application
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DecoderCounters
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackRuntimeTest.Companion.exercise
import java.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The video stays covered from the start of a target install until that target's own first frame. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VideoCoverRuntimeTest {
    @Test fun installCoversTheOldPictureBeforeThePlayerPausesAndTheNewTargetStarts() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            renderFirstFrame()
            val oldEpoch = runtime.videoPresentation.value.epoch
            assertTrue(runtime.videoPresentation.value.visible)
            var atPause: AppVideoPresentation? = null
            afterPause = { if (atPause == null) atPause = runtime.videoPresentation.value }
            live(channel = 2)

            // Covered while the old target is still the presented one: before its pause and the new start.
            assertEquals(AppVideoPresentation(epoch = oldEpoch, visible = false), atPause)
            assertNotEquals(oldEpoch, runtime.videoPresentation.value.epoch)
            assertFalse(runtime.videoPresentation.value.visible)
            renderFirstFrame()
            assertTrue(runtime.videoPresentation.value.visible)
        }

    @Test fun failedReplacementUncoversThePictureOfTheTargetThatStays() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live(timeshift = false)
            renderFirstFrame()
            val kept = runtime.videoPresentation.value
            assertTrue(kept.visible)
            session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
            }
            await { install.isCompleted }

            assertFalse(install.await()?.isStarted == true)
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            assertEquals(kept, runtime.videoPresentation.value)
        }

    @Test fun failedReplacementUncoversAndPausesTheTargetThatStaysWhenItsPauseWasHeld() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            renderFirstFrame()
            val kept = runtime.videoPresentation.value
            val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
            assertEquals(null, runtime.pauseTimeshiftPlayback())
            assertEquals(emptyList<Int>(), connection.speeds)
            session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))), intent)
            }
            await { install.isCompleted }

            assertFalse(install.await()?.isStarted == true)
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            assertEquals(kept, runtime.videoPresentation.value)
            // The viewer's latest transport intent was Pause: the channel that stays takes it.
            await { connection.speeds == listOf(0) }
            settle()
            assertEquals(listOf(0), connection.speeds)
            assertFalse(player.playWhenReady)
            assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        }

    @Test fun failedReplacementWhoseHeldPauseTheServerRejectsKeepsPlayingAndTellsTheViewer() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            renderFirstFrame()
            val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
            assertEquals(null, runtime.pauseTimeshiftPlayback())
            connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
            session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))), intent)
            }
            await { install.isCompleted }

            assertFalse(install.await()?.isStarted == true)
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            // The channel that stays was asked once, refused, and plays on; the viewer is told.
            await { runtime.livePauseNotice.value != null }
            settle()
            assertEquals(listOf(0), connection.speeds)
            assertTrue(player.playWhenReady)
            assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        }

    @Test fun firstFrameRenderedDuringAFailedReplacementUncoversTheTargetThatStays() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live(timeshift = false)
            val kept = runtime.videoPresentation.value
            assertFalse(kept.visible)
            session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
            // The kept target's first frame arrives while the replacement is still installing.
            assertTrue(player.playWhenReady)
            var pausedAtFrame = false
            beforeLiveBinding = {
                beforeLiveBinding = {}
                pausedAtFrame = !player.playWhenReady
                playerListeners.toList().forEach { it.onRenderedFirstFrame() }
            }
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
            }
            await { install.isCompleted }

            assertTrue(pausedAtFrame)
            assertFalse(install.await()?.isStarted == true)
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            assertEquals(kept.copy(visible = true), runtime.videoPresentation.value)
        }

    @Test fun equalButNewFormatOfTheNextSourceUncoversIt() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            forcedVideoFormat = videoFormat("same")
            renderFirstFrame()
            assertTrue(runtime.videoPresentation.value.visible)
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch
            assertFalse(runtime.videoPresentation.value.visible)

            // The next source reports its own, separately built format, equal to the old one.
            forcedVideoFormat = videoFormat("same")
            renderFirstFrame()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun queuedFirstFrameOfTheOldSourceDoesNotUncoverTheNewTarget() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            forcedVideoFormat = videoFormat("old")
            renderFirstFrame()
            assertTrue(runtime.videoPresentation.value.visible)
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch

            // Still the old source's format: this frame was rendered before the source change.
            renderFirstFrame()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)

            forcedVideoFormat = videoFormat("new")
            renderFirstFrame()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun firstFrameWhileBufferingKeepsTheCoverUntilTheTargetIsReady() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            val epoch = runtime.videoPresentation.value.epoch
            // Media3 renders the first frame while it still buffers: that picture would stand still.
            buffering()
            renderFirstFrame()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            playerReady()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun readyBeforeTheFirstFrameUncoversAtTheFirstFrame() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            assertFalse("ready without a picture stays covered", runtime.videoPresentation.value.visible)
            renderFirstFrame()
            assertTrue(runtime.videoPresentation.value.visible)
        }

    @Test fun pausedTargetUncoversOnceReadyWithItsFirstFrame() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            player.playWhenReady = false
            buffering()
            renderFirstFrame()
            assertFalse(runtime.videoPresentation.value.visible)
            playerReady()
            assertFalse(player.playWhenReady)
            assertTrue("a paused target shows its picture once ready", runtime.videoPresentation.value.visible)
        }

    @Test fun anUnreadyFrameOfTheOldTargetDoesNotUncoverTheNextTarget() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            forcedVideoFormat = videoFormat("old")
            buffering()
            renderFirstFrame()
            assertFalse(runtime.videoPresentation.value.visible)
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch
            // The next target is ready, but its own first frame has not been rendered.
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            renderFirstFrame()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            forcedVideoFormat = videoFormat("new")
            renderFirstFrame()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun failedReplacementKeepsAnUnreadyPictureCoveredUntilTheTargetThatStaysIsReady() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live(timeshift = false)
            buffering()
            renderFirstFrame()
            val kept = runtime.videoPresentation.value
            assertFalse(kept.visible)
            session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
            }
            await { install.isCompleted }

            assertFalse(install.await()?.isStarted == true)
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            assertEquals(kept, runtime.videoPresentation.value)
            playerReady()
            assertEquals(kept.copy(visible = true), runtime.videoPresentation.value)
        }

    @Test fun startupProjectionRequiresTheCurrentInstalledSessionAndServedIntent() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
            val epoch = runtime.liveTargetPresentation(selection)!!.epoch
            assertEquals(AppLiveTargetPresentation(epoch, false, false, false), runtime.liveTargetPresentation(selection))
            val intent = runtime.notePlaybackIntent()
            assertEquals(null, runtime.liveTargetPresentation(selection, intent))
            runtime.notePlaybackIntentServed(intent)
            renderFirstFrame()
            assertEquals(AppLiveTargetPresentation(epoch, true, false, false), runtime.liveTargetPresentation(selection, intent))
            runtime.notePlaybackIntent()
            assertEquals(null, runtime.liveTargetPresentation(selection, intent))

            // Same channel inventory in a new profile/session is not proof for the installed stream.
            session.replaceGeneration(session.observation.value)
            settle()
            val replacement = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
            assertEquals(null, runtime.liveTargetPresentation(selection))
            assertEquals(null, runtime.liveTargetPresentation(replacement))
        }

    @Test fun sameChannelRetuneCannotReuseThePriorEpochOrAnInstallationFrame() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
            forcedVideoFormat = videoFormat("old")
            renderFirstFrame()
            val prior = runtime.liveTargetPresentation(selection)!!
            var duringInstall: AppLiveTargetPresentation? = prior
            beforeLiveBinding = { duringInstall = runtime.liveTargetPresentation(selection) }
            live()
            assertEquals(null, duringInstall)
            val next = runtime.liveTargetPresentation(selection)!!
            assertNotEquals(prior.epoch, next.epoch)
            assertFalse(next.visible)
            renderFirstFrame()
            assertFalse(runtime.liveTargetPresentation(selection)!!.visible)
            forcedVideoFormat = videoFormat("new")
            renderFirstFrame()
            assertEquals(next.copy(visible = true), runtime.liveTargetPresentation(selection))
        }

    @Test fun bufferingAndStoppedIntentsDoNotInventANewStartupPresentation() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
            val intent = runtime.notePlaybackIntent().also(runtime::notePlaybackIntentServed)
            renderFirstFrame()
            val presented = runtime.liveTargetPresentation(selection, intent)
            playerListeners.toList().forEach { it.onPlaybackStateChanged(androidx.media3.common.Player.STATE_BUFFERING) }
            assertEquals(presented, runtime.liveTargetPresentation(selection, intent))
            val stop = scope.async { runtime.stop() }
            await { stop.isCompleted }
            stop.await()
            assertEquals(null, runtime.liveTargetPresentation(selection, intent))
        }

    @Test fun startupProjectionIgnoresLaggedPlayingAndAudioOnlyConsumptionAcrossTargetReplacement() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            forcedPlaybackState = Player.STATE_READY
            forcedPlaying = true
            forcedTracks = audioTracks()
            live(timeshift = false)
            var uiState = runtime.state.value
            var uiTracks = runtime.player.currentTracks
            var lagUi = false
            val resumeUi = CompletableDeferred<Unit>()
            val collector = scope.launch {
                runtime.state.collect { state ->
                    if (lagUi) resumeUi.await()
                    uiState = state
                }
            }
            val tracksConsumer = object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) { if (!lagUi) uiTracks = tracks }
            }
            runtime.player.addListener(tracksConsumer)
            await { uiState is AppPlaybackState.Playing }
            val oldEpoch = runtime.videoPresentation.value.epoch
            lagUi = true
            forcedPlaying = false
            forcedPlaybackState = Player.STATE_BUFFERING
            // New video target: the UI intentionally still consumes the old radio's snapshots.
            forcedTracks = Tracks(audioTracks().groups + Tracks.Group(
                TrackGroup(videoFormat("new")), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
            ))
            live(channel = 2, timeshift = false)
            val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2)))
            val next = requireNotNull(runtime.liveTargetPresentation(selection))
            assertNotEquals(oldEpoch, next.epoch)
            assertTrue(uiState is AppPlaybackState.Playing)
            assertTrue(uiTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected })
            assertTrue(uiTracks.groups.none { it.type == C.TRACK_TYPE_VIDEO })
            assertFalse(next.playing)
            assertFalse(next.audioOnly)
            assertFalse(next.visible)
            forcedPlaybackState = Player.STATE_READY
            forcedPlaying = true
            playerReady()
            val playingVideo = requireNotNull(runtime.liveTargetPresentation(selection))
            assertTrue(playingVideo.playing)
            assertFalse("old radio tracks cannot release this video's cover", playingVideo.audioOnly)
            assertFalse(playingVideo.visible)
            forcedVideoFormat = videoFormat("new")
            renderFirstFrame()
            assertTrue(runtime.liveTargetPresentation(selection)!!.visible)
            lagUi = false
            resumeUi.complete(Unit)
            runtime.player.removeListener(tracksConsumer)
            collector.cancel()
        }

    @Test fun cancelledStartupInstallStopsWithoutRecordingAUserStopEvenAfterProfileWithdrawal() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live(timeshift = false)
            val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
            val intent = runtime.notePlaybackIntent().also(runtime::notePlaybackIntentServed)
            val epoch = requireNotNull(runtime.installedLiveTargetEpoch(selection, intent))
            session.replaceGeneration(session.observation.value)
            assertEquals(null, runtime.liveTargetPresentation(selection, intent))
            val stop = scope.async { runtime.stopAfterLoss(selection, epoch, intent) }
            await { stop.isCompleted }
            stop.await()
            assertEquals(null, runtime.activeTarget.value)
            assertTrue(runtime.state.value is AppPlaybackState.Idle)
            assertFalse(player.playWhenReady)
            assertTrue("non-user cleanup leaves the next player entry admissible", runtime.enterPlayerScreen() != null)
        }

    @Test fun serializedLateStartupCancellationCannotStopAReplacementOnTheSameChannel() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live(timeshift = false)
            val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
            val oldIntent = runtime.notePlaybackIntent().also(runtime::notePlaybackIntentServed)
            val oldEpoch = requireNotNull(runtime.installedLiveTargetEpoch(selection, oldIntent))
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            beforeSettingsRead = { entered.complete(Unit); release.await() }
            val nextIntent = runtime.notePlaybackIntent()
            val replacement = scope.async { runtime.playLive(selection, nextIntent) }
            await { entered.isCompleted }
            val lateCancellation = scope.async { runtime.stopAfterLoss(selection, oldEpoch, oldIntent) }
            scheduler.runCurrent()
            assertFalse(lateCancellation.isCompleted)
            beforeSettingsRead = {}
            release.complete(Unit)
            await { replacement.isCompleted && lateCancellation.isCompleted }
            assertTrue(replacement.await()?.isStarted == true)
            assertEquals(null, lateCancellation.await())
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            assertNotEquals(oldEpoch, runtime.installedLiveTargetEpoch(selection, nextIntent))
        }

    @Test fun aPresentedOrPositivelyAudioOnlyPlayingTargetIsNotRetiredByStartupCleanup() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live(timeshift = false)
            val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
            val intent = runtime.notePlaybackIntent().also(runtime::notePlaybackIntentServed)
            val epoch = requireNotNull(runtime.installedLiveTargetEpoch(selection, intent))
            renderFirstFrame()
            val cleanup = scope.async { runtime.stopAfterLoss(selection, epoch, intent) }
            await { cleanup.isCompleted }
            assertEquals(null, cleanup.await())
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            live(timeshift = false)
            val radioIntent = runtime.notePlaybackIntent().also(runtime::notePlaybackIntentServed)
            val radioEpoch = requireNotNull(runtime.installedLiveTargetEpoch(selection, radioIntent))
            forcedTracks = audioTracks()
            forcedPlaybackState = Player.STATE_READY
            forcedPlaying = true
            playerReady()
            val radioCleanup = scope.async { runtime.stopAfterLoss(selection, radioEpoch, radioIntent) }
            await { radioCleanup.isCompleted }
            assertEquals(null, radioCleanup.await())
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        }

    @Test fun playingReadyFirstFrameStaysCoveredUntilTheDecoderRendersAnotherFrame() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            val epoch = runtime.videoPresentation.value.epoch
            assertTrue(player.playWhenReady)
            renderFirstFrame()
            // Ready with its first frame, but the audio clock has not started: the picture stands.
            renderFrames(counters, 0)
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            renderFrames(counters)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun soundUnderTheCoverIsPublishedForTheBusyRingUntilThePictureMoves() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            val epoch = runtime.videoPresentation.value.epoch
            renderFirstFrame()
            renderFrames(counters, 0)
            forcedPlaying = true
            scheduler.advanceTimeBy(40)
            soundAdvancing()
            val since = scheduler.currentTime
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false, soundSinceMillis = since), runtime.videoPresentation.value)
            assertEquals(300L, runtime.videoPresentation.value.busyRingQuietInMillis(since))
            renderFrames(counters)
            assertTrue(runtime.videoPresentation.value.visible)
            assertNull("a moving picture never quiets the ring", runtime.videoPresentation.value.busyRingQuietInMillis(since + 1_000))
        }

    @Test fun soundThatStopsOrStallsBringsTheBusyRingBack() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            countVideoFrames()
            live()
            val epoch = runtime.videoPresentation.value.epoch
            renderFirstFrame()
            forcedPlaying = true
            soundAdvancing()
            assertEquals(scheduler.currentTime, runtime.videoPresentation.value.soundSinceMillis)
            forcedPlaying = false
            buffering()
            playerListeners.toList().forEach { it.onIsPlayingChanged(false) }
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            // The position advancing again after the stall is a new sound.
            forcedPlaying = true
            playerReady()
            soundAdvancing()
            assertEquals(scheduler.currentTime, runtime.videoPresentation.value.soundSinceMillis)
        }

    @Test fun soundReportedBeforeIsPlayingIsPublishedWhenThePlayerPlays() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            countVideoFrames()
            live()
            val epoch = runtime.videoPresentation.value.epoch
            renderFirstFrame()
            forcedPlaying = false
            soundAdvancing()
            val reported = scheduler.currentTime
            assertNull("not playing yet", runtime.videoPresentation.value.soundSinceMillis)
            scheduler.advanceTimeBy(25)
            forcedPlaying = true
            playerListeners.toList().forEach { it.onIsPlayingChanged(true) }
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false, soundSinceMillis = reported), runtime.videoPresentation.value)
        }

    @Test fun soundReportedBeforeIsPlayingIsDroppedWhenThePlayerStopsInstead() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            countVideoFrames()
            live()
            renderFirstFrame()
            forcedPlaying = false
            soundAdvancing()
            playerListeners.toList().forEach { it.onIsPlayingChanged(false) }
            forcedPlaying = true
            playerListeners.toList().forEach { it.onIsPlayingChanged(true) }
            assertNull(runtime.videoPresentation.value.soundSinceMillis)
        }

    @Test fun soundReportedBeforeIsPlayingIsDroppedWhenThePlayerStallsBeforeItPlays() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            countVideoFrames()
            live()
            renderFirstFrame()
            forcedPlaying = false
            soundAdvancing()
            // Media3 may mask isPlaying back without ever reporting true: the stall alone ends it.
            playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_BUFFERING) }
            forcedPlaying = true
            playerListeners.toList().forEach { it.onIsPlayingChanged(true) }
            assertNull(runtime.videoPresentation.value.soundSinceMillis)
        }

    @Test fun replacedTargetsSoundNeverDescribesTheNextTargetsCover() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            countVideoFrames()
            live()
            renderFirstFrame()
            forcedPlaying = true
            soundAdvancing()
            assertEquals(scheduler.currentTime, runtime.videoPresentation.value.soundSinceMillis)
            var atPause: AppVideoPresentation? = null
            afterPause = { if (atPause == null) atPause = runtime.videoPresentation.value }
            val oldEpoch = runtime.videoPresentation.value.epoch
            live(channel = 2)
            assertEquals(AppVideoPresentation(epoch = oldEpoch, visible = false), atPause)
            val epoch = runtime.videoPresentation.value.epoch
            assertNotEquals(oldEpoch, epoch)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
        }

    @Test fun pausedTargetShowsItsReadyFirstFrameWithoutMotion() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            countVideoFrames()
            live()
            player.playWhenReady = false
            renderFirstFrame()
            assertTrue("a deliberately paused picture is not a frozen one", runtime.videoPresentation.value.visible)
        }

    @Test fun rebufferAfterTheMovingPictureShowsDoesNotCoverItAgain() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            renderFirstFrame()
            renderFrames(counters)
            val shown = runtime.videoPresentation.value
            assertTrue(shown.visible)
            buffering()
            renderFrames(counters, 0)
            assertEquals(shown, runtime.videoPresentation.value)
            playerReady()
            renderFrames(counters, 0)
            assertEquals(shown, runtime.videoPresentation.value)
        }

    @Test fun replacementStopsTheWatchOfThePictureItReplaces() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            forcedVideoFormat = videoFormat("old")
            renderFirstFrame()
            renderFrames(counters, 0)
            assertFalse("the old picture is watched", runtime.videoPresentation.value.visible)
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch
            val reads = videoDecoderCounterReads
            // The old watch would see this frame; the new target has not rendered its own first frame.
            renderFrames(counters)
            renderFrames(counters)
            assertEquals("the replaced picture is no longer watched", reads, videoDecoderCounterReads)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
        }

    @Test fun bufferingStopsTheWatchOfTheCoveredPictureUntilItIsReadyAgain() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            val epoch = runtime.videoPresentation.value.epoch
            renderFirstFrame()
            renderFrames(counters, 0)
            buffering()
            val reads = videoDecoderCounterReads
            renderFrames(counters)
            renderFrames(counters)
            assertEquals("a buffering picture is not watched", reads, videoDecoderCounterReads)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            playerReady()
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            renderFrames(counters)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun viewerPauseShowsTheCoveredPictureThatWaitedForMotion() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            val epoch = runtime.videoPresentation.value.epoch
            renderFirstFrame()
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            pauseAtTheServer()
            assertFalse(player.playWhenReady)
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun playWaitingForTheServerResumeKeepsItsStillFirstFrameCovered() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            val epoch = runtime.videoPresentation.value.epoch
            // Paused at the server before the first frame arrived.
            pauseAtTheServer()
            settle()
            assertFalse(player.playWhenReady)
            val resumeEntered = CompletableDeferred<Unit>()
            val finishResume = CompletableDeferred<Unit>()
            beforeSpeed = { speed ->
                if (speed == 100) {
                    resumeEntered.complete(Unit)
                    finishResume.await()
                }
            }
            val resume = scope.async { runtime.resumeTimeshift() }
            await { resumeEntered.isCompleted }
            assertFalse(player.playWhenReady)
            // The first frame arrives while the player still stands for the server's resume.
            renderFirstFrame()
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            finishResume.complete(Unit)
            await { resume.isCompleted && player.playWhenReady }
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            renderFrames(counters)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun firstFrameOfTheNextSourceBeforeItsPresentationBeginsCounts() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            forcedVideoFormat = videoFormat("old")
            renderFirstFrame()
            renderFrames(counters)
            val oldEpoch = runtime.videoPresentation.value.epoch
            var renderedBeforeItsPresentation = false
            val next = DecoderCounters()
            onNextSource {
                // Only the replaced target's frame listener is registered yet.
                renderedBeforeItsPresentation = runtime.videoPresentation.value.epoch == oldEpoch
                // Media3 reports the next source's renderer enable before its first frame.
                forcedVideoDecoderCounters = next
                forcedVideoFormat = videoFormat("new")
                playerListeners.toList().forEach { it.onRenderedFirstFrame() }
            }
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch
            assertNotEquals(oldEpoch, epoch)
            assertTrue(renderedBeforeItsPresentation)
            renderFrames(next, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            renderFrames(next)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun queuedFirstFrameOfTheReplacedSourceDoesNotCountForTheNextTarget() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            forcedVideoFormat = videoFormat("old")
            renderFirstFrame()
            renderFrames(counters)
            val oldEpoch = runtime.videoPresentation.value.epoch
            var renderedBeforeItsPresentation = false
            val next = DecoderCounters()
            onNextSource {
                // Delivered after the next source is set, still with the replaced source's format.
                renderedBeforeItsPresentation = runtime.videoPresentation.value.epoch == oldEpoch
                playerListeners.toList().forEach { it.onRenderedFirstFrame() }
                // The new source disables the old renderer before it can be ready.
                forcedVideoDecoderCounters = next
            }
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch
            assertTrue(renderedBeforeItsPresentation)
            renderFrames(next)
            renderFrames(next)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            forcedVideoFormat = videoFormat("new")
            renderFirstFrame()
            renderFrames(next)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun lateFormatAndFirstFrameOfAReplacedSourceWithoutFormatDoNotCountForTheNextTarget() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            // The covered target has neither a video format nor an enabled video decoder yet.
            live()
            val oldEpoch = runtime.videoPresentation.value.epoch
            val late = DecoderCounters()
            val next = DecoderCounters()
            onNextSource {
                // Its decoder, format and first frame arrive only after the next source is set,
                // then the new source disables its renderer and enables its own.
                assertEquals(oldEpoch, runtime.videoPresentation.value.epoch)
                forcedVideoDecoderCounters = late
                forcedVideoFormat = videoFormat("late-old")
                playerListeners.toList().forEach { it.onRenderedFirstFrame() }
                forcedVideoDecoderCounters = next
            }
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch
            assertNotEquals(oldEpoch, epoch)
            renderFrames(next)
            renderFrames(next)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            forcedVideoFormat = videoFormat("new")
            renderFirstFrame()
            renderFrames(next, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            renderFrames(next)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun startCutShortAfterItsCommitKeepsItsInstallFrameCoveredUntilThePictureMoves() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            val epoch = startCutShort(paused = false)
            playerReady()
            renderFrames(counters, 0)
            assertEquals("the install's stopped player is no pause", AppVideoPresentation(epoch = epoch, visible = false),
                runtime.videoPresentation.value)
            runtime.play()
            await { player.playWhenReady }
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            renderFrames(counters)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun pausedStartCutShortAfterItsCommitShowsItsReadyInstallFrame() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            val epoch = startCutShort(paused = true)
            assertFalse(player.playWhenReady)
            renderFrames(counters, 0)
            assertEquals("not ready yet", AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            playerReady()
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun viewerPauseShowsTheReadyPictureOfAPlayingStartCutShort() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            val epoch = startCutShort(paused = false)
            playerReady()
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            runtime.pause()
            await { runtime.videoPresentation.value.visible }
            assertFalse(player.playWhenReady)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun failedReplacementKeepsThePlayingTargetThatStaysCoveredUntilItsPictureMovesAgain() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live(timeshift = false)
            renderFirstFrame()
            renderFrames(counters)
            val kept = runtime.videoPresentation.value
            assertTrue(kept.visible)
            session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
            }
            await { install.isCompleted }

            assertFalse(install.await()?.isStarted == true)
            assertTrue(player.playWhenReady)
            // Paused for the attempt and resumed: its last frame stands until its audio runs again.
            renderFrames(counters, 0)
            assertEquals(kept.copy(visible = false), runtime.videoPresentation.value)
            renderFrames(counters)
            assertEquals(kept, runtime.videoPresentation.value)
        }

    @Test fun firstFrameOfTheNewTargetDuringItsInstallCountsOnceInstalled() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val counters = countVideoFrames()
            live()
            forcedVideoFormat = videoFormat("old")
            renderFirstFrame()
            renderFrames(counters)
            val oldEpoch = runtime.videoPresentation.value.epoch
            // The new target's frame listener is registered inside the install; its own first frame follows.
            onListenerAdded = {
                if (runtime.videoPresentation.value.epoch != oldEpoch) {
                    onListenerAdded = {}
                    forcedVideoFormat = videoFormat("new")
                    playerListeners.toList().forEach { it.onRenderedFirstFrame() }
                }
            }
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch
            assertNotEquals(oldEpoch, epoch)
            renderFrames(counters, 0)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            renderFrames(counters)
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    @Test fun firstFrameOfTheReplacedTargetDuringTheInstallDoesNotCountForTheNewTarget() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            live()
            val oldEpoch = runtime.videoPresentation.value.epoch
            // Rendered while the next install runs, before the next target's presentation begins.
            beforeLiveBinding = {
                beforeLiveBinding = {}
                forcedVideoFormat = videoFormat("old")
                playerListeners.toList().forEach { it.onRenderedFirstFrame() }
            }
            live(channel = 2)
            val epoch = runtime.videoPresentation.value.epoch
            assertNotEquals(oldEpoch, epoch)
            settle()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = false), runtime.videoPresentation.value)
            forcedVideoFormat = videoFormat("new")
            renderFirstFrame()
            assertEquals(AppVideoPresentation(epoch = epoch, visible = true), runtime.videoPresentation.value)
        }

    /**
     * Starts channel 1 whose new source renders its first frame inside the install, and cuts the
     * start short once it committed, as its play intent is applied: a [paused] start, which the
     * viewer paused while it was pending, at its pause, else at its play. Returns its epoch.
     */
    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.startCutShort(paused: Boolean): Long {
        settings.setTimeshiftEnabled(false)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val idleEpoch = runtime.videoPresentation.value.epoch
        val cutShort = { throw kotlinx.coroutines.CancellationException("start cut short") }
        onListenerAdded = {
            if (runtime.videoPresentation.value.epoch != idleEpoch) {
                onListenerAdded = {}
                forcedVideoFormat = videoFormat("new")
                playerListeners.toList().forEach { it.onRenderedFirstFrame() }
                // The install paused the player before; the next pause is the start's own.
                if (paused) afterPause = { afterPause = {}; cutShort() }
            }
        }
        if (!paused) beforePlay = { beforePlay = {}; cutShort() }
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        if (paused) assertNull(runtime.pauseTimeshiftPlayback())
        val install = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))), intent)
        }
        await { connection.subscribeCount > 0 }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { install.isCompleted }
        assertTrue(install.isCancelled)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        return runtime.videoPresentation.value.epoch
    }

    @Test fun ownFirstFrameDuringThePlayIntentOfAPausedStartOutlastsAStaleCarriedFrame() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val next = ownFrameDuringThePlayIntentAfterAStaleCarriedFrame(paused = true)
            assertFalse(player.playWhenReady)
            playerReady()
            renderFrames(next, 0)
            assertTrue(runtime.videoPresentation.value.visible)
        }

    @Test fun ownFirstFrameDuringThePlayIntentOfAPlayingStartOutlastsAStaleCarriedFrame() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            val next = ownFrameDuringThePlayIntentAfterAStaleCarriedFrame(paused = false)
            assertTrue(player.playWhenReady)
            playerReady()
            renderFrames(next, 0)
            assertFalse(runtime.videoPresentation.value.visible)
            renderFrames(next)
            assertTrue(runtime.videoPresentation.value.visible)
        }

    /**
     * Replaces channel 1 by channel 2. The replaced source's late first frame is carried into the
     * new presentation on its own counters, then the new source renders its first frame on new
     * counters while the start applies its play intent. Returns the new source's counters.
     */
    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.ownFrameDuringThePlayIntentAfterAStaleCarriedFrame(
        paused: Boolean,
    ): DecoderCounters {
        // A held pause needs a channel the server can pause.
        live(timeshift = paused)
        val oldEpoch = runtime.videoPresentation.value.epoch
        val stale = DecoderCounters()
        val next = DecoderCounters()
        onNextSource {
            forcedVideoDecoderCounters = stale
            forcedVideoFormat = videoFormat("late-old")
            playerListeners.toList().forEach { it.onRenderedFirstFrame() }
            forcedVideoDecoderCounters = next
        }
        val ownFrame = {
            forcedVideoFormat = videoFormat("new")
            playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        }
        var ownFrameRendered = false
        onListenerAdded = {
            if (runtime.videoPresentation.value.epoch != oldEpoch) {
                onListenerAdded = {}
                // The install paused the player before; the next pause is the start's own.
                if (paused) afterPause = { afterPause = {}; ownFrame(); ownFrameRendered = true }
                else beforePlay = { beforePlay = {}; ownFrame(); ownFrameRendered = true }
            }
        }
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        if (paused) assertNull(runtime.pauseTimeshiftPlayback())
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (paused) 120 else 0)))
        val previous = connection.subscribeCount
        val install = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))), intent)
        }
        await { connection.subscribeCount > previous }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { install.isCompleted }
        assertTrue(install.await()?.isStarted == true)
        assertTrue(ownFrameRendered)
        assertNotEquals(oldEpoch, runtime.videoPresentation.value.epoch)
        return next
    }

    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.pauseAtTheServer() {
        val pause = scope.async { runtime.pauseTimeshiftPlayback() }
        await { pause.isCompleted }
        assertEquals(TimeshiftCommandResult.ACCEPTED, pause.await())
    }

    /** Runs [action] once, as the next install sets its source on the player. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.onNextSource(action: () -> Unit) {
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
                player.removeListener(this)
                action()
            }
        })
    }

    private fun BackgroundPlaybackRuntimeTest.Fixture.countVideoFrames() =
        DecoderCounters().also { forcedVideoDecoderCounters = it }

    /** The decoder renders [count] further frames, and the motion watch looks once. */
    private fun renderFrames(counters: DecoderCounters, count: Int = 1) {
        counters.renderedOutputBufferCount += count
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(MOTION_POLL_MILLIS))
    }

    private fun audioTracks() = Tracks(listOf(Tracks.Group(
        TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
    )))

    /** The audio output reports that its position advances: the sound is audibly playing out. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.soundAdvancing() {
        player.analyticsCollector.onAudioPositionAdvancing(0L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun BackgroundPlaybackRuntimeTest.Fixture.renderFirstFrame() {
        playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The fake stream is reported ready by [BackgroundPlaybackRuntimeTest.Fixture.live]; a real one may still buffer. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.buffering() {
        playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_BUFFERING) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun videoFormat(id: String) =
        Format.Builder().setId(id).setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(320).setHeight(240).build()
}
