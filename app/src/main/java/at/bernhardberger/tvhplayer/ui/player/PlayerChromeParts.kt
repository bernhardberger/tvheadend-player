package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.media3.common.Format
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
import at.bernhardberger.tvhplayer.core.recordingEndsAtSec
import at.bernhardberger.tvhplayer.core.glanceBadges
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderStatusGap
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.programmeMetadata
import coil3.ImageLoader
import java.time.ZoneId
import kotlinx.coroutines.delay

/* Player chrome wiring helpers. */

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

/** Track facts the glance badges show: selected video/audio formats, audio role and text-track presence. */
internal data class TrackGlance(
    val audioDescription: Boolean = false,
    val subtitles: Boolean = false,
    val teletext: Boolean = false,
    /** Positively audio-only: the player reports audio tracks and no video track. */
    val audioOnly: Boolean = false,
    val videoHeight: Int? = null,
    val videoSampleMimeType: String? = null,
    val audioSampleMimeType: String? = null,
    val audioChannelCount: Int? = null,
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
    val video = selectedFormat(tracks, C.TRACK_TYPE_VIDEO)
    val audio = selectedFormat(tracks, C.TRACK_TYPE_AUDIO)
    return TrackGlance(
        audioDescription,
        subtitles = text.any { it != TELETEXT_CUES_MIME },
        teletext = teletext,
        audioOnly = audioOnly,
        videoHeight = video?.height?.takeIf { it > 0 },
        videoSampleMimeType = video?.sampleMimeType,
        audioSampleMimeType = audio?.sampleMimeType,
        audioChannelCount = audio?.channelCount?.takeIf { it > 0 },
    )
}

private fun selectedFormat(tracks: Tracks, type: Int): Format? = tracks.groups
    .firstOrNull { it.type == type && it.isSelected }
    ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }

private const val TELETEXT_CUES_MIME = "application/x-media3-cues"

/** Formats: diagnostics (Stats/details open) win when present, else the selected tracks. */
internal fun playerGlanceBadges(diagnostics: AppPlaybackDiagnostics, tracks: TrackGlance): List<GlanceBadge> = glanceBadges(
    videoHeight = diagnostics.video?.height ?: tracks.videoHeight,
    videoSampleMimeType = diagnostics.video?.sampleMimeType ?: tracks.videoSampleMimeType,
    audioMimeType = diagnostics.audio?.sampleMimeType ?: tracks.audioSampleMimeType,
    audioChannelCount = diagnostics.audio?.channelCount ?: tracks.audioChannelCount,
    audioDescription = tracks.audioDescription,
    subtitles = tracks.subtitles,
    teletext = tracks.teletext,
)

/** Header slot: the top-end clock over a full-width top fade. */
@Composable
internal fun PlayerChromeHeader(clock: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (LocalPlayerPageScrim.current == null || LocalPlayerControlsMotion.current == null) {
            Box(Modifier.fillMaxWidth().height(PlayerChromeTokens.topScrimHeight).background(PlayerChromeTokens.topScrim))
        }
        Text(clock, style = MaterialTheme.typography.titleLarge, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = TvOverlayTopPadding, end = PlayerChromeTokens.gridMargin)
                .testTag("player-top-cluster"))
    }
}

/** Metadata line of the tray preview: time, time left, then genre, above the title. */
@Composable
internal fun trayPreviewMetadata(event: EpgEventEntry?, nowSec: Long): String = listOfNotNull(
    event?.let(::programmeTimeRange),
    event?.takeIf { nowSec >= it.start.epochSeconds && nowSec < it.stop.epochSeconds }
        ?.let { playerMinutesLeft(((it.stop.epochSeconds - nowSec + 59) / 60).toInt()) },
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
    val subtitle: String? = null,
)

/** How long focus must rest on a quick-zap card before its preview fades in. */
internal const val QuickZapPreviewSettleMs = 250L

/**
 * [value] once it has stayed unchanged for [settleMs], otherwise null. The initial value is
 * settled immediately, unless [settleFirst]; every later change waits the full delay again.
 */
@Composable
internal fun <T : Any> rememberSettled(value: T?, settleMs: Long, settleFirst: Boolean = false): T? {
    var settled by remember { mutableStateOf(value.takeUnless { settleFirst }) }
    LaunchedEffect(value) {
        if (value != settled) {
            settled = null
            delay(settleMs)
            settled = value
        }
    }
    return value?.takeIf { it == settled }
}

/**
 * Passive preview of the focused quick-zap card. It fades out as soon as focus
 * moves and fades in once focus rests, bottom-anchored in a fixed slot so neither the tray
 * nor the cards move with the preview's length.
 */
@Composable
internal fun QuickZapTrayPreview(
    channel: Channel?,
    event: EpgEventEntry?,
    next: EpgEventEntry?,
    nowSec: Long,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
    /** Without artwork and summary. */
    compact: Boolean = false,
    /** Trial: a rail that opens with a step waits for it to settle, as any later step does. */
    settleFirst: Boolean = false,
) {
    val settledId = rememberSettled(channel?.id, QuickZapPreviewSettleMs, settleFirst)
    val snapshot = channel?.takeIf { it.id == settledId }?.let {
        QuickZapPreviewSnapshot(
            channelId = it.id,
            title = event?.title?.takeIf(String::isNotBlank) ?: it.name.orEmpty(),
            metadata = trayPreviewMetadata(event, nowSec),
            summary = if (compact) null else event?.summary?.takeIf(String::isNotBlank) ?: event?.description?.takeIf(String::isNotBlank),
            subtitle = event?.subtitle?.takeIf(String::isNotBlank),
            next = next?.title?.takeIf(String::isNotBlank)?.let { title -> "${formatClock(next.start.epochSeconds)} $title" },
            image = if (compact) null else event?.image,
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
                imageLoader = imageLoader,
                currentSession = currentSession,
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
        transitionSpec = {
            (fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard)) togetherWith
                fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate))).using(null)
        },
        contentKey = { it?.channelId },
        modifier = modifier.focusProperties { canFocus = false }.testTag("player-zap-preview"),
        label = "quick-zap-preview",
    ) { shown ->
        PlayerMotionFrame(leaving = leaving) {
            if (shown != null) content(shown)
        }
    }
}

/**
 * The info bar for a recording: `Recorded 12 Sep · Ends 23:45` (now plus the remaining playback)
 * or, still recording, `Recording until 22:08`, above the title; the episode fills the last row.
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
        playerRecordedDate(java.time.Instant.ofEpochSecond(it.epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    val endsAt = recordingEndsAtSec(nowSec, positionMs, durationMs)
    return PlayerInfoBarData(
        channelNumber = channelNumber?.toString().orEmpty(),
        channelName = entry.channelName.orEmpty(),
        title = entry.title.orEmpty(),
        timeRange = if (start != null && stop != null) {
            "${formatClock(start.epochSeconds)}–${formatClock(stop.epochSeconds)}"
        } else "",
        metadata = when {
            growing -> stop?.let { stringResource(R.string.player_recording_until, formatClock(it.epochSeconds)) }
            recordedDate != null && endsAt != null -> stringResource(R.string.player_recorded_ends, recordedDate, formatClock(endsAt))
            else -> null
        },
        subtitle = entry.subtitle,
        recordedDate = recordedDate,
    )
}
