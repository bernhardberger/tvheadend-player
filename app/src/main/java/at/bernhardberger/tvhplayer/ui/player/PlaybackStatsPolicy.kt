package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.di.PlayerStatsLevel
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource
import at.bernhardberger.tvhplayer.playback.AppPlaybackFormatDiagnostics
import java.util.ArrayDeque
import kotlin.math.round

/**
 * The level the card draws. The lower chrome's programme rows reach into the debug card's height, so
 * under the controls or the Banner the debug level draws the stream path card; the level stays.
 */
internal fun presentedStatsLevel(level: PlayerStatsLevel, lowerChromeShown: Boolean): PlayerStatsLevel =
    if (level == PlayerStatsLevel.DEBUG && lowerChromeShown) PlayerStatsLevel.STREAM_PATH else level

internal enum class StreamPathStep { TUNER, SERVER, APP, DECODER, SCREEN }

internal data class StatsWindow(
    val uncorrected: Long = 0,
    val bitErrors: Long = 0,
    val serverDrops: Long = 0,
    val clientDrops: Long = 0,
    val decoderDrops: Long = 0,
    val bufferChangeMs: Long = 0,
    val queueChangeUs: Long? = null,
    val signalChange: Double? = null,
    val snrChange: Double? = null,
    /** When the oldest counted growth leaves the 60 s span; null without growth. */
    val growthExpiresAtMillis: Long? = null,
) {
    val hasGrowth: Boolean get() = uncorrected > 0 || bitErrors > 0 || serverDrops > 0 || clientDrops > 0 || decoderDrops > 0
}

internal data class StreamPath(
    val steps: List<StreamPathStep>,
    val flagged: Set<StreamPathStep>,
) {
    val firstFlagged: StreamPathStep? get() = steps.firstOrNull { it in flagged }
}

/** Only facts flag a step. A low measurement, latency or empty buffer is not a diagnosis. */
internal fun streamPath(
    diagnostics: AppPlaybackDiagnostics,
    window: StatsWindow,
    outputRate: String?,
    requestedRate: String?,
): StreamPath {
    val live = diagnostics.source == AppPlaybackSource.LIVE_TV
    val tuner = live && diagnostics.live?.frontend != null
    val steps = buildList {
        if (tuner) add(StreamPathStep.TUNER)
        if (live) add(StreamPathStep.SERVER)
        add(StreamPathStep.APP)
        add(StreamPathStep.DECODER)
        add(StreamPathStep.SCREEN)
    }
    val flagged = buildSet {
        if (tuner && (diagnostics.live?.frontend?.state?.locked == false || window.uncorrected > 0)) {
            add(StreamPathStep.TUNER)
        }
        if (live && window.serverDrops > 0) add(StreamPathStep.SERVER)
        if (window.clientDrops > 0) add(StreamPathStep.APP)
        if (window.decoderDrops > 0) add(StreamPathStep.DECODER)
        if (outputRate != null && requestedRate != null && outputRate != requestedRate) add(StreamPathStep.SCREEN)
    }
    return StreamPath(steps, flagged)
}

/** The growth fact a stream path row leads with; Screen has none. */
internal fun pathGrowth(step: StreamPathStep, window: StatsWindow): Long = when (step) {
    StreamPathStep.TUNER -> window.uncorrected
    StreamPathStep.SERVER -> window.serverDrops
    StreamPathStep.APP -> window.clientDrops
    StreamPathStep.DECODER -> window.decoderDrops
    StreamPathStep.SCREEN -> 0
}

/** Digits up to 9999, then whole thousands (12k) and tenths of millions (1.2M), never rounded up. */
internal fun compactCount(value: Long): String = when {
    value < 10_000 -> value.toString()
    value < 1_000_000 -> "${value / 1_000}k"
    else -> "${value / 1_000_000}.${value / 100_000 % 10}M"
}

/** The codec family the stream path names; anything unknown stays as reported. */
internal fun videoCodecFamily(codec: String?, mimeType: String?): String? {
    val family = when (mimeType) {
        "video/avc" -> "H.264"
        "video/hevc" -> "HEVC"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/mpeg2" -> "MPEG-2"
        else -> when (codec?.substringBefore('.')) {
            "avc1", "avc3" -> "H.264"
            "hvc1", "hev1" -> "HEVC"
            "av01" -> "AV1"
            "vp09", "vp9" -> "VP9"
            else -> null
        }
    }
    return family ?: codec
}

/** Measured/nominal, the nominal alone without a measurement, and one value where both read the same. */
/**
 * The video size as its height where that is a standard raster, with the scan letter only when
 * [raster] (the chrome's declared raster badge, "1080i") is for the same height; else width×height.
 */
