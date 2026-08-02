package at.bernhardberger.tvhplayer.player

import androidx.media3.common.Format
import java.util.concurrent.atomic.AtomicLong

enum class PlaybackDiagnosticsSource {
    NONE,
    LIVE_TV,
    RECORDING,
}

enum class PlaybackThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
}

data class PlaybackTunerDiagnostics(
    val status: String? = null,
    val signalPercent: Float? = null,
    val signalMilliDbm: Long? = null,
    val snrPercent: Float? = null,
    val snrMilliDb: Long? = null,
    val bitErrorRate: Long? = null,
    val uncorrectedBlocks: Long? = null,
)

data class PlaybackQueueDiagnostics(
    val packets: Long? = null,
    val bytes: Long? = null,
    val delayMicros: Long? = null,
    val bFrameDrops: Long? = null,
    val pFrameDrops: Long? = null,
    val iFrameDrops: Long? = null,
)

data class PlaybackTransportDiagnostics(
    val tuner: PlaybackTunerDiagnostics? = null,
    val queue: PlaybackQueueDiagnostics? = null,
)

data class PlaybackOutputMode(
    val width: Int,
    val height: Int,
    val refreshRateHz: Float,
)

data class PlaybackSystemDiagnostics(
    val outputMode: PlaybackOutputMode? = null,
    val thermalLevel: PlaybackThermalLevel? = null,
    val appPssBytes: Long? = null,
    val lowMemory: Boolean? = null,
)

data class PlaybackFormatDiagnostics(
    val codec: String?,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val language: String? = null,
    val channelCount: Int? = null,
    val sampleRateHz: Int? = null,
)

data class PlaybackDiagnosticsSnapshot(
    val source: PlaybackDiagnosticsSource = PlaybackDiagnosticsSource.NONE,
    val state: PlaybackSessionState = PlaybackSessionState.Idle,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val bufferedMs: Long = 0L,
    val video: PlaybackFormatDiagnostics? = null,
    val videoDecoder: String? = null,
    val renderedFrames: Int = 0,
    val droppedFrames: Int = 0,
    val audio: PlaybackFormatDiagnostics? = null,
    val audioDecoder: String? = null,
    val audioUnderruns: Int = 0,
    val readRateBitsPerSecond: Long? = null,
    val transport: PlaybackTransportDiagnostics? = null,
    val system: PlaybackSystemDiagnostics? = null,
)

internal fun Format.toPlaybackFormatDiagnostics(): PlaybackFormatDiagnostics =
    PlaybackFormatDiagnostics(
        codec = (codecs ?: sampleMimeType)?.takeIf(String::isNotBlank),
        resolution = if (width != Format.NO_VALUE && height != Format.NO_VALUE) {
            "${width}x$height"
        } else {
            null
        },
        frameRate = frameRate.takeIf { it != Format.NO_VALUE.toFloat() && it > 0f },
        language = language?.takeIf { it.isNotBlank() && it != "und" },
        channelCount = channelCount.takeIf { it != Format.NO_VALUE },
        sampleRateHz = sampleRate.takeIf { it != Format.NO_VALUE },
    )

internal fun readRateBitsPerSecond(
    previousBytes: Long,
    currentBytes: Long,
    elapsedMillis: Long,
): Long? {
    val byteDelta = currentBytes - previousBytes
    if (elapsedMillis <= 0L || byteDelta < 0L) return null
    return byteDelta * 8_000L / elapsedMillis
}

internal fun decoderCounterDelta(current: Int, baseline: Int): Int =
    (current - baseline).coerceAtLeast(0)

fun droppedFramePercentage(rendered: Int, dropped: Int): Float? {
    val total = rendered + dropped
    return if (total > 0) dropped * 100f / total else null
}

internal fun playbackThermalLevel(status: Int): PlaybackThermalLevel? = when (status) {
    0 -> PlaybackThermalLevel.NONE
    1 -> PlaybackThermalLevel.LIGHT
    2 -> PlaybackThermalLevel.MODERATE
    3 -> PlaybackThermalLevel.SEVERE
    4 -> PlaybackThermalLevel.CRITICAL
    5 -> PlaybackThermalLevel.EMERGENCY
    6 -> PlaybackThermalLevel.SHUTDOWN
    else -> null
}

internal fun relativeSignalPercent(value: Long?): Float? =
    value?.takeIf { it in 0L..65_535L }?.let { it * 100f / 65_535f }

internal class PlaybackReadMetrics {
    private val bytesRead = AtomicLong()

    fun record(byteCount: Int) {
        if (byteCount > 0) bytesRead.addAndGet(byteCount.toLong())
    }

    fun totalBytesRead(): Long = bytesRead.get()
}
