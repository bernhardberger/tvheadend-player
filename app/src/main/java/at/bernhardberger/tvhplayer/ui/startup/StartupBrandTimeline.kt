package at.bernhardberger.tvhplayer.ui.startup

import at.bernhardberger.tvhplayer.core.MainStartupPresentation

/** The actual startup surface owns admission and lifetime, independently of metadata. */
internal fun mainStartupBrandPassiveHint(presentation: MainStartupPresentation): Boolean? = when (presentation) {
    is MainStartupPresentation.Passive -> true
    else -> false
}

internal const val StartupBrandDurationMillis = 1850f

/** Calmer native adaptation of the selected assembly; never a readiness delay. */
internal data class StartupBrandFrame(
    val trace: Float,
    val segmentFill: Float,
    val gap: Float,
    val ring: Float,
    val stroke: Float,
    val play: Float,
    val playScale: Float,
    val playDy: Float,
    val wordmark: Float,
)

internal fun startupBrandFrame(millis: Float): StartupBrandFrame {
    fun track(start: Float, end: Float, cubic: Boolean = false): Float {
        val t = ((millis - start) / (end - start)).coerceIn(0f, 1f)
        return if (cubic) 1f - (1f - t) * (1f - t) * (1f - t) else t * t * (3f - 2f * t)
    }
    val settle = track(1000f, 1480f, cubic = true)
    return StartupBrandFrame(
        trace = track(250f, 880f, cubic = true),
        segmentFill = track(460f, 970f),
        gap = 8f * (1f - track(700f, 1120f, cubic = true)),
        ring = track(970f, 1240f),
        stroke = track(250f, 450f) * (1f - track(1030f, 1270f)),
        play = track(1000f, 1330f),
        playScale = 1.1f - 0.1f * settle,
        playDy = -6f * (1f - settle),
        wordmark = track(400f, 940f, cubic = true),
    )
}

/** Main-thread, activity-local decision. A terminated intro can never be rearmed. */
internal class StartupBrandEligibility(eligible: Boolean) {
    var finished = !eligible
        private set
    var running = false
        private set

    fun update(entranceReady: Boolean, resumed: Boolean, focused: Boolean, passive: Boolean?, motion: Boolean) {
        if (finished) return
        if (passive == false || !motion) finish()
        else if (running) {
            if (!resumed || !focused) finish()
        } else if (entranceReady && resumed && focused && passive == true) running = true
    }

    fun finish() {
        running = false
        finished = true
    }
}
