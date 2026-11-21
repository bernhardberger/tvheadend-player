package at.bernhardberger.tvhplayer.touch.player

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/*
 * The brightness slider moves the way the system's own slider does. Android stores brightness as
 * backlight level (0–255 in Settings.System.SCREEN_BRIGHTNESS; 0–1 in the window's
 * screenBrightness), but its slider runs on a perceptual curve (the hybrid log-gamma curve of the
 * platform's settings library, BrightnessUtils). Read as a plain fraction, a normal indoor level
 * sits near the slider's bottom.
 */

/** The slider position (0–1) for a backlight level (0–1). */
internal fun brightnessSlider(level: Float): Float {
    val scaled = level.coerceIn(0f, 1f) * CURVE_SCALE
    val position = if (scaled <= 1f) sqrt(scaled) * CURVE_R else CURVE_A * ln(scaled - CURVE_B) + CURVE_C
    return position.coerceIn(0f, 1f)
}

/** The backlight level (0–1) for a slider position (0–1). */
internal fun brightnessLevel(slider: Float): Float {
    val position = slider.coerceIn(0f, 1f)
    val scaled = if (position <= CURVE_R) {
        (position / CURVE_R).let { it * it }
    } else {
        exp((position - CURVE_C) / CURVE_A) + CURVE_B
    }
    return (scaled / CURVE_SCALE).coerceIn(0f, 1f)
}

private const val CURVE_SCALE = 12f
private const val CURVE_R = 0.5f
private const val CURVE_A = 0.17883277f
private const val CURVE_B = 0.28466892f
private const val CURVE_C = 0.55991073f
