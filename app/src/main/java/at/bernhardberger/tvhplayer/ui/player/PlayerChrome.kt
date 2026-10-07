package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvGridColumnWidth
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvSpacing4
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * The player chrome of one screen: header, info bar, timeline and, with the controls, the action
 * row, in one container whose [mode] the screen derives ([playerChromeMode]). Screens supply data and
 * callbacks; layout, styling and motion live here.
 *
 * Revealed from the hidden player the Banner fades in, and the controls travel in or, on a zap
 * ([entry] FADE), fade in, both once their first frames are drawn. The controls taken over from the
 * Banner keep its header and info bar where they rest and raise them by the action row, which fades
 * in underneath. Hidden chrome is not composed.
 *
 * @param panelOpen a panel covers the chrome: the chrome leaves quickly and returns once it closes.
 * @param controlsDecoration hosts the controls, such as live's quick-zap rail; it receives the
 * controls' emphasis alpha. While [decorationCoversControls] the controls take no focus.
 */
@Composable
internal fun PlayerChrome(
    mode: PlayerChromeMode,
    content: PlayerChromeContent,
    timeline: PlayerChromeTimeline,
    actions: PlayerChromeActions,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    onTogglePause: () -> Unit,
    onSeek: (deltaMs: Long) -> Unit,
    onStop: () -> Unit,
    onInfo: () -> Unit,
    onOptions: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    entry: PlayerControlsEntry = PlayerControlsEntry.TRAVEL,
    panelOpen: Boolean = false,
    onCommitSeek: () -> Unit = {},
    onPauseUnavailable: (reason: String) -> Unit = {},
    onActionFocused: (String) -> Unit = {},
    onFocusRestored: () -> Unit = {},
    /** Down on the action row, which it always consumes. */
    onDownFromActions: () -> Unit = {},
    decorationCoversControls: Boolean = false,
    controlsDecoration: @Composable (emphasisAlpha: () -> Float, controls: @Composable () -> Unit) -> Unit =
        { _, controls -> controls() },
    /** A recording's marker navigation, opened from its timeline. */
    markerNavigation: RecordingMarkerNavigation = remember { RecordingMarkerNavigation() },
    onSeekMarker: (Long) -> Unit = {},
    /** Left/Right on live's focused top identity change channel (-1, +1). */
    onChannelZap: ((direction: Int, keyTimeMs: Long, repeat: Boolean) -> Unit)? = null,
    /** What Down from the action row opens, shown centred in the row. */
    downHint: String? = null,
    /** The recent channels, newest first: a row below live's top identity that OK opens; OK switches. */
    recents: List<RecentChannelPeek> = emptyList(),
    onRecentPick: ((Int) -> Unit)? = null,
    recentRowOpen: Boolean = false,
    onRecentRowOpenChange: (Boolean) -> Unit = {},
    /** Focus arrived on, or left, live's top identity or its recent row. */
    onChannelCardFocusChanged: (Boolean) -> Unit = {},
) {
    val liveBar = rememberLiveBarPresentation(timeline as? PlayerChromeTimeline.Live)
    PlayerControlsLayer(
        visible = mode != PlayerChromeMode.HIDDEN,
        modalVisible = panelOpen,
        modifier = modifier,
        // Taken when the chrome appears. Only the Banner hands over; revealed from the hidden
        // player, controls meant to take the Banner over travel in like any reveal.
        entry = when {
            mode.isBanner -> PlayerControlsEntry.FADE
            entry == PlayerControlsEntry.FROM_BANNER -> PlayerControlsEntry.TRAVEL
            else -> entry
        },
    ) {
        // Leaving, the chrome keeps what it showed.
        val shown = rememberLastShown(mode.takeUnless { it == PlayerChromeMode.HIDDEN }) ?: return@PlayerControlsLayer
        val handover = rememberBannerHandover(shown)
        val stateCell = rememberStateCellInset(shown)
        // Live sources keep the last info line clear of the behind-live label, even at the live edge.
        val liveEnd = when (timeline) {
            is PlayerChromeTimeline.Live -> true
            is PlayerChromeTimeline.Recording -> timeline.growing
        }
        val lastLineEndReserve = if (liveEnd) liveDistanceReserve() else 0.dp
        val header: @Composable (Modifier) -> Unit = { PlayerChromeHeader(content.clock, it.testTag("player-header")) }
        // One chrome for every source: the channel at the top, the info bar leading with the programme's
        // artwork, and the [timelineRow] the full width below it.
        val recentList = if (onRecentPick != null) recents else emptyList()
        val infoBar: @Composable (contentAlpha: () -> Float, timelineRow: @Composable () -> Unit) -> Unit =
            { contentAlpha, timelineRow ->
                // Two grid columns of artwork, the text from the third: title 28, 2, programme line 16,
                // 8, last row 16 is the artwork's 70dp at font scale 1.0; a larger scale grows the
                // text below the artwork's bottom.
                PlayerInfoBar(
                    data = content.info,
                    modifier = Modifier.testTag("player-info-bar"),
                    contentAlpha = contentAlpha,
                    lastLineEndReserve = lastLineEndReserve,
                ) { faded -> ProgrammeArtworkTile(content.artwork, imageLoader, currentSession, faded) }
                timelineRow()
            }
        CompositionLocalProvider(LocalStateCellInset provides stateCell) {
        if (shown == PlayerChromeMode.CONTROLS) {
            PlayerChromeControls(
                timeline = timeline,
                liveBar = liveBar,
                actions = actions,
                identity = content.info.channelNumber to content.info.channelName,
                header = header,
                infoBar = infoBar,
                state = content.state,
                bannerDrop = { 1f - handover.rise.value },
                actionsAlpha = { handover.actions.value },
                onTogglePause = onTogglePause,
                onSeek = onSeek,
                onStop = onStop,
                onOptions = onOptions,
                onInteraction = onInteraction,
                onCommitSeek = onCommitSeek,
                onPauseUnavailable = onPauseUnavailable,
                onActionFocused = onActionFocused,
                onFocusRestored = onFocusRestored,
                onDownFromActions = onDownFromActions,
                covered = decorationCoversControls,
                decoration = controlsDecoration,
                markerNavigation = markerNavigation,
                onSeekMarker = onSeekMarker,
                onChannelZap = onChannelZap,
                downHint = downHint,
                topIdentity = { placed, focus, onRecentDown ->
                    PlayerTopIdentity(content, imageLoader, currentSession, placed, focus = focus,
                        zaps = onChannelZap != null,
                        // OK opens the recent channels, and has nothing to open without any; without a
                        // recent pick at all (a recording) it opens the programme's info.
                        onClick = {
                            onInteraction()
                            if (onRecentPick == null) onInfo() else if (recentList.isNotEmpty()) onRecentRowOpenChange(true)
                        },
                        recents = recentList,
                        recentOpen = recentRowOpen,
                        onRecentOpenChange = onRecentRowOpenChange,
                        onRecentClick = { onInteraction(); onRecentPick?.invoke(it) },
                        onRecentInteraction = onInteraction,
                        onRecentDown = onRecentDown,
                        onFocusWithinChanged = onChannelCardFocusChanged)
                },
            )
        } else {
            // Passive: nothing in the Banner takes focus. Without the action row its info and
            // timeline rest on the bottom safe area, one action row lower than the controls'.
            PlayerOverlayChrome(
                headerContent = { placed ->
                    header(placed)
                    PlayerTopIdentity(content, imageLoader, currentSession, placed)
                },
                footerPadding = PaddingValues(start = TvGridHorizontalMargin, end = TvGridHorizontalMargin,
                    top = TvOverlayFooterGradientRunout, bottom = PlayerBannerBottomPadding),
                modifier = Modifier.focusProperties { canFocus = false }.testTag("player-banner"),
            ) {
                infoBar({ 1f }) {
                when (timeline) {
                    is PlayerChromeTimeline.Live -> {
                        val step = timeline.step
                        // A quick step's preview keeps the programme in the info bar, so it is not repeated.
                        if (shown == PlayerChromeMode.BANNER_STEP && step != null) TimeshiftSeekPreviewTimeline(
                            state = timeline.committedTimeshift,
                            decision = step,
                            programmeWindow = timeline.programmeWindow,
                            feedback = timeline.feedback,
                            feedbackIsError = timeline.feedbackIsError,
                            titleAsFeedback = false,
                            // The readout shows the target; the distance stays where playback is.
                            barStatus = timeline.barStatus(content.state, window = timeline.programmeWindow),
                            nowSec = timeline.nowSec,
                        ) else PlayerPresentedLiveTimeline(requireNotNull(liveBar), content.state,
                            feedback = timeline.feedback, feedbackIsError = timeline.feedbackIsError)
                    }
                    is PlayerChromeTimeline.Recording ->
                        RecordingBannerTimeline(timeline, step = shown == PlayerChromeMode.BANNER_STEP, state = content.state)
                }
                }
            }
        }
        }
    }
}

