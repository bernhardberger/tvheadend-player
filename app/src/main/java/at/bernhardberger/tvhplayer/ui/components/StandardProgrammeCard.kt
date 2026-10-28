package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardContainerColors
import androidx.tv.material3.CardContainerDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import androidx.tv.material3.StandardCardContainer
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

/**
 * The kit's standard card as the details page's airings use it: a 16:9 [picture] card [width]
 * wide with its [title] and [subtitle] below. [modifier] goes to the focusable picture card. The
 * text starts [StandardCardTextGap] below the picture, clear of the focused card's scale and outline,
 * in [contentColor] for the card's focus state.
 */
@Composable
internal fun StandardProgrammeCard(
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    art: ArtworkId?,
    picon: ChannelIcon?,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    contentColor: CardContainerColors = CardContainerDefaults.contentColor(),
    picture: @Composable BoxScope.() -> Unit,
) {
    StandardCardContainer(
        modifier = Modifier.width(width),
        imageCard = { interaction ->
            val ring = rememberCardFocusRing(interaction)
            Card(onClick = onClick, onLongClick = onLongClick, interactionSource = interaction,
                modifier = modifier.cardFocusRing(ring, MaterialTheme.colorScheme.onSurface)
                    .fillMaxWidth().aspectRatio(16f / 9f),
                colors = CardDefaults.colors(containerColor = TvSurfaceColors.containerLowest),
                glow = artworkCardGlow(art, picon, interaction),
                scale = EmbeddedProgressCardScale,
                border = embeddedProgressCardBorder()) {
                Box(Modifier.fillMaxSize(), content = picture)
            }
        },
        title = title,
        subtitle = subtitle,
        contentColor = contentColor,
    )
}

// Shared 1.05 focus scale plus the outline stays clear of the text below the picture.
internal val StandardCardTextGap = 16.dp
