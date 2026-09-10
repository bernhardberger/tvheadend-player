package at.bernhardberger.tvhplayer.profiling

import android.os.Trace
import at.bernhardberger.tvhplayer.BuildConfig

/** Named synchronous attribution only, enabled in the existing profiling build family. */
internal inline fun <T> profileTrace(name: String, block: () -> T): T {
    if (!BuildConfig.PROFILE_TRACE) return block()
    Trace.beginSection(name)
    return try { block() } finally { Trace.endSection() }
}
