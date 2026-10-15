package at.bernhardberger.tvhplayer.core

import kotlin.math.abs

/** Preserve the seed's hue, with HSL saturation capped at 35% and lightness fixed at 7.5%, the theme background's lightness. */
internal fun detailsTintRgb(seed: Int): Int {
    val (hue, saturation, value) = rgbToHsv(seed)
    val delta = saturation * value
    val lightness = value - delta / 2f
    val hslSaturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
    val s = hslSaturation.coerceIn(0f, .35f)
    // HSL → HSV, reusing the existing pure RGB conversion.
    return hsvToRgb(hue, 2f * s / (1f + s), .075f * (1f + s))
}
