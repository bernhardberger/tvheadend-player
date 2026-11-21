package at.bernhardberger.tvhplayer.ui.player

import kotlin.math.abs
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateChange

data class DisplayModeSpec(
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val modeId: Int = 0,
    val alternativeRefreshRates: List<Float> = emptyList(),
)

// 0.05% accepts mode-reporting rounding, but separates NTSC rates from whole rates.
fun matchesVideoRate(displayRate: Float, videoRate: Float): Boolean =
    abs(displayRate - videoRate) / videoRate <= 0.0005f

/** Latch a clean unrequested observation until release; a cleared vote may still be switching back. */
fun nextBaseMode(
    current: DisplayModeSpec?,
    observed: DisplayModeSpec?,
    unrequested: Boolean,
    lastRequestedRate: Float?,
): DisplayModeSpec? {
    if (current != null) return current
    if (!unrequested || observed == null) return null
    if (lastRequestedRate != null && matchesVideoRate(observed.refreshRate, lastRequestedRate)) return null
    return observed
}

/** Keep non-seamless requests over browse; compare with the unrequested base, never a switched mode. */
fun keepVideoFrameRateInBrowse(change: SurfaceFrameRateChange, rate: Float?, base: DisplayModeSpec?): Boolean {
    if (rate == null || !rate.isFinite() || rate <= 0f) return false
    return when (change) {
        SurfaceFrameRateChange.DISPLAY_MODE -> true
        SurfaceFrameRateChange.SEAMLESS_DEFAULT -> false
        SurfaceFrameRateChange.ALWAYS -> base == null ||
            (!matchesVideoRate(base.refreshRate, rate) && base.alternativeRefreshRates.none { matchesVideoRate(it, rate) })
    }
}

/** Exact display cadence at the current resolution, preferring the current mode. No multiples. */
fun videoDisplayMode(rate: Float, current: DisplayModeSpec, supported: List<DisplayModeSpec>): DisplayModeSpec? {
    if (!rate.isFinite() || rate <= 0f) return null
    fun matches(mode: DisplayModeSpec): Boolean =
        mode.width == current.width && mode.height == current.height && matchesVideoRate(mode.refreshRate, rate)

    return current.takeIf(::matches) ?: supported.firstOrNull(::matches)
}

/** Window preference update, or null to leave it unchanged. No matching mode clears a preference. */
fun displayModeIdToRequest(
    selected: DisplayModeSpec?,
    current: DisplayModeSpec,
    preferred: Int,
    pin: Boolean = false,
): Int? {
    if (!pin && selected?.modeId == current.modeId && preferred == 0) return null
    // Pin the current mode when replacing a pending switch; clearing it could restore the default.
    val modeId = selected?.modeId ?: 0
    return modeId.takeIf { it != preferred }
}

/** Refresh rates the display offers at its current resolution, lowest first; near-equal reports count once. */
fun displayRefreshRateChoices(current: DisplayModeSpec, supported: List<DisplayModeSpec>): List<Float> =
    (supported + current).filter { it.width == current.width && it.height == current.height }
        .map { it.refreshRate }.filter { it.isFinite() && it > 0f }.sorted()
        .fold(emptyList()) { rates, rate -> if (rates.any { matchesVideoRate(it, rate) }) rates else rates + rate }

/** Release the app's pin for an installed modern vote only if Android allows non-seamless switches. */
fun windowDisplayMode(
    videoChange: SurfaceFrameRateChange?,
    videoRate: Float?,
    appRate: Float?,
    current: DisplayModeSpec,
    supported: List<DisplayModeSpec>,
    systemAllowsAnySwitch: Boolean,
): DisplayModeSpec? {
    if (videoChange == SurfaceFrameRateChange.ALWAYS && videoRate != null && systemAllowsAnySwitch) return null
    val requested = videoRate?.takeIf { videoChange == SurfaceFrameRateChange.DISPLAY_MODE }
    return requested?.let { videoDisplayMode(it, current, supported) }
        ?: appRate?.let { videoDisplayMode(it, current, supported) }
}
