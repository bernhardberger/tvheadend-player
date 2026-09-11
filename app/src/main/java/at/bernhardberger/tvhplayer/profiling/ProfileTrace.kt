package at.bernhardberger.tvhplayer.profiling

import android.os.Trace
import android.os.SystemClock
import android.view.KeyEvent
import at.bernhardberger.tvhplayer.BuildConfig

/** Named synchronous attribution only, enabled in the existing profiling build family. */
internal inline fun <T> profileTrace(name: String, block: () -> T): T {
    if (!BuildConfig.PROFILE_TRACE) return block()
    Trace.beginSection(name)
    return try { block() } finally { Trace.endSection() }
}

/** Event age includes input waiting before Activity dispatch; it is not key-to-photon. */
internal fun profileNavigationInput(event: KeyEvent) {
    if (!BuildConfig.PROFILE_TRACE || event.action != KeyEvent.ACTION_DOWN) return
    if (event.keyCode !in 19..23 && event.keyCode != KeyEvent.KEYCODE_BACK) return
    profileTrace("P48:key:${event.keyCode}:time:${event.eventTime}:age:${SystemClock.uptimeMillis() - event.eventTime}") { }
}
