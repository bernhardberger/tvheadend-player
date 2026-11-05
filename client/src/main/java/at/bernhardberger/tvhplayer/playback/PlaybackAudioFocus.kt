package at.bernhardberger.tvhplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Call on the player's application looper. Each request replaces the previous callback. */
interface PlaybackAudioFocus {
    fun request(onInterruption: (AudioInterruption) -> Unit): Boolean
    fun abandon()

    /** Explicit opt-out for synthetic/profile hosts that do not own audible playback. */
    object None : PlaybackAudioFocus {
        override fun request(onInterruption: (AudioInterruption) -> Unit) = true
        override fun abandon() = Unit
    }
}

/** HDMI mode switches can emit NOISY too; only personal listening outputs should pause. */
internal fun noisyPausesPlayback(outputTypes: Collection<Int>): Boolean = outputTypes.any { type ->
    when (type) {
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
}

internal const val PERSONAL_OUTPUT_REMOVAL_WINDOW_MS = 5_000L

internal fun recentPersonalOutputRemoval(lastRemovalMs: Long?, nowMs: Long): Boolean =
    lastRemovalMs != null && nowMs >= lastRemovalMs &&
        nowMs - lastRemovalMs <= PERSONAL_OUTPUT_REMOVAL_WINDOW_MS

class AndroidPlaybackAudioFocus(context: Context, looper: Looper) : PlaybackAudioFocus {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(AudioManager::class.java)
    private val handler = Handler(looper)
    private var request: AudioFocusRequest? = null
    private var receiver: BroadcastReceiver? = null
    private var deviceCallback: AudioDeviceCallback? = null
    private val personalOutputIds = mutableSetOf<Int>()
    private var lastPersonalOutputRemovalMs: Long? = null
    private var held = false
    private var callback: ((AudioInterruption) -> Unit)? = null

    override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean {
        if (held) {
            callback = onInterruption
            return true
        }
        abandon()
        callback = onInterruption
        lateinit var next: AudioFocusRequest
        next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setWillPauseWhenDucked(false)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                if (request === next) {
                    val event = when (change) {
                        AudioManager.AUDIOFOCUS_GAIN -> AudioInterruption.GAIN
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioInterruption.TRANSIENT_LOSS
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioInterruption.TRANSIENT_LOSS_CAN_DUCK
                        AudioManager.AUDIOFOCUS_LOSS -> AudioInterruption.PERMANENT_LOSS
                        else -> null
                    }
                    if (event == AudioInterruption.GAIN) {
                        held = true
                        registerNoisy(next)
                    }
                    if (event == AudioInterruption.PERMANENT_LOSS || event == AudioInterruption.TRANSIENT_LOSS) {
                        held = false
                        unregisterNoisy()
                    }
                    event?.let { callback?.invoke(it) }
                }
            }, handler)
            .build()
        request = next
        val granted = manager.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        held = granted
        if (granted) registerNoisy(next)
        else {
            request = null
            callback = null
        }
        return granted
    }

    private fun registerNoisy(current: AudioFocusRequest) {
        if (receiver != null) return
        val devices = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (deviceCallback !== this || request !== current) return
                addedDevices.filter { it.isSink && noisyPausesPlayback(listOf(it.type)) }
                    .forEach { personalOutputIds.add(it.id) }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (deviceCallback !== this || request !== current) return
                removedDevices.forEach { personalOutputIds.remove(it.id) }
                if (noisyPausesPlayback(removedDevices.filter { it.isSink }.map { it.type })) {
                    lastPersonalOutputRemovalMs = SystemClock.elapsedRealtime()
                }
            }
        }
        deviceCallback = devices
        manager.registerAudioDeviceCallback(devices, handler)
        val next = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (request === current && intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    val outputTypes = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
                    // The device-list change can precede its callback queued behind this broadcast.
                    if (noisyPausesPlayback(outputTypes) || personalOutputIds.isNotEmpty() ||
                        recentPersonalOutputRemoval(lastPersonalOutputRemovalMs, SystemClock.elapsedRealtime())) {
                        callback?.invoke(AudioInterruption.NOISY)
                    }
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(next, filter, null, handler, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(next, filter, null, handler)
        }
        receiver = next
    }

    private fun unregisterNoisy() {
        deviceCallback?.let(manager::unregisterAudioDeviceCallback)
        deviceCallback = null
        personalOutputIds.clear()
        lastPersonalOutputRemovalMs = null
        receiver?.let(context::unregisterReceiver)
        receiver = null
    }

    override fun abandon() {
        val previous = request
        request = null
        held = false
        callback = null
        unregisterNoisy()
        previous?.let(manager::abandonAudioFocusRequest)
    }
}
