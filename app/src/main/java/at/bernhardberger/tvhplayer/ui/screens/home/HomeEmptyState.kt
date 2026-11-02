package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.player.PlayerBusyRing
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import kotlinx.coroutines.delay

/** Most cold starts have their rows by then; those never see the indicator. */
private const val HomeLoadingIndicatorDelayMs = 1_500L

/**
 * No fictional cards: which rows are on their way is not known yet, so loading shows a spinner, and
 * only once it has taken a while. Known empty data has explanatory copy.
 */
@Composable
internal fun HomeEmptyState(
    input: HomeInput,
    loading: Boolean,
    hasChannelsOutsideScope: Boolean,
    loadingDelayed: Boolean,
    contentPadding: PaddingValues,
    actionFocus: FocusRequester,
    headerFocus: FocusRequester,
    onConnectionSettings: () -> Unit,
    onBrowseChannels: () -> Unit,
    onViewOptions: () -> Unit,
) {
    val hidden = input.hiddenSections.size == HomeSectionKind.entries.size
    val direction = LocalLayoutDirection.current
    val leading = LocalBrowseNavigationFocus.current ?: FocusRequester.Default
    var indicatorDue by remember(loading) { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) { delay(HomeLoadingIndicatorDelayMs); indicatorDue = true }
    }
    Column(Modifier.fillMaxSize().padding(
        start = contentPadding.calculateStartPadding(direction) + TvBrowseLeadingInset,
        end = maxOf(contentPadding.calculateEndPadding(direction), TvBrowseTrailingInset),
        bottom = TvGridVerticalMargin,
    ).testTag("home-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        if (loading) {
            AnimatedVisibility(indicatorDue, enter = fadeIn(tween(PlayerMotion.ShortMs))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlayerBusyRing(Modifier.testTag("home-loading"))
                    Text(stringResource(when {
                        loadingDelayed -> R.string.home_still_connecting
                        input.channels.isEmpty() -> R.string.home_loading_channels
                        else -> R.string.loading
                    }), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
        } else {
            Text(stringResource(if (hidden) R.string.channels_view_options else R.string.home_start_watching),
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
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
