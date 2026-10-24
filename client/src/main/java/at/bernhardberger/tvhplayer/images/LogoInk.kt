package at.bernhardberger.tvhplayer.images

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Darkens the parts of a channel logo that would vanish on a light [background], such as a focused
 * row: pale parts (white, light grey, pastel) drawn straight onto the transparent ground rather than
 * onto another colour of the logo. Their lightness is inverted with hue and chroma kept, so white
 * lettering turns black, while brand colours, white plates carrying dark lettering, and lettering
 * on the logo's own colour fields stay as they are.
 *
 * [pixels] are non-premultiplied ARGB, row by row; [background] is opaque ARGB. Returns the changed
 * pixels, or null when no part would vanish. The thresholds were set against the 400×240 picons of
 * a real line-up; parts and their edges are judged relative to the logo's size.
 */
fun inkFadingLogoParts(pixels: IntArray, width: Int, height: Int, background: Int): IntArray? {
    require(pixels.size == width * height) { "pixels must hold width × height values" }
    val size = pixels.size
    val ground = luminance(background)
    val colouredLimit = if (ground < DARK_GROUND_LUMINANCE) PALE_COLOURED_CONTRAST_ON_DARK else PALE_COLOURED_CONTRAST
    val luma = FloatArray(size)
    val pale = BooleanArray(size)
    val solid = BooleanArray(size)
    var solidCount = 0
    for (i in 0 until size) {
        val p = pixels[i]
        luma[i] = luminance(p)
        if (p ushr 24 > 127) {
            solid[i] = true
            solidCount++
            val limit = if (chroma(p) < NEUTRAL_CHROMA) PALE_NEUTRAL_CONTRAST else colouredLimit
            pale[i] = contrast(luma[i], ground) < limit
        }
    }
    if (solidCount == 0) return null

    // Connected pale parts, and what each one borders: the transparent ground or the logo's own ink.
    val part = IntArray(size)
    val stack = IntArray(size)
    var parts = 0
    for (start in 0 until size) {
        if (!pale[start] || part[start] != 0) continue
        parts++
        part[start] = parts
        var top = 0
        stack[top++] = start
        while (top > 0) {
            val i = stack[--top]
            val x = i % width
            if (x > 0 && pale[i - 1] && part[i - 1] == 0) { part[i - 1] = parts; stack[top++] = i - 1 }
            if (x < width - 1 && pale[i + 1] && part[i + 1] == 0) { part[i + 1] = parts; stack[top++] = i + 1 }
            if (i >= width && pale[i - width] && part[i - width] == 0) { part[i - width] = parts; stack[top++] = i - width }
            if (i + width < size && pale[i + width] && part[i + width] == 0) { part[i + width] = parts; stack[top++] = i + width }
        }
    }
    if (parts == 0) return null
    val area = IntArray(parts + 1)
    val groundEdges = IntArray(parts + 1)
    val inkEdges = IntArray(parts + 1)
    for (i in 0 until size) {
        val id = part[i]
        if (id == 0) continue
        area[id]++
        val x = i % width
        fun border(n: Int, inside: Boolean) {
            if (!inside || !solid[n]) groundEdges[id]++ else if (!pale[n]) inkEdges[id]++
        }
        border(i - 1, x > 0)
        border(i + 1, x < width - 1)
        border(i - width, i >= width)
        border(i + width, i + width < size)
    }

    // Median contrast of each part on the background, as it is and once darkened.
    val offset = IntArray(parts + 2)
    for (id in 1..parts) offset[id + 1] = offset[id] + area[id]
    val fill = offset.copyOf()
    val now = FloatArray(offset[parts + 1])
    val inked = FloatArray(offset[parts + 1])
    for (i in 0 until size) {
        val id = part[i]
        if (id == 0) continue
        val at = fill[id]++
        now[at] = contrast(luma[i], ground)
        inked[at] = contrast(luminance(invertLightness(pixels[i])), ground)
    }
    fun median(values: FloatArray, id: Int): Float {
        val slice = values.copyOfRange(offset[id], offset[id + 1]).apply { sort() }
        return slice[slice.size / 2]
    }

    val darken = BooleanArray(parts + 1)
    val candidates = mutableListOf<Pair<Int, Float>>()
    for (id in 1..parts) {
        val edges = groundEdges[id] + inkEdges[id]
        val onGround = area[id] >= MIN_PART_SHARE * solidCount && edges > 0 && inkEdges[id] < MAX_INK_BORDER_SHARE * edges
        if (!onGround) continue
        val darkened = median(inked, id)
        if (darkened >= READABLE_CONTRAST) darken[id] = true
        else candidates += id to darkened / median(now, id)
    }
    if (darken.none { it }) return null
    // Once a logo has a vanishing part, its other pale parts go dark with it whenever that clearly
    // helps, so one wordmark doesn't end up half dark and half pale.
    for ((id, gain) in candidates) if (gain >= GROUP_CONTRAST_GAIN) darken[id] = true

    // Grow the chosen parts over their anti-aliased rim, so no light fringe stays around the ink.
    val radius = max(1, (RIM_RADIUS_AT_REFERENCE * max(width, height) / REFERENCE_WIDTH).roundToInt())
    val chosen = BooleanArray(size) { darken[part[it]] }
    val rim = dilate(dilate(chosen, width, height, radius, horizontal = true), width, height, radius, horizontal = false)
    val out = pixels.copyOf()
    for (i in 0 until size) {
        if (rim[i] && pixels[i] ushr 24 > 0 && contrast(luma[i], ground) < RIM_CONTRAST) out[i] = invertLightness(pixels[i])
    }
    return out
}

