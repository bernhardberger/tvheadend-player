package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
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
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.ui.components.HomeCardWidth
import at.bernhardberger.tvhplayer.ui.components.HomeRecentTileSize

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
    Column(Modifier.padding(start = 40.dp, end = 40.dp, bottom = if (loading) TvGridVerticalMargin else 0.dp).testTag("home-empty"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            input.hiddenSections.size == HomeSectionKind.entries.size -> Text(stringResource(R.string.home_sections_hidden))
            loading -> HomeLoadingSkeletons(Modifier.weight(1f, fill = false).clipToBounds())
            input.channels.isEmpty() && hasChannelsOutsideScope -> Text(stringResource(R.string.empty_channel_tag))
            else -> {
                val explainHistory = HomeSectionKind.RECENT_CHANNELS !in input.hiddenSections
                val explainEpg = HomeSectionKind.ON_NOW !in input.hiddenSections
                val explainRecordings = HomeSectionKind.RECENTLY_RECORDED !in input.hiddenSections &&
                    (observation.dvrSnapshotForDisplay == null || partitionDvrLibrary(input.dvrEntries).archive.isEmpty())
                if (explainHistory) Text(stringResource(R.string.home_no_history))
                if (explainEpg) {
                    if (observation.epgSnapshotForDisplay == null) HomeLoadingSkeletons(Modifier.weight(1f, fill = false).clipToBounds())
                    else Text(stringResource(R.string.no_epg))
                }
                if (explainRecordings) {
                    Text(stringResource(if (observation.dvrSnapshotForDisplay == null) R.string.recordings_loading else R.string.home_no_recordings))
                }
                if (!explainHistory && !explainEpg && !explainRecordings) Text(stringResource(R.string.home_sections_hidden))
            }
        }
        Button(onClick = onConnectionSettings, modifier = Modifier.focusRequester(actionFocus)
            .focusProperties { up = headerFocus }.testTag("home-recovery")) {
            Text(stringResource(R.string.action_connection_settings))
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
