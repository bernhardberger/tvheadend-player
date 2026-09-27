package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.core.PlayerStatus
import at.bernhardberger.tvhplayer.core.PlayerStatusKind
import at.bernhardberger.tvhplayer.core.glanceBadges
import at.bernhardberger.tvhplayer.core.playerStatus
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.programmeMetadata
import coil3.ImageLoader
import java.time.ZoneId

/*
 * New player design ("Player design: New") wiring helpers. Everything here is used
 * only when the New design is selected; the Current chrome never composes it.
 */

internal fun programmeTimeRange(event: EpgEventEntry): String =
    "${formatClock(event.start.epochSeconds)}–${formatClock(event.stop.epochSeconds)}"

/** The info bar for the displayed live programme; missing facts are omitted. */
internal fun liveInfoBarData(
    channelNumber: Long?,
    channelName: String,
    event: EpgEventEntry?,
    next: EpgEventEntry?,
    nextScheduled: Boolean,
    nowSec: Long,
    unavailableTitle: String,
): PlayerInfoBarData = PlayerInfoBarData(
    channelNumber = channelNumber?.toString().orEmpty(),
    channelName = channelName,
    title = event?.title?.takeIf(String::isNotBlank) ?: unavailableTitle,
    timeRange = event?.let(::programmeTimeRange).orEmpty(),
    metadata = event?.let(::programmeMetadata),
    subtitle = event?.subtitle,
    remainingMinutes = event?.takeIf { nowSec >= it.start.epochSeconds && nowSec < it.stop.epochSeconds }
        ?.let { ((it.stop.epochSeconds - nowSec + 59) / 60).toInt() },
    next = next?.title?.takeIf(String::isNotBlank)?.let { "${formatClock(next.start.epochSeconds)} $it" },
    nextScheduled = nextScheduled && next != null,
)

/** Track facts the glance badges show: the selected audio's role and text-track presence. */
internal data class TrackGlance(
    val audioDescription: Boolean = false,
    val subtitles: Boolean = false,
    val teletext: Boolean = false,
    /** Positively audio-only: the player reports audio tracks and no video track. */
    val audioOnly: Boolean = false,
)

internal fun trackGlance(tracks: Tracks): TrackGlance {
    val audioDescription = tracks.groups.any { group ->
        group.type == C.TRACK_TYPE_AUDIO && (0 until group.length).any {
            group.isTrackSelected(it) && (group.getTrackFormat(it).roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO) != 0
        }
    }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).sampleMimeType } }
    // Tvheadend delivers its teletext subtitles as Media3 cues (see TrackLabelPolicy).
    val teletext = text.any { it == TELETEXT_CUES_MIME }
    val audioOnly = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO } &&
        tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO }
    return TrackGlance(
        audioDescription,
        subtitles = text.any { it != TELETEXT_CUES_MIME },
        teletext = teletext,
        audioOnly = audioOnly,
    )
}

private const val TELETEXT_CUES_MIME = "application/x-media3-cues"

@Composable
internal fun playerTrialBadges(diagnostics: AppPlaybackDiagnostics, tracks: TrackGlance): List<GlanceBadge> {
    val locale = LocalConfiguration.current.locales[0]
    val frontend = diagnostics.live?.frontend?.takeIf { diagnostics.source == AppPlaybackSource.LIVE_TV }
    return glanceBadges(
        videoHeight = diagnostics.video?.height,
        videoSampleMimeType = diagnostics.video?.sampleMimeType,
        audioMimeType = diagnostics.audio?.sampleMimeType,
        audioChannelCount = diagnostics.audio?.channelCount,
        audioDescription = tracks.audioDescription,
        subtitles = tracks.subtitles,
        teletext = tracks.teletext,
        liveFrontend = frontend != null,
        relativeSnrPercent = frontend?.relativeSnrPercent,
        absoluteSnrDecibels = frontend?.absoluteSnrDecibels,
        locale = locale,
    )
}

/** Kinds the standalone bottom-end chip shows while the chrome is hidden. */
internal val TRIAL_STANDALONE_STATUS_KINDS = setOf(PlayerStatusKind.TUNING, PlayerStatusKind.BUFFERING, PlayerStatusKind.PAUSED)

