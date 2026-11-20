// Keep the reference's numerical image/color formulas together and directly comparable.
@file:Suppress("MagicNumber", "MatchingDeclarationName", "TooManyFunctions")

package at.bernhardberger.tvhplayer.images

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import java.util.Random

internal data class ArtworkWindow(val x: Int, val y: Int, val width: Int, val height: Int)
internal data class ArtworkCrop(val x: Double, val y: Double, val width: Double, val height: Double)
// title_first.py: tuned on 65 covers. Keep these thresholds and candidate/merge rules unchanged.
private const val EDGE_T = 34
private const val GROW = .42
private const val GROW_SPLIT = .6
private const val GAP_ROWS = 3
private const val MAX_BAND_SHARE = .38
private const val MAX_CANDIDATES = 4
private const val CONTEXT_ROWS = 8
private const val CONTRAST_MIN = 1.8
private const val DENSITY_MIN = .10
private const val MERGE_GAP = .09
private const val MERGE_DENSITY = .40
private const val MERGE_CONTRAST = .0
private const val TITLE_BOTTOM = .52
private const val BAND_MARGIN = .0
private const val TOP_MARGIN = .06
private const val FACE_TOP = .10
private const val SCENE_TOP = .08
internal const val CARD_EDGE_FADE = .12
private const val BACKDROP_MARGIN = .03
private const val BACKDROP_MIN_SHARE = .22
private const val FLAT_STEP = 4
private const val BACKDROP_FLAT_MAX = .64
private const val SKIN_Y_MIN = 60
private const val SKIN_ROW_SHARE = .10
private const val SKIN_MIN_ROWS = 4

/** Pillow BILINEAR widens its triangle for reduction; point-sampled Android filtering aliases text/texture. */
internal fun artworkAnalysis(width: Int, height: Int, outputWidth: Int, outputHeight: Int,
    readRow: (Int, IntArray) -> Unit): IntArray {
    fun weights(input: Int, output: Int, index: Int): List<Pair<Int, Double>> {
        val center = (index + .5) * input / output
        val support = maxOf(1.0, input.toDouble() / output)
        val start = maxOf(0, kotlin.math.ceil(center - support - .5).toInt())
        val end = minOf(input - 1, kotlin.math.floor(center + support - .5).toInt())
        val raw = (start..end).map { it to maxOf(0.0, 1 - abs(it + .5 - center) / support) }
        val sum = raw.sumOf { it.second }
        return raw.map { it.first to it.second / sum }
    }
    fun sample(weights: List<Pair<Int, Double>>, pixel: (Int) -> Int): Int {
        fun channel(shift: Int): Double = weights.sumOf { (index, weight) -> (pixel(index) shr shift and 255) * weight }
        return rgb(channel(16), channel(8), channel(0))
    }
    val horizontalWeights = List(outputWidth) { weights(width, outputWidth, it) }
    val row = IntArray(width)
    val sums = DoubleArray(outputWidth * 3)
    val output = IntArray(outputWidth * outputHeight)
    // Only one decoded row and one destination row are live, never a full-cover or full-height intermediate.
    for (y in 0 until outputHeight) {
        sums.fill(0.0)
        val center = (y + .5) * height / outputHeight
        val support = maxOf(1.0, height.toDouble() / outputHeight)
        val start = maxOf(0, kotlin.math.ceil(center - support - .5).toInt())
        val end = minOf(height - 1, kotlin.math.floor(center + support - .5).toInt())
        fun verticalWeight(row: Int): Double = maxOf(0.0, 1 - abs(row + .5 - center) / support)
        val totalWeight = (start..end).sumOf { verticalWeight(it) }
        for (sourceY in start..end) {
            val weight = verticalWeight(sourceY) / totalWeight
            readRow(sourceY, row)
            for (x in 0 until outputWidth) {
                val p = sample(horizontalWeights[x]) { row[it] }
                sums[x * 3] += (p shr 16 and 255) * weight
                sums[x * 3 + 1] += (p shr 8 and 255) * weight
                sums[x * 3 + 2] += (p and 255) * weight
            }
        }
        for (x in 0 until outputWidth) output[y * outputWidth + x] = rgb(sums[x * 3], sums[x * 3 + 1], sums[x * 3 + 2])
    }
    return output
}

