package at.bernhardberger.tvhplayer.ui.startup

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.MainStartupBriefWaitMillis
import at.bernhardberger.tvhplayer.core.MainStartupLoadingTiming
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.WaitingConnectionFailure
import at.bernhardberger.tvhplayer.core.coldStartPresentation
import at.bernhardberger.tvhplayer.core.coldStartRemainingVisibleMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The startup screen of a cold start without autoplay, and its two actions' state changes. */
@Immutable
internal class ColdStart(
    val presentation: MainStartupPresentation,
    val retry: () -> Unit,
    val settle: () -> Unit,
)

/**
 * Shows [coldStartPresentation] until channels can be drawn, once per activity. Saved channels
 * restore beside connecting with no signal of their own. The startup screen's own brief wait
 * ([loadingTiming], the one grace) shows no stage text, so channels arriving within it lead
 * straight to them; after it the stage text joins the static brand, and the screen stays
 * [at.bernhardberger.tvhplayer.core.ColdStartMinimumVisibleMillis]. A failure shows
 * at once. A launch request ends it for good: autoplay keeps its own startup.
 */
@Composable
internal fun rememberColdStart(
    launchIdle: Boolean,
    connectionState: ConnectionUiState,
    currentChannelReadiness: CurrentChannelReadiness,
    loadingTiming: MainStartupLoadingTiming,
): ColdStart {
    var settled by rememberSaveable { mutableStateOf(false) }
    var failures by remember { mutableStateOf(WaitingConnectionFailure()) }
    var shownAtMillis by remember { mutableStateOf<Long?>(null) }
    val live = if (settled || !launchIdle) {
        MainStartupPresentation.Inactive
    } else {
        coldStartPresentation(connectionState, currentChannelReadiness, failures.shown(connectionState))
    }
    SideEffect { failures = failures.observe(connectionState) }
    val waiting = live != MainStartupPresentation.Inactive
    val failed = live is MainStartupPresentation.Actionable
    LaunchedEffect(waiting, failed) {
        if (!waiting || shownAtMillis != null) return@LaunchedEffect
        // The loading feedback's own deadline, on the same Handler clock; the timing restarts when
        // its wait does, so it is read again until its grace has passed.
        while (!failed) {
            val elapsed = loadingTiming.elapsedMillis(true, null, SystemClock.uptimeMillis())
            if (elapsed >= MainStartupBriefWaitMillis) break
            withContext(Dispatchers.Main.immediate) { delay(MainStartupBriefWaitMillis - elapsed) }
        }
        shownAtMillis = SystemClock.uptimeMillis()
    }
    var lastShown by remember { mutableStateOf<MainStartupPresentation>(MainStartupPresentation.Inactive) }
    if (waiting) SideEffect { lastShown = live }
    LaunchedEffect(waiting, settled) {
        if (waiting || settled) return@LaunchedEffect
        val remaining = coldStartRemainingVisibleMillis(shownAtMillis, SystemClock.uptimeMillis())
        withContext(Dispatchers.Main.immediate) { delay(remaining) }
        settled = true
    }
    return ColdStart(
        presentation = when {
            settled -> MainStartupPresentation.Inactive
            waiting -> live
            shownAtMillis != null -> lastShown
            else -> MainStartupPresentation.Inactive
        },
        retry = { failures = failures.retry() },
        settle = { settled = true },
    )
}