/** Lightness becomes 1 − L with hue and chroma kept as far as the gamut allows; alpha stays. */
private fun invertLightness(argb: Int): Int {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val lightness = (high + low) / 2f
    val target = 1f - lightness
    val above = high - lightness
    val below = lightness - low
    var scale = 1f
    if (above > 0f) scale = min(scale, (1f - target) / above)
    if (below > 0f) scale = min(scale, target / below)
    scale = scale.coerceIn(0f, 1f)
    fun channel(value: Float) = ((target + (value - lightness) * scale).coerceIn(0f, 1f) * 255f).roundToInt()
    return (argb and 0xFF000000.toInt()) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

private fun chroma(argb: Int): Float {
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    return (max(r, max(g, b)) - min(r, min(g, b))) / 255f
}

/** WCAG relative luminance of the colour, ignoring alpha. */
private fun luminance(argb: Int): Float {
    fun linear(value: Int): Float {
        val c = value / 255f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * linear(argb shr 16 and 0xFF) + 0.7152f * linear(argb shr 8 and 0xFF) + 0.0722f * linear(argb and 0xFF)
}

private fun contrast(a: Float, b: Float): Float = (max(a, b) + 0.05f) / (min(a, b) + 0.05f)

private fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int, horizontal: Boolean): BooleanArray {
    val out = BooleanArray(mask.size)
    for (y in 0 until height) for (x in 0 until width) {
        if (!mask[y * width + x]) continue
        if (horizontal) for (k in max(0, x - radius)..min(width - 1, x + radius)) out[y * width + k] = true
        else for (k in max(0, y - radius)..min(height - 1, y + radius)) out[k * width + x] = true
    }
    return out
}

/** Below this chroma a colour counts as white or grey. */
private const val NEUTRAL_CHROMA = 0.25f

/** White and greys count as pale under this contrast on the background... */
private const val PALE_NEUTRAL_CONTRAST = 1.8f

/** ...colours only under this one, so a mid-tone brand colour that still reads keeps its colour. */
private const val PALE_COLOURED_CONTRAST = 1.5f

/**
 * On a dark ground a dim colour gets a little more room: dark blues like PULS 24's sit just above
 * 1.5 and still vanish, while on a light ground 1.6 already browns a mid orange like teleSUR's.
 */
private const val PALE_COLOURED_CONTRAST_ON_DARK = 1.6f

/** A background darker than this counts as a dark ground. */
private const val DARK_GROUND_LUMINANCE = 0.18f

/** A part sits on the ground when under this share of its border touches the logo's own ink. */
private const val MAX_INK_BORDER_SHARE = 0.25f

/** Smaller parts are anti-aliasing specks, not lettering. */
private const val MIN_PART_SHARE = 0.002f

/** A part is darkened when the darkened part reaches this median contrast on the background... */
private const val READABLE_CONTRAST = 2.5f

/** ...or, beside such a part, when darkening raises its contrast at least this much. */
private const val GROUP_CONTRAST_GAIN = 1.5f

/** Rim pixels lighter than this contrast join the ink; the logo's darker colours never do. */
private const val RIM_CONTRAST = 1.9f

private const val RIM_RADIUS_AT_REFERENCE = 2f
private const val REFERENCE_WIDTH = 400f
