package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.common.formatHm

/** Separator between the start time and the programme title. */
private const val SUBTITLE_SEPARATOR = " · "

/**
 * Programme line: `HH:MM · title`. The start time keeps its natural width;
 * only the title yields to the remaining space and ellipsizes, so the
 * real text layout decides how much of it fits instead of a character budget.
 */
@Composable
internal fun ChannelProgrammeSubtitle(
    title: String,
    startSec: Long?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (startSec != null) {
            Text(
                text = formatHm(startSec) + SUBTITLE_SEPARATOR,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("channel-programme-start"),
            )
        }
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag("channel-programme-title"),
        )
    }
}
