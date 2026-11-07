package at.bernhardberger.tvhplayer.core

/** A mark's symbol above the bar: its mark's position and its width in pixels, and its kind for collision priority. */
data class RecordingMarkHint(val centerPx: Float, val widthPx: Float, val kind: RecordingMarkKind)

/** The symbol's left edge: centred on its mark, held inside a track [trackWidthPx] wide. */
fun RecordingMarkHint.leftPx(trackWidthPx: Float): Float =
    (centerPx - widthPx / 2).coerceIn(0f, maxOf(0f, trackWidthPx - widthPx))

/**
 * The hint symbols to draw, as indices into [hints]. Greedy by [priority] (ad break, then cut, then
 * chapter; left to right within a kind) on where the symbols are drawn, held inside the track: a
 * symbol is kept while it stays [gapPx] clear of every kept neighbour, edge to edge, and does not
 * overlap [blockedPx] (the open marks row's pill). Only symbols thin: the bar's ticks and the marks
 * row's stops do not depend on this.
 */
fun visibleRecordingMarkHints(
    hints: List<RecordingMarkHint>,
    gapPx: Float,
    trackWidthPx: Float,
    blockedPx: ClosedFloatingPointRange<Float>? = null,
): Set<Int> {
    val lefts = hints.map { it.leftPx(trackWidthPx) }
    val kept = mutableListOf<Int>()
    val order = hints.indices.sortedWith(compareBy({ hints[it].kind.priority }, { lefts[it] }))
    for (index in order) {
        val left = lefts[index]
        val right = left + hints[index].widthPx
        if (blockedPx != null && right > blockedPx.start && left < blockedPx.endInclusive) continue
        val clear = kept.none { other ->
            val otherLeft = lefts[other]
            left < otherLeft + hints[other].widthPx + gapPx && otherLeft < right + gapPx
        }
        if (clear) kept += index
    }
    return kept.toSet()
}