private data class TitleBand(val top: Double, val bottom: Double, val density: Double, val contrast: Double) {
    val tall: Boolean get() = bottom - top > MAX_BAND_SHARE
}

private fun channelStep(a: Int, b: Int): Int = maxOf(abs((a shr 16 and 255) - (b shr 16 and 255)),
    abs((a shr 8 and 255) - (b shr 8 and 255)), abs((a and 255) - (b and 255)))

private fun titleDensity(pixels: IntArray, width: Int, height: Int): DoubleArray {
    if (width < 2 || height < 3) return DoubleArray(height)
    val raw = DoubleArray(height) { y ->
        (0 until width - 1).count { x -> channelStep(pixels[y * width + x], pixels[y * width + x + 1]) > EDGE_T }
            .toDouble() / (width - 1)
    }
    return DoubleArray(height) { y ->
        if (y == 0 || y == height - 1) 0.0 else (raw[y - 1] + raw[y] + raw[y + 1]) / 3
    }
}

@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements") // Reference band growth and context score.
private fun titleCandidates(d: DoubleArray): List<TitleBand> {
    val height = d.size
    val masked = BooleanArray(height)
    val out = mutableListOf<TitleBand>()
    repeat(MAX_CANDIDATES) {
        val peak = d.indices.maxBy { if (masked[it]) -1.0 else d[it] }
        if (masked[peak] || d[peak] <= 0) return out
        fun end(step: Int, threshold: Double): Int {
            var y = peak; var gap = 0; var last = peak
            while (y + step in d.indices && !masked[y + step]) {
                y += step
                if (d[y] >= threshold) { gap = 0; last = y } else {
                    gap++
                    if (gap > GAP_ROWS) break
                }
                if (abs(last - peak) > MAX_BAND_SHARE * height) break
            }
            return last
        }
        var top = end(-1, GROW * d[peak]); var bottom = end(1, GROW * d[peak]) + 1
        if ((bottom - top).toDouble() / height > MAX_BAND_SHARE) {
            top = end(-1, GROW_SPLIT * d[peak]); bottom = end(1, GROW_SPLIT * d[peak]) + 1
        }
        val context = (maxOf(0, top - CONTEXT_ROWS) until maxOf(0, top - 1)).map { d[it] } +
            (minOf(height, bottom + 1) until minOf(height, bottom + CONTEXT_ROWS)).map { d[it] }
        val inside = (top until bottom).map { d[it] }.average()
        val contrast = inside / ((if (context.isEmpty()) 0.0 else context.average()) + .02)
        out += TitleBand(top.toDouble() / height, bottom.toDouble() / height, inside, contrast)
        for (y in maxOf(0, top - 2) until minOf(height, bottom + 2)) masked[y] = true
    }
    return out
}

@Suppress("CyclomaticComplexMethod", "ComplexCondition") // Reference's repeated adjacent/overlapping title-line merge.
private fun findTitle(pixels: IntArray, width: Int, height: Int): TitleBand? {
    val candidates = titleCandidates(titleDensity(pixels, width, height))
    val best = candidates.filter { it.contrast >= CONTRAST_MIN && it.density >= DENSITY_MIN && !it.tall }
        .maxByOrNull { it.contrast * it.density } ?: return null
    var title = best
    do {
        var merged = false
        for (candidate in candidates) {
            if (candidate === best || candidate.tall || candidate.density < MERGE_DENSITY * best.density ||
                candidate.contrast < MERGE_CONTRAST) continue
            val gap = maxOf(candidate.top - title.bottom, title.top - candidate.bottom)
            if (gap in 0.0..MERGE_GAP || (candidate.top < title.bottom && candidate.bottom > title.top)) {
                val top = minOf(title.top, candidate.top); val bottom = maxOf(title.bottom, candidate.bottom)
                if (bottom - top <= MAX_BAND_SHARE && (top != title.top || bottom != title.bottom)) {
                    title = title.copy(top = top, bottom = bottom); merged = true
                }
            }
        }
    } while (merged)
    return title
}

