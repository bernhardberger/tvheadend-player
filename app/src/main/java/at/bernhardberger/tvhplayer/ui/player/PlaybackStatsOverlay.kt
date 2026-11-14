package at.bernhardberger.tvhplayer.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.di.PlayerStatsLevel
import at.bernhardberger.tvhplayer.playback.*
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.ui.*
import at.bernhardberger.tvhplayer.ui.common.formatHms
import java.util.Locale
import kotlin.time.DurationUnit
import kotlinx.coroutines.delay

/** Visibility changes do not participate in player input or focus. */
@Composable
internal fun PlaybackStatsVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)),
        modifier = Modifier.semanticsWhileShown(visible).then(modifier),
        label = "playback-stats",
    ) { content() }
}

@Composable
internal fun PlaybackStatsOverlay(
    diagnostics: AppPlaybackDiagnostics,
    aspectRatio: AspectRatioMode,
    modifier: Modifier = Modifier,
    timeshiftState: AppTimeshiftState? = null,
    frameRate: PlaybackFrameRate? = null,
    level: PlayerStatsLevel = PlayerStatsLevel.STREAM_PATH,
    accessible: Boolean = true,
    targetKey: Any? = diagnostics.source,
    raster: String? = null,
) {
    val window = rememberStatsWindow(targetKey, diagnostics)
    val output = rememberDisplayOutput()
    val declared = frameRate?.declared?.collectAsStateWithLifecycle()?.value
    val requested = frameRate?.requested?.collectAsStateWithLifecycle()?.value?.rate?.let(::formatRefreshRate)
    val measured = rememberMeasuredFrameRate(frameRate)
    val declaredRate = declared?.let { formatRefreshRate((1.0 / it.toDouble(DurationUnit.SECONDS)).toFloat()) }
        ?: diagnostics.video?.frameRate?.let(::formatRefreshRate)
    val path = streamPath(diagnostics, window, output?.refreshRate, requested)
    val debug = level == PlayerStatsLevel.DEBUG
    val foreground = MaterialTheme.colorScheme.onSurface
    val bodySmall = MaterialTheme.typography.bodySmall
    val valueStyle = remember(bodySmall) { bodySmall.copy(fontFeatureSettings = "tnum") }
    Surface(
        modifier = modifier
            .width(if (debug) TvGridColumnWidth * 12 + TvGridGutter * 11 else TvGridColumnWidth * 5 + TvGridGutter * 4)
            .testTag("playback-stats-overlay")
            .then(if (accessible) Modifier.semantics(mergeDescendants = true) { heading() }
                else Modifier.clearAndSetSemantics {}),
        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = TvOverlayBoxAlpha), contentColor = foreground),
        shape = MaterialTheme.shapes.medium,
    ) { ProvideTextStyle(valueStyle) {
        Row(Modifier.padding(TvSpacing16), horizontalArrangement = Arrangement.spacedBy(TvGridGutter)) {
            Column(Modifier.then(if (debug) Modifier.width(324.dp) else Modifier.fillMaxWidth())) {
                Row {
                    Text(stringResource(R.string.stats_for_nerds), Modifier.weight(1f).alignByBaseline(),
                        style = MaterialTheme.typography.labelLarge)
                    // The rows' growth facts no longer name their window; the header does, while one shows.
                    if (path.steps.any { pathGrowth(it, window) > 0 }) StatsText("last 60 s", Modifier.alignByBaseline(), maxLines = 1)
                }
                StatsText(audioSummary(diagnostics), maxLines = 1)
                if (debug && diagnostics.source == AppPlaybackSource.LIVE_TV) {
                    diagnostics.live?.source?.let { source ->
                        val names = listOfNotNull(source.adapterName, source.muxName, source.networkName,
                            source.providerName, source.serviceName)
                        // One name per line; a name too long for the column ellipsizes alone.
                        names.forEach { StatsText(it, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(TvSpacing4))
                path.steps.forEachIndexed { index, step ->
                    StatsStep(
                        number = index + 1, first = index == 0, last = index == path.steps.lastIndex,
                        filled = step == path.firstFlagged, flagged = step in path.flagged,
                        name = stepName(step),
                        value = stepValue(step, diagnostics, window, output, requested,
                            measured?.framesPerSecond?.let(::formatRefreshRate), declaredRate, timeshiftState, raster),
                    )
                }
            }
            if (debug) {
                Column(Modifier.weight(1f)) {
                    Text("Debug details", style = MaterialTheme.typography.labelLarge)
                    DebugDetails(diagnostics, aspectRatio, timeshiftState, output, declared?.let(::formatFrameDurationMillis),
                        declaredRate, requested, measured, window)
                }
            }
        }
    } }
}

/**
 * The target's 60 s window. It is evaluated again when its oldest growth leaves the span, since
 * an unchanged observation arrives as no observation; nothing runs while there is no growth.
 */
@Composable
internal fun rememberStatsWindow(targetKey: Any?, diagnostics: AppPlaybackDiagnostics): StatsWindow {
    val history = remember { PlaybackStatsHistory(SystemClock::elapsedRealtime) }
    var expiries by remember { mutableIntStateOf(0) }
    val window = remember(targetKey, diagnostics, expiries) { history.update(targetKey, diagnostics) }
    val expiresAt = window.growthExpiresAtMillis
    if (expiresAt != null) LaunchedEffect(expiresAt, expiries) {
        delay(expiresAt - SystemClock.elapsedRealtime())
        expiries++
    }
    return window
}

/** The line uses the whole row's height, with endpoints only at the first and last ring centres. */
@Composable
private fun StatsStep(number: Int, first: Boolean, last: Boolean, filled: Boolean, flagged: Boolean, name: String, value: String) {
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = TvGridGutter).semantics {
            if (flagged) stateDescription = "Problem: $name"
        }.drawBehind {
            val x = 8.dp.toPx()
            val y = 10.dp.toPx()
            drawLine(color.copy(alpha = TvTextTertiaryAlpha), Offset(x, if (first) y else 0f),
                Offset(x, if (last) y else size.height), 1.dp.toPx())
            drawCircle(if (filled) color else Color.Black, 8.dp.toPx(), Offset(x, y))
            drawCircle(color, 8.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), Modifier.width(TvSpacing16).clearAndSetSemantics {},
            color = if (filled) Color.Black else color,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.width(10.dp))
        Text(name, modifier = Modifier.width(84.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        StatsText(value, modifier = Modifier.weight(1f), maxLines = 1)
        // The flag's own gutter at the inner edge, kept in every row so values end at the same place.
        Text(if (flagged) "!" else "", Modifier.width(TvSpacing16).clearAndSetSemantics {},
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun StatsText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    // The card provides the tabular value style once.
    Text(text, modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

private fun decimal(value: Double, digits: Int = 1): String = String.format(Locale.ROOT, "%.${digits}f", value)
private fun count(value: Long): String = String.format(Locale.ROOT, "%,d", value)
private fun growth(value: Long, label: String): String? = value.takeIf { it > 0 }?.let { "+${count(it)} $label / 60 s" }
private fun change(value: Double?, unit: String, digits: Int = 1): String? =
    displayedStatsChange(value, digits)?.let { "${if (it > 0) "+" else ""}${decimal(it, digits)} $unit / 60 s" }
private fun join(vararg values: String?): String = values.filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")
private fun audioSummary(d: AppPlaybackDiagnostics): String = d.audio?.let { audio ->
    join(audio.codec, audio.language, audio.channelCount?.let { "$it ch" }, audio.sampleRateHz?.let { "${count(it.toLong())} Hz" })
}?.takeIf { it.isNotBlank() } ?: "Audio —"
private fun videoSummary(d: AppPlaybackDiagnostics): String = join(d.video?.codec, d.video?.resolution).ifBlank { "Video —" }

/** A path row's growth fact: compact, and without its window, which the header names. */
private fun pathGrowthFact(step: StreamPathStep, w: StatsWindow, label: String): String? =
    pathGrowth(step, w).takeIf { it > 0 }?.let { "+${compactCount(it)} $label" }

/**
 * A stream path row's text. A row with a growth fact shows that fact and one related value only,
 * so it fits beside the flag; the debug details keep every full value.
 */
internal fun stepValue(
    step: StreamPathStep, d: AppPlaybackDiagnostics, w: StatsWindow, output: DisplayOutput?, requested: String?,
    measuredRate: String?, declaredRate: String?, timeshift: AppTimeshiftState?, raster: String? = null,
): String = when (step) {
    StreamPathStep.TUNER, StreamPathStep.SERVER -> liveStepValue(step, d, w)
    StreamPathStep.APP -> {
        val buffer = "${decimal(d.bufferedMs / 1000.0)} s buffer"
        pathGrowthFact(step, w, "drops")?.let { join(it, buffer) } ?: join(buffer, timing(d, timeshift))
    }
    StreamPathStep.DECODER -> {
        val rate = frameRateText(measuredRate, declaredRate)
        val size = videoSizeLabel(d.video, raster)
        // A non-standard width×height or a second rate takes the codec family's room; debug keeps the codec.
        val family = videoCodecFamily(d.video?.codec, d.video?.sampleMimeType)
            .takeIf { (size == null || d.video?.height in StandardHeights) && !showsTwoRates(measuredRate, declaredRate) }
        pathGrowthFact(step, w, "drops")?.let { join(it, rate ?: size) }
            ?: join(family, size, rate).ifBlank { "Video —" }
    }
    // A mismatch names the output rate, then the request; the resolution then stays in debug.
    StreamPathStep.SCREEN -> when {
        output == null -> "Output —"
        requested != null && requested != output.refreshRate -> "${output.refreshRate} Hz • requested $requested Hz"
        else -> "${output.width}×${output.height} @ ${output.refreshRate} Hz"
    }
}

private fun stepName(step: StreamPathStep): String = when (step) {
    StreamPathStep.TUNER -> "Tuner"
    StreamPathStep.SERVER -> "Tvheadend"
    StreamPathStep.APP -> "This app"
    StreamPathStep.DECODER -> "Decoder"
    StreamPathStep.SCREEN -> "Screen"
}

/** The Tuner and Tvheadend rows' text; other steps have none here. */
private fun liveStepValue(step: StreamPathStep, d: AppPlaybackDiagnostics, w: StatsWindow): String = when (step) {
    StreamPathStep.TUNER -> {
        val f = d.live?.frontend
        val noLock = f?.state?.locked == false
        val snr = f?.relativeSnrPercent?.let { "${decimal(it, 0)}% SNR" }
        val growth = pathGrowthFact(step, w, "uncorrected")
        when {
            growth != null && noLock -> join("No lock", growth)
            growth != null -> join(growth, snr)
            // Lock is the normal state and goes unsaid; without lock the signal is the fact, its SNR stays in debug.
            else -> join(if (noLock) "No lock" else null,
                f?.relativeSignalPercent?.let { "${decimal(it, 0)}% signal" }, snr.takeUnless { noLock })
        }
    }
    StreamPathStep.SERVER -> join(pathGrowthFact(step, w, "drops"),
        d.live?.queue?.mediaSpanMicroseconds?.let { "${decimal(it / 1000.0, 0)} ms queue" })
    else -> ""
}

/**
 * The card's Tuner and Tvheadend rows, without flags, for a message the card gives way to: only
 * the steps the path has, and only with data. The debug level adds the tuner and queue groups.
 */
internal fun liveStatsRows(d: AppPlaybackDiagnostics, w: StatsWindow, level: PlayerStatsLevel): List<String> {
    if (!level.enabled) return emptyList()
    val path = streamPath(d, w, outputRate = null, requestedRate = null).steps
        .filter { it == StreamPathStep.TUNER || it == StreamPathStep.SERVER }
        .mapNotNull { step -> liveStepValue(step, d, w).takeIf { it.isNotEmpty() }?.let { "${stepName(step)}: $it" } }
    return if (level == PlayerStatsLevel.DEBUG) path + liveSignalDebugRows(d) else path
}

private fun statsLine(label: String, value: String?) = "$label: ${value ?: "—"}"

/** The debug card's tuner and server-queue groups. */
private fun liveSignalDebugRows(d: AppPlaybackDiagnostics): List<String> {
    val f = d.live?.frontend
    val q = d.live?.queue
    return listOfNotNull(
        f?.let { statsLine("Frontend", join(it.state?.let { s -> when {
            s.locked -> "Locked"
            s.partiallySynchronized -> "Synchronizing"
            s.signalDetected -> "Signal detected"
            else -> "No signal"
        } }, it.relativeSignalPercent?.let { v -> "${decimal(v)}% signal" },
            it.absoluteSignalDbm?.let { v -> "${decimal(v)} dBm" })) },
        f?.let { statsLine("SNR", join(it.relativeSnrPercent?.let { v -> "${decimal(v)}%" },
            it.absoluteSnrDecibels?.let { v -> "${decimal(v)} dB" })) },
        f?.let { statsLine("BER / uncorrected", join(it.bitErrorRateRaw?.let(::count), it.uncorrectedBlockCount?.let(::count))) },
        q?.let { statsLine("Queue", join("${count(it.packetCount)} packets • ${count(it.byteCount)} B",
            it.mediaSpanMicroseconds?.let { us -> "${decimal(us / 1000.0)} ms" })) },
        q?.let { statsLine("Server drops", "I ${count(it.droppedIFrameCount)} • P ${count(it.droppedPFrameCount)} • B ${count(it.droppedBFrameCount)}") },
        d.live?.clientDroppedPacketCount?.let { statsLine("Client drops", "${count(it)} packets") },
    )
}

private fun timing(d: AppPlaybackDiagnostics, timeshift: AppTimeshiftState?): String =
    timeshift?.let {
        when {
            it.available && !it.timingKnown -> "Timing unavailable"
            !it.available || timeshiftPositionPresentation(it).atLiveEdge -> "Live"
            else -> "${formatHms(-it.positionMs / 1000)} behind"
        }
    } ?: (formatHms(d.positionMs / 1000) + (d.durationMs?.let { "/${formatHms(it / 1000)}" } ?: ""))

@Composable
private fun DebugDetails(
    d: AppPlaybackDiagnostics, aspect: AspectRatioMode, timeshift: AppTimeshiftState?, output: DisplayOutput?,
    duration: String?, declared: String?, requested: String?, measured: MeasuredFrameRate?, window: StatsWindow,
) {
    val bufferTemplate = stringResource(R.string.stats_video_buffers_value)
    val rows = listOfNotNull(
        statsLine("State", when (d.state) {
            AppPlaybackState.Playing -> if (d.isPlaying) "Playing" else "Paused"
            is AppPlaybackState.Recovering -> "Recovering"
            else -> d.state.toString().substringAfterLast('.')
        }),
        statsLine("Timing", join(timing(d, timeshift), "${decimal(d.bufferedMs / 1000.0)} s buffer")),
        statsLine("Video", join(videoSummary(d), d.video?.frameRate?.let { "${formatRefreshRate(it)} fps" })),
        statsLine("Frame rate", join(duration?.let { "$it ms • $declared fps declared" },
            measured?.framesPerSecond?.let { "${formatRefreshRate(it)} measured" },
            measured?.sameTimePercent?.let { "$it% same-time" }).ifEmpty { null }),
        d.videoDecoderCounters?.let { statsLine("Video buffers", formatVideoDecoderCounters(it, bufferTemplate)) },
        statsLine("Display mode", when (aspect) {
            AspectRatioMode.FIT -> "Auto (original aspect)"
            AspectRatioMode.FORCE_16_9 -> "16:9"
            AspectRatioMode.FORCE_4_3 -> "4:3"
        }),
        output?.let { statsLine("Output", join("${it.width}×${it.height} @ ${it.refreshRate} Hz",
            requested?.let { rate -> "requested $rate Hz${if (duration == null) " (kept)" else ""}" })) },
        output?.refreshRatesByResolution?.takeIf { it.isNotEmpty() }?.let { statsLine("Refresh rates", it) },
        output?.matchFrameRate?.let { statsLine("Match frame rate", when (it) {
            MatchFrameRatePreference.NEVER -> "Never"
            MatchFrameRatePreference.SEAMLESS_ONLY -> "Seamless only"
            MatchFrameRatePreference.ALWAYS -> "Always"
        }) },
        d.startupBuffer?.let { statsLine("Startup buffer", "${it.millis} ms (${if (it.automatic) "automatic" else "fixed"})") },
        "Stall recoveries: ${d.stallRecoveries} • Fresh decoder per zap: ${if (d.freshDecoderPerZap) "On" else "Off"}",
    ) + liveSignalDebugRows(d) + listOfNotNull(
        join(growth(window.uncorrected, "uncorrected"), growth(window.bitErrors, "BER"),
            growth(window.serverDrops, "server drops"), growth(window.clientDrops, "client drops"),
            growth(window.decoderDrops, "decoder drops")).takeIf { it.isNotEmpty() },
        join(change(window.bufferChangeMs / 1000.0, "s buffer"), change(window.queueChangeUs?.div(1000.0), "ms queue"),
            change(window.signalChange, "% signal"), change(window.snrChange, "% SNR")).takeIf { it.isNotEmpty() },
    )
    rows.forEach { StatsText(it) }
}

/** Raw failure fields only, without synthesis or protocol interpretation. */
internal fun failureStatRows(failed: AppPlaybackState.Failed): List<Pair<Int, String>> = listOfNotNull(
    failed.recoveryReason?.let { R.string.stats_recovery_reason to it.toString() },
    failed.playerErrorCode?.let { R.string.stats_error_code to it },
    failed.playerErrorClassification?.let { R.string.stats_error_classification to it },
    failed.targetResult?.let { R.string.stats_target_result to it.toString() },
)

internal fun formatVideoDecoderCounters(counters: AppVideoDecoderCounters, template: String): String =
    String.format(Locale.ROOT, template, counters.renderedOutputBufferCount, counters.droppedBufferCount,
        counters.skippedOutputBufferCount)
