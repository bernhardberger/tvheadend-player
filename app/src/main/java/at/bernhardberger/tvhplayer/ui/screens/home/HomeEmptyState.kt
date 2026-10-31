package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.HomeInput
import at.bernhardberger.tvhplayer.core.HomeSectionKind

/** No fictional cards: unavailable metadata has skeletons; known empty data has explanatory copy. */
@Composable
internal fun HomeEmptyState(
    input: HomeInput,
    observation: SessionObservation,
    catalogCurrent: Boolean,
    hasChannelsOutsideScope: Boolean,
    actionFocus: FocusRequester,
    headerFocus: FocusRequester,
    onConnectionSettings: () -> Unit,
) {
    val loading = !catalogCurrent && input.channels.isEmpty()
    Column(Modifier.padding(start = 40.dp, end = 40.dp).testTag("home-empty"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            input.hiddenSections.size == HomeSectionKind.entries.size -> Text(stringResource(R.string.home_sections_hidden))
            loading -> HomeLoadingSkeletons()
            input.channels.isEmpty() && hasChannelsOutsideScope -> Text(stringResource(R.string.empty_channel_tag))
            else -> {
                if (HomeSectionKind.RECENT_CHANNELS !in input.hiddenSections) Text(stringResource(R.string.home_no_history))
                if (HomeSectionKind.ON_NOW !in input.hiddenSections) {
                    if (observation.epgSnapshotForDisplay == null) HomeLoadingSkeletons()
                    else Text(stringResource(R.string.no_epg))
                }
                if (HomeSectionKind.RECENTLY_RECORDED !in input.hiddenSections) {
                    Text(stringResource(if (observation.dvrSnapshotForDisplay == null) R.string.recordings_loading else R.string.home_no_recordings))
                }
            }
        }
        Button(onClick = onConnectionSettings, modifier = Modifier.focusRequester(actionFocus)
            .focusProperties { up = headerFocus }.testTag("home-recovery")) {
            Text(stringResource(R.string.action_connection_settings))
        }
    }
}

@Composable
private fun HomeLoadingSkeletons() {
    val label = stringResource(R.string.loading)
    Column(Modifier.testTag("home-loading").semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(160.dp, 20.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            repeat(3) {
                Box(Modifier.size(220.dp, 124.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}
