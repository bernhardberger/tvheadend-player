@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingMarkStop
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.presentation.R as SharedR

@Composable
internal fun RecordingClock(state: RecordingPlayerState, preview: Long?, modifier: Modifier) {
    ClockText(formatPlaybackDuration(preview ?: state.positionMs.value), modifier)
}

@Composable
internal fun RecordingPositionLine(
    state: RecordingPlayerState,
    preview: Long?,
    layout: PlayerLayout,
    onAction: (PlayerAction) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (state.durationMs > 0) {
            val position = formatPlaybackDuration(preview ?: state.positionMs.value)
            val length = formatPlaybackDuration(state.durationMs)
            Text(
                stringResource(R.string.player_recording_position, position, length),
                style = MaterialTheme.typography.labelLarge, color = Color.White,
            )
        }
        Spacer(Modifier.weight(1f))
        RecordingAdAction(state, preview != null, onChoice)
        if (layout == PlayerLayout.Phone && preview == null) FullScreenButton(state.offered, onAction)
    }
}

@Composable
internal fun RecordingAdAction(
    state: RecordingPlayerState,
    dragging: Boolean,
    onChoice: (PlayerChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offer = currentAdOffer(state) ?: return
    Box(modifier) { SkipAdButton(offer, dragging, bare = false, onChoice) }
}

@Composable
internal fun BareAdOffer(
    state: RecordingPlayerState,
    visible: Boolean,
    onChoice: (PlayerChoice) -> Unit,
    modifier: Modifier,
) {
    val offer = currentAdOffer(state)
    var dismissed by remember(state.key, offer) { mutableStateOf(false) }
    if (offer == null || !visible || dismissed) return
    if (state.adBreakMode != AdBreakMode.ASK || !state.askOnEntry) return
    Row(modifier.testTag("recording-ad-prompt"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PictureOfferButton(SharedR.string.server_keep_watching, null, false, {
            dismissed = true
            onChoice(PlayerChoice.KeepWatching(offer))
        }, bare = true)
        SkipAdButton(offer, false, bare = true) {
            dismissed = true
            onChoice(it)
        }
    }
}

@Composable
private fun currentAdOffer(state: RecordingPlayerState): RecordingMark? {
    if (state.status != LivePlayerStatus.Playing || PlayerControl.Timeshift !in state.offered) return null
    val offer by remember(state.marks, state.positionMs, state.adBreakMode) {
        derivedStateOf { recordingAdOffer(state.marks, state.positionMs.value, state.adBreakMode) }
    }
    return offer
}

@Composable
private fun SkipAdButton(offer: RecordingMark, dragging: Boolean, bare: Boolean, onChoice: (PlayerChoice) -> Unit) {
    PictureOfferButton(SharedR.string.skip_ad_break, R.drawable.player_skip_ad, dragging, {
        onChoice(PlayerChoice.SkipAdBreak(offer))
    }, bare)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InlineRecordingMarkers(state: RecordingPlayerState, onSeek: (Long) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.surfaceContainer,
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            BottomSheetDefaults.DragHandle(
                Modifier.align(Alignment.CenterHorizontally).height(16.dp)
                    .wrapContentHeight(unbounded = true).clearAndSetSemantics {},
                color = colors.onSurfaceVariant.copy(alpha = .55f),
            )
            Text(
                stringResource(R.string.player_markers),
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            RecordingMarkerRows(state, onSeek, Modifier.weight(1f), inline = true)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecordingMarkerRows(
    state: RecordingPlayerState,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    inline: Boolean = false,
) {
    val stops = state.stops
    val passed by remember(stops, state.positionMs) {
        derivedStateOf { stops.indexOfLast { it.positionMs <= state.positionMs.value } }
    }
    // Keep a little context above playback on upright; a sheet initially shows the beginning.
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = if (inline) (passed - 3).coerceAtLeast(0) else 0)
    LazyColumn(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("recording-markers"),
        state = scroll,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(stops, key = { _, stop -> stop.positionMs }) { index, stop ->
            val name = recordingMarkName(stop) ?: stringResource(R.string.player_marker_number, index)
            val time = formatPlaybackDuration(stop.positionMs)
            ListItem(
                selected = passed == index,
                onClick = { onSeek(stop.positionMs) },
                enabled = state.seekable && PlayerControl.Timeshift in state.offered,
                modifier = Modifier.testTag("recording-marker-${stop.positionMs}"),
                leadingContent = {
                    Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                        stop.mark?.let { RecordingSymbol(it.kind) }
                    }
                },
                trailingContent = { Text(time, style = MaterialTheme.typography.bodyMedium) },
                shapes = ListItemDefaults.segmentedShapes(index, stops.size),
                colors = ListItemDefaults.segmentedColors(),
            ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
internal fun RecordingInfo(state: RecordingPlayerState) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(state.title, style = MaterialTheme.typography.titleLarge)
        state.episode?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(recordedDate(state), style = MaterialTheme.typography.bodyMedium)
        state.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun recordedDate(state: RecordingPlayerState): String = state.endsAt?.let {
    stringResource(SharedR.string.player_recorded_ends, state.recordedOn, it)
} ?: stringResource(SharedR.string.player_recorded_on, state.recordedOn)

@Composable
internal fun recordingMarkName(stop: RecordingMarkStop): String? = when (stop.mark?.kind) {
    RecordingMarkKind.AD_BREAK -> stringResource(
        if (stop.atEnd) SharedR.string.recording_mark_ads_end else SharedR.string.recording_mark_ad_break,
    )
    RecordingMarkKind.CUT ->
        stringResource(if (stop.atEnd) SharedR.string.recording_mark_cut_ends else SharedR.string.recording_mark_cut)
    RecordingMarkKind.PROGRAM_START -> stringResource(SharedR.string.recording_mark_program_starts)
    RecordingMarkKind.PROGRAM_END -> stringResource(SharedR.string.recording_mark_program_ends)
    RecordingMarkKind.CHAPTER -> stop.mark?.name?.takeIf { it.isNotBlank() }
    null -> if (stop.positionMs == 0L) stringResource(SharedR.string.recording_marker_start) else null
}

/** TV symbols: on video these convey kind, not a count or button. */
@Composable
internal fun RecordingSymbol(kind: RecordingMarkKind, tint: Color = LocalContentColor.current) {
    when (kind) {
        RecordingMarkKind.CHAPTER -> Spacer(Modifier.size(5.dp).background(tint, CircleShape))
        RecordingMarkKind.AD_BREAK -> Surface(
            color = Color.Transparent, contentColor = tint, border = BorderStroke(1.dp, tint),
            shape = RoundedCornerShape(3.dp),
        ) {
            Text(
                stringResource(SharedR.string.recording_mark_ad_badge), Modifier.padding(horizontal = 3.dp),
                style = MaterialTheme.typography.labelSmall, maxLines = 1,
            )
        }
        else -> {
            val icon = when (kind) {
                RecordingMarkKind.CUT -> R.drawable.player_marker_cut
                RecordingMarkKind.PROGRAM_START -> R.drawable.player_marker_start
                else -> R.drawable.player_marker_end
            }
            Icon(painterResource(icon), null, Modifier.size(16.dp), tint)
        }
    }
}
