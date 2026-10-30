package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardContainerDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors

/** The card width Home's recording shelves and their end card use, as in the approved mockup. */
internal val HomeCardWidth = 196.dp

/**
 * The end card of Home's recording shelves: an ordinary card-sized tile whose picture is a
 * plain high container with the library glyph, the shelf's own "All recordings" name under it
 * only while the row shows text, as in the approved mockup. Opening goes to the library; the
 * caller owns the action and the focus requester/test tag in [modifier].
 */
@Composable
internal fun HomeAllRecordingsCard(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    textShown: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.home_all_recordings)
    StandardProgrammeCard(
        width = HomeCardWidth,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
        art = null,
        picon = null,
        title = {
            AnimatedVisibility(visible = textShown, enter = RowTextEnter, exit = RowTextExit) {
                Text(
                    label,
                    Modifier.fillMaxWidth().padding(top = StandardCardTextGap),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        subtitle = {},
        contentColor = CardContainerDefaults.contentColor(
            contentColor = TvTextColors.secondary,
            focusedContentColor = TvTextColors.primary,
        ),
        picture = {
            Box(
                Modifier.fillMaxSize().background(TvSurfaceColors.containerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_video_library),
                    null,
                    Modifier.size(36.dp),
                    tint = TvTextColors.secondary,
                )
            }
        },
    )
}

/**
 * Upcoming's one summary card: a quiet row-height card with the locale's next scheduled
 * recording, as in the approved mockup. [modifier] carries the row's start keyline, focus room,
 * the section's focus requester and its test tag.
 */
@Composable
internal fun HomeUpcomingCard(
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val ring = rememberCardFocusRing(interaction)
    val contextMenu = rememberBrowseContextMenuTrigger(onLongClick, menuKey = true)
    Card(
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        interactionSource = interaction,
        modifier = modifier
            .cardFocusRing(ring, MaterialTheme.colorScheme.onSurface)
            .height(HomeGenreCardHeight)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .then(contextMenu.modifier),
        colors = CardDefaults.colors(containerColor = TvSurfaceColors.containerHigh),
        scale = EmbeddedProgressCardScale,
        border = embeddedProgressCardBorder(),
        glow = CardDefaults.glow(),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_event),
                null,
                Modifier.size(24.dp),
                tint = TvTextColors.secondary,
            )
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = TvTextColors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
