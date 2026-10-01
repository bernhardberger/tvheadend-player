package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.PlayerBarDistance
import at.bernhardberger.tvhplayer.core.PlayerBarEnd
import at.bernhardberger.tvhplayer.core.PlayerEndAnnouncement
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.hiddenStatusText
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineRowHeight
import at.bernhardberger.tvhplayer.ui.TvRecordingColor

/**
 * What the bar row shows around the bar, the state cell before it and the end after it, and a live
 * end's distance behind live above it. The distance ticks, so it travels apart from [end] as a
 * deferred read that only [PlayerLiveDistance] takes: a tick recomposes that leaf and nothing above it.
 */
data class PlayerBarStatus(
    val state: PlayerStateCell,
    /** The end without the distance; see [PlayerBarEnd.withoutDistance]. */
    val end: PlayerBarEnd?,
    val distance: State<PlayerBarDistance>,
)

/** The bar row for [state] and the complete [end]; equal from tick to tick while only its distance changes. */
@Composable
internal fun rememberPlayerBarStatus(state: PlayerStateCell, end: PlayerBarEnd?): PlayerBarStatus {
    val distance = rememberUpdatedState(end?.distance ?: PlayerBarDistance())
    return PlayerBarStatus(state, end?.withoutDistance(), distance)
}

/** The state cell is as tall and wide as a text line's glyph, so it never shifts the row. */
@Composable
@ReadOnlyComposable
internal fun playerStateCellSize(): Dp = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.fontSize.toDp() }

@Composable
private fun stateAnnouncement(state: PlayerStateCell): String = stringResource(when (state) {
    PlayerStateCell.PLAYING -> R.string.player_playing
    PlayerStateCell.PAUSED -> R.string.player_state_paused
})

/** The passive playback-intent glyph: ▶ or ❚❚. */
@Composable
internal fun PlayerStateGlyph(state: PlayerStateCell, size: Dp, color: Color) {
    when (state) {
        PlayerStateCell.PLAYING -> Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(size), tint = color)
        PlayerStateCell.PAUSED -> Icon(painterResource(R.drawable.ic_pause), null, Modifier.size(size), tint = color)
    }
}

/**
 * What the bar row keeps of the state cell before the bar. The Banner has the whole cell; the
 * controls hand its room to the bar, animated with the Banner taking them over.
 * [announces] is false where the cell no longer speaks for the state.
 */
internal class StateCellInset(val fraction: () -> Float, val announces: Boolean)

internal val LocalStateCellInset = compositionLocalOf { StateCellInset({ 1f }, announces = true) }

/**
 * The passive state cell at the start of the bar row: playing or paused.
 * Never focusable. It announces playback-intent changes politely,
 * unless it only fades out of the controls ([announces] false).
 */
@Composable
internal fun PlayerStateCellView(state: PlayerStateCell, modifier: Modifier = Modifier, announces: Boolean = true) {
    val description = stateAnnouncement(state)
    Box(
        modifier.then(if (announces) Modifier.testTag("player-state") else Modifier).clearAndSetSemantics {
            if (announces) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
        },
    ) { PlayerStateGlyph(state, playerStateCellSize(), MaterialTheme.colorScheme.onSurface) }
}

/**
 * The end after the bar: the programme's end clock (`21:45`) or the recording's length (`1:30:00`),
 * end-aligned in the row's fixed box at the content's end edge.
 */
@Composable
internal fun PlayerBarEndClock(end: PlayerBarEnd, emphasis: State<Float>, modifier: Modifier = Modifier) {
    val text = end.end ?: return
    Text(
        text, style = timelineLabelStyle(), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.graphicsLayer { alpha = emphasis.value }.testTag("player-end-clock"),
    )
}

/**
 * How far live TV or a growing recording plays behind its live end, `3:23 behind live`, in the
 * colour of the bar's timeshift segment, also while a step's readout shows the target. Nothing is
 * drawn at the live edge or while the distance is unknown; Live's owner holds it through timing
 * gaps. The caller sizes the box, which is there in every state: end-aligned tabular digits never
 * move anything, and the state (live or behind live) is announced politely from it with nothing
 * drawn as well. Only this leaf reads [distance]; the ticking distance is never announced.
 */
