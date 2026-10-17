package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.components.RowChangeMs
import at.bernhardberger.tvhplayer.ui.components.RowChangeMotion
import at.bernhardberger.tvhplayer.ui.components.RowDimAlpha
import at.bernhardberger.tvhplayer.ui.components.RowHeaderQuietAlpha
import at.bernhardberger.tvhplayer.ui.components.rowEmphasis

/** How much of the next row shows below the focused one: its quiet title and the top of its cards. */
internal val PlayerChannelRowPeek = 52.dp

/** From a row's title to its cards' room, as for the focused channel row. */
private val PlayerChannelRowTitleGap = 12.dp

/** Room above and below the cards for their focus scale and border. */
internal val ChannelCardRoom = 8.dp

/** Swallows the rest of a key whose press moved focus to another row. */
private fun Modifier.relocatedKeys(state: PlayerChannelBrowserState) = onPreviewKeyEvent { event ->
    if (event.key != state.relocatingKey) false else {
        if (event.type == KeyEventType.KeyUp) state.relocatingKey = null
        true
    }
}

/**
 * Where Up goes from the rail's focused row: back to the TV from the first row, to the first row
 * from below it. It changes with the rows.
 */
@Composable
internal fun PlayerChannelBrowserHint(
    state: PlayerChannelBrowserState,
    hasRecent: Boolean,
    channelsTitle: String,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(hasRecent && state.row == PlayerChannelRow.RECENT, modifier, label = "player-channel-hint",
        contentAlignment = Alignment.Center,
        transitionSpec = {
            (fadeIn(RowChangeMotion) togetherWith
                fadeOut(tween(RowChangeMs / 2, easing = PlayerMotion.StandardAccelerate)))
                .using(SizeTransform(clip = false))
        }) { belowFirst ->
        PlayerDownHint(if (belowFirst) channelsTitle else stringResource(R.string.details_back_to_tv), up = true)
    }
}

/**
 * The rail's rows: the active channel group, and below it Recent channels once there is one. As in the channel
 * rows, the focused row reads at full strength in one place while the others keep a quiet title
 * and dimmed cards; the next row peeks below and runs off the screen. Up from the first row
 * closes the rail.
 */
