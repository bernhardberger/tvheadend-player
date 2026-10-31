package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors

/** The square tile size of Home's recent-channels row, as in the approved mockup. */
internal val HomeRecentTileSize = 104.dp

/**
 * Home's recent-channels tile: a compact square card on the channel's ambient accent fill with
 * its picon top-centre, and the channel's name only while its row shows text, as in the approved
 * mockup. Focusing never tunes; actions are the caller's. The caller's modifier (focus requester,
 * test tag) belongs on the focusable card, so it is applied here rather than to the column.
 */
@Composable
internal fun HomeRecentChannelTile(
    channel: Channel,
    nameShown: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    width: Dp = HomeRecentTileSize,
) {
    val interaction = remember { MutableInteractionSource() }
    val ring = rememberCardFocusRing(interaction)
    val contextMenu = rememberBrowseContextMenuTrigger(onLongClick, menuKey = true)
    var focused by remember { mutableStateOf(false) }
    val name = channelTitleText(channel.visibleChannelNumber, channel.name.orEmpty())
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            onLongClick = contextMenu.onLongClick,
            interactionSource = interaction,
            modifier = modifier
                .semantics(mergeDescendants = true) { contentDescription = name }
                .cardFocusRing(ring, MaterialTheme.colorScheme.onSurface)
                .fillMaxWidth()
                .aspectRatio(1f)
                .onFocusChanged { focused = it.isFocused }
                .then(contextMenu.modifier),
            colors = CardDefaults.colors(containerColor = TvSurfaceColors.containerLowest),
            glow = artworkCardGlow(null, channel.icon, interaction),
            scale = EmbeddedProgressCardScale,
            border = embeddedProgressCardBorder(),
        ) {
            Box(Modifier.fillMaxSize()) {
                ChannelAccentFill(channel.icon, channel.id, Modifier.fillMaxSize())
                if (channel.icon != null) {
                    PiconBox(
                        channel.icon,
                        Modifier.align(Alignment.Center)
                            .fillMaxWidth(0.72f),
                        ground = channelAccentGround(channel.icon, channel.id, depth = 0.5f),
                    )
                }
            }
        }
        AnimatedVisibility(visible = nameShown, enter = RowTextEnter, exit = RowTextExit) {
            Text(
                name,
                style = channelCaptionStyle().copy(fontWeight = FontWeight.Normal),
                textAlign = TextAlign.Center,
                color = if (focused) TvTextColors.primary else TvTextColors.secondary,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = StandardCardTextGap),
            )
        }
    }
}
