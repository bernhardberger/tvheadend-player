package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackAudioOutputsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(context.getSystemService(AudioManager::class.java))

    @Test fun monitorRejectsOffMainConstructionAndClose() {
        val construct = FutureTask {
            assertThrows(IllegalStateException::class.java) { AndroidPlaybackAudioOutputs(context) }
        }
        Thread(construct).start()
        construct.get(5, TimeUnit.SECONDS)
        val outputs = AndroidPlaybackAudioOutputs(context)
        try {
            val close = FutureTask {
                assertThrows(IllegalStateException::class.java) { outputs.close() }
            }
            Thread(close).start()
            close.get(5, TimeUnit.SECONDS)
        } finally { outputs.close() }
    }

    @Test fun passthroughPolicyRespectsUserSettingAndPersonalOutputs() {
        assertTrue(effectiveAudioPassthrough(userEnabled = true, personalOutputConnected = false))
        assertFalse(effectiveAudioPassthrough(userEnabled = true, personalOutputConnected = true))
        assertFalse(effectiveAudioPassthrough(userEnabled = false, personalOutputConnected = false))
        assertFalse(effectiveAudioPassthrough(userEnabled = false, personalOutputConnected = true))
        for (type in listOf(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_LINE_ANALOG)) {
            assertTrue(effectiveAudioPassthrough(true, isPersonalAudioOutput(type)))
        }
    }

    @Test fun alreadyConnectedOutputsAreKnownBeforeCallbackDeliveryAndPartialRemovalKeepsPcm() {
        val headphones = device(1, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        val usb = device(2, AudioDeviceInfo.TYPE_USB_DEVICE)
        manager.setOutputDevices(listOf(headphones, usb))
        val outputs = AndroidPlaybackAudioOutputs(context)
        try {
            assertTrue(outputs.personalOutputConnected.value)
            shadowOf(Looper.getMainLooper()).idle()
            manager.removeOutputDevice(headphones, true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(outputs.personalOutputConnected.value)
            manager.removeOutputDevice(usb, true)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(outputs.personalOutputConnected.value)
        } finally { outputs.close() }
    }

    @Test fun hdmiAndLineAnalogDoNotCountAndCloseUnregistersTheMonitor() {
        manager.setOutputDevices(listOf(device(1, AudioDeviceInfo.TYPE_HDMI), device(2, AudioDeviceInfo.TYPE_LINE_ANALOG)))
        val outputs = AndroidPlaybackAudioOutputs(context)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(outputs.personalOutputConnected.value)
        val headphones = device(3, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        manager.addOutputDevice(headphones, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(outputs.personalOutputConnected.value)
        manager.removeOutputDevice(headphones, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(outputs.personalOutputConnected.value)
        outputs.close()
        outputs.close()
        manager.addOutputDevice(headphones, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(outputs.personalOutputConnected.value)
    }

    private fun device(id: Int, type: Int): AudioDeviceInfo = AudioDeviceInfoBuilder.newBuilder()
        .setType(type).build().also {
            // Robolectric's builder leaves the port role unset; AudioPort.ROLE_SINK is 2.
            val port = ReflectionHelpers.getField<Any>(it, "mPort")
            ReflectionHelpers.setField(port, "mRole", 2)
            val handle = ReflectionHelpers.getField<Any>(port, "mHandle")
            ReflectionHelpers.setField(handle, "mId", id)
        }
}
