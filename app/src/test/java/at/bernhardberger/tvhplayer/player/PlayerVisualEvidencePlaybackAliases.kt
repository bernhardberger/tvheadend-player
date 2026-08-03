@file:kotlin.OptIn(
    at.bernhardberger.tvheadend.playback.ExperimentalPlaybackDiagnosticsApi::class,
)

package at.bernhardberger.tvhplayer.player

internal enum class PlaybackDiagnosticsSource {
    NONE,
    LIVE_TV,
    RECORDING,
}

internal enum class PlaybackThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
}

internal data class PlaybackTunerDiagnostics(
    val status: String? = null,
    val signalPercent: Float? = null,
    val signalMilliDbm: Long? = null,
    val snrPercent: Float? = null,
    val snrMilliDb: Long? = null,
    val bitErrorRate: Long? = null,
    val uncorrectedBlocks: Long? = null,
)

internal data class PlaybackQueueDiagnostics(
    val packets: Long? = null,
    val bytes: Long? = null,
    val delayMicros: Long? = null,
    val bFrameDrops: Long? = null,
    val pFrameDrops: Long? = null,
    val iFrameDrops: Long? = null,
)

internal data class PlaybackTransportDiagnostics(
    val tuner: PlaybackTunerDiagnostics? = null,
    val queue: PlaybackQueueDiagnostics? = null,
)

internal data class PlaybackOutputMode(
    val width: Int,
    val height: Int,
    val refreshRateHz: Float,
)

internal data class PlaybackSystemDiagnostics(
    val outputMode: PlaybackOutputMode? = null,
    val thermalLevel: PlaybackThermalLevel? = null,
    val appPssBytes: Long? = null,
    val lowMemory: Boolean? = null,
)

internal data class PlaybackFormatDiagnostics(
    val codec: String?,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val language: String? = null,
    val channelCount: Int? = null,
    val sampleRateHz: Int? = null,
)

object PlaybackSessionState {
    val Playing: at.bernhardberger.tvheadend.playback.PlaybackSessionState =
        at.bernhardberger.tvheadend.playback.PlaybackSessionState.Playing

    fun Recovering(retryDelayMillis: Long): at.bernhardberger.tvheadend.playback.PlaybackSessionState =
        at.bernhardberger.tvheadend.playback.PlaybackSessionState.Recovering(retryDelayMillis)
}

internal data class PlaybackDiagnosticsSnapshot(
    val source: PlaybackDiagnosticsSource = PlaybackDiagnosticsSource.NONE,
    val state: at.bernhardberger.tvheadend.playback.PlaybackSessionState =
        at.bernhardberger.tvheadend.playback.PlaybackSessionState.Idle,
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
) {
    internal fun toSdkPlaybackDiagnosticsSnapshot() =
        at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSnapshot(
            source = source.toSdk(),
            state = state,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            video = video?.toSdk(),
            videoDecoder = videoDecoder,
            renderedFrames = renderedFrames,
            droppedFrames = droppedFrames,
            audio = audio?.toSdk(),
            audioDecoder = audioDecoder,
            audioUnderruns = audioUnderruns,
            readRateBitsPerSecond = readRateBitsPerSecond,
            transport = transport?.toSdk(),
            system = system?.toSdk(),
        )
}

private fun PlaybackDiagnosticsSource.toSdk() = when (this) {
    PlaybackDiagnosticsSource.NONE ->
        at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSource.NONE
    PlaybackDiagnosticsSource.LIVE_TV ->
        at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSource.LIVE_TV
    PlaybackDiagnosticsSource.RECORDING ->
        at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSource.RECORDING
}

private fun PlaybackFormatDiagnostics.toSdk() =
    at.bernhardberger.tvheadend.playback.PlaybackFormatDiagnostics(
        codec = codec,
        resolution = resolution,
        frameRate = frameRate,
        language = language,
        channelCount = channelCount,
        sampleRateHz = sampleRateHz,
    )

private fun PlaybackTransportDiagnostics.toSdk() =
    at.bernhardberger.tvheadend.playback.PlaybackTransportDiagnostics(
        tuner = tuner?.let {
            at.bernhardberger.tvheadend.playback.PlaybackTunerDiagnostics(
                status = it.status,
                signalPercent = it.signalPercent,
                signalMilliDbm = it.signalMilliDbm,
                snrPercent = it.snrPercent,
                snrMilliDb = it.snrMilliDb,
                bitErrorRate = it.bitErrorRate,
                uncorrectedBlocks = it.uncorrectedBlocks,
            )
        },
        queue = queue?.let {
            at.bernhardberger.tvheadend.playback.PlaybackQueueDiagnostics(
                packets = it.packets,
                bytes = it.bytes,
                delayMicros = it.delayMicros,
                bFrameDrops = it.bFrameDrops,
                pFrameDrops = it.pFrameDrops,
                iFrameDrops = it.iFrameDrops,
            )
        },
    )

private fun PlaybackSystemDiagnostics.toSdk() =
    at.bernhardberger.tvheadend.playback.PlaybackSystemDiagnostics(
        outputMode = outputMode?.let {
            at.bernhardberger.tvheadend.playback.PlaybackOutputMode(
                width = it.width,
                height = it.height,
                refreshRateHz = it.refreshRateHz,
            )
        },
        thermalLevel = thermalLevel?.toSdk(),
        appPssBytes = appPssBytes,
        lowMemory = lowMemory,
    )

private fun PlaybackThermalLevel.toSdk() = when (this) {
    PlaybackThermalLevel.NONE -> at.bernhardberger.tvheadend.playback.PlaybackThermalLevel.NONE
    PlaybackThermalLevel.LIGHT -> at.bernhardberger.tvheadend.playback.PlaybackThermalLevel.LIGHT
    PlaybackThermalLevel.MODERATE ->
        at.bernhardberger.tvheadend.playback.PlaybackThermalLevel.MODERATE
    PlaybackThermalLevel.SEVERE -> at.bernhardberger.tvheadend.playback.PlaybackThermalLevel.SEVERE
    PlaybackThermalLevel.CRITICAL ->
        at.bernhardberger.tvheadend.playback.PlaybackThermalLevel.CRITICAL
    PlaybackThermalLevel.EMERGENCY ->
        at.bernhardberger.tvheadend.playback.PlaybackThermalLevel.EMERGENCY
    PlaybackThermalLevel.SHUTDOWN ->
        at.bernhardberger.tvheadend.playback.PlaybackThermalLevel.SHUTDOWN
}
