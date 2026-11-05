package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Looper
import android.os.Handler
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackAudioFocusTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(context.getSystemService(AudioManager::class.java))
    private val focus = AndroidPlaybackAudioFocus(context, Looper.getMainLooper())
    private val noisy = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

    @Test fun noisyPausesOnlyForPersonalListeningOutputs() {
        val cases = listOf(
            emptyList<Int>() to false,
            listOf(AudioDeviceInfo.TYPE_HDMI) to false,
            listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) to false,
            listOf(AudioDeviceInfo.TYPE_HDMI_ARC) to false,
            listOf(AudioDeviceInfo.TYPE_HDMI_EARC) to false,
            listOf(AudioDeviceInfo.TYPE_LINE_ANALOG) to false,
            listOf(AudioDeviceInfo.TYPE_USB_DEVICE) to true,
            listOf(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_WIRED_HEADPHONES) to true,
            listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET) to true,
            listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES) to true,
            listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) to true,
            listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO) to true,
            listOf(AudioDeviceInfo.TYPE_USB_HEADSET) to true,
            listOf(AudioDeviceInfo.TYPE_HEARING_AID) to true,
            listOf(AudioDeviceInfo.TYPE_BLE_HEADSET) to true,
            listOf(Int.MAX_VALUE) to false,
        )
        cases.forEach { (types, expected) ->
            assertEquals("Connected output types: $types", expected, noisyPausesPlayback(types))
        }
    }

    @Test fun requestsMovieMediaGainWithoutDelayedGainOrPauseWhenDucked() {
        assertTrue(focus.request {})
        val request = manager.lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, request.focusGain)
        assertEquals(AudioAttributes.USAGE_MEDIA, request.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_MOVIE, request.audioAttributes.contentType)
        assertFalse(request.acceptsDelayedFocusGain())
        assertFalse(request.willPauseWhenDucked())
        focus.abandon()
        assertSame(request, manager.lastAbandonedAudioFocusRequest)
    }

    @Test fun personalRemovalWindowIsBoundedAndRequiresARemoval() {
        assertFalse(recentPersonalOutputRemoval(null, 0L))
        assertTrue(recentPersonalOutputRemoval(100L, 100L))
        assertTrue(recentPersonalOutputRemoval(100L, 5_100L))
        assertFalse(recentPersonalOutputRemoval(100L, 5_101L))
        assertFalse(recentPersonalOutputRemoval(100L, 99L))
    }

    @Test fun noisyReceiverExistsOnlyWhileFocusHeldAndOldRequestsAreIgnored() {
        manager.setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()))
        val events = mutableListOf<AudioInterruption>()
        focus.request(events::add)
        val old = manager.lastAudioFocusRequest.listener
        assertTrue(shadowOf(context).hasReceiverForIntent(noisy))
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(AudioInterruption.NOISY), events)
        old.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse(shadowOf(context).hasReceiverForIntent(noisy))
        old.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue(shadowOf(context).hasReceiverForIntent(noisy))
        focus.abandon()
        focus.request(events::add)
        val size = events.size
        old.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(size, events.size)
        focus.abandon()
        assertFalse(shadowOf(context).hasReceiverForIntent(noisy))
    }

    @Test fun deniedRequestDoesNotRegisterNoisyReceiver() {
        val events = mutableListOf<AudioInterruption>()
        manager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        assertFalse(focus.request(events::add))
        manager.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue(events.isEmpty())
        assertFalse(shadowOf(context).hasReceiverForIntent(noisy))
        focus.abandon()
    }

    @Test fun heldFocusReusesFrameworkRequestButReplacesCallback() {
        manager.setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()))
        val oldEvents = mutableListOf<AudioInterruption>()
        val newEvents = mutableListOf<AudioInterruption>()
        assertTrue(focus.request(oldEvents::add))
        val original = manager.lastAudioFocusRequest.audioFocusRequest
        assertTrue(focus.request(newEvents::add))
        assertSame(original, manager.lastAudioFocusRequest.audioFocusRequest)
        assertNull(manager.lastAbandonedAudioFocusRequest)
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(oldEvents.isEmpty())
        assertEquals(listOf(AudioInterruption.NOISY), newEvents)
        manager.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue(focus.request {})
        assertNotSame(original, manager.lastAudioFocusRequest.audioFocusRequest)
        focus.abandon()
    }

    @Test fun noisyBroadcastChecksConnectedOutputsAtDelivery() {
        val events = mutableListOf<AudioInterruption>()
        manager.setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_HDMI).build()))
        assertTrue(focus.request(events::add))
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(events.isEmpty())

        manager.setOutputDevices(listOf(
            AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_HDMI).build(),
            AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build(),
        ))
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(AudioInterruption.NOISY), events)
        focus.abandon()
    }

    @Test fun noisyAfterPersonalOutputRemovalStillPauses() {
        val events = mutableListOf<AudioInterruption>()
        val headphones = AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()
        // Robolectric's builder leaves the port role unset; AudioPort.ROLE_SINK is 2.
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(headphones, "mPort"), "mRole", 2)
        manager.setOutputDevices(listOf(headphones))
        assertTrue(focus.request(events::add))
        shadowOf(Looper.getMainLooper()).idle()
        manager.removeOutputDevice(headphones, true)
        shadowOf(Looper.getMainLooper()).idle()

        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(AudioInterruption.NOISY), events)
        events.clear()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(5_001))
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(events.isEmpty())
        focus.abandon()
    }

    @Test fun queuedNoisyBeforeRemovalCallbackStillPauses() {
        val events = mutableListOf<AudioInterruption>()
        val headphones = AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(headphones, "mPort"), "mRole", 2)
        manager.setOutputDevices(listOf(headphones))
        assertTrue(focus.request(events::add))
        shadowOf(Looper.getMainLooper()).idle()

        context.sendBroadcast(noisy)
        manager.removeOutputDevice(headphones, false)
        // ShadowAudioManager notifies synchronously; enqueue the registered callback explicitly
        // to reproduce Android's handler delivery behind the already queued NOISY broadcast.
        val devices = ReflectionHelpers.getField<Set<AudioDeviceCallback>>(manager, "audioDeviceCallbacks").single()
        Handler(Looper.getMainLooper()).post { devices.onAudioDevicesRemoved(arrayOf(headphones)) }
        assertTrue(context.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS).isEmpty())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(AudioInterruption.NOISY), events)
        focus.abandon()
    }

    @Test fun abandonClearsPersonalRemovalAndUnregistersDeviceCallback() {
        val headphones = AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(headphones, "mPort"), "mRole", 2)
        manager.setOutputDevices(listOf(headphones))
        focus.request {}
        shadowOf(Looper.getMainLooper()).idle()
        manager.removeOutputDevice(headphones, true)
        shadowOf(Looper.getMainLooper()).idle()
        focus.abandon()

        // A removal while abandoned must not leak into the next focus ownership period.
        manager.addOutputDevice(headphones, true)
        manager.removeOutputDevice(headphones, true)
        shadowOf(Looper.getMainLooper()).idle()
        val events = mutableListOf<AudioInterruption>()
        focus.request(events::add)
        shadowOf(Looper.getMainLooper()).idle()
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(events.isEmpty())
        focus.abandon()
    }

    @Test fun abandonClearsCachedPersonalOutputs() {
        val headphones = AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(headphones, "mPort"), "mRole", 2)
        manager.setOutputDevices(listOf(headphones))
        focus.request {}
        shadowOf(Looper.getMainLooper()).idle()
        focus.abandon()
        manager.removeOutputDevice(headphones, true)

        val events = mutableListOf<AudioInterruption>()
        focus.request(events::add)
        shadowOf(Looper.getMainLooper()).idle()
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(events.isEmpty())
        focus.abandon()
    }
}
