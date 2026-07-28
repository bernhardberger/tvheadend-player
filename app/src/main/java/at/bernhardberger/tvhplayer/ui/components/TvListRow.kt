package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha

private val TvListRowHeight = 56.dp

@Composable
fun TvListRow(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val listItemColors = ListItemDefaults.colors()
    Surface(
        selected = selected,
        onClick = onClick,
        colors = SelectableSurfaceDefaults.colors(
            containerColor = listItemColors.containerColor,
            contentColor = listItemColors.contentColor,
            focusedContainerColor = listItemColors.focusedContainerColor,
            focusedContentColor = listItemColors.focusedContentColor,
            pressedContainerColor = listItemColors.pressedContainerColor,
            pressedContentColor = listItemColors.pressedContentColor,
            selectedContainerColor = listItemColors.selectedContainerColor,
            selectedContentColor = listItemColors.selectedContentColor,
            disabledContainerColor = listItemColors.disabledContainerColor,
            disabledContentColor = listItemColors.disabledContentColor,
            focusedSelectedContainerColor = listItemColors.focusedSelectedContainerColor,
            focusedSelectedContentColor = listItemColors.focusedSelectedContentColor,
            pressedSelectedContainerColor = listItemColors.pressedSelectedContainerColor,
            pressedSelectedContentColor = listItemColors.pressedSelectedContentColor,
        ),
        scale = SelectableSurfaceDefaults.scale(
            focusedScale = 1f,
            focusedSelectedScale = 1f,
        ),
        modifier = modifier.height(TvListRowHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvSpacing16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.let {
                it()
                Spacer(Modifier.width(TvSpacing16))
            }
            Column(Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium, headlineContent)
                supportingContent?.let {
                    CompositionLocalProvider(
                        LocalContentColor provides LocalContentColor.current.copy(
                            alpha = TvTextTertiaryAlpha,
                        )
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall, it)
                    }
                }
            }
            trailingContent?.let {
                Spacer(Modifier.width(TvSpacing16))
                ProvideTextStyle(MaterialTheme.typography.labelLarge, it)
            }
        }
    }
}
