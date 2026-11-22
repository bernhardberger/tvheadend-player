package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.core.GlanceBadge

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.recordingSkipAction
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.playerStateCell
import at.bernhardberger.tvhplayer.core.recordingMarkStops
import kotlin.time.Instant

/** A finished recording's info bar data, as the recording screen derives it from its entry. */
@Composable
internal fun fixtureRecordingInfo(
    title: String = "Zeit im Bild",
    growing: Boolean = false,
    subtitle: String? = null,
    positionMs: Long = 1_800_000,
    durationMs: Long? = 3_600_000,
    nowSec: Long = 1_700_010_000,
    stopExtra: kotlin.time.Duration? = null,
    startSec: Long = 1_700_000_000,
    state: at.bernhardberger.tvheadend.sdk.core.DvrEntryState =
        if (growing) at.bernhardberger.tvheadend.sdk.core.DvrEntryState.RECORDING
        else at.bernhardberger.tvheadend.sdk.core.DvrEntryState.COMPLETED,
): PlayerInfoBarData =
    recordingInfoBarData(
        DvrEntry.create(
            id = DvrEntryId(1),
            title = title,
            subtitle = subtitle,
            channelName = "Documentary",
            start = Instant.fromEpochSeconds(startSec),
            stop = Instant.fromEpochSeconds(startSec + 3_600),
            stopExtraMinutes = stopExtra,
            state = state,
        ),
        channelNumber = 1,
        nowSec = nowSec,
        positionMs = positionMs,
        durationMs = durationMs,
        growing = growing,
    )

/** Unnamed chapters at [startsMs]: the marks row's stops are the start and these. */
internal fun chapterMarks(vararg startsMs: Long): List<RecordingMark> =
    startsMs.filter { it > 0 }.map { RecordingMark(RecordingMarkKind.CHAPTER, it) }

/** The production chrome over a recording, composed as the recording screen composes it. */
@Composable
internal fun RecordingChromeFixture(
    modifier: Modifier = Modifier,
    mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
    positionMs: Long = 1_800_000,
    durationMs: Long? = 3_600_000,
    targetMs: Long? = null,
    originMs: Long? = null,
    stepSettled: Boolean = false,
    stepId: Any? = null,
    growing: Boolean = false,
    canSeek: Boolean = true,
    paused: Boolean = false,
    active: Boolean = mode == PlayerChromeMode.CONTROLS,
    panelOpen: Boolean = false,
    restoreFocus: String? = null,
    buffering: Boolean = false,
    info: PlayerInfoBarData? = null,
    badges: List<GlanceBadge> = emptyList(),
    picon: ArtworkId? = null,
    artwork: ArtworkId? = null,
    marks: List<RecordingMark> = emptyList(),
    withoutChannel: Boolean = false,
    markerNavigation: RecordingMarkerNavigation = remember { RecordingMarkerNavigation() },
    markerRevision: Long = 0L,
    onSeekMarker: (Long) -> Unit = {},
    onSkip: (RecordingMark) -> Unit = {},
    onTogglePause: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onStop: () -> Unit = {},
    onInfo: () -> Unit = {},
    onOptions: () -> Unit = {},
    onCommitSeek: () -> Unit = {},
    onActionFocused: (String) -> Unit = {},
    onFocusRestored: () -> Unit = {},
    entry: PlayerControlsEntry = PlayerControlsEntry.TRAVEL,
    onBannerInfoBarBottomDistance: (Int) -> Unit = {},
) {
    val settings = PlayerSettings()
    val skip = recordingSkipAction(marks, positionMs, settings).takeIf { canSeek }
    Box(Modifier.fillMaxSize()) {
    PlayerChrome(
        mode = mode,
        content = PlayerChromeContent(
            clockSec = PlayerChromeClockSec,
            info = info ?: fixtureRecordingInfo(growing = growing, positionMs = positionMs, durationMs = durationMs),
            state = playerStateCell(paused),
            badges = badges,
            picon = picon?.let { ChannelIcon.ImageCache(it) },
            artwork = artwork?.let(ChannelIcon::ImageCache),
            withoutChannel = withoutChannel,
        ),
        timeline = PlayerChromeTimeline.Recording(
            positionMs = positionMs,
            durationMs = durationMs,
            growing = growing,
            canSeek = canSeek,
            targetMs = targetMs,
            originMs = originMs,
            stepSettled = stepSettled,
            stepId = stepId,
            marks = marks,
            markStops = durationMs?.let { recordingMarkStops(marks, it) }.orEmpty(),
            markerRevision = markerRevision,
        ),
        actions = PlayerChromeActions(
            active = active, paused = paused, restoreFocus = restoreFocus,
            skip = skip?.let { mark ->
                PlayerSkipAction(
                    mark,
                    stringResource(if (mark.kind == RecordingMarkKind.PROGRAM_START) R.string.skip_to_program_start else R.string.skip_ad_break),
                    { onSkip(mark) },
                )
            },
        ),
        onTogglePause = onTogglePause,
        onSeek = onSeek,
        onStop = onStop,
        onInfo = onInfo,
        onOptions = onOptions,
        onInteraction = {},
        onCommitSeek = onCommitSeek,
        onActionFocused = onActionFocused,
        onFocusRestored = onFocusRestored,
        entry = entry,
        panelOpen = panelOpen,
        markerNavigation = markerNavigation,
        onSeekMarker = onSeekMarker,
        onBannerInfoBarBottomDistance = onBannerInfoBarBottomDistance,
        modifier = modifier,
    )
    PlayerBusyIndicator(PlayerBusyStatus.BUFFERING.takeIf { buffering && !paused }, Modifier.align(Alignment.Center))
    }
}

/** What the top identity and its badges say, in the controls (the identity) or the Banner (the badges). */
internal fun identitySpeech(compose: androidx.compose.ui.test.SemanticsNodeInteractionsProvider): String =
    compose.onAllNodes(
        androidx.compose.ui.test.hasTestTag(PlayerTopIdentityTag) or
            androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(PlayerTopIdentityTag)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().flatMap {
        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }
    }.joinToString(". ")