private data class FaceBand(val top: Double, val centerX: Double)
private fun faceRows(pixels: IntArray, width: Int, height: Int): FaceBand? {
    val skin = BooleanArray(pixels.size) {
        val p = pixels[it]; val r = (p shr 16 and 255).toDouble(); val g = (p shr 8 and 255).toDouble()
        val b = (p and 255).toDouble()
        val y = (.299 * r + .587 * g + .114 * b).roundToInt()
        val cb = (128 - .168736 * r - .331264 * g + .5 * b).roundToInt()
        val cr = (128 + .5 * r - .418688 * g - .081312 * b).roundToInt()
        cb in 77..127 && cr in 137..173 && y >= SKIN_Y_MIN
    }
    val rows = BooleanArray(height) { y -> (0 until width).count { skin[y * width + it] } >= width * SKIN_ROW_SHARE }
    var run = 0
    for (y in rows.indices) {
        run = if (rows[y]) run + 1 else 0
        if (run < SKIN_MIN_ROWS) continue
        val start = y - run + 1
        var end = start
        while (end < height && rows[end]) end++
        var sum = 0.0; var count = 0
        for (row in start until end) for (x in 0 until width) if (skin[row * width + x]) { sum += x; count++ }
        return FaceBand(start.toDouble() / height, sum / maxOf(1, count) / width)
    }
    return null
}

/** The caller has decided treatment from source dimensions, before rounding the analysis copy. */
internal fun titleCardWindow(pixels: IntArray, width: Int, height: Int, ratio: Double): ArtworkCrop {
    val cropHeight = width / ratio
    val title = findTitle(pixels, width, height)
    val face = if (title == null) faceRows(pixels, width, height) else null
    val top = if (title != null) {
        val proposed = title.bottom * height + BAND_MARGIN * cropHeight - TITLE_BOTTOM * cropHeight
        if (title.top * height - proposed < TOP_MARGIN * cropHeight) title.top * height - TOP_MARGIN * cropHeight
        else proposed
    } else if (face != null) face.top * height - FACE_TOP * cropHeight else SCENE_TOP * height
    return ArtworkCrop(0.0, minOf(maxOf(0.0, top), height - .45 * cropHeight), width.toDouble(), cropHeight)
}

internal fun titleBackdropWindow(pixels: IntArray, width: Int, height: Int, ratio: Double): ArtworkCrop {
    val full = width / ratio
    val title = findTitle(pixels, width, height)
    val face = faceRows(pixels, width, height)
    if (title == null) {
        val proposed = face?.let { it.top * height - FACE_TOP * full } ?: (SCENE_TOP * height)
        val top = minOf(maxOf(0.0, proposed), height - full)
        return ArtworkCrop(0.0, top, width.toDouble(), full)
    }
    val margin = BACKDROP_MARGIN * height
    val regions = listOf(0.0 to maxOf(0.0, title.top * height - margin),
        minOf(height.toDouble(), title.bottom * height + margin) to height.toDouble())
    var index = if (regions[0].second >= regions[1].second - regions[1].first) 0 else 1
    if (regions[index].second - regions[index].first < BACKDROP_MIN_SHARE * height) index = 1 - index
    val (start, end) = regions[index]
    val cropHeight = minOf(end - start, full); val cropWidth = cropHeight * ratio
    val faceInRegion = face?.takeIf { it.top * height >= start && it.top * height < end }
    val top = faceInRegion?.let { (it.top * height - FACE_TOP * cropHeight).coerceIn(start, end - cropHeight) } ?: start
    val center = faceInRegion?.let { it.centerX * width } ?: (width / 2.0)
    return ArtworkCrop((center - cropWidth / 2).coerceIn(0.0, width - cropWidth), top, cropWidth, cropHeight)
}

/** Receives the selected crop, resized to 96px wide, just like flat_share in the reference. */
internal fun backdropHasScene(pixels: IntArray, width: Int, height: Int): Boolean {
    var flat = 0
    for (y in 0 until height - 1) for (x in 0 until width - 1) {
        val p = pixels[y * width + x]
        if (channelStep(p, pixels[y * width + x + 1]) < FLAT_STEP &&
            channelStep(p, pixels[(y + 1) * width + x]) < FLAT_STEP) flat++
    }
    return flat.toDouble() / ((width - 1) * (height - 1)) <= BACKDROP_FLAT_MAX
}