/** Live status for the chip; null while nothing is presented and nothing is pending. */
internal fun liveTrialStatus(
    timeshift: AppTimeshiftState,
    paused: Boolean,
    tuning: Boolean,
    buffering: Boolean,
    presented: Boolean,
    unavailable: Boolean,
): PlayerStatus? {
    if (unavailable || (!presented && !tuning && !buffering)) return null
    val position = timeshift.takeIf { it.available && it.timingKnown }?.let(::timeshiftPositionPresentation)
    return playerStatus(
        live = true,
        paused = paused,
        behindLiveSeconds = position?.behindLiveMs?.div(1000),
        tuning = tuning,
        buffering = buffering,
        timingKnown = !timeshift.available || timeshift.timingKnown,
    )
}

/** Header slot of the New design: slim top-end clock and status cluster over its own feathered fade. */
@Composable
internal fun PlayerTrialHeader(
    clock: String,
    status: PlayerStatus?,
    modifier: Modifier = Modifier,
    recording: PlayerStatus? = null,
) {
    var clusterSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    // The cluster carries 8dp of its own padding; its text rests on the overlay safe area.
    val top = TvOverlayTopPadding - 8.dp
    val end = TvOverlaySidePadding - 8.dp
    Box(modifier) {
        with(density) {
            PlayerTopEndScrim(
                clusterWidthFromEnd = clusterSize.width.toDp() + end,
                clusterBottom = clusterSize.height.toDp() + top,
                modifier = Modifier.matchParentSize(),
            )
        }
        PlayerTopStatusCluster(
            clock = clock,
            status = status,
            recording = recording,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = top, end = end)
                .onSizeChanged { clusterSize = it }.testTag("trial-top-cluster"),
        )
    }
}

/** With the chrome hidden: the passive bottom-end chip for buffering, tuning or paused. */
@Composable
internal fun PlayerStandaloneStatusChip(status: PlayerStatus?, modifier: Modifier = Modifier) {
    val shown = rememberLastShown(status)
    AnimatedVisibility(
        visible = status != null,
        enter = fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
        modifier = Modifier.semanticsWhileShown(status != null).then(modifier),
    ) {
        shown?.let { PlayerStatusChip(it, Modifier.testTag("trial-standalone-status")) }
    }
}

/**
 * The New design's Banner: the controls' info bar, top cluster and passive schedule
 * progress without the action row or tray peek. Nothing in it takes focus.
 */
@Composable
internal fun PlayerBanner(
    visible: Boolean,
    header: @Composable (Modifier) -> Unit,
    infoBar: @Composable () -> Unit,
    event: EpgEventEntry?,
    nowSec: Long,
    channelId: ChannelId?,
    modifier: Modifier = Modifier,
) {
    PlayerControlsLayer(visible = visible, modalVisible = false, modifier = modifier, entry = PlayerControlsEntry.FADE) {
        PlayerOverlayChrome(
            headerContent = header,
            modifier = Modifier.focusProperties { canFocus = false }.testTag("trial-banner"),
            footerPadding = PaddingValues(
                start = TvOverlaySidePadding, end = TvOverlaySidePadding,
                top = TvOverlayFooterGradientRunout, bottom = playerSeekPreviewBottomPadding(true),
            ),
            newDesign = true,
        ) {
            infoBar()
            val airing = event?.takeIf { nowSec >= it.start.epochSeconds && nowSec < it.stop.epochSeconds }
            PlayerTimelineBlock(
                progress = airing?.let {
                    ((nowSec - it.start.epochSeconds).toDouble() / (it.stop.epochSeconds - it.start.epochSeconds)).toFloat()
                },
                tone = PlayerTimelineTone.AMBIENT,
                collapsed = false,
                fillColor = MaterialTheme.colorScheme.onSurface,
                showTrack = true,
                motionKey = channelId to airing?.id,
                leadingLabel = airing?.let { formatClock(it.start.epochSeconds) },
                trailingLabel = airing?.let { formatClock(it.stop.epochSeconds) },
                reserveLabelSpace = true,
                progressSemantics = false,
            )
        }
    }
}

