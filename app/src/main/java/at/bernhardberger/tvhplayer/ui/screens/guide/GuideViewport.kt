package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/** The channel column, the same in both row densities. */
internal val GuideChannelWidth = 104.dp
internal val GuideChannelGap = 8.dp
internal val GuideEdgeFade = 48.dp
/** Breathing room between a focused cell and a fade or the edge; the focus ring draws inside the cell. */
internal val GuideFocusReserve = 8.dp

/** Blank space, not extra time: only the physical outer edge needs a terminal reserve. */
internal fun guideTerminalViewportReserve(
    earlierContent: Boolean,
    laterContent: Boolean,
    isRtl: Boolean,
): Dp {
    val outerContinuation = if (isRtl) earlierContent else laterContent
    return if (outerContinuation) 0.dp else GuideFocusReserve
}

/** The edge next to the channel column: earlier in LTR, later in RTL, where the column sits on the right. */
internal fun guideInnerContinuation(earlierContent: Boolean, laterContent: Boolean, isRtl: Boolean): Boolean =
    if (isRtl) laterContent else earlierContent

/** The physical left and right insets that keep a focused cell clear of the inner fade and the outer edge. */
internal fun guideFocusInsets(
    earlierContent: Boolean,
    laterContent: Boolean,
    isRtl: Boolean,
): Pair<Dp, Dp> {
    val fade = if (guideInnerContinuation(earlierContent, laterContent, isRtl)) GuideEdgeFade else 0.dp
    return if (isRtl) GuideFocusReserve to GuideFocusReserve + fade else GuideFocusReserve + fade to GuideFocusReserve
}

/** Physical chronology is identical in LTR and RTL, including between ruler ticks. */
internal fun guideTimePositionPx(timeSec: Long, startSec: Long, endSec: Long, widthPx: Float): Float =
    ((timeSec - startSec).toDouble() / (endSec - startSec).coerceAtLeast(1L) * widthPx).toFloat()

/** Whole-hour capacity from the available width and the actual native time-label measurement. */
internal fun guideVisibleWindowSec(trackWidthPx: Float, timeLabelWidthPx: Int, density: Float): Long {
    val halfHourWidth = maxOf(100f * density, timeLabelWidthPx * 2f + 24f * density)
    val hours = ((trackWidthPx - 48f * density) / (halfHourWidth * 2)).toInt().coerceIn(1, 3)
    return hours * 3600L
}

/** How much of the programme on air must be readable for it to keep the window around now. */
private const val GuideOnAirReadableSec = 1800L

internal val GuideMinReadableOverlap = 48.dp

/** Round up so a qualifying edge fragment always spans at least the requested pixels. */
internal fun guideMinReadableOverlapSec(windowDurationSec: Long, trackWidthPx: Float, overlapPx: Float): Long =
    if (trackWidthPx <= 0f) 1L else ceil(overlapPx * (windowDurationSec.toDouble() / trackWidthPx)).toLong().coerceAtLeast(1L)

/** Whole seconds clear of the physical edge insets, shared by focus selection and scrolling. */
internal fun guideReadableTimeRange(
    windowStartSec: Long,
    windowDurationSec: Long,
    trackWidthPx: Float,
    leftInsetPx: Float,
    rightInsetPx: Float,
): LongRange {
    if (trackWidthPx <= leftInsetPx + rightInsetPx) return windowStartSec until windowStartSec + windowDurationSec
    val secondsPerPixel = windowDurationSec.toDouble() / trackWidthPx
    val start = windowStartSec + ceil(leftInsetPx * secondsPerPixel).toLong()
    val end = windowStartSec + floor((trackWidthPx - rightInsetPx) * secondsPerPixel).toLong()
    return start until maxOf(start + 1, end)
}