/** Progress, 0 to 1, of the controls taking the Banner over: the footer's rise and the actions' fade. */
private class BannerHandover(val rise: State<Float>, val actions: State<Float>)

private class ModeHistory(var previous: PlayerChromeMode, var handovers: Int = 0)

/** Each change from the Banner to the controls starts a handover once its first frames are drawn. */
@Composable
private fun rememberBannerHandover(mode: PlayerChromeMode): BannerHandover {
    val history = remember { ModeHistory(mode) }
    if (history.previous != mode) {
        if (history.previous.isBanner && mode == PlayerChromeMode.CONTROLS) history.handovers++
        history.previous = mode
    }
    val handovers = history.handovers
    val rise = remember(handovers) { Animatable(if (handovers == 0) 1f else 0f) }
    val actions = remember(handovers) { Animatable(if (handovers == 0) 1f else 0f) }
    LaunchedEffect(handovers) {
        if (handovers == 0) return@LaunchedEffect
        // The controls' first composition on a slow TV must not use the motion up.
        withFrameNanos { }
        withFrameNanos { }
        launch { rise.animateTo(1f, tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard)) }
        actions.animateTo(1f, tween(PlayerMotion.ShortMs, delayMillis = PlayerMotion.FastMs, easing = PlayerMotion.Standard))
    }
    return remember(rise, actions) { BannerHandover(rise.asState(), actions.asState()) }
}

