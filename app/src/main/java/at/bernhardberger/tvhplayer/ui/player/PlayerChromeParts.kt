package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.components.focusedInfoSwap
import at.bernhardberger.tvhplayer.ui.components.rememberUnlessHeld
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import at.bernhardberger.tvheadend.sdk.media3.VideoScanType
import at.bernhardberger.tvheadend.sdk.media3.videoScanType
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.core.VideoScan
import at.bernhardberger.tvhplayer.core.glanceBadges
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvhplayer.core.recordingEndsAtSec
import at.bernhardberger.tvhplayer.core.recordingPaddedStop
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderStatusGap
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.formatTimeIntervalSpoken
import at.bernhardberger.tvhplayer.ui.common.programmeMetadata
import java.time.ZoneId

/* Player chrome wiring helpers. */

internal fun programmeTimeRange(event: EpgEventEntry): String =
    formatTimeInterval(event.start.epochSeconds, event.stop.epochSeconds)

/** The info bar for the displayed live programme; missing facts are omitted. */
internal fun liveInfoBarData(
    resources: android.content.res.Resources,
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
    spokenTimeRange = event?.let { formatTimeIntervalSpoken(it.start.epochSeconds, it.stop.epochSeconds, resources) }.orEmpty(),
    metadata = event?.let { programmeMetadata(it, resources) },
    subtitle = event?.subtitle,
    remainingMinutes = event?.takeIf { nowSec >= it.start.epochSeconds && nowSec < it.stop.epochSeconds }
        ?.let { ((it.stop.epochSeconds - nowSec + 59) / 60).toInt() },
    next = next?.title?.takeIf(String::isNotBlank)?.let { "${formatClock(next.start.epochSeconds)} $it" },
    nextScheduled = nextScheduled && next != null,
)

/** Positively audio-only: the player reports audio tracks and no video track. */
internal fun tracksAudioOnly(tracks: Tracks): Boolean =
    tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO } && tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO }

/** Track roles use the screen's snapshot; codec, raster and scan follow the decoded input. */
internal data class TrackGlance(val audioOnly: Boolean, val badges: List<GlanceBadge>)

@OptIn(ExperimentalTvheadendApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun rememberTrackGlance(player: Player, tracks: Tracks, available: Boolean): TrackGlance {
    val exoPlayer = player as? ExoPlayer
    var video by remember(player) { mutableStateOf(exoPlayer?.videoFormat) }
    var audio by remember(player) { mutableStateOf(exoPlayer?.audioFormat) }
    DisposableEffect(player) {
        val listener = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                video = format
            }
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                audio = format
            }
            override fun onMediaItemTransition(
                eventTime: AnalyticsListener.EventTime, mediaItem: MediaItem?, reason: Int,
            ) {
                video = null
                audio = null
            }
        }
        exoPlayer?.addAnalyticsListener(listener)
        onDispose { exoPlayer?.removeAnalyticsListener(listener) }
    }
    return TrackGlance(tracksAudioOnly(tracks), if (!available) emptyList() else glanceBadges(
        videoHeight = video?.height,
        audioMimeType = audio?.sampleMimeType,
        audioChannelCount = audio?.channelCount,
        audioDescription = selectedFormat(tracks, C.TRACK_TYPE_AUDIO)?.let {
            (it.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO) != 0
        } == true,
        scan = when (video?.videoScanType()) {
            VideoScanType.PROGRESSIVE -> VideoScan.PROGRESSIVE
            VideoScanType.INTERLACED -> VideoScan.INTERLACED
            VideoScanType.UNKNOWN, null -> null
        },
    ))
}

private fun selectedFormat(tracks: Tracks, type: Int): Format? = tracks.groups
    .firstOrNull { it.type == type && it.isSelected }
    ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }

/** Header slot: the top-end clock over a full-width top fade. */
@Composable
internal fun PlayerChromeHeader(clock: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (LocalPlayerPageScrim.current == null || LocalPlayerControlsMotion.current == null) {
            Box(Modifier.fillMaxWidth().height(PlayerChromeTokens.topScrimHeight).background(PlayerChromeTokens.topScrim))
        }
        // The room's header band, as a browse room's header actions stand in it.
        Box(Modifier.align(Alignment.TopEnd).padding(top = TvGridVerticalMargin, end = TvGridHorizontalMargin)
            .heightIn(min = TvBrowseHeaderHeight), contentAlignment = Alignment.CenterEnd) {
            Text(clock, style = MaterialTheme.typography.titleLarge, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
                modifier = Modifier.testTag("player-top-cluster"))
        }
    }
}

/** Metadata line of the tray preview: time, time left, then genre, above the title. */
@Composable
internal fun trayPreviewMetadata(event: EpgEventEntry?, nowSec: Long): String = listOfNotNull(
    event?.let(::programmeTimeRange),
    event?.takeIf { nowSec >= it.start.epochSeconds && nowSec < it.stop.epochSeconds }
        ?.let { playerMinutesLeft(((it.stop.epochSeconds - nowSec + 59) / 60).toInt()) },
    event?.let { programmeMetadata(it, androidx.compose.ui.platform.LocalContext.current.resources) },
).joinToString(" • ")

