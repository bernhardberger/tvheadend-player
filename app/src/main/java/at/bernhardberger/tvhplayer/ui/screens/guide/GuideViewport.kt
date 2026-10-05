package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

internal val GuideChannelWidth = 172.dp
internal val GuideChannelGap = 8.dp
internal val GuideEdgeFade = 48.dp
internal val GuideFocusReserve = 8.dp
// TV Material 1.1 ListItem's default 1.05 focus scale grows each side by 2.5%.
internal const val GuideFocusHalfGrowth = .025f

/** Blank space, not extra time: only the physical outer edge needs a terminal reserve. */
internal fun guideTerminalViewportReserve(
    earlierContent: Boolean,
    laterContent: Boolean,
    isRtl: Boolean,
    focusReserve: Dp,
): Dp {
    val outerContinuation = if (isRtl) earlierContent else laterContent
    return if (outerContinuation) 0.dp else GuideEdgeFade + focusReserve
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

/** Move the existing time window, not the cells, to leave room for native focus growth. */
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
): Long {
    if (trackWidthPx <= leftInsetPx + rightInsetPx) return windowStartSec
    val secondsPerPixel = windowDurationSec.toDouble() / trackWidthPx
    val leftReserve = ceil(leftInsetPx * secondsPerPixel).toLong()
    val rightLimit = floor((trackWidthPx - rightInsetPx) * secondsPerPixel).toLong()
    // A programme longer than the readable viewport cannot fit in one time window.
    // Its visible fragment is clipped to that viewport, never stretched or overlapped.
    if (eventEndSec - eventStartSec > rightLimit - leftReserve &&
        eventStartSec < windowStartSec + rightLimit && eventEndSec > windowStartSec + leftReserve
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

/** Local alpha masks: no scrim is added to headings, scopes, controls or live video. */
internal fun Modifier.guideViewportFades(
    trackLeftPx: Float,
    trackWidthPx: Float,
    earlier: Boolean,
    later: Boolean,
    above: () -> Boolean = { false },
    below: () -> Boolean = { false },
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val fade = GuideEdgeFade.toPx()
    if (earlier) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), trackLeftPx, trackLeftPx + fade),
            Offset(trackLeftPx, 0f), Size(fade, size.height), blendMode = BlendMode.DstIn,
        )
    }
    if (later) {
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
    if (below()) {
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - fade, size.height),
            Offset(0f, size.height - fade), Size(size.width, fade), blendMode = BlendMode.DstIn,
        )
    }
}