@Composable
internal fun PlayerLiveDistance(
    end: PlayerBarEnd,
    distance: State<PlayerBarDistance>,
    modifier: Modifier = Modifier,
    /** For the drawn text alone, such as hiding it under the step readout; the box keeps announcing. */
    textModifier: Modifier = Modifier,
) {
    val current = end.withDistance(distance.value)
    val text = current.distanceMs?.let { stringResource(R.string.timeshift_behind_live, formatPlaybackDuration(it)) }
    val spoken = current.announcement?.let {
        stringResource(if (it == PlayerEndAnnouncement.LIVE) R.string.player_live else R.string.player_behind)
    }
    Box(
        modifier.testTag("player-live-state").clearAndSetSemantics {
            if (spoken != null) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            }
        },
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (text != null) Text(
            text, style = timelineLabelStyle(), color = MaterialTheme.colorScheme.tertiary, maxLines = 1,
            softWrap = false, overflow = TextOverflow.Ellipsis, modifier = textModifier.testTag("player-distance"),
        )
    }
}

/**
 * Bottom-start position of the hidden chrome's chip: the state cell's place in the Banner's bar row,
 * whose block rests on the bottom safe area with its own vertical padding.
 */
@Composable
@ReadOnlyComposable
internal fun playerHiddenChipPadding(): PaddingValues {
    val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() }
    val row = maxOf(TvOverlayTimelineRowHeight, lineHeight)
    return PaddingValues(
        start = TvOverlaySidePadding - HiddenChipPadding,
        bottom = TvOverlayBottomPadding + 8.dp + (row - PlayerChromeTokens.chipHeight).coerceAtLeast(0.dp) / 2,
    )
}

private val HiddenChipPadding = 8.dp

/**
 * With the chrome hidden: while paused the chip `❚❚ −3:23` (`❚❚ Live` at the live edge),
 * on a subtle scrim so it reads over video. Nothing shows while playing.
 */
@Composable
internal fun PlayerHiddenStatusChip(state: PlayerStateCell, end: PlayerBarEnd?, modifier: Modifier = Modifier) {
    val current = state.takeUnless { it == PlayerStateCell.PLAYING }
    val shown = rememberLastShown(current)
    val liveLabel = stringResource(R.string.timeshift_live)
    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
        modifier = Modifier.semanticsWhileShown(current != null).then(modifier),
    ) {
        shown?.let {
            val text = hiddenStatusText(it, end, liveLabel)
            val description = stateAnnouncement(it)
            Row(
                Modifier.testTag("player-hidden-slot").clearAndSetSemantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                }.background(Color.Black.copy(alpha = 0.56f), PlayerChromeTokens.chipShape)
                    .heightIn(min = PlayerChromeTokens.chipHeight).padding(horizontal = HiddenChipPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerStateGlyph(it, playerStateCellSize(), MaterialTheme.colorScheme.onSurface)
                if (text != null) Text(
                    text, style = timelineLabelStyle(), maxLines = 1,
                    softWrap = false, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("player-hidden-slot-text"),
                )
            }
        }
    }
}

/** `● REC` in the badge row while the watched programme or recording is being recorded now. */
@Composable
internal fun PlayerRecBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.player_recording)
    Row(
        modifier.testTag("player-rec-badge").border(1.dp, TvRecordingColor.copy(alpha = 0.8f), PlayerChromeTokens.badgeShape)
            .height(PlayerChromeTokens.badgeHeight).padding(horizontal = 5.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(TvRecordingColor, androidx.compose.foundation.shape.CircleShape))
        Text(stringResource(R.string.player_status_rec), style = MaterialTheme.typography.labelMedium, maxLines = 1,
            softWrap = false, color = TvRecordingColor)
    }
}
