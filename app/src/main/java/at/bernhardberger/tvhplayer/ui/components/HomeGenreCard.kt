package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.tvGenreColors

/** The height of Home's genre text cards, as in the approved mockup. */
internal val HomeGenreCardHeight = 56.dp

/**
 * Home's genre chip: a compact text card in the family's colour with its base-coloured dot and
 * label, as in the approved mockup. Opening it goes to the Guide's genre family in Guide; the
 * caller owns the action and the focus requester/test tag in [modifier].
 */
@Composable
internal fun HomeGenreCard(
    category: ProgrammeCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = tvGenreColors(category)
    val label = programmeCategoryLabel(category)
    val dot = colors?.base ?: Color.Transparent
    val container = colors?.tint ?: TvSurfaceColors.containerLowest
    HomeTextCard(label, onClick, modifier, onLongClick, containerColor = container,
        leading = { Box(Modifier.size(10.dp).background(dot, CircleShape)) })
}

/** The same compact text-card kit for genres and scheduled recordings. */
@Composable
internal fun HomeTextCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    subtitle: String? = null,
    containerColor: Color = TvSurfaceColors.containerLowest,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val ring = rememberCardFocusRing(interaction)
    val contextMenu = rememberBrowseContextMenuTrigger(onLongClick, menuKey = true)
    Card(
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        interactionSource = interaction,
        modifier = modifier
            .height(HomeGenreCardHeight)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(title, subtitle).joinToString(", ") }
            .cardFocusRing(ring, MaterialTheme.colorScheme.onSurface)
            .then(contextMenu.modifier),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
        ),
        glow = CardDefaults.glow(),
        scale = EmbeddedProgressCardScale,
        border = embeddedProgressCardBorder(),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TvTextColors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = TvTextColors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
