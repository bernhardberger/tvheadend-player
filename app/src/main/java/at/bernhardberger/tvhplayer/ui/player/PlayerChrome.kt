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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvSpacing4
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
 * action row's emphasis alpha. While [decorationCoversControls] the controls take no focus.
 */
@Composable
internal fun PlayerChrome(
    mode: PlayerChromeMode,
    content: PlayerChromeContent,
    timeline: PlayerChromeTimeline,
    actions: PlayerChromeActions,
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
    onPauseUnavailable: () -> Unit = {},
    onActionFocused: (String) -> Unit = {},
    /** Opens the Audio or Subtitles quick list from its button, shown per [PlayerChromeActions.tracks]. */
    onTrackList: (PlaybackOptionsPage) -> Unit = {},
    onSchedule: (() -> Unit)? = null,
    scheduleAvailable: Boolean = true,
    /** Any control that takes focus, the timeline included: where focus returns after number entry. */
    onControlFocused: (String) -> Unit = {},
    onFocusRestored: () -> Unit = {},
    /** Down on the action row, which it always consumes. */
    onDownFromActions: () -> Unit = {},
    decorationCoversControls: Boolean = false,
    controlsDecoration: @Composable (emphasisAlpha: () -> Float, controls: @Composable () -> Unit) -> Unit =
        { _, controls -> controls() },
    /** A recording's marker navigation, opened from its timeline. */
    markerNavigation: RecordingMarkerNavigation = remember { RecordingMarkerNavigation() },
    onSeekMarker: (Long) -> Unit = {},
    /** Retained trial caller input; the passive top identity never handles channel gestures. */
    onChannelZap: ((direction: Int, keyTimeMs: Long, repeat: Boolean) -> Unit)? = null,
    /** What Down from the action row opens, shown centred in the row. */
    downHint: String? = null,
    /** Retained trial caller input; Recent is now hosted by the bottom browser. */
    recents: List<RecentChannelPeek> = emptyList(),
    onRecentPick: ((Int) -> Unit)? = null,
    recentRowOpen: Boolean = false,
    onRecentRowOpenChange: (Boolean) -> Unit = {},
    /** Retained trial caller input; the passive top identity cannot take focus. */
    onChannelCardFocusChanged: (Boolean) -> Unit = {},
    /**
     * The channel number entry stands in the top identity's place, which gives way to it; the
     * controls kept up under the entry take no focus.
     */
    numberEntryShown: Boolean = false,
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
        val infoBar: @Composable (contentAlpha: () -> Float, programmeFocus: Modifier, timelineRow: @Composable () -> Unit) -> Unit =
            { contentAlpha, programmeFocus, timelineRow ->
                // The artwork card, then the text from its top: now (title, time and metadata, subtitle)
                // at its top edge, Next on its bottom edge at font scale 1.0.
                PlayerInfoBar(
                    data = content.info,
                    modifier = Modifier.testTag("player-info-bar"),
                    contentAlpha = contentAlpha,
                    lastLineEndReserve = lastLineEndReserve,
                ) { faded ->
                    ProgrammeArtworkTile(content.artwork, content.picon, content.channelId,
                        content.info.channelNumber, faded)
                }
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
                onInfo = onInfo,
                onSchedule = onSchedule,
                scheduleAvailable = scheduleAvailable,
                onOptions = onOptions,
                onTrackList = onTrackList,
                onInteraction = onInteraction,
                onCommitSeek = onCommitSeek,
                onPauseUnavailable = onPauseUnavailable,
                onActionFocused = onActionFocused,
                onControlFocused = onControlFocused,
                onFocusRestored = onFocusRestored,
                onDownFromActions = onDownFromActions,
                // Number entry owns input: the controls it keeps up take no focus.
                covered = decorationCoversControls || numberEntryShown,
                decoration = controlsDecoration,
                markerNavigation = markerNavigation,
                onSeekMarker = onSeekMarker,
                onChannelZap = onChannelZap,
                downHint = downHint,
                topIdentity = { placed, _, _ ->
                    PlayerTopIdentity(content, placed, numberEntryShown = numberEntryShown)
                },
            )
        } else {
            // Passive: nothing in the Banner takes focus. Without the action row its info and
            // timeline rest on the bottom safe area, one action row lower than the controls'.
            PlayerOverlayChrome(
                headerContent = { placed ->
                    header(placed)
                    PlayerTopIdentity(content, placed, numberEntryShown = numberEntryShown)
                },
                footerPadding = PaddingValues(start = TvGridHorizontalMargin, end = TvGridHorizontalMargin,
                    top = TvOverlayFooterGradientRunout, bottom = PlayerBannerBottomPadding),
                modifier = Modifier.focusProperties { canFocus = false }.testTag("player-banner"),
            ) {
                infoBar({ 1f }, Modifier) {
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
                            settled = timeline.stepSettled,
                            onAnnounced = timeline.onStepAnnounced,
                            stepId = timeline.stepId,
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
 * The info bar's programme artwork: the programme's 16:9 image, or without it the channel's [picon]
 * (or [channelNumber]) on the channel's own ground, as the details hero falls back to, so the bar
 * keeps its shape on every channel. It takes no focus and has no description; the info bar speaks
 * for the programme.
 */
@Composable
private fun ProgrammeArtworkTile(
    artwork: ArtworkId?,
    picon: ArtworkId?,
    channelId: ChannelId?,
    channelNumber: String,
    modifier: Modifier,
) {
    val id = artwork
    val currentSession = LocalArtworkContext.current.currentSession
    var failed by remember(id, currentSession) { mutableStateOf(false) }
    Box(modifier.size(ArtworkTileWidth, ArtworkTileHeight).clip(ArtworkTileShape)
        .focusProperties { canFocus = false }.testTag("player-info-artwork-tile")) {
        // Under the picture too, so loading shows the same channel ground as a missing picture.
        ChannelArtworkFallback(picon, channelId, channelNumber)
        if (id != null && currentSession != null && !failed) {
            AsyncImage(AppArtworkSource(currentSession, id), null,
                Modifier.fillMaxSize().testTag("player-info-artwork"), contentScale = ContentScale.Crop, onError = { failed = true })
        }
    }
}

/** The channel in the artwork's place: its picon, or its number, on [channelFallbackGround]. */
@Composable
private fun ChannelArtworkFallback(
    picon: ArtworkId?,
    channelId: ChannelId?,
    channelNumber: String,
) {
    val currentSession = LocalArtworkContext.current.currentSession
    Box(Modifier.fillMaxSize().background(channelFallbackGround(picon, channelId)).testTag("player-info-artwork-fallback"),
        contentAlignment = Alignment.Center) {
        if (picon != null && currentSession != null) {
            // On its own colour's gradient, so no ground to ink it for.
            PiconBox(picon, Modifier.padding(horizontal = 24.dp, vertical = 16.dp), ground = null)
        } else if (!AppChannelNumbers.shown) {
            PiconBox(null, Modifier.padding(horizontal = 24.dp, vertical = 16.dp), ground = null)
        } else Text(channelNumber, style = MaterialTheme.typography.headlineMedium, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The artwork slot: a 16:9 card three grid columns wide, as in every row, so the text starts on the fourth. */
private val ArtworkTileWidth = TvGridColumnWidth * 3 + TvGridGutter * 2
private val ArtworkTileHeight = 110.dp
private val ArtworkTileShape = RoundedCornerShape(8.dp)
