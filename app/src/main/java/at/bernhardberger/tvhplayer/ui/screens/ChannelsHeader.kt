package at.bernhardberger.tvhplayer.ui.screens

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
 * content's remembered item, the start edge leads to the navigation rail and Up stays.
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
) {
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val rail = navigationFocus ?: FocusRequester.Default
    TopLevelBrowseHeader(
        title = title,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onMoveToContent()
            }
            .testTag("channels-header"),
        actions = {
            if (showGroup) RoundIconButton(
                icon = { Icon(painterResource(R.drawable.ic_filter_list), stringResource(R.string.channels_group)) },
                onClick = { onOpen(ChannelsPanel.GROUP) },
                focusRequester = focus.group,
                onFocused = onFocused,
                modifier = Modifier
                    .focusProperties {
                        up = FocusRequester.Cancel
                        if (ltr) left = rail else right = rail
                    }
                    .testTag("channels-group-button"),
            )
            RoundIconButton(
                icon = { Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.channels_view_options)) },
                onClick = { onOpen(ChannelsPanel.VIEW_OPTIONS) },
                focusRequester = focus.view,
                onFocused = onFocused,
                modifier = Modifier
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
