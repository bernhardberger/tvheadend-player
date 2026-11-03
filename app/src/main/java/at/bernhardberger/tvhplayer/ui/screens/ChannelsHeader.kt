package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader

/** Focus targets of the Channels header and the empty group's action below it. */
internal class ChannelsHeaderFocus {
    val tags = FocusRequester()
    val filter = FocusRequester()
    val view = FocusRequester()
    val showAll = FocusRequester()

    /** Up from the first content row lands here: Tags, or View options without a choice of tags. */
    fun entry(hasTagChoice: Boolean): FocusRequester = if (hasTagChoice) tags else view
}

/**
 * The Channels header: the [title] is its pane title, not drawn; the Tags and View options buttons
 * sit at its end, with the Guide's Filter between them when [showFilter]. Down returns to the
 * content's remembered item, the start edge leads to the navigation rail (or back to content that
 * reached it with its end edge, see [onReturnToContent]) and Up stays. A single channel set has
 * nothing to choose, so Tags is not shown. Tags names the active [tag], or "All channels" on its
 * resting tone without one; Filter names the active [filter] after its [filterDot].
 * A [leading] action sits before the buttons and takes the start edge; it applies the modifier it
 * receives to its first focusable.
 */
@Composable
internal fun ChannelsHeader(
    title: String,
    tag: String?,
    focus: ChannelsHeaderFocus,
    showTags: Boolean,
    navigationFocus: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: (ChannelsPanel) -> Unit,
    onMoveToContent: () -> Boolean,
    modifier: Modifier = Modifier,
    onReturnToContent: (() -> Boolean)? = null,
    leading: (@Composable RowScope.(startEdge: Modifier) -> Unit)? = null,
    showFilter: Boolean = false,
    filter: String? = null,
    filterDot: Color? = null,
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
    // A focused button outlives the data that offered it, so a reload never drops the focus;
    // it goes once focus moves on.
    var tagsFocused by remember { mutableStateOf(false) }
    var filterFocused by remember { mutableStateOf(false) }
    val tagsShown = showTags || tagsFocused
    val filterShown = showFilter || filterFocused
    val tagsIsFirst = leading == null
    val filterIsFirst = leading == null && !tagsShown
    val viewIsFirst = leading == null && !tagsShown && !filterShown
    TopLevelBrowseHeader(
        title = title,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onMoveToContent()
            }
            .testTag("channels-header"),
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TvGridGutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke(this, startEdge.focusProperties { up = FocusRequester.Cancel })
                if (tagsShown || filterShown) Row(
                    horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tagsShown) RoundIconButton(
                        icon = painterResource(R.drawable.ic_sell),
                        label = stringResource(R.string.browse_tags),
                        value = tag ?: stringResource(R.string.all_channels),
                        active = tag != null,
                        onClick = { onOpen(ChannelsPanel.TAGS) },
                        focusRequester = focus.tags,
                        onFocused = onFocused,
                        modifier = (if (tagsIsFirst) startEdge else Modifier)
                            .focusProperties { up = FocusRequester.Cancel }
                            .onFocusChanged { tagsFocused = it.hasFocus }
                            .testTag("channels-tags-button"),
                    )
                    if (filterShown) RoundIconButton(
                        icon = painterResource(R.drawable.ic_filter_list),
                        label = stringResource(R.string.guide_filter),
                        value = filter,
                        dot = filterDot,
                        onClick = { onOpen(ChannelsPanel.FILTER) },
                        focusRequester = focus.filter,
                        onFocused = onFocused,
                        modifier = (if (filterIsFirst) startEdge else Modifier)
                            .focusProperties { up = FocusRequester.Cancel }
                            .onFocusChanged { filterFocused = it.hasFocus }
                            .testTag("guide-filter-button"),
                    )
                }
                RoundIconButton(
                    icon = painterResource(R.drawable.ic_tune),
                    label = stringResource(R.string.channels_view_options),
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
            }
        },
    )
}
