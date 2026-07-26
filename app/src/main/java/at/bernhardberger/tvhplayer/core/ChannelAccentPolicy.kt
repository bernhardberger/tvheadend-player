package at.bernhardberger.tvhplayer.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Channel accent colours are derived from the channel's own picon, never from its name.
 *
 * A logo is mostly background — white, black, or transparent — so the raw dominant colour
 * of a picon is usually useless. [isUsableAccent] rejects those, and [normalizeAccentRgb]
 * pulls whatever survives into a single dark-theme band so a row of cards reads as one
 * system rather than a paint chart.
 *
 * Deliberately free of `android.graphics` so it stays unit-testable: the project has no
 * Robolectric on the test classpath.
 */

/** Minimum saturation before a sampled colour counts as a brand colour rather than grey. */
private const val MIN_ACCENT_SATURATION = 0.15f
private const val MIN_ACCENT_VALUE = 0.10f
private const val MAX_ACCENT_VALUE = 0.95f

/** Dark-theme band every accent is normalized into. */
private const val ACCENT_MIN_SATURATION = 0.35f
private const val ACCENT_MAX_SATURATION = 0.60f
private const val ACCENT_VALUE = 0.38f

/** Neutral fallback (a desaturated slate) used when no usable colour can be sampled. */
const val NEUTRAL_ACCENT_RGB: Int = 0x2E3440

/**
 * True when [rgb] is saturated and mid-toned enough to read as a brand colour.
 * Rejects the white/black/grey that dominates most picons.
 */
fun isUsableAccent(rgb: Int): Boolean {
    val (_, s, v) = rgbToHsv(rgb)
    return s >= MIN_ACCENT_SATURATION && v >= MIN_ACCENT_VALUE && v <= MAX_ACCENT_VALUE
}

/**
 * Preserve the hue of [rgb] but clamp saturation and value into the dark-theme band,
 * so every channel accent has the same weight behind a picon.
 */
fun normalizeAccentRgb(rgb: Int): Int {
    val (h, s, _) = rgbToHsv(rgb)
    val clampedS = min(ACCENT_MAX_SATURATION, max(ACCENT_MIN_SATURATION, s))
    return hsvToRgb(h, clampedS, ACCENT_VALUE)
}

/**
 * Pick the first usable colour from a swatch preference chain, normalized for the theme.
 * Returns [NEUTRAL_ACCENT_RGB] when nothing in [candidates] is usable.
 */
fun selectAccentRgb(candidates: List<Int?>): Int {
    val usable = candidates.firstOrNull { it != null && isUsableAccent(it) }
    return if (usable == null) NEUTRAL_ACCENT_RGB else normalizeAccentRgb(usable)
}

/** Returns hue in 0..360, saturation in 0..1, value in 0..1. */
internal fun rgbToHsv(rgb: Int): Triple<Float, Float, Float> {
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC

    val hue = when {
        delta < 1e-6f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val saturation = if (maxC <= 0f) 0f else delta / maxC
    return Triple(if (hue < 0f) hue + 360f else hue, saturation, maxC)
}

internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val c = value * saturation
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = value - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(component: Float): Int =
        ((component + m) * 255f).toInt().coerceIn(0, 255)
    return (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
}
