// Sizes are the drawings' (round 37).
@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.recordings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.FirstLoadLine
import at.bernhardberger.tvhplayer.touch.ui.FirstLoadList
import at.bernhardberger.tvhplayer.touch.ui.ListFailure
import at.bernhardberger.tvhplayer.touch.ui.ListMessage
import at.bernhardberger.tvhplayer.touch.ui.LocalBrowseMargin
import at.bernhardberger.tvhplayer.touch.ui.MessageKind
import at.bernhardberger.tvhplayer.touch.ui.OfflineBanner
import at.bernhardberger.tvhplayer.touch.ui.OfflineTexts
import at.bernhardberger.tvhplayer.touch.ui.SkeletonBlock
import at.bernhardberger.tvhplayer.touch.ui.SkeletonLines
import at.bernhardberger.tvhplayer.touch.ui.SkeletonRow

/**
 * What stands in for the list while there is none: section 12's first-load form (the loading line
 * and two-line skeleton rows; the tablet's pane stays empty), and the shared message form for
 * sign-in needed and for failures, in the catalog's words.
 */
@Composable
internal fun RecordingsStandIn(
    availability: RecordingsAvailability,
    layout: RecordingsLayout,
    actions: RecordingsActions,
) {
    when (availability) {
        RecordingsAvailability.Loading -> RecordingsFirstLoad(layout)
        RecordingsAvailability.SignInNeeded -> ListMessage(
            MessageKind.SignInNeeded, actions.onSignIn ?: actions.onOpenConnectionSettings,
        )
        is RecordingsAvailability.Failed -> ListFailure(
            availability.error, actions.onRetry, actions.onOpenConnectionSettings,
        )
        RecordingsAvailability.Current, RecordingsAvailability.Offline -> Unit
    }
}

/** The first load's progress line under the top bar. */
@Composable
internal fun RecordingsLoadingLine(availability: RecordingsAvailability) {
    if (availability == RecordingsAvailability.Loading) FirstLoadLine(LocalBrowseMargin.current)
}

@Composable
private fun RecordingsFirstLoad(layout: RecordingsLayout) {
    val text = stringResource(SharedR.string.recordings_loading)
    val margin = LocalBrowseMargin.current
    val rows = if (layout.compact) PHONE_SKELETON_ROWS else TABLET_SKELETON_ROWS
    val list: @Composable (Modifier) -> Unit = { modifier ->
        FirstLoadList(text, modifier) { repeat(rows) { SkeletonRecording(it, rows) } }
    }
    when (layout) {
        RecordingsLayout.Wide -> Row(Modifier.fillMaxSize().padding(start = 8.dp, end = margin)) {
            list(Modifier.weight(1f))
            Spacer(Modifier.width(24.dp))
            Surface(
                Modifier.weight(1f).padding(top = 16.dp, bottom = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {}
        }
        RecordingsLayout.Upright, RecordingsLayout.Phone -> list(Modifier.padding(horizontal = margin))
    }
}

/** A recording row's shape: the thumbnail and the title over the supporting line. */
@Composable
private fun SkeletonRecording(index: Int, count: Int) {
    SkeletonRow(index, count, RecordingRowHeight) {
        SkeletonBlock(ThumbWidth, ThumbHeight, ThumbShape)
        SkeletonLines(index)
    }
}

/** Channels' offline line above the saved list, in Recordings' words. */
@Composable
internal fun RecordingsOfflineBanner(layout: RecordingsLayout, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val texts = OfflineTexts(R.string.recordings_offline, R.string.recordings_saved)
    OfflineBanner(layout.compact, texts, onSettings, modifier)
}

private val RecordingRowHeight: Dp = 72.dp
private const val TABLET_SKELETON_ROWS = 10
private const val PHONE_SKELETON_ROWS = 8
