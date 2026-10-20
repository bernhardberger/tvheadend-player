package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButtonDefaults
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader

/** Focus targets of the Channels header and the empty group's action below it. */
internal class ChannelsHeaderFocus {
    val group = FocusRequester()
    val view = FocusRequester()
    val showAll = FocusRequester()

    /** Up from the first content row lands here: the group button, or View options without a choice. */
    fun entry(hasGroupChoice: Boolean): FocusRequester = if (hasGroupChoice) group else view
}

/**
 * The Channels title with the Group and View options buttons at its end. Down returns to the
 * content's remembered item, the start edge leads to the navigation rail (or back to content
 * that reached it with its end edge, see [startReturnsToContent]) and Up stays.
 * A single visible group has nothing to choose, so its button is not shown.
 */
@Composable
internal fun ChannelsHeader(
    title: String,
    focus: ChannelsHeaderFocus,
    showGroup: Boolean,
    navigationFocus: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: (ChannelsPanel) -> Unit,
    onMoveToContent: () -> Boolean,
    modifier: Modifier = Modifier,
    startReturnsToContent: Boolean = false,
) {
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val rail = navigationFocus ?: FocusRequester.Default
    val towardStart = if (ltr) Key.DirectionLeft else Key.DirectionRight
    // Content whose end edge leads here takes the start edge back; otherwise the rail does.
    val startToContent = Modifier.onPreviewKeyEvent { event ->
        startReturnsToContent && event.type == KeyEventType.KeyDown && event.key == towardStart && onMoveToContent()
    }
    TopLevelBrowseHeader(
        title = title,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onMoveToContent()
            }
            .testTag("channels-header"),
        actions = {
            if (showGroup) RoundIconButton(
                icon = { Icon(painterResource(R.drawable.ic_filter_list), stringResource(R.string.channels_group), Modifier.size(IconButtonDefaults.MediumIconSize)) },
                onClick = { onOpen(ChannelsPanel.GROUP) },
                focusRequester = focus.group,
                onFocused = onFocused,
                modifier = startToContent
                    .focusProperties {
                        up = FocusRequester.Cancel
                        if (ltr) left = rail else right = rail
                    }
                    .testTag("channels-group-button"),
            )
            RoundIconButton(
                icon = { Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.channels_view_options), Modifier.size(IconButtonDefaults.MediumIconSize)) },
                onClick = { onOpen(ChannelsPanel.VIEW_OPTIONS) },
                focusRequester = focus.view,
                onFocused = onFocused,
                modifier = (if (showGroup) Modifier else startToContent)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        if (ltr) right = FocusRequester.Cancel else left = FocusRequester.Cancel
                        if (!showGroup) { if (ltr) left = rail else right = rail }
                    }
                    .testTag("channels-view-button"),
            )
        },
    )
}
