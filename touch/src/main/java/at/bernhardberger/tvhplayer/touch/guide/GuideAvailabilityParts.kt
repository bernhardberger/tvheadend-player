// Sizes are the drawings' (round 37).
@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * What stands in for the Guide while there is no list: section 12's first-load form, and the shared
 * message form for sign-in needed and for failures, in the catalog's words.
 */
@Composable
internal fun GuideStandIn(availability: GuideAvailability, layout: GuideLayout, actions: GuideActions) {
    when (availability) {
        GuideAvailability.Loading -> GuideFirstLoad(layout, LocalBrowseMargin.current)
        GuideAvailability.SignInNeeded -> ListMessage(
            MessageKind.SignInNeeded, actions.onSignIn ?: actions.onOpenConnectionSettings,
        )
        is GuideAvailability.Failed -> ListFailure(
            availability.error, actions.onRetry, actions.onOpenConnectionSettings,
        )
        GuideAvailability.Current, GuideAvailability.Offline -> Unit
    }
}

/** The first load's progress line under the top bar. */
@Composable
internal fun GuideLoadingLine(availability: GuideAvailability) {
    if (availability == GuideAvailability.Loading) FirstLoadLine(LocalBrowseMargin.current)
}

/** The overview's shape: the phone's channel rows; the tablet's channel columns with their programs. */
@Composable
internal fun GuideFirstLoad(layout: GuideLayout, margin: Dp = if (layout.compact) 12.dp else 24.dp) {
    val text = stringResource(SharedR.string.epg_loading)
    if (layout.compact) {
        FirstLoadList(text, Modifier.padding(horizontal = margin)) {
            repeat(PHONE_ROWS) { index ->
                SkeletonRow(index, PHONE_ROWS, 88.dp) {
                    SkeletonBlock(88.dp, 48.dp)
                    SkeletonLines(index, modifier = Modifier.weight(1f))
                    SkeletonBlock(96.dp, 54.dp, RoundedCornerShape(8.dp))
                }
            }
        }
        return
    }
    val width = if (layout == GuideLayout.Wide) 264.dp else 232.dp
    FirstLoadList(text, Modifier.padding(horizontal = margin)) {
        Row(
            Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(TABLET_COLUMNS) { column ->
                Column(
                    Modifier.width(width),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBlock(72.dp, 40.dp, RoundedCornerShape(10.dp))
                        SkeletonLines(column, supportingLine = false)
                    }
                    repeat(TABLET_PROGRAMS) { index ->
                        SkeletonRow(index, TABLET_PROGRAMS, 72.dp) { SkeletonLines(index + column) }
                    }
                }
            }
        }
    }
}

/** Channels' offline line above the saved guide, in the Guide's words. */
@Composable
internal fun GuideOfflineBanner(layout: GuideLayout, onSettings: (() -> Unit)?, modifier: Modifier = Modifier) {
    OfflineBanner(layout.compact, OfflineTexts(R.string.guide_offline, R.string.guide_saved), onSettings, modifier)
}

/** A picked day or time still loading inside the shown Guide: the stock indicator, a short wait. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DayLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val loading = stringResource(SharedR.string.epg_loading)
        LoadingIndicator(Modifier.semantics { contentDescription = loading })
    }
}

private const val PHONE_ROWS = 7
private const val TABLET_COLUMNS = 6
private const val TABLET_PROGRAMS = 7