/**
 * How much of the state cell the bar row keeps: the whole cell in the Banner, none in the controls,
 * It moves with the Banner and the controls taking
 * each other over, in the footer's rise timing, and only then.
 */
@Composable
private fun rememberStateCellInset(mode: PlayerChromeMode): StateCellInset {
    val controls = mode == PlayerChromeMode.CONTROLS
    val fraction = remember { Animatable(if (controls) 0f else 1f) }
    LaunchedEffect(controls) {
        val target = if (controls) 0f else 1f
        if (fraction.value == target) return@LaunchedEffect
        // The new layout's first frames must not use the motion up.
        withFrameNanos { }
        withFrameNanos { }
        fraction.animateTo(target, tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard))
    }
    return remember(controls, fraction) { StateCellInset({ fraction.value }, announces = !controls) }
}

/** Passive geometry for the Banner and for controls without current seek coordinates. */
@Composable
internal fun PlayerPresentedLiveTimeline(
    presentation: LiveBarPresentation,
    state: PlayerStateCell,
    feedback: String?,
    feedbackIsError: Boolean,
    modifier: Modifier = Modifier,
) {
    PlayerTimelineBlock(
        progress = presentation.progress,
        tone = if (presentation.availableStart != null) PlayerTimelineTone.INTERACTIVE else PlayerTimelineTone.AMBIENT,
        collapsed = false,
        fillColor = if (presentation.availableStart != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        showTrack = presentation.showTrack,
        motionKey = presentation.motionKey,
        leadingLabel = presentation.startLabel,
        status = rememberPlayerBarStatus(state, presentation.end).takeIf { presentation.end != null },
        programmeWindow = presentation.window,
        rewindableStartFraction = presentation.availableStart,
        liveEdgeFraction = presentation.liveEdge,
        reserveLabelSpace = true,
        progressSemantics = false,
        feedback = feedback,
        feedbackIsError = feedbackIsError,
        timelineModifier = modifier,
    )
}

/**
 * The info bar's programme artwork: the programme's 16:9 image, or without it [ProgrammeArtworkPlaceholder].
 * It takes no focus and has no description; the info bar speaks for the programme.
 */
@Composable
private fun ProgrammeArtworkTile(
    artwork: String?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier,
) {
    val id = ArtworkId.parse(artwork)
    var failed by remember(id, currentSession) { mutableStateOf(false) }
    Box(modifier.size(ArtworkTileWidth, ArtworkTileHeight).clip(ArtworkTileShape)
        .focusProperties { canFocus = false }.testTag("player-info-artwork-tile")) {
        if (id != null && currentSession != null && !failed) {
            Box(Modifier.fillMaxSize().background(TvSurfaceColors.containerLow))
            AsyncImage(AppArtworkSource(currentSession, id), null, imageLoader,
                Modifier.fillMaxSize().testTag("player-info-artwork"), contentScale = ContentScale.Crop, onError = { failed = true })
        } else ProgrammeArtworkPlaceholder()
    }
}

/** Trial: what the artwork tile shows without artwork, a plain tile until that is decided. */
@Composable
private fun ProgrammeArtworkPlaceholder() {
    Box(Modifier.fillMaxSize().background(TvSurfaceColors.containerLow).testTag("player-info-artwork-placeholder"))
}

/** The artwork slot: two grid columns at 16:9, from the title's top to the last row's bottom at font scale 1.0. */
private val ArtworkTileWidth = TvGridColumnWidth * 2 + TvGridGutter
private val ArtworkTileHeight = 70.dp
private val ArtworkTileShape = RoundedCornerShape(8.dp)
