package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.TvCardProgressStripHeight
import at.bernhardberger.tvhplayer.ui.TvLogoPlateColor
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.formatRecordedDuration
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.player.playerMinutesLeft
import coil3.compose.AsyncImage
import kotlin.math.ceil

/**
 * Home's current-activity card: a half-width 16:9 feature card with the programme's picture under a
 * bottom scrim, the channel plate, the title, the times and the on-air progress line, as in the
 * approved mockup. One card per activity item; the row never stretches it.
 */
@Composable
internal fun HomeActivityCard(
    channel: Channel?,
    event: EpgEvent?,
    entry: DvrEntry?,
    playing: Boolean,
    nowSec: Long,
    width: Dp,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    playingRecording: Boolean = false,
) {
    val contextMenu = rememberBrowseContextMenuTrigger(onLongClick, menuKey = true)
    val running = event?.takeIf { it.isRunning(nowSec) }
    val recordingOnly = !playing && entry != null
    val recordingNow = entry?.state == DvrEntryState.RECORDING || entry?.state == DvrEntryState.RECORDING_ERROR
    val viewer = if (playingRecording) recordingViewerPosition(entry?.id) else null
    val session = LocalArtworkContext.current.currentSession
    val image = (if (recordingOnly) entry?.image ?: entry?.fanartImage
        else running?.image ?: entry?.image ?: entry?.fanartImage)?.takeIf { session != null }
    val icon = channel?.icon
    val model = remember(session, image) { image?.let { AppArtworkSource(session, it) } }
    val load = rememberPictureLoad(model)
    val pictured = model != null && !load.failed
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = rememberCardFocusRing(interaction)
    // The current programme gives the title; a bare running entry shows its own text without EPG.
    val title = (if (recordingOnly) entry?.title else running?.title ?: entry?.title)?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.no_epg)
    val channelLabel = channel?.let { channelTitleText(it.visibleChannelNumber, it.name.orEmpty()) }
        ?: entry?.channelName.orEmpty()
    Card(
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        interactionSource = interaction,
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .cardFocusRing(ring, MaterialTheme.colorScheme.onSurface)
            .width(width)
            .aspectRatio(16f / 9f)
            .then(contextMenu.modifier),
        colors = CardDefaults.colors(containerColor = TvSurfaceColors.containerLowest),
        glow = artworkCardGlow(image, icon, interaction),
        scale = EmbeddedProgressCardScale,
        border = embeddedProgressCardBorder(),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The channel's logo on its ambient fill, until and unless the programme's picture covers it.
            if (load.needsFallback && channel != null) {
                ChannelAccentFill(icon, channel.id, Modifier.fillMaxSize())
                if (icon != null) PiconBox(
                    icon,
                    Modifier.align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(width * 0.55f, width * 0.4f)
                        .graphicsLayer {
                            alpha = 0.28f
                        },
                    ground = null,
                    placeholders = false,
                )
            }
            if (pictured) {
                AsyncImage(
                    model,
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = load::onSuccess,
                    onError = { load.onError() },
                )
            }
            // The scrim carries the labels; the picture stays readable above it.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.3f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f),
                    ),
                ),
            )
            if (playing || entry != null) {
                Row(
                    Modifier.align(if (pictured) Alignment.TopEnd else Alignment.TopStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playing) {
                        Box(
                            Modifier
                                .background(TvLogoPlateColor, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            androidx.compose.runtime.CompositionLocalProvider(
                                LocalContentColor provides TvTextColors.primary,
                            ) {
                                // REC below already owns the recording dot; keep only playback here.
                                ChannelNowIndicators(playingNow = true, recordingNow = false)
                            }
                        }
                    }
                    if (recordingNow) entry?.let { RecordingBadge(it.state) }
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(1f).alignBy(LastBaseline),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ProgrammeEyebrow(picon = icon, text = channelLabel, modifier = Modifier.fillMaxWidth()) {
                        if (playingRecording && !recordingNow) {
                            StatusBadge(stringResource(R.string.home_activity_recording), StatusBadgeTone.NEUTRAL)
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TvTextColors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val timesLine = if (playingRecording && viewer?.fraction != null) {
                        playerMinutesLeft(((viewer.durationMs - viewer.positionMs).coerceAtLeast(0L) + 59_999L).div(60_000L).toInt())
                    } else activityTimesLine(event, entry, playing, nowSec)
                    timesLine.takeIf(String::isNotEmpty)?.let { times ->
                        Text(
                            times,
                            style = MaterialTheme.typography.labelMedium,
                            color = TvTextColors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    Modifier.alignBy(LastBaseline),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hintColor = if (focused) TvTextColors.primary else TvTextColors.secondary
                    Icon(painterResource(if (playing) R.drawable.ic_fullscreen else R.drawable.ic_play_arrow),
                        contentDescription = null, modifier = Modifier.size(18.dp), tint = hintColor)
                    Text(stringResource(if (playing) R.string.home_back_to_player else R.string.watch),
                        style = MaterialTheme.typography.labelMedium, color = hintColor, maxLines = 1)
                }
            }
            val progress = if (playingRecording) viewer?.fraction else if (recordingOnly) {
                val start = entry?.start?.epochSeconds
                val stop = entry?.stop?.epochSeconds
                if (start != null && stop != null && stop > start && nowSec >= start) {
                    ((nowSec - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
                } else null
            } else running?.let { programmeProgress(it, nowSec) }
            // The strip follows the viewer when Home's resident channel plays this programme, as
            // Channels' cards do; colour follows the watching-progress locals.
            if (progress != null) {
                ProgressStrip(
                    progress,
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    color = progressColor(playingRecording || !recordingOnly && running?.let { watchingIndicator(it, nowSec).isPlayingItem } == true),
                    height = TvCardProgressStripHeight,
                )
            }
        }
    }
}

/** "18:58–21:05 • N min left" for the running programme, else the entry's schedule. */
@Composable
private fun activityTimesLine(event: EpgEvent?, entry: DvrEntry?, playing: Boolean, nowSec: Long): String {
    val recordingOnly = !playing && entry != null
    val start = if (recordingOnly) entry?.start?.epochSeconds else event?.start?.epochSeconds ?: entry?.start?.epochSeconds
    val stop = if (recordingOnly) entry?.stop?.epochSeconds else event?.stop?.epochSeconds ?: entry?.stop?.epochSeconds
    if (recordingOnly && start != null && nowSec < start) {
        return stringResource(R.string.home_recording_starts_at, formatHm(start))
    }
    val range = when {
        start != null && stop != null -> formatTimeInterval(start, stop)
        start != null -> formatHm(start)
        else -> return ""
    }
    // A playing recording resumes where it was left; a programme that is still on air counts down.
    val minutesLeft = stop
        ?.takeIf { (recordingOnly || event != null) && it > nowSec }
        ?.let { ceil((it - nowSec) / 60.0).toLong() }
    val left = minutesLeft?.let { minutes ->
        stringResource(
            R.string.player_minutes_left,
            formatRecordedDuration(minutes * 60L, LocalContext.current.resources),
        )
    }
    return listOfNotNull(range, left).joinToString(" • ")
}
