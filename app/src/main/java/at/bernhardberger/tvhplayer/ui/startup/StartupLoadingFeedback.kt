package at.bernhardberger.tvhplayer.ui.startup

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.core.MainStartupBriefWaitMillis
import at.bernhardberger.tvhplayer.core.MainStartupLoadingFeedback
import at.bernhardberger.tvhplayer.core.MainStartupLoadingTiming
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.mainStartupLoadingFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The timing owner is remembered above AppRoot's resolving/configured branch boundary. */
@Composable
internal fun startupLoadingFeedback(
    presentation: MainStartupPresentation,
    timing: MainStartupLoadingTiming,
    requestId: Long?,
    enabled: Boolean,
): MainStartupLoadingFeedback {
    val blocking = enabled && presentation is MainStartupPresentation.Passive
    var elapsed by remember(timing, blocking, requestId) {
        mutableLongStateOf(timing.elapsedMillis(blocking, requestId, SystemClock.uptimeMillis()))
    }
    LaunchedEffect(timing, blocking, requestId) {
        elapsed = timing.observe(blocking, requestId, SystemClock.uptimeMillis())
        if (!blocking) return@LaunchedEffect
        val remaining = MainStartupBriefWaitMillis - elapsed
        if (remaining > 0L) {
            // Uptime deadlines use Android's Handler clock, not a composition/animation clock.
            withContext(Dispatchers.Main.immediate) { delay(remaining) }
            elapsed = timing.elapsedMillis(true, requestId, SystemClock.uptimeMillis())
        }
    }
    return if (blocking) mainStartupLoadingFeedback(presentation, elapsed) else MainStartupLoadingFeedback.HIDDEN
}
