// Sizes are the drawings' (round 37).
@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R

/*
 * Section 12's first load of a list, as Channels draws it: one progress line on the top bar's lower
 * edge, the loading text in the line above the list, and skeleton rows in the list's own shape.
 */

/** The progress line under the top bar, inset to the browse keyline when shown in the frame. */
@Composable
internal fun FirstLoadLine(horizontalMargin: Dp) {
    LinearProgressIndicator(
        Modifier.fillMaxWidth().padding(horizontal = horizontalMargin),
    )
}

/** The loading [text] above the skeleton [rows], announced once; the skeleton itself stays silent. */
@Composable
internal fun FirstLoadList(text: String, modifier: Modifier = Modifier, rows: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().clipToBounds()) {
        Text(
            text,
            Modifier.padding(top = 12.dp, bottom = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = rows)
    }
}

/** One skeleton row of a segmented list: [index] of [count] picks the group's corner shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SkeletonRow(index: Int, count: Int, height: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(height)
            .background(
                MaterialTheme.colorScheme.surfaceContainer, ListItemDefaults.segmentedShapes(index, count).shape,
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A title bar and, optionally, a shorter supporting bar; [index] alternates their lengths. */
@Composable
internal fun SkeletonLines(index: Int, supportingLine: Boolean = true, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(if (index % 2 == 0) 116.dp else 144.dp, 12.dp)
        if (supportingLine) SkeletonBlock(if (index % 2 == 0) 164.dp else 128.dp, 10.dp)
    }
}

/** A placeholder block: a logo, a thumbnail or a bar of text. */
@Composable
internal fun SkeletonBlock(width: Dp, height: Dp, shape: Shape = RoundedCornerShape(6.dp)) {
    Spacer(Modifier.width(width).height(height).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape))
}

/**
 * Channels' offline line above saved content: the phone says the server is unreachable over
 * [OfflineTexts.saved] with a settings icon; the tablet says [OfflineTexts.offline] and that it is
 * reconnecting, in [retryIn] when the countdown is known, with a settings button.
 */
@Composable
internal fun OfflineBanner(
    compact: Boolean,
    texts: OfflineTexts,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    retryIn: String? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.channels_cloud_off), null)
        if (compact) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(stringResource(R.string.channels_unreachable), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(texts.saved), style = MaterialTheme.typography.bodySmall)
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(
                        painterResource(R.drawable.channels_settings),
                        stringResource(R.string.channels_connection_settings),
                    )
                }
            }
        } else {
            FlowRow(Modifier.weight(1f).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(texts.offline), style = MaterialTheme.typography.bodySmall)
                val reconnect = if (retryIn == null) {
                    stringResource(R.string.channels_reconnecting)
                } else {
                    stringResource(R.string.channels_reconnect_in, retryIn)
                }
                Text(
                    reconnect, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onSettings != null) {
                TextButton(onClick = onSettings) { Text(stringResource(R.string.channels_connection_settings)) }
            }
        }
    }
}

/** What an [OfflineBanner] says the list shows: the tablet's whole line and the phone's second line. */
internal data class OfflineTexts(@param:StringRes val offline: Int, @param:StringRes val saved: Int)
