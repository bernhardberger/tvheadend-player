package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleStartEffect
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime

/** Sampling belongs to each started player, not to the process-lifetime Stats switch. */
@Composable
internal fun PlayerDiagnosticsEffect(runtime: AppPlaybackRuntime, enabled: Boolean) {
    if (!enabled) return
    LifecycleStartEffect(runtime) {
        val owner = Any()
        runtime.setDiagnosticsClaim(owner, true)
        onStopOrDispose { runtime.setDiagnosticsClaim(owner, false) }
    }
}