/** Move the existing time window, not the cells, to keep a focused cell clear of the insets. */
internal fun guideFocusWindowStart(
    eventStartSec: Long,
    eventEndSec: Long,
    windowStartSec: Long,
    windowDurationSec: Long,
    trackWidthPx: Float,
    leftInsetPx: Float,
    rightInsetPx: Float,
    earliestWindowSec: Long,
    latestWindowSec: Long,
    nowSec: Long? = null,
): Long {
    if (trackWidthPx <= leftInsetPx + rightInsetPx) return windowStartSec
    val readable = guideReadableTimeRange(windowStartSec, windowDurationSec, trackWidthPx, leftInsetPx, rightInsetPx)
    val leftReserve = readable.first - windowStartSec
    val rightLimit = readable.last + 1 - windowStartSec
    // A programme longer than the readable viewport cannot fit in one time window.
    // Its visible fragment is clipped to that viewport, never stretched or overlapped.
    if (eventEndSec - eventStartSec > rightLimit - leftReserve &&
        eventStartSec < windowStartSec + rightLimit && eventEndSec > windowStartSec + leftReserve
    ) return windowStartSec
    // The programme on air keeps a window that shows now with half an hour of it readable, so
    // now stays where Now put it; its cell is clipped as a long one is.
    if (nowSec != null && nowSec in eventStartSec until eventEndSec &&
        nowSec in windowStartSec + leftReserve until windowStartSec + rightLimit &&
        minOf(eventEndSec, windowStartSec + rightLimit) - maxOf(eventStartSec, windowStartSec + leftReserve) >=
        GuideOnAirReadableSec
    ) return windowStartSec
    return when {
        eventStartSec < windowStartSec + leftReserve -> eventStartSec - leftReserve
        eventEndSec > windowStartSec + rightLimit -> eventEndSec - rightLimit
        else -> windowStartSec
    }.coerceIn(earliestWindowSec, latestWindowSec)
}

/** Draw after the entire list, so focus surfaces and inter-row spaces cannot cover Now. */
internal fun Modifier.guideNowLine(
    startSec: Long,
    endSec: Long,
    nowSec: () -> Long,
    trackLeftPx: Float,
    trackWidthPx: Float,
    color: Color,
): Modifier = drawWithContent {
    drawContent()
    val now = nowSec()
    if (now in startSec until endSec) {
        val x = trackLeftPx + guideTimePositionPx(now, startSec, endSec, trackWidthPx)
        drawRect(color, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}

/**
 * A row's track ends where its inner fade reaches zero, so a focused cell cannot paint past the fade
 * into the channel gap at full strength. The outer edge has no fade and is not clipped.
 */
internal fun Modifier.guideFadedEdgeClip(innerContinues: Boolean, isRtl: Boolean): Modifier =
    if (!innerContinues) this else drawWithContent {
        // Physical sides, like the fades: the inner edge is left in LTR and right in RTL.
        clipRect(
            left = if (isRtl) -size.width else 0f,
            top = -size.height,
            right = if (isRtl) size.width else size.width * 2,
            bottom = size.height * 2,
        ) { this@drawWithContent.drawContent() }
    }

/**
 * Local alpha masks: no scrim is added to headings, scopes, controls or live video. Only the edge
 * next to the channel column (left in LTR, right in RTL) and the top edge fade; the outer edges cut.
 */
internal fun Modifier.guideViewportFades(
    trackLeftPx: Float,
    trackWidthPx: Float,
    innerContinues: Boolean,
    isRtl: Boolean,
    above: () -> Boolean = { false },
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val fade = GuideEdgeFade.toPx()
    if (innerContinues && !isRtl) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), trackLeftPx, trackLeftPx + fade),
            Offset(trackLeftPx, 0f), Size(fade, size.height), blendMode = BlendMode.DstIn,
        )
    }
    if (innerContinues && isRtl) {
        val right = trackLeftPx + trackWidthPx
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), right - fade, right),
            Offset(right - fade, 0f), Size(fade, size.height), blendMode = BlendMode.DstIn,
        )
    }
    if (above()) {
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
            Offset.Zero, Size(size.width, fade), blendMode = BlendMode.DstIn,
        )
    }
}