internal fun bottomArtworkFill(pixels: IntArray, width: Int, height: Int, fallback: Int): Int {
    val pw = maxOf(1, (width * .15).toInt()); val ph = maxOf(1, (height * .10).toInt())
    val colors = IntArray(2) { side ->
        var r = 0.0; var g = 0.0; var b = 0.0
        for (y in height - ph until height) for (x in side * (width - pw) until side * (width - pw) + pw) {
            val p = pixels[y * width + x]; r += p shr 16 and 255; g += p shr 8 and 255; b += p and 255
        }
        val (h, l, s) = artworkHsl(rgb(r / (pw * ph), g / (pw * ph), b / (pw * ph)))
        if (s < .12 || l !in .05.. .95) fallback else fromHsl(h, l.coerceIn(.06, .12), minOf(s, .34))
    }
    // Reference truncates the mean of the two clamped bottom corners.
    return (0xff shl 24) or (((colors[0] shr 16 and 255) + (colors[1] shr 16 and 255)) / 2 shl 16) or
        (((colors[0] shr 8 and 255) + (colors[1] shr 8 and 255)) / 2 shl 8) or
        (((colors[0] and 255) + (colors[1] and 255)) / 2)
}
internal const val COVER_BOX_MAX_ASPECT = 1.25
internal fun artworkAnalysisSize(width: Int, height: Int): Pair<Int, Int> {
    return 96 to (height * 96.0 / width).roundToInt().coerceIn(1, 256)
}
internal fun needsArtworkTreatment(width: Int, height: Int, ratio: Double): Boolean {
    val aspect = width.toDouble() / height
    return aspect < ratio * .98 && abs(aspect / (16.0 / 9) - 1) > .02
}
internal fun usesCoverBox(width: Int, height: Int): Boolean = width.toDouble() / height <= COVER_BOX_MAX_ASPECT
internal fun coverWindow(width: Int, height: Int, outputWidth: Int, outputHeight: Int,
    startClearance: Int = 0, plateAtEnd: Boolean = false): ArtworkWindow {
    var coverWidth = (outputHeight * width.toDouble() / height).roundToInt().coerceIn(1, outputWidth)
    coverWidth += (outputWidth - coverWidth) % 2
    // The smallest shift clearing start also leaves the most possible room for an end mark.
    // If both cannot fit, start takes priority; the cover must still stay inside the card.
    val start = maxOf((outputWidth - coverWidth) / 2, startClearance).coerceAtMost(outputWidth - coverWidth)
    return ArtworkWindow(if (plateAtEnd) outputWidth - coverWidth - start else start, 0, coverWidth, outputHeight)
}

/** Round-4 attention.py: squared first-difference energy, edge/plate penalties and centre-top bias.
 * [pixels] is the bilinearly downsampled, approximately 96px-wide analysis copy, not the decode.
 * The horizontal search extends the same rule to pictures wider than the destination.
 */
