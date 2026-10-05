package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.rememberChannelAccent
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Avoid repeating a supplied subtitle as the synopsis when a fuller description exists. */
internal fun channelsProgrammeSynopsis(event: EpgEvent): String? =
    sequenceOf(event.summary, event.description)
        .mapNotNull { it?.replace("\\r\\n", "\n")?.replace("\\n", "\n")?.trim() }
        .firstOrNull { it.isNotBlank() && it != event.title?.trim() && it != event.subtitle?.trim() }

/** A passive preview with configuration-sized artwork and a reserved, bottom-aligned Next line. */
@Composable
internal fun ChannelsProgrammeDetails(
    channel: Channel,
    now: EpgEvent?,
    next: EpgEvent?,
    nowSec: Long,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
) = profileTrace("P1:compose:channelDetails") {
    val title = now?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_epg)
    val subtitle = now?.subtitle?.takeIf(String::isNotBlank)
    val synopsis = remember(now) { now?.let(::channelsProgrammeSynopsis) }
    val nextText = next?.let {
        stringResource(R.string.epg_next, formatHm(it.start.epochSeconds)) +
            it.title?.takeIf(String::isNotBlank)?.let { title -> " · $title" }.orEmpty()
    }
    val typography = MaterialTheme.typography
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val textConstraints = Constraints(maxWidth = constraints.maxWidth)
        val titleLayout = textMeasurer.measure(title, typography.titleLarge, constraints = textConstraints, maxLines = 2)
        val subtitleLayout = subtitle?.let {
            textMeasurer.measure(it, typography.titleMedium, constraints = textConstraints)
        }
        val synopsisLayout = synopsis?.let {
            textMeasurer.measure(it, typography.bodyMedium, constraints = textConstraints,
                maxLines = if (density.fontScale >= 1.3f) 2 else 3)
        }
        val smallGap = with(density) { 4.dp.roundToPx() }
        val contentGap = with(density) { 8.dp.roundToPx() }
        val footerHeight = textMeasurer.measure("M", typography.bodyMedium).size.height
        // The slot depends only on viewport and type metrics, never programme metadata.
        // Keep the full-width 16:9 card unless even one title line, one subtitle line
        // and the reserved Next line cannot fit. Reserve those lines when absent too.
        val minimumTextHeight = textMeasurer.measure("M", typography.titleLarge).size.height +
            textMeasurer.measure("M", typography.titleMedium).size.height + smallGap * 2 +
            contentGap + footerHeight
        val preferredImageHeight = constraints.maxWidth * 9f / 16f
        val imageHeight = minOf(preferredImageHeight, (constraints.maxHeight - minimumTextHeight).coerceAtLeast(0).toFloat())
        val textBudget = constraints.maxHeight - imageHeight.roundToInt() - smallGap - contentGap - footerHeight
        var titleLines = titleLayout.lineCount
        var subtitleLines = subtitleLayout?.lineCount ?: 0
        var synopsisLines = synopsisLayout?.lineCount ?: 0
        fun height(layout: TextLayoutResult?, lines: Int): Int =
            if (layout == null || lines == 0) 0 else ceil(layout.getLineBottom(lines - 1)).toInt()
        fun textHeight(): Int = height(titleLayout, titleLines) + height(subtitleLayout, subtitleLines) +
            height(synopsisLayout, synopsisLines) + (if (subtitleLines > 0) smallGap else 0) +
            (if (synopsisLines > 0) contentGap else 0)

        // Synopsis yields first, including its gap. Then ellipsize the larger text
        // block by whole lines, keeping at least one title and actual subtitle line.
        while (textHeight() > textBudget) {
            when {
                synopsisLines > 0 -> synopsisLines--
                titleLines > 1 && (subtitleLines <= 1 ||
                    height(titleLayout, titleLines) >= height(subtitleLayout, subtitleLines)) -> titleLines--
                subtitleLines > 1 -> subtitleLines--
                else -> break
            }
        }
        Column(Modifier.fillMaxSize()) {
            ChannelsProgrammePreview(
                channel = channel,
                now = now,
                nowSec = nowSec,
                imageLoader = imageLoader,
                currentSession = currentSession,
                modifier = Modifier.size(
                    width = with(density) { (imageHeight * 16f / 9f).toDp() },
                    height = with(density) { imageHeight.toDp() },
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("channels-detail-title"),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("channels-detail-subtitle"),
                )
            }
            if (synopsis != null && synopsisLines > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = synopsis,
                    style = typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = synopsisLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("channels-detail-description"),
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(with(density) { footerHeight.toDp() })) {
                if (nextText != null) Text(
                    text = nextText,
                    style = typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).testTag("channels-detail-next"),
                )
            }
        }
    }
}

