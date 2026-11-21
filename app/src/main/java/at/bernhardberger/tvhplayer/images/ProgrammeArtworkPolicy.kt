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

/** A line of lettering in the pixels of the analysed copy; [right] and [bottom] are past it. */
internal class ArtworkLettering(val left: Int, val top: Int, val right: Int, val bottom: Int)

// Neighbouring pixels this close on every colour channel belong to one flat region.
private const val LETTER_FLAT_STEP = 16
private const val LETTER_MIN_PIXELS = 6
// A letter's height and width as shares of the picture's, and its width for its height.
private const val LETTER_MIN_HEIGHT = .035
private const val LETTER_MAX_HEIGHT = .5
private const val LETTER_MAX_WIDTH = .6
private const val LETTER_MIN_ASPECT = .06
private const val LETTER_MAX_ASPECT = 5.0
// How much of its own box a letter fills: a thin diagonal stroke at least, never a full block.
private const val LETTER_MIN_FILL = .10
private const val LETTER_MAX_FILL = .95
// A letter stands out from what surrounds it by this much on its strongest colour channel.
private const val LETTER_CONTRAST = 55.0
// Letters of one line: of like height and colour, side by side, no further apart than this many heights.
private const val LINE_HEIGHT_RATIO = 1.9
private const val LINE_OVERLAP = .45
private const val LINE_GAP = 1.6
private const val LINE_COLOUR = 55.0
private const val LINE_LETTERS = 3
// A line must be this many times as wide as tall. A word of five or more letters can be squarer:
// a tall capital or descender adds height even to a short word.
private const val LINE_ASPECT = 1.8
private const val LINE_WORD_LETTERS = 5
private const val LINE_WORD_ASPECT = 1.5

private fun regionRoot(parent: IntArray, index: Int): Int {
    var at = index
    while (parent[at] != at) { parent[at] = parent[parent[at]]; at = parent[at] }
    return at
}

/**
 * The lines of lettering drawn into a picture: its title, a station's wordmark, a caption.
 * Neighbouring pixels of nearly one colour join into flat regions; the letter-shaped ones that
 * stand out from their surround and line up with at least two more of like height and colour
 * make a line. It reads shapes, not text: a row of windows can pass for a word, and lettering
 * in a soft gradient or thinner than a pixel of the copy is missed.
 */
internal fun artworkLettering(pixels: IntArray, width: Int, height: Int): List<ArtworkLettering> {
    val size = width * height
    val label = IntArray(size)
    val parent = IntArray(size)
    labelFlatRegions(pixels, width, height, label, parent)
    val sums = sumFlatRegions(pixels, width, height, label, parent)
    return chainLettersIntoLines(findLetters(pixels, width, height, sums), width, height)
}

private fun isFlatWith(r: Int, g: Int, b: Int, other: Int): Boolean =
    abs(r - (other shr 16 and 255)) <= LETTER_FLAT_STEP &&
        abs(g - (other shr 8 and 255)) <= LETTER_FLAT_STEP &&
        abs(b - (other and 255)) <= LETTER_FLAT_STEP

/** Fills [label] and [parent] by joining flat neighbours, with each region rooted at its first pixel. */
private fun labelFlatRegions(pixels: IntArray, width: Int, height: Int, label: IntArray, parent: IntArray) {
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            val index = row + x
            val pixel = pixels[index]
            val r = pixel shr 16 and 255; val g = pixel shr 8 and 255; val b = pixel and 255
            var region = if (x > 0 && isFlatWith(r, g, b, pixels[index - 1])) label[index - 1] else -1
            if (y > 0 && isFlatWith(r, g, b, pixels[index - width])) {
                val above = label[index - width]
                if (region < 0) region = above
                else if (region != above) {
                    val first = regionRoot(parent, region); val second = regionRoot(parent, above)
                    region = minOf(first, second)
                    parent[maxOf(first, second)] = region
                }
            }
            if (region < 0) { region = index; parent[index] = index }
            label[index] = region
        }
    }
}

private class FlatRegionSums(size: Int) {
    val count = IntArray(size); val minX = IntArray(size); val maxX = IntArray(size); val maxY = IntArray(size)
    val red = IntArray(size); val green = IntArray(size); val blue = IntArray(size)
}

