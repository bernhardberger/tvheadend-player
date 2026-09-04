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
import at.bernhardberger.tvheadend.sdk.playback.LiveFrontendState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.ui.common.formatHms
import java.util.Locale

@Composable
internal fun PlaybackStatsOverlay(
    diagnostics: AppPlaybackDiagnostics,
    aspectRatio: AspectRatioMode,
    modifier: Modifier = Modifier,
    timeshiftState: AppTimeshiftState? = null,
) {
    val locale = Locale.ROOT
    val video = diagnostics.video
    val audio = diagnostics.audio
    val live = diagnostics.live
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                StatsSection(stringResource(R.string.stats_playback))
                StatLine(
                    stringResource(R.string.stats_source),
                    when (diagnostics.source) {
                        AppPlaybackSource.LIVE_TV -> stringResource(R.string.stats_source_live)
                        AppPlaybackSource.RECORDING -> stringResource(R.string.stats_source_recording)
                        AppPlaybackSource.NONE -> stringResource(R.string.stats_unavailable)
                    },
                )
                StatLine(
                    stringResource(R.string.stats_state),
                    when (diagnostics.state) {
                        AppPlaybackState.Idle -> stringResource(R.string.stats_state_idle)
                        AppPlaybackState.Starting -> stringResource(R.string.stats_state_starting)
                        AppPlaybackState.Playing -> stringResource(
                            if (diagnostics.isPlaying) {
                                R.string.stats_state_playing
                            } else {
                                R.string.stats_state_paused
                            },
                        )
                        AppPlaybackState.Finished -> stringResource(R.string.stats_state_finished)
                        is AppPlaybackState.Recovering ->
                            stringResource(R.string.stats_state_recovering)
                        is AppPlaybackState.Failed -> stringResource(R.string.stats_state_failed)
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
                    ).joinToString(" · ").ifBlank {
                        stringResource(R.string.stats_unavailable)
                    },
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
                            stringResource(
                                R.string.stats_sample_rate,
                                formatCount(it.toLong(), locale),
                            )
                        },
                    ).joinToString(" · ").ifBlank {
                        stringResource(R.string.stats_unavailable)
                    },
                )
                StatLine(
                    stringResource(R.string.stats_display_mode),
                    stringResource(
                        when (aspectRatio) {
                            AspectRatioMode.FIT -> R.string.stats_display_mode_auto
                            AspectRatioMode.FORCE_16_9 -> R.string.stats_display_mode_16_9
                            AspectRatioMode.FORCE_4_3 -> R.string.stats_display_mode_4_3
                        },
                    ),
                )
                val tunerSource = live?.source
                val frontend = live?.frontend
                if (tunerSource != null || frontend != null) {
                    StatsSection(stringResource(R.string.stats_tuner))
                    listOfNotNull(
                        tunerSource?.adapterName,
                        tunerSource?.muxName,
                        tunerSource?.networkName,
                        tunerSource?.providerName,
                        tunerSource?.serviceName,
                    ).takeIf { it.isNotEmpty() }?.let { values ->
                        StatLine(
                            stringResource(R.string.stats_source),
                            values.joinToString(" · "),
                        )
                    }
                    frontend?.state?.let { state ->
                        StatLine(
                            stringResource(R.string.stats_tuner_status),
                            stringResource(state.presentationResource()),
                        )
                    }
                    frontend?.let { values ->
                        formattedMeasurement(
                            relativePercent = values.relativeSignalPercent,
                            absoluteValue = values.absoluteSignalDbm,
                            absoluteUnit = R.string.stats_unit_dbm,
                            locale = locale,
                        )?.let {
                            StatLine(stringResource(R.string.stats_signal), it)
                        }
                        formattedMeasurement(
                            relativePercent = values.relativeSnrPercent,
                            absoluteValue = values.absoluteSnrDecibels,
                            absoluteUnit = R.string.stats_unit_db,
                            locale = locale,
                        )?.let {
                            StatLine(stringResource(R.string.stats_snr), it)
                        }
                        values.bitErrorRateRaw?.let {
                            StatLine(
                                stringResource(R.string.stats_ber),
                                formatCount(it, locale),
                            )
                        }
                        values.uncorrectedBlockCount?.let {
                            StatLine(
                                stringResource(R.string.stats_uncorrected_blocks),
                                formatCount(it, locale),
                            )
                        }
                    }
                }
                live?.queue?.let { queue ->
                    StatsSection(stringResource(R.string.stats_server_queue))
                    StatLine(
                        stringResource(R.string.stats_queued),
                        listOf(
                            stringResource(
                                R.string.stats_packet_count,
                                formatCount(queue.packetCount, locale),
                            ),
                            formatBytes(queue.byteCount, locale),
                        ).joinToString(" · "),
                    )
                    queue.mediaSpanMicroseconds?.let { microseconds ->
                        StatLine(
                            stringResource(R.string.stats_queue_delay),
                            stringResource(
                                R.string.stats_queue_delay_value,
                                formatDecimal(microseconds / 1_000.0, 1, locale),
                            ),
                        )
                    }
                    StatLine(
                        stringResource(R.string.stats_server_drops),
                        stringResource(
                            R.string.stats_server_drops_value,
                            formatCount(queue.droppedIFrameCount, locale),
                            formatCount(queue.droppedPFrameCount, locale),
                            formatCount(queue.droppedBFrameCount, locale),
                        ),
                    )
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

private fun formatCount(value: Long, locale: Locale): String = String.format(locale, "%,d", value)

private fun formatDecimal(value: Double, fractionDigits: Int, locale: Locale): String =
    String.format(locale, "%.${fractionDigits}f", value)

@Composable
private fun formattedMeasurement(
    relativePercent: Double?,
    absoluteValue: Double?,
    absoluteUnit: Int,
    locale: Locale,
): String? = listOfNotNull(
    relativePercent?.let { "${formatDecimal(it, 1, locale)}%" },
    absoluteValue?.let {
        stringResource(absoluteUnit, formatDecimal(it, 1, locale))
    },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun formatBytes(value: Long, locale: Locale): String = when {
    value >= 1_000_000L -> stringResource(
        R.string.stats_bytes_mb,
        formatDecimal(value / 1_000_000.0, 1, locale),
    )
    value >= 1_000L -> stringResource(
        R.string.stats_bytes_kb,
        formatDecimal(value / 1_000.0, 1, locale),
    )
    else -> stringResource(R.string.stats_bytes_b, formatCount(value, locale))
}

private fun LiveFrontendState.presentationResource(): Int = when {
    locked -> R.string.stats_frontend_locked
    partiallySynchronized -> R.string.stats_frontend_synchronizing
    signalDetected -> R.string.stats_frontend_signal
    else -> R.string.stats_frontend_no_signal
}