@Composable
internal fun PlayerChannelBrowser(
    state: PlayerChannelBrowserState,
    channels: List<Channel>,
    recentChannels: List<Channel>,
    playingChannelId: ChannelId?,
    recordingChannelIds: Set<ChannelId>,
    nowEvent: (ChannelId) -> EpgEvent?,
    nowSec: Long,
    currentSession: CurrentSessionObservation?,
    active: Boolean,
    onFocusChannel: (ChannelId) -> Unit,
    onPickChannel: (Channel) -> Unit,
    onOpenDetails: (Channel, EpgEvent, Int?) -> Unit,
    onClose: (Int?) -> Unit,
    /** The first row's title: the active channel group, or All channels. */
    channelsTitle: String = stringResource(R.string.all_channels),
) {
    var activationKeyCode by remember(state) { mutableStateOf<Int?>(null) }
    val pageActive = LocalPlayerPageActive.current
    LaunchedEffect(state, active, pageActive) {
        // A hold that ended while the rail was away left no release behind.
        state.scrolling = false
        state.relocatingKey = null
    }
    val rows = if (recentChannels.isEmpty()) listOf(PlayerChannelRow.ON_NOW) else PlayerChannelRow.entries
    LaunchedEffect(state, rows) {
        // A row that went away does not come back focused when it returns.
        if (state.row !in rows) state.row = PlayerChannelRow.ON_NOW
    }
    val current = state.row.takeIf { it in rows } ?: PlayerChannelRow.ON_NOW
    val currentRows by rememberUpdatedState(rows)
    // Read when called: a retired card's retained click must not act for the row that left.
    fun owns(row: PlayerChannelRow) = row == (state.row.takeIf { it in currentRows } ?: PlayerChannelRow.ON_NOW)
    val position by animateFloatAsState(rows.indexOf(current).toFloat(),
        RowChangeMotion, label = "player-channel-row")
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val cardHeight = with(density) { 94.dp + typography.titleMedium.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp() }
    val rowHeight = with(density) { typography.titleMedium.lineHeight.toDp() } + PlayerChannelRowTitleGap + cardHeight
    val rowPx = with(density) { rowHeight.toPx() }
    // The other rows' titles read at titleSmall's size.
    val quietScale = typography.titleSmall.fontSize.value / typography.titleMedium.fontSize.value
    // Holds focus while the rows trade places. A row that stops owning input with a focused card
    // would hand focus along its other cards and remember the wrong one. Focusable only for the
    // change, so Left from a first card cannot land on it.
    val handover = remember { FocusRequester() }
    var handingOver by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()
        // Runs off the screen's bottom edge; the page may leave less room than it asks for.
        .height(if (rows.size > 1) rowHeight + PlayerChannelRowPeek else rowHeight)
        // The previous row slides out under the preview rather than across it.
        .clipToBounds()
        .testTag("player-channel-browser")
        .relocatedKeys(state)
        .onPreviewKeyEvent { event ->
            if (event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                if (event.type == KeyEventType.KeyDown) activationKeyCode = event.nativeKeyEvent.keyCode
                else if (event.type == KeyEventType.KeyUp && activationKeyCode == event.nativeKeyEvent.keyCode) activationKeyCode = null
            }
            if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                state.scrolling = event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0
            }
            val step = when (event.key) {
                Key.DirectionDown -> 1
                Key.DirectionUp -> -1
                else -> 0
            }
            // Up from the first row is the cards' own: it closes the rail.
            if (step == 0 || step < 0 && current == rows.first()) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                rows.getOrNull(rows.indexOf(current) + step)?.let {
                    handingOver = true
                    handover.requestFocus()
                    state.row = it
                    state.relocatingKey = event.key
                }
            }
            true
        }) {
        Box(Modifier.focusRequester(handover)
            .onFocusChanged { if (!it.isFocused) handingOver = false }
            .focusProperties { canFocus = handingOver && active && pageActive }
            .focusTarget())
        rows.forEachIndexed { index, row ->
            key(row) {
                val emphasis = { rowEmphasis(index - position) }
                Column(Modifier.fillMaxWidth()
                    .graphicsLayer {
                        // The focused row keeps one place, the same distance below the preview.
                        translationY = (index - position) * rowPx
                        // The preview owns the room above: the previous row is gone halfway up.
                        alpha = (1f + 2f * (index - position)).coerceIn(0f, 1f)
                    }
                    .testTag("player-channel-row-${row.name}")) {
                    Text(if (row == PlayerChannelRow.ON_NOW) channelsTitle else stringResource(R.string.player_row_recent),
                        Modifier.padding(horizontal = TvGridHorizontalMargin)
                            // The row above the focused one is invisible, so its title leaves the accessibility tree too.
                            .then(if (index < rows.indexOf(current)) Modifier.clearAndSetSemantics { }
                                else Modifier.semantics { heading() })
                            .graphicsLayer {
                                val e = emphasis()
                                scaleX = lerp(quietScale, 1f, e)
                                scaleY = scaleX
                                transformOrigin = TransformOrigin(0f, 1f)
                                alpha = lerp(RowHeaderQuietAlpha, 1f, e)
                            },
                        style = typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(PlayerChannelRowTitleGap))
                    Box(Modifier.height(cardHeight).graphicsLayer { alpha = lerp(RowDimAlpha, 1f, emphasis()) }) {
                        ChannelDrawer(
                            channels = if (row == PlayerChannelRow.ON_NOW) channels else recentChannels,
                            selectedId = state.collection(row).focusedId,
                            playingChannelId = playingChannelId,
                            recordingChannelIds = recordingChannelIds, nowEvent = nowEvent, nowSec = nowSec,
                            currentSession = currentSession,
                            active = active && row == current,
                            browserState = state.collection(row),
                            onFocusChannel = { if (owns(row)) onFocusChannel(it) },
                            onPickChannel = { if (owns(row)) onPickChannel(it) },
                            onLongClickChannel = { channel, event -> if (owns(row)) {
                                val heldKey = activationKeyCode
                                // The screen takes over this cycle, including its release while details own focus.
                                activationKeyCode = null
                                onOpenDetails(channel, event, heldKey)
                            } },
                            onCloseDrawer = { key -> if (owns(row)) onClose(key) },
                        )
                    }
                }
            }
        }
    }
}