// Direct reference candidate scoring, including horizontal/RTL cases.
@Suppress("CyclomaticComplexMethod")
internal fun attentionWindow(pixels: IntArray, width: Int, height: Int, ratio: Double, plate: Boolean,
    plateAtEnd: Boolean = false): ArtworkWindow {
    val cropWidth = minOf(width, (height * ratio).roundToInt().coerceAtLeast(1))
    val cropHeight = minOf(height, (width / ratio).roundToInt().coerceAtLeast(1))
    if (cropWidth == width && cropHeight == height) return ArtworkWindow(0, 0, width, height)
    val grey = DoubleArray(pixels.size) {
        val p = pixels[it]
        (p shr 16 and 255) * .299 + (p shr 8 and 255) * .587 + (p and 255) * .114
    }
    // Summed-area table makes each candidate's score independent of its area.
    val stride = width + 1
    val integral = DoubleArray(stride * (height + 1))
    for (y in 0 until height) for (x in 0 until width) {
        val i = y * width + x
        val gradient = abs(grey[i] - grey[y * width + maxOf(0, x - 1)]) +
            abs(grey[i] - grey[maxOf(0, y - 1) * width + x])
        val energy = if (x !in 2 until width - 2 || y !in 2 until height - 2) 0.0 else gradient * gradient
        val j = (y + 1) * stride + x + 1
        integral[j] = energy + integral[j - 1] + integral[j - stride] - integral[j - stride - 1]
    }
    fun sum(x0: Int, y0: Int, x1: Int, y1: Int): Double {
        val l = x0.coerceIn(0, width); val r = x1.coerceIn(0, width)
        val t = y0.coerceIn(0, height); val b = y1.coerceIn(0, height)
        return integral[b * stride + r] - integral[b * stride + l] - integral[t * stride + r] + integral[t * stride + l]
    }
    val vertical = cropHeight < height
    val length = if (vertical) cropHeight else cropWidth
    val extent = if (vertical) height else width
    val margin = maxOf(1, (length * .08).roundToInt())
    var best = Double.NEGATIVE_INFINITY
    var start = 0
    for (offset in 0..extent - length) {
        val x = if (vertical) 0 else offset
        val y = if (vertical) offset else 0
        val detail = if (vertical) sum(x, y + margin, x + cropWidth, y + cropHeight - margin)
            else sum(x + margin, y, x + cropWidth - margin, y + cropHeight)
        val edge = if (vertical) sum(0, y - 1, width, y + 2) + sum(0, y + cropHeight - 2, width, y + cropHeight + 1)
            else sum(x - 1, 0, x + 2, height) + sum(x + cropWidth - 2, 0, x + cropWidth + 1, height)
        val plateLeft = if (plateAtEnd) .69 else .04
        val plateRight = if (plateAtEnd) .96 else .31
        val covered = if (plate) sum(x + (plateLeft * cropWidth).roundToInt(), y + (.64 * cropHeight).roundToInt(),
            x + (plateRight * cropWidth).roundToInt(), y + (.94 * cropHeight).roundToInt()) else 0.0
        val bias = 1 - .15 * abs((offset + length / 2.0) / extent - if (vertical) .42 else .5)
        val score = (detail - 6 * edge - 8 * covered) * bias
        if (score > best) { best = score; start = offset }
    }
    return ArtworkWindow(if (vertical) 0 else start, if (vertical) start else 0, cropWidth, cropHeight)
}

/** Where a channel's logo stands on a programme picture. */
internal enum class ArtworkLogoCorner { BOTTOM_START, BOTTOM_END, TOP_START, TOP_END }

/** What a logo stands on in its corner: the corner shade, or the picture itself where that is calm and clearly light or dark. */
internal enum class ArtworkLogoGround { SHADE, LIGHT_PICTURE, DARK_PICTURE }

internal data class ArtworkLogoPlace(val corner: ArtworkLogoCorner, val ground: ArtworkLogoGround)

// A step between neighbouring pixels counts from the low value and fully at the high one, on the
// strongest colour channel: lettering in a colour of the picture's own brightness still counts.
private const val LOGO_STEP_LOW = 24.0
private const val LOGO_STEP_HIGH = 72.0
// The room a logo needs around its box, and the wider neighbourhood that only crowds it, in box widths.
private const val LOGO_ROOM = 6.0 / 56
private const val LOGO_NEIGHBOURHOOD = 20.0 / 56
private const val LOGO_NEIGHBOUR_WEIGHT = .5
// Detail low in a picture is far more often its drawn-in title than detail at the top, where
// faces and sky are: a logo over a face is the lesser fault.
private const val LOGO_BOTTOM_WEIGHT = 2.5
// Corners this close are equally good; the earlier one in reading order from the bottom start wins.
private const val LOGO_TIE = .01
// A ground this calm needs no shade when it is also clearly light or dark (linear luminance).
private const val LOGO_CALM = .04
private const val LOGO_LIGHT_GROUND = .4
private const val LOGO_DARK_GROUND = .03
/** The widest copy [artworkLogoPlaces] is scored on: about a card's own pixels, so lettering stays sharp. */
internal const val LOGO_CORNER_ANALYSIS_WIDTH = 384

/**
 * The logo's places on the picture as shown, best first: a card takes the first one nothing else
 * of its own stands in. A corner is scored on the detail in the space the logo needs, its box of
 * [logoShare] of the picture's width plus room around it, and on half the detail of its wider
 * neighbourhood, so a logo keeps off a drawn-in title and does not squeeze in beside one. Detail
 * in the bottom corners weighs more, because titles stand low. Nearly equal corners go to the
 * bottom start, then top start, bottom end, top end. [pixels] is a copy of exactly what the card
 * shows, at most [LOGO_CORNER_ANALYSIS_WIDTH] wide.
 */
