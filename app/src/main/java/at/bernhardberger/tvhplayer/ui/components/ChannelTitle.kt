package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text

internal fun channelTitleText(number: Int?, name: String): String =
    number?.let { "$it  $name" } ?: name

@Composable
fun ChannelTitle(
    number: Int?,
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
