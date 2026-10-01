package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.core.playerStateCell
import coil3.ImageLoader
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
): PlayerInfoBarData =
    recordingInfoBarData(
        DvrEntry.create(
            id = DvrEntryId(1),
            title = title,
            subtitle = subtitle,
            channelName = "Documentary",
            start = Instant.fromEpochSeconds(1_700_000_000),
            stop = Instant.fromEpochSeconds(1_700_003_600),
            state = if (growing) at.bernhardberger.tvheadend.sdk.core.DvrEntryState.RECORDING
                else at.bernhardberger.tvheadend.sdk.core.DvrEntryState.COMPLETED,
        ),
        channelNumber = 1,
        nowSec = nowSec,
        positionMs = positionMs,
        durationMs = durationMs,
        growing = growing,
    )

/** The production chrome over a recording, composed as the recording screen composes it. */
@Composable
internal fun RecordingChromeFixture(
    modifier: Modifier = Modifier,
    mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
    positionMs: Long = 1_800_000,
    durationMs: Long? = 3_600_000,
    targetMs: Long? = null,
    originMs: Long? = null,
    growing: Boolean = false,
    canSeek: Boolean = true,
    paused: Boolean = false,
    active: Boolean = mode == PlayerChromeMode.CONTROLS,
    panelOpen: Boolean = false,
    restoreFocus: String? = null,
    buffering: Boolean = false,
    info: PlayerInfoBarData? = null,
    picon: ArtworkId? = null,
    markers: List<Long> = emptyList(),
    markerNavigation: RecordingMarkerNavigation = remember { RecordingMarkerNavigation() },
    markerRevision: Long = 0L,
    onSeekMarker: (Long) -> Unit = {},
    onTogglePause: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onStop: () -> Unit = {},
    onInfo: () -> Unit = {},
    onOptions: () -> Unit = {},
    onCommitSeek: () -> Unit = {},
    onActionFocused: (String) -> Unit = {},
    onFocusRestored: () -> Unit = {},
    entry: PlayerControlsEntry = PlayerControlsEntry.TRAVEL,
    imageLoader: ImageLoader = LocalContext.current.let { context -> remember { ImageLoader(context) } },
) {
    Box(Modifier.fillMaxSize()) {
    PlayerChrome(
        mode = mode,
        content = PlayerChromeContent(
            clock = "20:15",
            info = info ?: fixtureRecordingInfo(growing = growing, positionMs = positionMs, durationMs = durationMs),
            state = playerStateCell(paused),
            recordingNow = growing,
            picon = picon,
        ),
        timeline = PlayerChromeTimeline.Recording(
            positionMs = positionMs,
            durationMs = durationMs,
            growing = growing,
            canSeek = canSeek,
            targetMs = targetMs,
            originMs = originMs,
            markers = markers,
            markerRevision = markerRevision,
        ),
        actions = PlayerChromeActions(active = active, paused = paused, record = false, restoreFocus = restoreFocus),
        imageLoader = imageLoader,
        currentSession = null,
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
        modifier = modifier,
    )
    PlayerBusyIndicator(PlayerBusyStatus.BUFFERING.takeIf { buffering && !paused }, Modifier.align(Alignment.Center))
    }
}
