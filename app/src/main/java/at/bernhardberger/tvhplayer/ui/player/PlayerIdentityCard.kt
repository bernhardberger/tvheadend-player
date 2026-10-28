package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.components.PiconBox

/** The [PlayerChromeActions.restoreFocus] token that returns focus to the controls' [PlayerTopIdentity]. */
internal const val PlayerIdentityCardTag = "player-identity-card"

/** The picon's room: centred, lifted clear of the label along the card's bottom edge. */
private val PiconWidth = 136.dp
private val PiconHeight = 60.dp
private val PiconLift = 12.dp

/** A channel card's face, used by the channel rows' tiles: its picon, or its name without one, on a plain surface. */
@Composable
internal fun BoxScope.ChannelCardFace(
    picon: ChannelIcon?,
    channelId: ChannelId?,
    name: String,
    logoTag: String = "player-identity-logo",
    nameTag: String = "player-identity-name",
    backdrop: Boolean = true,
    markWidth: Dp = PiconWidth,
    markHeight: Dp = PiconHeight,
    lift: Dp = PiconLift,
) {
    // Fills the card, which sizes the slot the mark is centred in. A plain surface for every
    // channel; the picon carries the colour.
    Box(Modifier.fillMaxSize().then(if (!backdrop) Modifier else Modifier.background(TvSurfaceColors.containerLowest)))
    val mark = Modifier.align(Alignment.Center).graphicsLayer { translationY = -lift.toPx() / 2 }
        .size(markWidth, markHeight)
    if (picon != null) {
        PiconBox(picon, mark.testTag(logoTag))
    } else {
        Box(mark, contentAlignment = Alignment.Center) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag(nameTag))
        }
    }
}
