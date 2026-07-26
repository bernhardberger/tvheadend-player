package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.player.PlaybackDiagnosticsSnapshot
import at.bernhardberger.tvhplayer.player.PlaybackDiagnosticsSource
import at.bernhardberger.tvhplayer.player.PlaybackSessionState
import at.bernhardberger.tvhplayer.player.PlaybackThermalLevel
import at.bernhardberger.tvhplayer.player.droppedFramePercentage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.ui.common.formatHms
import java.util.Locale

@Composable
internal fun PlaybackStatsOverlay(
    diagnostics: PlaybackDiagnosticsSnapshot,
    aspectRatio: AspectRatioMode,
    modifier: Modifier = Modifier,
    timeshiftState: TimeshiftState? = null,
) {
    val video = diagnostics.video
    val audio = diagnostics.audio
    val tuner = diagnostics.transport?.tuner
    val queue = diagnostics.transport?.queue
    val system = diagnostics.system
    val droppedPercent = droppedFramePercentage(
        diagnostics.renderedFrames,
        diagnostics.droppedFrames,
    )
    Surface(
        modifier = modifier.width(820.dp),
        colors = SurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.86f),
            contentColor = Color.White,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_for_nerds),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    StatsSection(stringResource(R.string.stats_playback))
                    StatLine(
                stringResource(R.string.stats_source),
                when (diagnostics.source) {
                    PlaybackDiagnosticsSource.LIVE_TV -> stringResource(R.string.stats_source_live)
                    PlaybackDiagnosticsSource.RECORDING -> stringResource(R.string.stats_source_recording)
                    PlaybackDiagnosticsSource.NONE -> stringResource(R.string.stats_unavailable)
                },
                    )
                    StatLine(
                stringResource(R.string.stats_state),
                when (diagnostics.state) {
                    PlaybackSessionState.Idle -> "Idle"
                    PlaybackSessionState.Starting -> "Starting"
                    PlaybackSessionState.Playing -> if (diagnostics.isPlaying) "Playing" else "Paused"
                    PlaybackSessionState.Finished -> "Finished"
                    is PlaybackSessionState.Recovering -> "Recovering"
                    is PlaybackSessionState.Failed -> "Failed"
                },
                    )
                    StatLine(
                stringResource(R.string.stats_timing),
                timeshiftState?.let { state ->
                    if (!state.available || state.positionMs >= -1_000L) {
                        stringResource(R.string.timeshift_live)
                    } else {
                        stringResource(
                            R.string.timeshift_behind_live,
                            formatHms(-state.positionMs / 1_000L),
                        )
                    }
                } ?: buildString {
                    append(formatHms(diagnostics.positionMs / 1_000L))
                    diagnostics.durationMs?.let { append(" / ${formatHms(it / 1_000L)}") }
                    append("  buffer ${diagnostics.bufferedMs / 1_000L}s")
                },
                    )
                    StatLine(
                stringResource(R.string.stats_video),
                listOfNotNull(video?.codec, video?.resolution, video?.frameRate?.let { "${it.oneDecimal()} fps" })
                    .joinToString(" · ").ifBlank { stringResource(R.string.stats_unavailable) },
                    )
                    StatLine(
                stringResource(R.string.stats_video_decoder),
                diagnostics.videoDecoder ?: stringResource(R.string.stats_unavailable),
                    )
                    StatLine(
                stringResource(R.string.stats_frames),
                buildString {
                    append("${diagnostics.renderedFrames} rendered · ${diagnostics.droppedFrames} dropped")
                    droppedPercent?.let { append(" (${it.oneDecimal()}%)") }
                },
                    )
                    StatLine(
                stringResource(R.string.stats_audio),
                listOfNotNull(
                    audio?.codec,
                    audio?.language,
                    audio?.channelCount?.let { "${it}ch" },
                    audio?.sampleRateHz?.let { "${it} Hz" },
                ).joinToString(" · ").ifBlank { stringResource(R.string.stats_unavailable) },
                    )
                    StatLine(
                stringResource(R.string.stats_audio_decoder),
                diagnostics.audioDecoder ?: stringResource(R.string.stats_unavailable),
                    )
                    StatLine(
                        stringResource(R.string.stats_underruns),
                        diagnostics.audioUnderruns.toString(),
                    )
                    StatLine(
                stringResource(
                    if (diagnostics.source == PlaybackDiagnosticsSource.RECORDING) {
                        R.string.stats_file_read_rate
                    } else {
                        R.string.stats_stream_read_rate
                    }
                ),
                diagnostics.readRateBitsPerSecond?.let(::formatBitRate)
                    ?: stringResource(R.string.stats_unavailable),
                    )
                    StatLine(
                stringResource(R.string.display_mode),
                stringResource(
                    when (aspectRatio) {
                        AspectRatioMode.FIT -> R.string.display_mode_auto
                        AspectRatioMode.FORCE_16_9 -> R.string.display_mode_16_9
                        AspectRatioMode.FORCE_4_3 -> R.string.display_mode_4_3
                    }
                ),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (tuner != null) {
                        StatsSection(stringResource(R.string.stats_tuner))
                        tuner.status?.let {
                            StatLine(stringResource(R.string.stats_tuner_status), it)
                        }
                        (tuner.signalMilliDbm?.let { formatMilliUnit(it, "dBm") }
                            ?: tuner.signalPercent?.let(::formatPercent))?.let {
                            StatLine(stringResource(R.string.stats_signal), it)
                        }
                        (tuner.snrMilliDb?.let { formatMilliUnit(it, "dB") }
                            ?: tuner.snrPercent?.let(::formatPercent))?.let {
                            StatLine(stringResource(R.string.stats_snr), it)
                        }
                        tuner.bitErrorRate?.let {
                            StatLine(stringResource(R.string.stats_ber), formatCount(it))
                        }
                        tuner.uncorrectedBlocks?.let {
                            StatLine(
                                stringResource(R.string.stats_uncorrected_blocks),
                                formatCount(it),
                            )
                        }
                    }

                    if (queue != null) {
                        StatsSection(stringResource(R.string.stats_server_queue))
                        StatLine(
                            stringResource(R.string.stats_queued),
                            listOfNotNull(
                                queue.packets?.let {
                                    stringResource(R.string.stats_packet_count, formatCount(it))
                                },
                                queue.bytes?.let(::formatBytes),
                            ).joinToString(" · ")
                                .ifBlank { stringResource(R.string.stats_unavailable) },
                        )
                        queue.delayMicros?.let {
                            StatLine(
                                stringResource(R.string.stats_queue_delay),
                                String.format(Locale.ROOT, "%.1f ms", it / 1_000.0),
                            )
                        }
                        StatLine(
                            stringResource(R.string.stats_server_drops),
                            "I ${formatCount(queue.iFrameDrops ?: 0)} · " +
                                "P ${formatCount(queue.pFrameDrops ?: 0)} · " +
                                "B ${formatCount(queue.bFrameDrops ?: 0)}",
                        )
                    }

                    if (system != null) {
                        StatsSection(stringResource(R.string.stats_system))
                        system.outputMode?.let { mode ->
                            StatLine(
                                stringResource(R.string.stats_output),
                                "${mode.width}x${mode.height} @ ${mode.refreshRateHz.oneDecimal()} Hz",
                            )
                        }
                        system.thermalLevel?.let { level ->
                            StatLine(
                                stringResource(R.string.stats_thermal),
                                stringResource(level.labelResource()),
                            )
                        }
                        system.appPssBytes?.let { bytes ->
                            StatLine(stringResource(R.string.stats_app_memory), formatBytes(bytes))
                        }
                        system.lowMemory?.let { lowMemory ->
                            StatLine(
                                stringResource(R.string.stats_low_memory),
                                stringResource(
                                    if (lowMemory) R.string.stats_yes else R.string.stats_no
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSection(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Float.oneDecimal(): String = String.format(Locale.ROOT, "%.1f", this)

private fun formatBitRate(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000L -> String.format(
        Locale.ROOT,
        "%.2f Mbps",
        bitsPerSecond / 1_000_000.0,
    )
    bitsPerSecond >= 1_000L -> String.format(Locale.ROOT, "%.0f kbps", bitsPerSecond / 1_000.0)
    else -> "$bitsPerSecond bps"
}

private fun formatPercent(value: Float): String =
    String.format(Locale.ROOT, "%.1f%%", value)

private fun formatMilliUnit(value: Long, unit: String): String =
    String.format(Locale.ROOT, "%.1f %s", value / 1_000.0, unit)

private fun formatCount(value: Long): String = String.format(Locale.ROOT, "%,d", value)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun PlaybackThermalLevel.labelResource(): Int = when (this) {
    PlaybackThermalLevel.NONE -> R.string.stats_thermal_none
    PlaybackThermalLevel.LIGHT -> R.string.stats_thermal_light
    PlaybackThermalLevel.MODERATE -> R.string.stats_thermal_moderate
    PlaybackThermalLevel.SEVERE -> R.string.stats_thermal_severe
    PlaybackThermalLevel.CRITICAL -> R.string.stats_thermal_critical
    PlaybackThermalLevel.EMERGENCY -> R.string.stats_thermal_emergency
    PlaybackThermalLevel.SHUTDOWN -> R.string.stats_thermal_shutdown
}
