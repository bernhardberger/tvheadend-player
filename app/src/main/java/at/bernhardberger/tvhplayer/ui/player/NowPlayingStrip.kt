package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import coil3.ImageLoader

/** Passive identity of the playing channel, independent of the programme being browsed. */
@Composable
internal fun NowPlayingStrip(
    channelNumber: String?,
    channelName: String,
    picon: ArtworkId?,
    programme: EpgEvent?,
    nowSec: Long,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
) {
    val current = programme?.takeIf { it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds }
    Row(modifier.testTag("player-now-playing").semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp, 27.dp).clip(RoundedCornerShape(6.dp))
            .background(TvSurfaceColors.containerLowest), contentAlignment = Alignment.Center) {
            if (picon != null) PiconBox(imageLoader, picon, Modifier.padding(3.dp), currentSession)
            else Text(channelNumber.orEmpty(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (current != null) ProgressStrip(
                progress = ((nowSec - current.start.epochSeconds).toDouble() /
                    (current.stop.epochSeconds - current.start.epochSeconds)).toFloat(),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), height = 2.dp,
            )
        }
        Column(Modifier.widthIn(max = 200.dp).width(IntrinsicSize.Max)) {
            Text(listOfNotNull(channelNumber, channelName.takeIf { it.isNotBlank() }).joinToString(" "),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(current?.title.orEmpty(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