internal fun artworkLogoPlaces(
    pixels: IntArray, width: Int, height: Int, logoShare: Double, startAtRight: Boolean = false,
): List<ArtworkLogoPlace> {
    val boxWidth = logoShare * width
    val boxHeight = boxWidth * 24 / 56
    val inset = boxWidth * 10 / 56
    val room = LOGO_ROOM * boxWidth
    val reach = LOGO_NEIGHBOURHOOD * boxWidth
    class Scored(val place: ArtworkLogoPlace, val cost: Double)
    fun score(corner: ArtworkLogoCorner, atRight: Boolean, atBottom: Boolean): Scored {
        val left = if (atRight) width - inset - boxWidth else inset
        val top = if (atBottom) height - inset - boxHeight else inset
        val x0 = (left - reach).roundToInt().coerceAtLeast(1); val x1 = (left + boxWidth + reach).roundToInt().coerceAtMost(width)
        val y0 = (top - reach).roundToInt().coerceAtLeast(1); val y1 = (top + boxHeight + reach).roundToInt().coerceAtMost(height)
        val rx0 = (left - room).roundToInt(); val rx1 = (left + boxWidth + room).roundToInt()
        val ry0 = (top - room).roundToInt(); val ry1 = (top + boxHeight + room).roundToInt()
        val bx0 = left.roundToInt(); val bx1 = (left + boxWidth).roundToInt()
        val by0 = top.roundToInt(); val by1 = (top + boxHeight).roundToInt()
        var near = 0.0; var nearCount = 0; var needed = 0.0; var neededCount = 0
        var r = 0L; var g = 0L; var b = 0L; var boxCount = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = pixels[y * width + x]; val l = pixels[y * width + x - 1]; val u = pixels[(y - 1) * width + x]
            val pr = p shr 16 and 255; val pg = p shr 8 and 255; val pb = p and 255
            val step = maxOf(
                maxOf(abs(pr - (l shr 16 and 255)), abs(pg - (l shr 8 and 255)), abs(pb - (l and 255))),
                maxOf(abs(pr - (u shr 16 and 255)), abs(pg - (u shr 8 and 255)), abs(pb - (u and 255))))
            val detail = ((step - LOGO_STEP_LOW) / (LOGO_STEP_HIGH - LOGO_STEP_LOW)).coerceIn(0.0, 1.0)
            near += detail; nearCount++
            if (x in rx0 until rx1 && y in ry0 until ry1) { needed += detail; neededCount++ }
            if (x in bx0 until bx1 && y in by0 until by1) { r += pr; g += pg; b += pb; boxCount++ }
        }
        val neededShare = needed / maxOf(1, neededCount)
        val cost = maxOf(neededShare, LOGO_NEIGHBOUR_WEIGHT * near / maxOf(1, nearCount)) *
            if (atBottom) LOGO_BOTTOM_WEIGHT else 1.0
        fun linear(sum: Long) = (sum.toDouble() / maxOf(1, boxCount) / 255).pow(2.2)
        val luminance = .2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b)
        val ground = when {
            neededShare >= LOGO_CALM -> ArtworkLogoGround.SHADE
            luminance >= LOGO_LIGHT_GROUND -> ArtworkLogoGround.LIGHT_PICTURE
            luminance <= LOGO_DARK_GROUND -> ArtworkLogoGround.DARK_PICTURE
            else -> ArtworkLogoGround.SHADE
        }
        return Scored(ArtworkLogoPlace(corner, ground), cost)
    }
    val left = mutableListOf(
        score(ArtworkLogoCorner.BOTTOM_START, startAtRight, atBottom = true),
        score(ArtworkLogoCorner.TOP_START, startAtRight, atBottom = false),
        score(ArtworkLogoCorner.BOTTOM_END, !startAtRight, atBottom = true),
        score(ArtworkLogoCorner.TOP_END, !startAtRight, atBottom = false),
    )
    val ranked = mutableListOf<ArtworkLogoPlace>()
    while (left.isNotEmpty()) {
        val best = left.minOf { it.cost }
        val next = left.first { it.cost <= best + LOGO_TIE }
        ranked += next.place; left -= next
    }
    return ranked
}

