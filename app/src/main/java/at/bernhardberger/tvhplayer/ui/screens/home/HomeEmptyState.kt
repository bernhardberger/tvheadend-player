package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.HomeInput
import at.bernhardberger.tvhplayer.core.HomeSectionKind
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.ui.components.HomeCardWidth
import at.bernhardberger.tvhplayer.ui.components.HomeRecentTileSize
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus

/** No fictional cards: unavailable metadata has skeletons; known empty data has explanatory copy. */
@Composable
internal fun HomeEmptyState(
    input: HomeInput,
    catalogCurrent: Boolean,
    hasChannelsOutsideScope: Boolean,
    loadingDelayed: Boolean,
    contentPadding: PaddingValues,
    actionFocus: FocusRequester,
    headerFocus: FocusRequester,
    onConnectionSettings: () -> Unit,
    onBrowseChannels: () -> Unit,
    onViewOptions: () -> Unit,
) {
    val loading = !catalogCurrent && input.channels.isEmpty()
    val hidden = input.hiddenSections.size == HomeSectionKind.entries.size
    val direction = LocalLayoutDirection.current
    val leading = LocalBrowseNavigationFocus.current ?: FocusRequester.Default
    Column(Modifier.fillMaxSize().padding(
        start = contentPadding.calculateStartPadding(direction) + TvBrowseLeadingInset,
        end = maxOf(contentPadding.calculateEndPadding(direction), TvBrowseTrailingInset),
        bottom = TvGridVerticalMargin,
    ).testTag("home-empty"),
        horizontalAlignment = if (loading) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = if (loading) Arrangement.spacedBy(16.dp) else Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text(stringResource(when {
            loadingDelayed -> R.string.home_still_connecting
            loading -> R.string.home_loading_channels
            hidden -> R.string.channels_view_options
            else -> R.string.home_start_watching
        }), style = MaterialTheme.typography.headlineMedium,
            textAlign = if (loading) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        if (loading) {
            HomeLoadingSkeletons(Modifier.weight(1f).clipToBounds())
        } else {
            Text(stringResource(when {
                hidden -> R.string.home_sections_hidden
                input.channels.isEmpty() && hasChannelsOutsideScope -> R.string.empty_channel_tag
                else -> R.string.home_start_watching_hint
            }), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        if (!loading || loadingDelayed) {
            Button(onClick = when {
                loading -> onConnectionSettings
                hidden -> onViewOptions
                else -> onBrowseChannels
            }, modifier = Modifier.focusRequester(actionFocus)
                .focusProperties { up = headerFocus; left = leading }.testTag("home-recovery")) {
                Text(stringResource(when {
                    loading -> R.string.action_connection_settings
                    hidden -> R.string.channels_view_options
                    else -> R.string.home_browse_channels
                }))
            }
        }
    }
}

@Composable
private fun HomeLoadingSkeletons(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    val headingHeight = with(LocalDensity.current) { MaterialTheme.typography.headlineMedium.lineHeight.toDp() }
    BoxWithConstraints(modifier.testTag("home-loading").semantics { contentDescription = label }) {
        val activityWidth = (maxWidth - TvGridGutter) / 2
        val tileCount = ((maxWidth + HomeRecentGap) / (HomeRecentTileSize + HomeRecentGap)).toInt()
        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true),
            verticalArrangement = Arrangement.spacedBy(HomeSectionGap)) {
            Column(verticalArrangement = Arrangement.spacedBy(HomeHeadingContentGap)) {
                Box(Modifier.size(HomeCardWidth, headingHeight).background(MaterialTheme.colorScheme.surfaceVariant))
                Row(horizontalArrangement = Arrangement.spacedBy(TvGridGutter)) {
                    repeat(2) {
                        Box(Modifier.width(activityWidth).aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(HomeHeadingContentGap)) {
                Box(Modifier.size(HomeCardWidth, headingHeight).background(MaterialTheme.colorScheme.surfaceVariant))
                Row(horizontalArrangement = Arrangement.spacedBy(HomeRecentGap)) {
                    repeat(tileCount) {
                        Box(Modifier.size(HomeRecentTileSize).background(MaterialTheme.colorScheme.surfaceVariant).testTag("home-loading-recent-$it"))
                    }
                }
            }
        }
    }
}
