package at.bernhardberger.tvhplayer.touch.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import at.bernhardberger.tvhplayer.touch.R

/** "A • B • C" with the localized separator. */
@Composable
internal fun joinParts(parts: List<String>): String {
    if (parts.isEmpty()) return ""
    val pattern = stringResource(R.string.recordings_parts)
    return parts.reduce { a, b -> pattern.format(a, b) }
}

/**
 * A line of parts joined by " • " with as many whole parts as fit, longest first, so it never cuts
 * inside a part (touch-design §11); only a first part wider than the line is ellipsized.
 */
@Composable
internal fun PartsLine(
    parts: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val pattern = stringResource(R.string.recordings_parts)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val candidates = (parts.size downTo 1).map { count ->
            parts.take(count).reduce { line, part -> pattern.format(line, part) }
        }
        val line = candidates.firstOrNull {
            measurer.measure(it, style, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth
        } ?: candidates.lastOrNull().orEmpty()
        Text(line, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
