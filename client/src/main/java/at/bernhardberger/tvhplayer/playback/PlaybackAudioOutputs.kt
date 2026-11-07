package at.bernhardberger.tvhplayer.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connected personal listening outputs, independent of whether playback currently holds focus. */
interface PlaybackAudioOutputs {
    val personalOutputConnected: StateFlow<Boolean>
    fun close()

    /** Synthetic/profile hosts that do not own audible playback. */
    object None : PlaybackAudioOutputs {
        override val personalOutputConnected = MutableStateFlow(false).asStateFlow()
        override fun close() = Unit
    }
}

internal fun isPersonalAudioOutput(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_HEARING_AID,
    // These integer constants are inlined, including on Android versions predating BLE audio.
    AudioDeviceInfo.TYPE_BLE_HEADSET -> true
    else -> false
}

internal fun effectiveAudioPassthrough(userEnabled: Boolean, personalOutputConnected: Boolean): Boolean =
    userEnabled && !personalOutputConnected

/** Created and closed on the main thread; callbacks use that same thread. */
class AndroidPlaybackAudioOutputs(context: Context) : PlaybackAudioOutputs {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val personalOutputIds = mutableSetOf<Int>()
    private val connected = MutableStateFlow(false)
    override val personalOutputConnected = connected.asStateFlow()
    @Volatile private var closed = false
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (closed) return
            addedDevices.filter { it.isSink && isPersonalAudioOutput(it.type) }
                .forEach { personalOutputIds.add(it.id) }
            connected.value = personalOutputIds.isNotEmpty()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (closed) return
            removedDevices.forEach { personalOutputIds.remove(it.id) }
            connected.value = personalOutputIds.isNotEmpty()
        }
    }

    init {
        check(Looper.myLooper() === Looper.getMainLooper()) { "Audio output monitor must be created on main" }
        // Registration also reports existing devices, but that delivery is queued. Seed the
        // synchronous state before a first target can configure its output on this main thread.
        // Register first so removals between registration and the snapshot cannot be lost.
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        callback.onAudioDevicesAdded(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
    }

    override fun close() {
        check(Looper.myLooper() === Looper.getMainLooper()) { "Audio output monitor must be closed on main" }
        if (closed) return
        closed = true
        manager.unregisterAudioDeviceCallback(callback)
        personalOutputIds.clear()
    }
}
