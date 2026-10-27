package at.bernhardberger.tvhplayer.core

import kotlin.math.pow

/** Minimum dim for the measured luminance pair, retaining the old floor when it already passes.
 * The measurement includes sRGB compositing (including translucent text), not a linear-light
 * interpolation of luminance: that would unnecessarily flatten even moderately bright art.
 */
internal fun detailsBackdropDimAlpha(floor: Float, luminanceAt: (Float) -> Pair<Double, Double>): Float {
    fun passes(alpha: Float): Boolean {
        val (foreground, background) = luminanceAt(alpha)
        return (foreground + .05) / (background + .05) >= 4.55
    }
    if (passes(floor)) return floor
    var low = floor
    var high = 1f
    repeat(12) {
        val middle = (low + high) / 2f
        if (passes(middle)) high = middle else low = middle
    }
    return high
}

internal fun detailsRelativeLuminance(red: Double, green: Double, blue: Double): Double {
    fun linear(value: Double) = if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
    return .2126 * linear(red) + .7152 * linear(green) + .0722 * linear(blue)
}

/** An upper RGB envelope preserves small bright details rather than averaging them away.
 * A bin's lightest channels may come from different pixels; this errs only toward a safer dim.
 */
internal fun detailsBackdropSamples(pixels: IntArray, width: Int, height: Int): IntArray {
    val samples = IntArray(160 * 90)
    for (row in 0 until 90) for (column in 0 until 160) {
        val left = column * width / 160
        val top = row * height / 90
        val right = ((column + 1) * width + 159) / 160
        val bottom = ((row + 1) * height + 89) / 90
        val index = row * 160 + column
        for (y in top until bottom) for (x in left until right) {
            val pixel = pixels[y * width + x]
            val coverage = (pixel ushr 24) / 255.0
            fun channel(shift: Int) = (((pixel shr shift and 255) * coverage + 26 * (1 - coverage)).toInt())
            val old = samples[index]
            samples[index] = (maxOf(old shr 16 and 255, channel(16)) shl 16) or
                (maxOf(old shr 8 and 255, channel(8)) shl 8) or maxOf(old and 255, channel(0))
        }
    }
    return samples
}

/** The sharp scrim's existing radial profile, shared by measurement and drawing. */
internal fun detailsScrimAlpha(x: Float, y: Float): Float {
    val radius = kotlin.math.sqrt((1f - x) * (1f - x) + (y / 1.2f) * (y / 1.2f))
    val index = DetailsScrimStops.indexOfFirst { it >= radius }
    if (index <= 0) return if (index == 0) DetailsScrimAlphas[0] else DetailsScrimAlphas.last()
    val fraction = (radius - DetailsScrimStops[index - 1]) / (DetailsScrimStops[index] - DetailsScrimStops[index - 1])
    return DetailsScrimAlphas[index - 1] + fraction * (DetailsScrimAlphas[index] - DetailsScrimAlphas[index - 1])
}

internal val DetailsScrimStops = floatArrayOf(0f, .3f, .55f, .8f, 1f)
internal val DetailsScrimAlphas = floatArrayOf(.05f, .2f, .6f, .85f, .92f)

/** Normalized bounds of readable sharp-state text; primary ink is opaque, other ink uses the
 * header's weakest metadata tone. Bounds come from layout, never from a per-frame image read.
 */
internal data class DetailsBackdropTextArea(val left: Float, val top: Float, val right: Float, val bottom: Float,
    val primary: Boolean)

/** Worst readable text over each bin, with the brightest possible details tint (#1a1a1a).
 * Quiet sections retain their existing row emphasis; the contrast floor concerns readable text.
 */
internal fun measuredDetailsDim(samples: IntArray, sharp: Boolean, floor: Float, textTop: Float = 0f,
    areas: List<DetailsBackdropTextArea> = emptyList()): Float =
    detailsBackdropDimAlpha(floor) { dim ->
        var worstRatio = Double.POSITIVE_INFINITY
        var worst = 1.0 to 0.0
        for (y in 0 until 90) for (x in 0 until 160) {
            // Sharp: include every possible text position inside the page's safe area. Taking the
            // top-right of each bin gives its weakest radial scrim, including cropped highlights.
            if ((y + 1) / 90f < textTop || x !in 8..152 || y > 86) continue
            val matching = if (sharp && areas.isNotEmpty()) areas.filter {
                x / 160f < it.right && (x + 1) / 160f > it.left && y / 90f < it.bottom && (y + 1) / 90f > it.top
            } else emptyList()
            if (sharp && areas.isNotEmpty() && matching.isEmpty()) continue
            val pixel = samples[y * 160 + x]
            val scrim = if (sharp) detailsScrimAlpha((x + 1) / 160f, y / 90f) else 0f
            val alpha = scrim + (1 - scrim) * dim
            fun channel(shift: Int) = ((pixel shr shift and 255) * (1 - alpha) + 26 * alpha) / 255.0
            val r = channel(16); val g = channel(8); val b = channel(0)
            val background = detailsRelativeLuminance(r, g, b)
            // The wide title can reach the right side; secondary text stays in the left column.
            val titleOnly = sharp && matching.isNotEmpty() && matching.all { it.primary }
            val primaryInk = titleOnly || !sharp
            val opacity = if (titleOnly) 1.0 else if (!sharp) .75 else .72
            fun ink(value: Double, primary: Int, secondary: Int) =
                (if (primaryInk) primary else secondary) / 255.0 * opacity + value * (1 - opacity)
            val foreground = detailsRelativeLuminance(ink(r, 225, 192), ink(g, 226, 199), ink(b, 229, 205))
            val ratio = (foreground + .05) / (background + .05)
            if (ratio < worstRatio) { worstRatio = ratio; worst = foreground to background }
        }
        worst
    }
