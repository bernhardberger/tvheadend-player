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
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackRuntimeTest.Companion.exercise
import kotlinx.coroutines.async
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

    private fun BackgroundPlaybackRuntimeTest.Fixture.renderFirstFrame() {
        playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun videoFormat(id: String) =
        Format.Builder().setId(id).setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(320).setHeight(240).build()
}
