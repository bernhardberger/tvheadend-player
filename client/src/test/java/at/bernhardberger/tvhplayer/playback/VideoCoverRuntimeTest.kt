@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackRuntimeTest.Companion.exercise
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
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

    private fun audioTracks() = Tracks(listOf(Tracks.Group(
        TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
    )))

    private fun BackgroundPlaybackRuntimeTest.Fixture.renderFirstFrame() {
        playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun videoFormat(id: String) =
        Format.Builder().setId(id).setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(320).setHeight(240).build()
}
