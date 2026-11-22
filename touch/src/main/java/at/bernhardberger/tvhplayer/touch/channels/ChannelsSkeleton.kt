package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.FirstLoadLine
import at.bernhardberger.tvhplayer.touch.ui.FirstLoadList
import at.bernhardberger.tvhplayer.touch.ui.SkeletonBlock
import at.bernhardberger.tvhplayer.touch.ui.SkeletonLines
import at.bernhardberger.tvhplayer.touch.ui.SkeletonRow

@Composable
internal fun ChannelsLoadingLine(layout: ChannelsLayout, margin: Dp = if (layout.tablet) 24.dp else 0.dp) {
    FirstLoadLine(margin)
}

/** The first load in the channel rows' shape: logo, now and, on the wide tablet, next. */
@Composable
internal fun ChannelsSkeleton(layout: ChannelsLayout, margin: Dp = if (layout.tablet) 24.dp else 12.dp) {
    val count = if (layout.tablet) TABLET_SKELETON_ROWS else PHONE_SKELETON_ROWS
    FirstLoadList(stringResource(R.string.channels_loading), Modifier.padding(horizontal = margin)) {
        repeat(count) { index ->
            SkeletonRow(index, count, layout.rowHeight) {
                SkeletonBlock(if (layout.tablet) 112.dp else 88.dp, if (layout.tablet) 56.dp else 48.dp)
                SkeletonLines(index, supportingLine = !layout.tablet, modifier = Modifier.weight(1f))
                if (layout.tablet) {
                    SkeletonLines(index, modifier = Modifier.weight(2f))
                    if (layout.showNext) SkeletonLines(index + 1, modifier = Modifier.weight(2f))
                }
            }
        }
    }
}

private const val TABLET_SKELETON_ROWS = 10
private const val PHONE_SKELETON_ROWS = 8