internal fun videoSizeLabel(video: AppPlaybackFormatDiagnostics?, raster: String?): String? {
    val height = video?.height ?: return video?.resolution
    if (height !in StandardHeights) return video.resolution
    return raster?.takeIf { it.trimEnd('i', 'p') == height.toString() } ?: height.toString()
}

private val StandardHeights = setOf(480, 576, 720, 1080, 1440, 2160, 4320)

internal fun frameRateText(measured: String?, nominal: String?): String? = when {
    measured == null && nominal == null -> null
    measured == null -> "$nominal fps"
    measured == nominal -> "$nominal fps"
    else -> "$measured/${nominal ?: "—"} fps"
}

/** A displayed change is absent, rather than a misleading "+0", at that value's precision. */
internal fun displayedStatsChange(value: Double?, decimals: Int): Double? {
    if (value == null) return null
    val scale = if (decimals == 1) 10.0 else 1.0
    return (round(value * scale) / scale).takeIf { it != 0.0 }
}

/**
 * Monotonic, app-side 60 s history. The key is the actual playback target, not the screen.
 * Counter resets and missing observations establish a baseline, never negative growth.
 * Keep one predecessor at the boundary to measure intervals; expire interval growth by
 * its observation time. No wall clock, polling or protocol state lives here.
 */
internal class PlaybackStatsHistory(private val nowMillis: () -> Long) {
    private data class Sample(
        val at: Long,
        val diagnostics: AppPlaybackDiagnostics,
        val growth: StatsWindow,
    )
    private val samples = ArrayDeque<Sample>()
    private var key: Any? = null
    private var source: AppPlaybackSource? = null
    private var liveSource: Any? = null

    fun update(target: Any?, diagnostics: AppPlaybackDiagnostics): StatsWindow {
        val now = nowMillis()
        if (target != key || source != diagnostics.source || liveSource != diagnostics.live?.source ||
            samples.lastOrNull()?.at?.let { now < it || now - it > 60_000L } == true) {
            samples.clear()
            key = target
            source = diagnostics.source
            liveSource = diagnostics.live?.source
        }
        val before = samples.lastOrNull()?.diagnostics
        val currentQueue = diagnostics.live?.queue
        val previousQueue = before?.live?.queue
        val growth = StatsWindow(
            uncorrected = growth(before?.live?.frontend?.uncorrectedBlockCount, diagnostics.live?.frontend?.uncorrectedBlockCount),
            bitErrors = growth(before?.live?.frontend?.bitErrorRateRaw, diagnostics.live?.frontend?.bitErrorRateRaw),
            serverDrops = growth(previousQueue?.droppedIFrameCount, currentQueue?.droppedIFrameCount) +
                growth(previousQueue?.droppedPFrameCount, currentQueue?.droppedPFrameCount) +
                growth(previousQueue?.droppedBFrameCount, currentQueue?.droppedBFrameCount),
            clientDrops = growth(before?.live?.clientDroppedPacketCount, diagnostics.live?.clientDroppedPacketCount),
            decoderDrops = growth(before?.videoDecoderCounters?.droppedBufferCount?.toLong(), diagnostics.videoDecoderCounters?.droppedBufferCount?.toLong()),
        )
        samples.addLast(Sample(now, diagnostics, growth))
        val cutoff = now - 60_000L
        while (samples.size > 1 && samples.elementAt(1).at <= cutoff) samples.removeFirst()
        val baseline = samples.first.diagnostics
        val intervals = samples.filter { it.at > cutoff && it !== samples.first }
        return StatsWindow(
            uncorrected = intervals.sumOf { it.growth.uncorrected },
            bitErrors = intervals.sumOf { it.growth.bitErrors },
            serverDrops = intervals.sumOf { it.growth.serverDrops },
            clientDrops = intervals.sumOf { it.growth.clientDrops },
            decoderDrops = intervals.sumOf { it.growth.decoderDrops },
            bufferChangeMs = diagnostics.bufferedMs - baseline.bufferedMs,
            queueChangeUs = change(baseline.live?.queue?.mediaSpanMicroseconds, currentQueue?.mediaSpanMicroseconds),
            signalChange = change(baseline.live?.frontend?.relativeSignalPercent, diagnostics.live?.frontend?.relativeSignalPercent),
            snrChange = change(baseline.live?.frontend?.relativeSnrPercent, diagnostics.live?.frontend?.relativeSnrPercent),
            growthExpiresAtMillis = intervals.filter { it.growth.hasGrowth }.minOfOrNull { it.at + 60_000L },
        )
    }

    private fun growth(before: Long?, after: Long?): Long =
        if (before == null || after == null) 0 else (after - before).coerceAtLeast(0)
    private fun change(before: Long?, after: Long?): Long? =
        if (before == null || after == null) null else after - before
    private fun change(before: Double?, after: Double?): Double? =
        if (before == null || after == null) null else after - before
}
