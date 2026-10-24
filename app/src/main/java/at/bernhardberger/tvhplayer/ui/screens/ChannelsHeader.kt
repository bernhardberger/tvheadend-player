package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
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

/** Longest the Group button grows before the active group's name ellipsizes. */
private val ChannelsGroupButtonMaxWidth = 280.dp

/**
 * The Channels header: the Group button, which shows the active group's name ([groupName], or
 * "All channels" without one), and View options at its end. Down returns to the
 * content's remembered item, the start edge leads to the navigation rail (or back to content
 * that reached it with its end edge, see [onReturnToContent]) and Up stays.
 * A single visible group has nothing to choose, so its button is not shown.
 * A [leading] action sits before the buttons and takes the start edge; it applies the modifier it
 * receives to its first focusable.
 */
@Composable
internal fun ChannelsHeader(
    title: String,
    groupName: String?,
    focus: ChannelsHeaderFocus,
    showGroup: Boolean,
    navigationFocus: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: (ChannelsPanel) -> Unit,
    onMoveToContent: () -> Boolean,
    modifier: Modifier = Modifier,
    onReturnToContent: (() -> Boolean)? = null,
    leading: (@Composable RowScope.(startEdge: Modifier) -> Unit)? = null,
    genre: String? = null,
) {
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val rail = navigationFocus ?: FocusRequester.Default
    val towardStart = if (ltr) Key.DirectionLeft else Key.DirectionRight
    // Content whose end edge leads here takes the start edge back; otherwise the rail does.
    val startEdge = Modifier
        .onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && event.key == towardStart && onReturnToContent?.invoke() == true
        }
        .focusProperties { if (ltr) left = rail else right = rail }
    val groupIsFirst = leading == null
    val viewIsFirst = leading == null && !showGroup
    TopLevelBrowseHeader(
        title = title,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onMoveToContent()
            }
            .testTag("channels-header"),
        actions = {
            leading?.invoke(this, startEdge.focusProperties { up = FocusRequester.Cancel })
            if (showGroup) {
                val label = groupName ?: stringResource(R.string.all_channels)
                val description = stringResource(R.string.channels_group)
                OutlinedButton(
                    onClick = { onOpen(ChannelsPanel.GROUP) },
                    // Vertical padding would grow the 40dp header slot past the round button it replaces.
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = (if (groupIsFirst) startEdge else Modifier)
                        .widthIn(max = ChannelsGroupButtonMaxWidth)
                        .focusRequester(focus.group)
                        .onFocusChanged { if (it.isFocused) onFocused() }
                        .focusProperties { up = FocusRequester.Cancel }
                        .semantics {
                            contentDescription = description
                            stateDescription = label
                        }
                        .testTag("channels-group-button"),
                ) {
                    Icon(painterResource(R.drawable.ic_filter_list), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RoundIconButton(
                icon = { Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.channels_view_options), Modifier.size(IconButtonDefaults.MediumIconSize)) },
                onClick = { onOpen(ChannelsPanel.VIEW_OPTIONS) },
                focusRequester = focus.view,
                onFocused = onFocused,
                modifier = (if (viewIsFirst) startEdge else Modifier)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        if (ltr) right = FocusRequester.Cancel else left = FocusRequester.Cancel
                    }
                    .testTag("channels-view-button"),
            )
        },
    )
}