/** Returns each region's pixel count, bounds and RGB sums, indexed by its root. */
private fun sumFlatRegions(
    pixels: IntArray, width: Int, height: Int, label: IntArray, parent: IntArray,
): FlatRegionSums {
    val sums = FlatRegionSums(width * height)
    val count = sums.count; val minX = sums.minX; val maxX = sums.maxX; val maxY = sums.maxY
    val red = sums.red; val green = sums.green; val blue = sums.blue
    var lastLabel = -1; var lastRegion = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            val index = row + x
            val named = label[index]
            val region = if (named == lastLabel) lastRegion else regionRoot(parent, named)
            lastLabel = named; lastRegion = region
            val pixel = pixels[index]
            if (count[region]++ == 0) {
                minX[region] = x; maxX[region] = x
            } else {
                if (x < minX[region]) minX[region] = x
                if (x > maxX[region]) maxX[region] = x
            }
            maxY[region] = y
            red[region] += pixel shr 16 and 255; green[region] += pixel shr 8 and 255; blue[region] += pixel and 255
        }
    }
    return sums
}

private class Letter(
    val left: Int, val top: Int, val right: Int, val bottom: Int, val r: Double, val g: Double, val b: Double,
)

/** Returns the flat regions that are shaped like letters and stand out from their surround. */
private fun findLetters(pixels: IntArray, width: Int, height: Int, sums: FlatRegionSums): List<Letter> {
    val letters = ArrayList<Letter>()
    for (region in sums.count.indices) {
        val pixelCount = sums.count[region]
        if (pixelCount >= LETTER_MIN_PIXELS) {
            val letter = letterInRegion(pixels, width, height, sums, region, pixelCount)
            if (letter != null) letters += letter
        }
    }
    return letters
}

private fun letterInRegion(
    pixels: IntArray, width: Int, height: Int, sums: FlatRegionSums, region: Int, pixelCount: Int,
): Letter? {
    // A region's root is its first pixel in reading order, so its top is the root's own row.
    val left = sums.minX[region]; val top = region / width
    val letterWidth = sums.maxX[region] - left + 1; val letterHeight = sums.maxY[region] - top + 1
    if (!isLetterShape(letterWidth, letterHeight, width, height, pixelCount)) return null
    val edge = maxOf(2, letterHeight / 6)
    val x0 = maxOf(0, left - edge); val x1 = minOf(width, left + letterWidth + edge)
    val y0 = maxOf(0, top - edge); val y1 = minOf(height, top + letterHeight + edge)
    val around = (x1 - x0) * (y1 - y0) - pixelCount
    return if (around < 4) null else {
        var r = 0; var g = 0; var b = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val pixel = pixels[y * width + x]
            r += pixel shr 16 and 255; g += pixel shr 8 and 255; b += pixel and 255
        }
        val meanR = sums.red[region].toDouble() / pixelCount; val meanG = sums.green[region].toDouble() / pixelCount
        val meanB = sums.blue[region].toDouble() / pixelCount
        val contrast = maxOf(abs(meanR - (r - sums.red[region]).toDouble() / around),
            abs(meanG - (g - sums.green[region]).toDouble() / around),
            abs(meanB - (b - sums.blue[region]).toDouble() / around))
        if (contrast < LETTER_CONTRAST) null
        else Letter(left, top, left + letterWidth, top + letterHeight, meanR, meanG, meanB)
    }
}

private fun isLetterShape(letterWidth: Int, letterHeight: Int, width: Int, height: Int, pixelCount: Int): Boolean {
    if (letterHeight < LETTER_MIN_HEIGHT * height || letterHeight > LETTER_MAX_HEIGHT * height ||
        letterWidth > LETTER_MAX_WIDTH * width
    ) return false
    val aspect = letterWidth.toDouble() / letterHeight
    return if (aspect < LETTER_MIN_ASPECT || aspect > LETTER_MAX_ASPECT) false
    else {
        val fill = pixelCount.toDouble() / (letterWidth * letterHeight)
        fill >= LETTER_MIN_FILL && fill <= LETTER_MAX_FILL
    }
}

private fun lettersLineUp(a: Letter, heightA: Int, b: Letter): Boolean {
    val heightB = b.bottom - b.top
    val taller = maxOf(heightA, heightB); val shorter = minOf(heightA, heightB)
    return taller <= LINE_HEIGHT_RATIO * shorter &&
        minOf(a.bottom, b.bottom) - maxOf(a.top, b.top) >= LINE_OVERLAP * shorter &&
        maxOf(a.left, b.left) - minOf(a.right, b.right) <= LINE_GAP * taller &&
        maxOf(abs(a.r - b.r), abs(a.g - b.g), abs(a.b - b.b)) <= LINE_COLOUR
}

