package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.ChannelTagSelector
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import at.bernhardberger.tvhplayer.ui.screens.formatDateTime

/** The Guide's title row with its date, Now, search and clear-filter actions, and the scope row below it. */
@Composable
internal fun GuideHeader(
    category: ProgrammeCategory,
    categoryLabel: String,
    windowStartSec: Long,
    searchEntryVisible: Boolean,
    hasScopeTabs: Boolean,
    channelScope: ChannelBrowsingScope,
    contentPadding: PaddingValues,
    startPadding: Dp,
    endPadding: Dp,
    density: Density,
    guideDateFocus: FocusRequester,
    guideNowFocus: FocusRequester,
    guideSearchFocus: FocusRequester,
    guideClearFilterFocus: FocusRequester,
    scopeFocus: FocusRequester,
    onDateClick: () -> Unit,
    onNowClick: () -> Unit,
    onSearchClick: () -> Unit,
    onClearCategory: () -> Unit,
    onDateFocused: () -> Unit,
    onNowFocused: () -> Unit,
    onSearchFocused: () -> Unit,
    onClearFilterFocused: () -> Unit,
    onHeaderDown: () -> Boolean,
    onSelectTag: (ChannelTagId?) -> Unit,
    onMoveToContent: () -> Boolean,
    onScopeFocused: () -> Unit,
    onScopeUp: () -> Boolean,
) {
    TopLevelBrowseHeader(
        title = stringResource(R.string.epg_title),
        filter = categoryLabel.takeIf { category != ProgrammeCategory.ALL },
        modifier = Modifier
            .padding(
                start = startPadding + TvBrowseLeadingInset,
                top = contentPadding.calculateTopPadding(),
                end = maxOf(endPadding, TvBrowseTrailingInset),
            )
            .heightIn(min = with(density) {
                maxOf(40.dp, MaterialTheme.typography.headlineMedium.lineHeight.toDp(),
                    MaterialTheme.typography.labelLarge.lineHeight.toDp() + 16.dp)
            }),
        actions = {
            OutlinedButton(
                onClick = onDateClick,
                modifier = Modifier
                    .focusRequester(guideDateFocus)
                    .onFocusChanged {
                        if (it.isFocused) onDateFocused()
                    }
                    .onPreviewKeyEvent { event ->
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown
                        ) {
                            onHeaderDown()
                        } else {
                            false
                        }
                    },
            ) {
                Text(windowStartSec.formatDateTime(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]))
            }
            OutlinedButton(
                onClick = onNowClick,
                modifier = Modifier
                    .focusRequester(guideNowFocus)
                    .onFocusChanged {
                        if (it.isFocused) onNowFocused()
                    }
                    .onPreviewKeyEvent { event ->
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown
                        ) {
                            onHeaderDown()
                        } else {
                            false
                        }
                    },
            ) {
                Text(stringResource(R.string.now))
            }
            if (searchEntryVisible) {
                OutlinedButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .focusRequester(guideSearchFocus)
                        .onFocusChanged {
                            if (it.isFocused) onSearchFocused()
                        }
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionDown
                            ) {
                                onHeaderDown()
                            } else {
                                false
                            }
                        }
                        .testTag("epg-search-open"),
                ) {
                    Text(stringResource(R.string.epg_search))
                }
            }
            if (category != ProgrammeCategory.ALL) {
                OutlinedButton(
                    onClick = onClearCategory,
                    modifier = Modifier
                        .focusRequester(guideClearFilterFocus)
                        .onFocusChanged {
                            if (it.isFocused) onClearFilterFocused()
                        }
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionDown
                            ) {
                                onHeaderDown()
                            } else {
                                false
                            }
                        },
                ) {
                    Text(stringResource(R.string.epg_clear_filter))
                }
            }
        },
    )
    if (hasScopeTabs) {
        Spacer(Modifier.height(TvSpacing8))
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(bottom = 8.dp)) {
        ChannelTagSelector(
            tags = channelScope.tags,
            activeTagId = channelScope.activeTagId,
            onSelectTag = onSelectTag,
            allChannelsVisible = channelScope.allChannelsVisible,
            activeFocusRequester = scopeFocus,
            onMoveToContent = onMoveToContent,
            modifier = Modifier
                .padding(start = startPadding + TvBrowseLeadingInset)
                .onFocusChanged {
                    if (it.hasFocus) onScopeFocused()
                }
                .onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionUp
                    ) {
                        onScopeUp()
                    } else {
                        false
                    }
                },
        )
        }
    }
}