@Composable
private fun ChannelsProgrammePreview(
    channel: Channel,
    now: EpgEvent?,
    nowSec: Long,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier,
) {
    val accent = rememberChannelAccent(imageLoader, currentSession, channel.icon, channel.id)
    val artwork = ArtworkId.parse(now?.image)
    val timedEvent = now?.takeIf { it.stop > it.start }
    val timing = timedEvent?.let {
        stringResource(R.string.channels_preview_time_range, formatHm(it.start.epochSeconds), formatHm(it.stop.epochSeconds))
    }
    val metadataStyle = MaterialTheme.typography.bodyMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clip(MaterialTheme.shapes.medium).testTag("channels-preview")) {
        val timingLayout = textMeasurer.measure(timing.orEmpty(), metadataStyle, maxLines = 1)
        // Preserve the whole time range. Stack it when it would take more than half
        // the inset width; the channel identity alone may ellipsize.
        val stacked = timing != null && timingLayout.size.width * 2 + with(density) { 8.dp.toPx() } >
            constraints.maxWidth - with(density) { 24.dp.toPx() }
        val metadataHeight = with(density) {
            (timingLayout.size.height * if (stacked) 2 else 1).toDp()
        } + 24.dp + if (timing != null) 10.dp else 0.dp
        val fallback: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(
                        if (channel.icon != null && currentSession != null) accent.copy(alpha = 0.48f) else TvSurfaceColors.containerLow,
                        TvSurfaceColors.containerLowest,
                    )))
                    .testTag("channels-preview-fallback")
                    .padding(top = 8.dp, bottom = metadataHeight),
                contentAlignment = Alignment.Center,
            ) {
                PiconBox(
                    imageLoader = imageLoader,
                    piconPath = channel.icon,
                    currentSession = currentSession,
                    modifier = Modifier.fillMaxSize(0.6f).testTag("channels-preview-identity"),
                )
            }
        }
        if (artwork != null && currentSession != null) {
            SubcomposeAsyncImage(
                model = AppArtworkSource(currentSession, artwork),
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { fallback() },
                error = { fallback() },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize().testTag("channels-preview-artwork")) },
            )
        } else fallback()
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(metadataHeight + 24.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f), Color.Black))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            val identity: @Composable (Modifier) -> Unit = { textModifier ->
                Text(
                    text = channel.name.orEmpty(),
                    style = metadataStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = textModifier.testTag("channels-detail-channel"),
                )
            }
            val time: @Composable () -> Unit = {
                if (timing != null) Text(
                    text = timing,
                    style = metadataStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.testTag("channels-detail-timing"),
                )
            }
            if (stacked) {
                identity(Modifier.fillMaxWidth())
                time()
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                identity(Modifier.weight(1f, fill = false))
                if (timing != null) {
                    Text(" · ", style = metadataStyle, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    time()
                }
            }
            if (timedEvent != null) {
                Spacer(Modifier.height(8.dp))
                ProgressStrip(
                    progress = timedEvent.progress(nowSec),
                    height = 2.dp,
                    modifier = Modifier.fillMaxWidth().testTag("channels-detail-progress"),
                )
            }
        }
    }
}