/** Returns a bounding box for each chain of enough neighbouring letters of like height and colour. */
private fun chainLettersIntoLines(letters: List<Letter>, width: Int, height: Int): List<ArtworkLettering> {
    val line = IntArray(letters.size) { it }
    fun lineOf(index: Int): Int {
        var at = index
        while (line[at] != at) { line[at] = line[line[at]]; at = line[at] }
        return at
    }
    for (i in letters.indices) {
        val a = letters[i]; val heightA = a.bottom - a.top
        for (j in i + 1 until letters.size) {
            if (lettersLineUp(a, heightA, letters[j])) line[lineOf(i)] = lineOf(j)
        }
    }
    val lines = ArrayList<ArtworkLettering>()
    val members = IntArray(letters.size)
    for (i in letters.indices) members[lineOf(i)]++
    for (head in letters.indices) {
        if (members[head] < LINE_LETTERS) continue
        var left = width; var top = height; var right = 0; var bottom = 0
        for (i in letters.indices) if (lineOf(i) == head) {
            val letter = letters[i]
            left = minOf(left, letter.left); top = minOf(top, letter.top)
            right = maxOf(right, letter.right); bottom = maxOf(bottom, letter.bottom)
        }
        val aspect = if (members[head] >= LINE_WORD_LETTERS) LINE_WORD_ASPECT else LINE_ASPECT
        if (right - left >= aspect * (bottom - top)) lines += ArtworkLettering(left, top, right, bottom)
    }
    return lines
}

/** Where a channel's logo stands on a programme picture. */
internal enum class ArtworkLogoCorner { BOTTOM_START, BOTTOM_END, TOP_START, TOP_END }

/** A corner shade, or the picture itself where the corner is calm and clearly light or dark. */
internal enum class ArtworkLogoGround { SHADE, LIGHT_PICTURE, DARK_PICTURE }

internal data class ArtworkLogoPlace(val corner: ArtworkLogoCorner, val ground: ArtworkLogoGround)

// A step between neighbouring pixels counts from the low value and fully at the high one, on the
// strongest colour channel: lettering in a colour of the picture's own brightness still counts.
private const val LOGO_STEP_LOW = 24
private const val LOGO_STEP_HIGH = 72
// The room a logo needs around its box, and the wider neighbourhood that only crowds it, in box widths.
private const val LOGO_ROOM = 6.0 / 56
private const val LOGO_NEIGHBOURHOOD = 20.0 / 56
private const val LOGO_NEIGHBOUR_WEIGHT = .5
// The share of the logo's space that drawn-in lettering covers weighs this much against detail:
// a logo over a face or scenery is the lesser fault than one over the programme's own title.
private const val LOGO_LETTERING_WEIGHT = 4.0
// Lettering close by weighs less than lettering in the logo's own space, but still crowds it.
private const val LOGO_LETTERING_NEAR_WEIGHT = 1.0
// Corners this close are equally good; the earlier one in reading order from the bottom start wins.
private const val LOGO_TIE = .03
// A ground this calm needs no shade when it is also clearly light or dark (linear luminance).
private const val LOGO_CALM = .04
private const val LOGO_LIGHT_GROUND = .4
private const val LOGO_DARK_GROUND = .03
/** The widest copy [artworkLogoPlaces] is scored on: about a card's own pixels, so lettering stays sharp. */
internal const val LOGO_CORNER_ANALYSIS_WIDTH = 512

/**
 * The logo's places on the picture as shown, best first: a card takes the first one nothing else
 * of its own stands in. A corner is scored on the detail in the space the logo needs, its box of
 * [logoShare] of the picture's width plus room around it, and on half the detail of its wider
 * neighbourhood, so a logo does not squeeze in beside a busy part. Lettering drawn into the
 * picture ([artworkLettering]) weighs far more than any other detail in that space, and counts
 * even when it stands close beside it. Nearly equal
 * corners go to the bottom start, then top start, bottom end, top end. [pixels] is a copy of exactly what the card
 * shows, at most [LOGO_CORNER_ANALYSIS_WIDTH] wide.
 */
