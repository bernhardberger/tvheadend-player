package at.bernhardberger.tvhplayer.ui.player

import kotlin.math.abs

internal data class DisplayModeSpec(val width: Int, val height: Int, val refreshRate: Float, val modeId: Int = 0)

/** Exact display cadence at the current resolution, preferring the current mode. No multiples. */
internal fun videoDisplayMode(rate: Float, current: DisplayModeSpec, supported: List<DisplayModeSpec>): DisplayModeSpec? {
    if (!rate.isFinite() || rate <= 0f) return null
    // 0.05% accepts mode-reporting rounding, but separates NTSC rates from whole rates.
    fun matches(mode: DisplayModeSpec): Boolean =
        mode.width == current.width && mode.height == current.height && abs(mode.refreshRate - rate) / rate <= 0.0005f

    return current.takeIf(::matches) ?: supported.firstOrNull(::matches)
}

/** Window preference update, or null to leave it unchanged. No matching mode clears a preference. */
internal fun displayModeIdToRequest(selected: DisplayModeSpec?, current: DisplayModeSpec, preferred: Int): Int? {
    if (selected?.modeId == current.modeId && preferred == 0) return null
    // Pin the current mode when replacing a pending switch; clearing it could restore the default.
    val modeId = selected?.modeId ?: 0
    return modeId.takeIf { it != preferred }
}
