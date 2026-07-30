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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.ui.common.formatHms
import java.util.Locale

@Composable
internal fun PlaybackStatsOverlay(
    diagnostics: PlaybackDiagnosticsSnapshot,
    aspectRatio: AspectRatioMode,
    modifier: Modifier = Modifier,
    timeshiftState: TimeshiftState? = null,
) {
    val locale = Locale.ROOT
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
        modifier = modifier
            .width(820.dp)
            .testTag("playback-stats-overlay"),
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
                modifier = Modifier.semantics { heading() },
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
                    PlaybackSessionState.Idle -> stringResource(R.string.stats_state_idle)
                    PlaybackSessionState.Starting -> stringResource(R.string.stats_state_starting)
                    PlaybackSessionState.Playing -> stringResource(
                        if (diagnostics.isPlaying) {
                            R.string.stats_state_playing
                        } else {
                            R.string.stats_state_paused
                        },
                    )
                    PlaybackSessionState.Finished -> stringResource(R.string.stats_state_finished)
                    is PlaybackSessionState.Recovering ->
                        stringResource(R.string.stats_state_recovering)
                    is PlaybackSessionState.Failed -> stringResource(R.string.stats_state_failed)
                },
                    )
                    StatLine(
                stringResource(R.string.stats_timing),
                timeshiftState?.let { state ->
                    if (!state.available || timeshiftPositionPresentation(state).atLiveEdge) {
                        stringResource(R.string.stats_timeshift_live)
                    } else {
                        stringResource(
                            R.string.stats_timeshift_behind_live,
                            formatHms(-state.positionMs / 1_000L),
                        )
                    }
                } ?: diagnostics.durationMs?.let { durationMs ->
                    stringResource(
                        R.string.stats_timing_buffered_with_duration,
                        formatHms(diagnostics.positionMs / 1_000L),
                        formatHms(durationMs / 1_000L),
                        diagnostics.bufferedMs / 1_000L,
                    )
                } ?: stringResource(
                    R.string.stats_timing_buffered,
                    formatHms(diagnostics.positionMs / 1_000L),
                    diagnostics.bufferedMs / 1_000L,
                ),
                    )
                    StatLine(
                stringResource(R.string.stats_video),
                listOfNotNull(
                    video?.codec,
                    video?.resolution,
                    video?.frameRate?.let {
                        stringResource(R.string.stats_frame_rate, it.oneDecimal(locale))
                    },
                )
                    .joinToString(" · ").ifBlank { stringResource(R.string.stats_unavailable) },
                    )
                    StatLine(
                stringResource(R.string.stats_video_decoder),
                diagnostics.videoDecoder ?: stringResource(R.string.stats_unavailable),
                    )
                    StatLine(
                stringResource(R.string.stats_frames),
                droppedPercent?.let {
                    stringResource(
                        R.string.stats_frames_value_with_percentage,
                        formatCount(diagnostics.renderedFrames.toLong(), locale),
                        formatCount(diagnostics.droppedFrames.toLong(), locale),
                        it.oneDecimal(locale),
                    )
                } ?: stringResource(
                    R.string.stats_frames_value,
                    formatCount(diagnostics.renderedFrames.toLong(), locale),
                    formatCount(diagnostics.droppedFrames.toLong(), locale),
                ),
                    )
                    StatLine(
                stringResource(R.string.stats_audio),
                listOfNotNull(
                    audio?.codec,
                    audio?.language,
                    audio?.channelCount?.let {
                        stringResource(R.string.stats_audio_channels, it)
                    },
                    audio?.sampleRateHz?.let {
                        stringResource(R.string.stats_sample_rate, formatCount(it.toLong(), locale))
                    },
                ).joinToString(" · ").ifBlank { stringResource(R.string.stats_unavailable) },
                    )
                    StatLine(
                stringResource(R.string.stats_audio_decoder),
                diagnostics.audioDecoder ?: stringResource(R.string.stats_unavailable),
                    )
                    StatLine(
                        stringResource(R.string.stats_underruns),
                        formatCount(diagnostics.audioUnderruns.toLong(), locale),
                    )
                    StatLine(
                stringResource(
                    if (diagnostics.source == PlaybackDiagnosticsSource.RECORDING) {
                        R.string.stats_file_read_rate
                    } else {
                        R.string.stats_stream_read_rate
                    }
                ),
                diagnostics.readRateBitsPerSecond?.let { formatBitRate(it, locale) }
                    ?: stringResource(R.string.stats_unavailable),
                    )
                    StatLine(
                stringResource(R.string.stats_display_mode),
                stringResource(
                    when (aspectRatio) {
                        AspectRatioMode.FIT -> R.string.stats_display_mode_auto
                        AspectRatioMode.FORCE_16_9 -> R.string.stats_display_mode_16_9
                        AspectRatioMode.FORCE_4_3 -> R.string.stats_display_mode_4_3
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
                        (tuner.signalMilliDbm?.let {
                            formatMilliUnit(it, R.string.stats_unit_dbm, locale)
                        } ?: tuner.signalPercent?.let { formatPercent(it, locale) })?.let {
                            StatLine(stringResource(R.string.stats_signal), it)
                        }
                        (tuner.snrMilliDb?.let {
                            formatMilliUnit(it, R.string.stats_unit_db, locale)
                        } ?: tuner.snrPercent?.let { formatPercent(it, locale) })?.let {
                            StatLine(stringResource(R.string.stats_snr), it)
                        }
                        tuner.bitErrorRate?.let {
                            StatLine(stringResource(R.string.stats_ber), formatCount(it, locale))
                        }
                        tuner.uncorrectedBlocks?.let {
                            StatLine(
                                stringResource(R.string.stats_uncorrected_blocks),
                                formatCount(it, locale),
                            )
                        }
                    }

                    if (queue != null) {
                        StatsSection(stringResource(R.string.stats_server_queue))
                        StatLine(
                            stringResource(R.string.stats_queued),
                            listOfNotNull(
                                queue.packets?.let {
                                    stringResource(
                                        R.string.stats_packet_count,
                                        formatCount(it, locale),
                                    )
                                },
                                queue.bytes?.let { formatBytes(it, locale) },
                            ).joinToString(" · ")
                                .ifBlank { stringResource(R.string.stats_unavailable) },
                        )
                        queue.delayMicros?.let {
                            StatLine(
                                stringResource(R.string.stats_queue_delay),
                                stringResource(
                                    R.string.stats_queue_delay_value,
                                    formatDecimal(it / 1_000.0, 1, locale),
                                ),
                            )
                        }
                        StatLine(
                            stringResource(R.string.stats_server_drops),
                            stringResource(
                                R.string.stats_server_drops_value,
                                formatCount(queue.iFrameDrops ?: 0, locale),
                                formatCount(queue.pFrameDrops ?: 0, locale),
                                formatCount(queue.bFrameDrops ?: 0, locale),
                            ),
                        )
                    }

                    if (system != null) {
                        StatsSection(stringResource(R.string.stats_system))
                        system.outputMode?.let { mode ->
                            StatLine(
                                stringResource(R.string.stats_output),
                                stringResource(
                                    R.string.stats_output_value,
                                    mode.width,
                                    mode.height,
                                    mode.refreshRateHz.oneDecimal(locale),
                                ),
                            )
                        }
                        system.thermalLevel?.let { level ->
                            StatLine(
                                stringResource(R.string.stats_thermal),
                                stringResource(level.labelResource()),
                            )
                        }
                        system.appPssBytes?.let { bytes ->
                            StatLine(
                                stringResource(R.string.stats_app_memory),
                                formatBytes(bytes, locale),
                            )
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
        modifier = Modifier.padding(top = 5.dp).semantics { heading() },
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

private fun Float.oneDecimal(locale: Locale): String = formatDecimal(toDouble(), 1, locale)

@Composable
private fun formatBitRate(bitsPerSecond: Long, locale: Locale): String = when {
    bitsPerSecond >= 1_000_000L -> stringResource(
        R.string.stats_bit_rate_mbps,
        formatDecimal(bitsPerSecond / 1_000_000.0, 2, locale),
    )
    bitsPerSecond >= 1_000L -> stringResource(
        R.string.stats_bit_rate_kbps,
        formatDecimal(bitsPerSecond / 1_000.0, 0, locale),
    )
    else -> stringResource(
        R.string.stats_bit_rate_bps,
        formatCount(bitsPerSecond, locale),
    )
}

private fun formatPercent(value: Float, locale: Locale): String =
    "${value.oneDecimal(locale)}%"

@Composable
private fun formatMilliUnit(value: Long, unitResource: Int, locale: Locale): String =
    stringResource(unitResource, formatDecimal(value / 1_000.0, 1, locale))

private fun formatCount(value: Long, locale: Locale): String = String.format(locale, "%,d", value)

@Composable
private fun formatBytes(bytes: Long, locale: Locale): String = when {
    bytes >= 1024L * 1024L -> stringResource(
        R.string.stats_bytes_mb,
        formatDecimal(bytes / (1024.0 * 1024.0), 1, locale),
    )
    bytes >= 1024L -> stringResource(
        R.string.stats_bytes_kb,
        formatDecimal(bytes / 1024.0, 1, locale),
    )
    else -> stringResource(R.string.stats_bytes_b, formatCount(bytes, locale))
}

private fun formatDecimal(value: Double, fractionDigits: Int, locale: Locale): String =
    String.format(locale, "%.${fractionDigits}f", value)

private fun PlaybackThermalLevel.labelResource(): Int = when (this) {
    PlaybackThermalLevel.NONE -> R.string.stats_thermal_none
    PlaybackThermalLevel.LIGHT -> R.string.stats_thermal_light
    PlaybackThermalLevel.MODERATE -> R.string.stats_thermal_moderate
    PlaybackThermalLevel.SEVERE -> R.string.stats_thermal_severe
    PlaybackThermalLevel.CRITICAL -> R.string.stats_thermal_critical
    PlaybackThermalLevel.EMERGENCY -> R.string.stats_thermal_emergency
    PlaybackThermalLevel.SHUTDOWN -> R.string.stats_thermal_shutdown
}