internal fun artworkLogoPlaces(
    pixels: IntArray, width: Int, height: Int, logoShare: Double, startAtRight: Boolean = false,
    lettering: List<ArtworkLettering> = artworkLettering(pixels, width, height),
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
        val x0 = (left - reach).roundToInt().coerceAtLeast(1)
        val x1 = (left + boxWidth + reach).roundToInt().coerceAtMost(width)
        val y0 = (top - reach).roundToInt().coerceAtLeast(1)
        val y1 = (top + boxHeight + reach).roundToInt().coerceAtMost(height)
        val rx0 = (left - room).roundToInt(); val rx1 = (left + boxWidth + room).roundToInt()
        val ry0 = (top - room).roundToInt(); val ry1 = (top + boxHeight + room).roundToInt()
        val bx0 = left.roundToInt(); val bx1 = (left + boxWidth).roundToInt()
        val by0 = top.roundToInt(); val by1 = (top + boxHeight).roundToInt()
        var near = 0; var nearCount = 0; var needed = 0; var neededCount = 0
        var r = 0; var g = 0; var b = 0; var boxCount = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = pixels[y * width + x]; val l = pixels[y * width + x - 1]; val u = pixels[(y - 1) * width + x]
            val pr = p shr 16 and 255; val pg = p shr 8 and 255; val pb = p and 255
            val step = maxOf(
                maxOf(abs(pr - (l shr 16 and 255)), abs(pg - (l shr 8 and 255)), abs(pb - (l and 255))),
                maxOf(abs(pr - (u shr 16 and 255)), abs(pg - (u shr 8 and 255)), abs(pb - (u and 255))))
            val detail = (step - LOGO_STEP_LOW).coerceIn(0, LOGO_STEP_HIGH - LOGO_STEP_LOW)
            near += detail; nearCount++
            if (x in rx0 until rx1 && y in ry0 until ry1) { needed += detail; neededCount++ }
            if (x in bx0 until bx1 && y in by0 until by1) { r += pr; g += pg; b += pb; boxCount++ }
        }
        val full = (LOGO_STEP_HIGH - LOGO_STEP_LOW).toDouble()
        val neededShare = needed / full / maxOf(1, neededCount)
        val cost = maxOf(neededShare, LOGO_NEIGHBOUR_WEIGHT * near / full / maxOf(1, nearCount)) +
            LOGO_LETTERING_WEIGHT * letteringShare(lettering, left, top, boxWidth, boxHeight, room) +
            LOGO_LETTERING_NEAR_WEIGHT * letteringShare(lettering, left, top, boxWidth, boxHeight, reach)
        fun linear(sum: Int) = (sum.toDouble() / maxOf(1, boxCount) / 255).pow(2.2)
        val luminance = .2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b)
        return Scored(ArtworkLogoPlace(corner, logoGround(neededShare, luminance)), cost)
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

private fun letteringShare(
    lettering: List<ArtworkLettering>, left: Double, top: Double, boxWidth: Double, boxHeight: Double, margin: Double,
): Double {
    var covered = 0.0
    for (line in lettering) {
        val across = minOf(left + boxWidth + margin, line.right.toDouble()) - maxOf(left - margin, line.left.toDouble())
        val down = minOf(top + boxHeight + margin, line.bottom.toDouble()) - maxOf(top - margin, line.top.toDouble())
        if (across > 0 && down > 0) covered += across * down
    }
    return minOf(1.0, covered / ((boxWidth + 2 * margin) * (boxHeight + 2 * margin)))
}

private fun logoGround(neededShare: Double, luminance: Double): ArtworkLogoGround = when {
    neededShare >= LOGO_CALM -> ArtworkLogoGround.SHADE
    luminance >= LOGO_LIGHT_GROUND -> ArtworkLogoGround.LIGHT_PICTURE
    luminance <= LOGO_DARK_GROUND -> ArtworkLogoGround.DARK_PICTURE
    else -> ArtworkLogoGround.SHADE
}

// A logo needs a light ground when little of it is light itself and it reads clearly better on
// a light ground than on a dark one (linear luminance of the two grounds under their shades).
private const val LOGO_LIGHT_PART = .6
private const val LOGO_LIGHT_PART_SHARE = .25
private const val LOGO_ON_DARK_GROUND = .03
private const val LOGO_ON_LIGHT_GROUND = .6
private const val LOGO_GROUND_GAIN = .06
private const val LOGO_FULL_CONTRAST = 7.0

/**
 * Whether a logo is a dark one, which a dark corner shade would swallow and a light one carries:
 * dark blue or black lettering without a light part of its own. [pixels] are non-premultiplied
 * ARGB; transparent ones do not count. A logo with white in it, or one that reads as well on dark,
 * keeps the dark shade.
 */
internal fun logoNeedsLightGround(pixels: IntArray): Boolean {
    var count = 0; var light = 0; var onDark = 0.0; var onLight = 0.0
    fun contrast(a: Double, b: Double) =
        (kotlin.math.ln((maxOf(a, b) + .05) / (minOf(a, b) + .05)) / kotlin.math.ln(LOGO_FULL_CONTRAST))
            .coerceAtMost(1.0)
    for (p in pixels) {
        if (p ushr 24 <= 127) continue
        fun linear(v: Int) = (v / 255.0).pow(2.2)
        val luminance = .2126 * linear(p shr 16 and 255) + .7152 * linear(p shr 8 and 255) + .0722 * linear(p and 255)
        count++
        if (luminance.pow(1 / 2.2) >= LOGO_LIGHT_PART) light++
        onDark += contrast(luminance, LOGO_ON_DARK_GROUND); onLight += contrast(luminance, LOGO_ON_LIGHT_GROUND)
    }
    if (count == 0) return false
    return light < LOGO_LIGHT_PART_SHARE * count && onLight / count > onDark / count + LOGO_GROUND_GAIN
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
