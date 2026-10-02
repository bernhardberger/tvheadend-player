package at.bernhardberger.tvhplayer.ui.startup

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import at.bernhardberger.tvhplayer.R
import org.xmlpull.v1.XmlPullParser

// Four open traces/bands from soft-tv-finalists/build_soft_tv_finalists.py SEGS.
// The final ring and play paths are read from the canonical generated artwork, not redrawn.
private val SegmentOutlines = listOf(
    "M228.19,84.13 L84.13,228.19 C68.77,243.55 68.77,268.45 84.13,283.81",
    "M84.13,283.81 L228.19,427.87 C243.55,443.23 268.45,443.23 283.81,427.87",
    "M283.81,427.87 L427.87,283.81 C443.23,268.45 443.23,243.55 427.87,228.19",
    "M427.87,228.19 L283.81,84.13 C268.45,68.77 243.55,68.77 228.19,84.13",
)
private val SegmentClosures = listOf(
    "L115.09,278.46 C102.68,266.06 102.68,245.94 115.09,233.54 L233.54,115.09 Z",
    "L278.46,396.91 C266.06,409.32 245.94,409.32 233.54,396.91 L115.09,278.46 Z",
    "L396.91,233.54 C409.32,245.94 409.32,266.06 396.91,278.46 L278.46,396.91 Z",
    "L233.54,115.09 C245.94,102.68 266.06,102.68 278.46,115.09 L396.91,233.54 Z",
)

@Composable
@SuppressLint("ResourceType") // Canonical vector drawable is compiled XML; reuse its exact paths.
internal fun StartupBrandSymbol(millis: () -> Float, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val canonical = remember(resources) {
        buildList {
            resources.getXml(R.drawable.startup_brand_symbol).use { xml ->
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path") {
                        add(PathParser().parsePathString(
                            xml.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData"),
                        ).toPath())
                    }
                    xml.next()
                }
            }
        }
    }
    val outlines = remember { SegmentOutlines.map { PathParser().parsePathString(it).toPath() } }
    val bands = remember {
        SegmentOutlines.zip(SegmentClosures) { outer, closure ->
            PathParser().parsePathString("$outer $closure").toPath()
        }
    }
    val measures = remember { outlines.map { path -> PathMeasure().apply { setPath(path, false) } } }
    val trace = remember { Path() }
    Canvas(modifier) {
        val frame = startupBrandFrame(millis())
        val cyan = Color(0xFF00BCFA)
        // Keep the approved 80dp/368-unit framing; overflow is intentionally NOT clipped.
        // This is the padded creative master's edge fix, not a smaller symbol.
        scale(size.width / 368f, size.height / 368f, pivot = Offset.Zero) {
            translate(-72f, -72f) {
                bands.forEachIndexed { index, band ->
                    val dx = if (index < 2) -1f else 1f
                    val dy = if (index == 0 || index == 3) -1f else 1f
                    translate(dx * frame.gap / 1.41421356f, dy * frame.gap / 1.41421356f) {
                        trace.reset()
                        measures[index].getSegment(0f, measures[index].length * frame.trace, trace)
                        drawPath(trace, cyan, alpha = frame.stroke,
                            style = Stroke(11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(band, cyan, alpha = frame.segmentFill)
                    }
                }
                drawPath(canonical[0], cyan, alpha = frame.ring)
                // Creative dy is preview pixels at 2px/dp, unlike the source-unit segment gap.
                translate(top = frame.playDy * 368f / 192f) {
                    scale(frame.playScale, frame.playScale, pivot = Offset(256f, 256f)) {
                        drawPath(canonical[1], Color(0xFFFA7F00), alpha = frame.play)
                    }
                }
            }
        }
    }
}