/** Metadata line of the tray preview: time, time left, then genre, above the title. */
@Composable
internal fun trayPreviewMetadata(event: EpgEventEntry?, nowSec: Long): String = listOfNotNull(
    event?.let(::programmeTimeRange),
    event?.takeIf { nowSec >= it.start.epochSeconds && nowSec < it.stop.epochSeconds }
        ?.let { trialMinutesLeft(((it.stop.epochSeconds - nowSec + 59) / 60).toInt()) },
    event?.let(::programmeMetadata),
).joinToString(" · ")

/** Everything one tray preview renders, so a leaving preview keeps showing its own card. */
internal data class QuickZapPreviewSnapshot(
    val channelId: ChannelId,
    val title: String,
    val metadata: String,
    val summary: String?,
    val next: String?,
    val image: String?,
)

/** New design: passive preview of the focused quick-zap card; it crossfades between cards. */
@Composable
internal fun QuickZapTrayPreview(
    channel: Channel?,
    event: EpgEventEntry?,
    next: EpgEventEntry?,
    nowSec: Long,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
) {
    val snapshot = channel?.let {
        QuickZapPreviewSnapshot(
            channelId = it.id,
            title = event?.title?.takeIf(String::isNotBlank) ?: it.name.orEmpty(),
            metadata = trayPreviewMetadata(event, nowSec),
            summary = event?.summary?.takeIf(String::isNotBlank) ?: event?.description?.takeIf(String::isNotBlank),
            next = next?.title?.takeIf(String::isNotBlank)?.let { title -> "${formatClock(next.start.epochSeconds)} $title" },
            image = event?.image,
        )
    }
    QuickZapTrayPreviewFrame(snapshot, modifier) {
        QuickZapPreview(
            title = it.title,
            metadata = it.metadata,
            summary = it.summary,
            next = it.next,
            image = it.image,
            imageLoader = imageLoader,
            currentSession = currentSession,
        )
    }
}

/**
 * Crossfades between the focused cards' previews. Each side renders its own [snapshot];
 * a new card crossfades, a changed programme of the same card updates in place, and the
 * leaving side drops semantics and focus on its first exit frame.
 */
@Composable
internal fun QuickZapTrayPreviewFrame(
    snapshot: QuickZapPreviewSnapshot?,
    modifier: Modifier = Modifier,
    content: @Composable (QuickZapPreviewSnapshot) -> Unit,
) {
    AnimatedContent(
        targetState = snapshot,
        transitionSpec = {
            (fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard)) togetherWith
                fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate))).using(null)
        },
        contentKey = { it?.channelId },
        modifier = modifier.focusProperties { canFocus = false }.testTag("trial-zap-preview"),
        label = "quick-zap-preview",
    ) { shown ->
        PlayerMotionFrame(leaving = leaving) {
            if (shown != null) content(shown)
        }
    }
}

/**
 * The info bar for a recording: `Recorded 12 Sep · 20:15–21:45` above the title. A
 * recording has no real next item, so the Next line is omitted (UX-02).
 */
@Composable
internal fun recordingInfoBarData(entry: DvrEntry, channelNumber: Long?): PlayerInfoBarData {
    val start = entry.start
    val stop = entry.stop
    return PlayerInfoBarData(
        channelNumber = channelNumber?.toString().orEmpty(),
        channelName = entry.channelName.orEmpty(),
        title = entry.title.orEmpty(),
        timeRange = if (start != null && stop != null) {
            "${formatClock(start.epochSeconds)}–${formatClock(stop.epochSeconds)}"
        } else "",
        subtitle = entry.subtitle,
        recordedDate = start?.let {
            trialRecordedDate(java.time.Instant.ofEpochSecond(it.epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate())
        },
    )
}

/** The Info panel's "Stream & signal" row summary: the glance badges' own short labels. */
@Composable
internal fun glanceSummary(badges: List<GlanceBadge>): String = badges.map { badgeLabel(it, spoken = false) }.joinToString(" · ")

/** The Info panel's "Stream & signal" page: heading and the stream and signal details rows. */
@Composable
internal fun LiveStreamSignalPage(diagnostics: AppPlaybackDiagnostics) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.trial_stream_signal),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        StreamSignalDetailsPage(
            sections = formatStreamSignalDetails(diagnostics),
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("live-info-stream-signal-details"),
        )
    }
}