/** HSL in turns, lightness and saturation (the order used by Python colorsys). */
internal fun artworkHsl(color: Int): DoubleArray {
    val r = (color shr 16 and 255) / 255.0; val g = (color shr 8 and 255) / 255.0; val b = (color and 255) / 255.0
    val hi = maxOf(r, g, b); val lo = minOf(r, g, b)
    val light = (hi + lo) / 2
    val delta = hi - lo
    if (delta == 0.0) return doubleArrayOf(0.0, light, 0.0)
    val hue = when (hi) {
        r -> (g - b) / delta + if (g < b) 6 else 0
        g -> (b - r) / delta + 2
        else -> (r - g) / delta + 4
    } / 6
    return doubleArrayOf(hue, light, delta / (1 - abs(2 * light - 1)))
}

private fun rgb(r: Double, g: Double, b: Double): Int = (0xff shl 24) or
    (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or
    b.roundToInt().coerceIn(0, 255)

private fun fromHsl(h: Double, l: Double, s: Double): Int {
    val a = s * minOf(l, 1 - l)
    fun channel(n: Int): Double {
        val k = (n + h * 12) % 12
        return (l - a * maxOf(-1.0, minOf(k - 3, 9 - k, 1.0))) * 255
    }
    return rgb(channel(0), channel(8), channel(4))
}

/** Mean RGB corners (15% wide, 10% high), diagonal de-clashing, then F1c clamping. */
internal fun fillCorners(pixels: IntArray, width: Int, height: Int, fallback: Int): IntArray {
    val pw = maxOf(1, (width * .15).toInt()); val ph = maxOf(1, (height * .10).toInt())
    val corners = IntArray(4) { corner ->
        val left = if (corner % 2 == 0) 0 else width - pw
        val top = if (corner < 2) 0 else height - ph
        var r = 0.0; var g = 0.0; var b = 0.0
        for (y in top until top + ph) for (x in left until left + pw) {
            val p = pixels[y * width + x]
            r += p shr 16 and 255; g += p shr 8 and 255; b += p and 255
        }
        rgb(r / (pw * ph), g / (pw * ph), b / (pw * ph))
    }
    fun usable(c: Int): Boolean = artworkHsl(c).let { it[2] >= .12 && it[1] in .05.. .95 }
    val usable = BooleanArray(4) { usable(corners[it]) }
    for ((a, b) in listOf(0 to 3, 1 to 2)) {
        val hue = abs(artworkHsl(corners[a])[0] - artworkHsl(corners[b])[0])
        if (usable[a] && usable[b] && minOf(hue, 1 - hue) > 60.0 / 360) {
            val mean = rgb(((corners[a] shr 16 and 255) + (corners[b] shr 16 and 255)) / 2.0,
                ((corners[a] shr 8 and 255) + (corners[b] shr 8 and 255)) / 2.0,
                ((corners[a] and 255) + (corners[b] and 255)) / 2.0)
            corners[a] = mean; corners[b] = mean
            usable[a] = usable(mean); usable[b] = usable[a]
        }
    }
    return IntArray(4) { i ->
        if (!usable[i]) fallback else {
            val (h, l, s) = artworkHsl(corners[i])
            fromHsl(h, l.coerceIn(.06, .12), minOf(s, if (h in (60.0 / 360)..(150.0 / 360)) .20 else .34))
        }
    }
}

internal fun artworkFill(pixels: IntArray, width: Int, height: Int, outputWidth: Int, outputHeight: Int,
    fallback: Int): IntArray {
    val corners = fillCorners(pixels, width, height, fallback)
    if (corners.all { it == fallback }) return IntArray(outputWidth * outputHeight) { fallback }
    val random = Random(25)
    return IntArray(outputWidth * outputHeight) { i ->
        val x = (i % outputWidth).toDouble() / maxOf(1, outputWidth - 1)
        val y = (i / outputWidth).toDouble() / maxOf(1, outputHeight - 1)
        val noise = random.nextGaussian() * 1.1
        fun channel(shift: Int): Double {
            fun c(index: Int) = (corners[index] shr shift and 255).toDouble()
            return ((c(0) * (1 - x) + c(1) * x) * (1 - y) + (c(2) * (1 - x) + c(3) * x) * y) + noise
        }
        rgb(channel(16), channel(8), channel(0))
    }
}