/** Everything one tray preview renders, so a leaving preview keeps showing its own card. */
internal data class QuickZapPreviewSnapshot(
    val channelId: ChannelId,
    val title: String,
    val metadata: String,
    val summary: String?,
    val next: String?,
    val image: ChannelIcon?,
    val subtitle: String? = null,
)

/**
 * Passive preview of the focused quick-zap card, bottom-anchored in a fixed slot so neither the
 * tray nor the cards move with the preview's length. A press crossfades to the new card's
 * preview; while [scrolling] it hides until focus rests.
 */
@Composable
internal fun QuickZapTrayPreview(
    channel: Channel?,
    event: EpgEventEntry?,
    next: EpgEventEntry?,
    nowSec: Long,
    modifier: Modifier = Modifier,
    /** Without artwork and summary. */
    compact: Boolean = false,
    scrolling: Boolean = false,
) {
    val settledId = rememberUnlessHeld(channel?.id, scrolling)
    val snapshot = channel?.takeIf { it.id == settledId }?.let {
        QuickZapPreviewSnapshot(
            channelId = it.id,
            title = event?.title?.takeIf(String::isNotBlank) ?: it.name.orEmpty(),
            metadata = trayPreviewMetadata(event, nowSec),
            summary = if (compact) null else event?.summary?.takeIf(String::isNotBlank) ?: event?.description?.takeIf(String::isNotBlank),
            subtitle = event?.subtitle?.takeIf(String::isNotBlank),
            next = next?.title?.takeIf(String::isNotBlank)?.let { title -> "${formatClock(next.start.epochSeconds)} $title" },
            image = if (compact) null else event?.let(::programmeArtwork),
        )
    }
    Box(modifier, contentAlignment = Alignment.BottomStart) {
        QuickZapPreviewReserve(Modifier.fillMaxWidth())
        QuickZapTrayPreviewFrame(snapshot) {
            QuickZapPreview(
                title = it.title,
                metadata = it.metadata,
                summary = it.summary,
                next = it.next,
                image = it.image,
                subtitle = it.subtitle,
            )
        }
    }
}


/**
 * Fades between the focused cards' previews: out quickly, in a little slower. Each side
 * renders its own [snapshot]; a new card fades, a changed programme of the same card updates
 * in place, and the leaving side drops semantics and focus on its first exit frame.
 */
@Composable
internal fun QuickZapTrayPreviewFrame(
    snapshot: QuickZapPreviewSnapshot?,
    modifier: Modifier = Modifier,
    content: @Composable (QuickZapPreviewSnapshot) -> Unit,
) {
    AnimatedContent(
        targetState = snapshot,
        transitionSpec = { focusedInfoSwap() },
        contentKey = { it?.channelId },
        // Both sides sit on the cards while they overlap, as the slot itself does; a shorter
        // preview would otherwise start at the taller one's top and drop when that one leaves.
        contentAlignment = Alignment.BottomStart,
        modifier = modifier.focusProperties { canFocus = false }.testTag("player-zap-preview"),
        label = "quick-zap-preview",
    ) { shown ->
        PlayerMotionFrame(leaving = leaving) {
            if (shown != null) content(shown)
        }
    }
}

/**
 * The info bar keeps the recorded date and start time above the title, followed by the estimated
 * playback end or, while still recording, the padded recording stop. The episode fills the last row.
 * A recording has no real next item, so there is no Next (UX-02).
 */
@Composable
internal fun recordingInfoBarData(
    entry: DvrEntry,
    channelNumber: Long?,
    nowSec: Long,
    positionMs: Long,
    durationMs: Long?,
    growing: Boolean,
): PlayerInfoBarData {
    val start = entry.start
    val stop = entry.stop
    val recordedDate = start?.let {
        val local = java.time.Instant.ofEpochSecond(it.epochSeconds).atZone(ZoneId.systemDefault())
        "${playerRecordedDate(local.toLocalDate())} ${formatClock(it.epochSeconds)}"
    }
    val endsAt = recordingEndsAtSec(nowSec, positionMs, durationMs)
    return PlayerInfoBarData(
        channelNumber = channelNumber?.toString().orEmpty(),
        channelName = entry.channelName.orEmpty(),
        title = entry.title.orEmpty(),
        timeRange = if (start != null && stop != null) formatTimeInterval(start.epochSeconds, stop.epochSeconds) else "",
        spokenTimeRange = if (start != null && stop != null) {
            formatTimeIntervalSpoken(start.epochSeconds, stop.epochSeconds, androidx.compose.ui.platform.LocalResources.current)
        } else "",
        metadata = when {
            growing -> listOfNotNull(
                recordedDate?.let { stringResource(R.string.player_recorded_on, it) },
                recordingPaddedStop(entry)?.let { stringResource(R.string.player_recording_until, formatClock(it.epochSeconds)) },
            ).joinToString(" • ").takeIf(String::isNotBlank)
            recordedDate != null && endsAt != null -> stringResource(R.string.player_recorded_ends, recordedDate, formatClock(endsAt))
            else -> null
        },
        subtitle = entry.subtitle,
        recordedDate = recordedDate,
    )
}
