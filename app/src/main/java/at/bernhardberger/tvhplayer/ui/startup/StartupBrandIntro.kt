package at.bernhardberger.tvhplayer.ui.startup

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.MainStartupEntry
import at.bernhardberger.tvhplayer.core.MainStartupBrandDecision

internal val LocalStartupBrandIntro = staticCompositionLocalOf<StartupBrandIntro?> { null }

/** Owned above AppRoot's bootstrap/ready branches. Never gates startup or owns playback. */
internal class StartupBrandIntro(eligible: Boolean) {
    private val entry = MainStartupEntry(eligible)
    private val policy = StartupBrandEligibility(eligible)
    private var entranceReady = false
    private var resumed = false
    private var focused = false
    private var passive: Boolean? = null
    var running by mutableStateOf(false)
        private set
    private var handoffMillis: Float? = null
    val outgoingMillis: Float get() = handoffMillis ?: millis
    var millis by mutableFloatStateOf(if (eligible && ValueAnimator.areAnimatorsEnabled()) 0f else StartupBrandDurationMillis)
        private set

    /** Activity entrance completion makes motion eligible; Android owns its splash exit. */
    fun entranceReady() { entranceReady = true; update() }
    fun resumed(value: Boolean) { resumed = value; if (!value) finish() else update() }
    fun focused(value: Boolean) {
        val lostFocus = focused && !value
        focused = value
        if (lostFocus) finish() else update()
    }
    fun observe(connection: ConnectionUiState) {
        entry.observe(connection)
        update()
    }
    fun passive(value: Boolean?, gracePassed: Boolean = false) {
        // A further visible wait really renders settled branding. Only an immediate
        // readiness handoff may retain the frame stopped by cancellation.
        if (value == true && !running && entry.decision == MainStartupBrandDecision.SETTLED) {
            handoffMillis = null
        }
        passive = value
        if (value == false) entry.cancel()
        else if (gracePassed) entry.afterGrace()
        update()
    }

    private fun update() {
        if (entry.decision == MainStartupBrandDecision.SETTLED || !ValueAnimator.areAnimatorsEnabled()) {
            finish()
            return
        }
        policy.update(entranceReady, resumed, focused,
            if (entry.decision == MainStartupBrandDecision.PENDING) null else passive,
            ValueAnimator.areAnimatorsEnabled())
        if (policy.finished) finish()
        else if (policy.running && !running) {
            millis = 0f
            running = true
        }
    }

    fun frame(elapsed: Float) {
        if (!running) return
        if (!ValueAnimator.areAnimatorsEnabled() || elapsed >= StartupBrandDurationMillis) finish()
        else millis = elapsed
    }

    fun finish() {
        if (running) handoffMillis = millis
        entry.cancel()
        policy.finish()
        running = false
        millis = StartupBrandDurationMillis
    }
}

@Composable
internal fun StartupBrandClock(intro: StartupBrandIntro) {
    DisposableEffect(intro) { onDispose { intro.finish() } }
    LaunchedEffect(intro, intro.running) {
        if (!intro.running) return@LaunchedEffect
        val start = withFrameNanos { it }
        intro.frame(0f)
        while (intro.running) {
            withFrameNanos { intro.frame((it - start) / 1_000_000f) }
        }
    }
}
