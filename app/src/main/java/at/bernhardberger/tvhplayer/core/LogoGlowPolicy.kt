package at.bernhardberger.tvhplayer.core

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The logo glow's pixels: a page [background] lit by a channel's [accent] from the top-right
 * corner, fading into the background toward the left and the bottom, as Google TV lights a
 * person's page with their photo's colour. Both colours are 0xRRGGBB; the result is opaque ARGB,
 * [width] by [height], row by row.
 *
 * It is baked once, so it is dithered here: a large, slow, dark ramp in 8 bits would show bands
 * on an OLED screen, and dithering a gradient while drawing it costs every frame. An ordered
 * 8×8 Bayer threshold keeps each block's average at the exact colour, so a smooth stretch of
 * the baked picture keeps it free of bands too.
 */
fun logoGlowPixels(accent: Int, background: Int, width: Int, height: Int): IntArray {
    val pixels = IntArray(width * height)
    val reachX = width * LogoGlowReachX
    val reachY = height * LogoGlowReachY
    for (y in 0 until height) {
        val dy = (y + .5f) / reachY
        for (x in 0 until width) {
            val dx = (width - x - .5f) / reachX
            val rest = (1f - sqrt(dx * dx + dy * dy)).coerceAtLeast(0f)
            val share = LogoGlowPeak * rest * rest
            val threshold = (BAYER[(y and 7) * 8 + (x and 7)] + .5f) / 64f
            fun channel(shift: Int): Int {
                val base = background shr shift and 0xff
                val lit = base + ((accent shr shift and 0xff) - base) * share
                return floor(lit + threshold).toInt().coerceIn(0, 0xff) shl shift
            }
            pixels[y * width + x] = (0xff shl 24) or channel(16) or channel(8) or channel(0)
        }
    }
    return pixels
}

/** The accent's share at the glow's centre: a normalized accent there reads like Google TV's corner. */
private const val LogoGlowPeak = 0.55f

/** The glow's reach from the top-right corner, as shares of the width and the height. */
private const val LogoGlowReachX = 0.9f
private const val LogoGlowReachY = 1.2f

/** The 8×8 ordered-dither matrix, thresholds 0 to 63. */
private val BAYER = intArrayOf(
    0, 32, 8, 40, 2, 34, 10, 42,
    48, 16, 56, 24, 50, 18, 58, 26,
    12, 44, 4, 36, 14, 46, 6, 38,
    60, 28, 52, 20, 62, 30, 54, 22,
    3, 35, 11, 43, 1, 33, 9, 41,
    51, 19, 59, 27, 49, 17, 57, 25,
    15, 47, 7, 39, 13, 45, 5, 37,
    63, 31, 55, 23, 61, 29, 53, 21,
)
