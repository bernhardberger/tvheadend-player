package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text

/** Like AppClock, snapshot state updates both rendered titles and their spoken descriptions. */
internal object AppChannelNumbers {
    var shown by mutableStateOf(true)
}

/** Presentation only: never use this to order channels or resolve remote digit entry. */
internal fun channelTitleNumber(
    number: Long?, name: String, showNumbers: Boolean = AppChannelNumbers.shown,
): Long? = number?.takeIf { showNumbers || name.isBlank() }

internal fun channelTitleText(
    number: Long?, name: String, showNumbers: Boolean = AppChannelNumbers.shown,
): String = channelTitleNumber(number, name, showNumbers)
    ?.let { if (name.isBlank()) it.toString() else "$it $name" } ?: name

@Composable
fun ChannelTitle(
    number: Long?,
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1,
) {
    Text(
        text = channelTitleText(number = number, name = name),
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
