package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource

/** Formats only published facts. The SDK exposes source names, not a delivery-system field. */
@Composable
fun formatStreamSignalDetails(diagnostics: AppPlaybackDiagnostics): Map<StreamSignalSection, List<StreamSignalRow>> {
    val locale = LocalConfiguration.current.locales[0]
    val live = diagnostics.live.takeIf { diagnostics.source == AppPlaybackSource.LIVE_TV }
    val source = live?.source
    val frontend = live?.frontend
    val video = diagnostics.video
    val audio = diagnostics.audio
    return streamSignalDetails(
        stream = listOf(
            StreamSignalRow(stringResource(R.string.trial_video), listOfNotNull(
                video?.resolution, humanCodecName(video?.sampleMimeType),
                video?.frameRate?.let { stringResource(R.string.trial_fps, String.format(locale, "%.0f", it)) },
            ).joinToString(" · ")),
            StreamSignalRow(stringResource(R.string.trial_audio), listOfNotNull(
                humanCodecName(audio?.sampleMimeType), audio?.channelCount?.let { stringResource(R.string.trial_channels, it) },
            ).joinToString(" · ")),
        ),
        source = listOf(
            StreamSignalRow(stringResource(R.string.trial_service), source?.serviceName),
            StreamSignalRow(stringResource(R.string.trial_provider), source?.providerName),
            StreamSignalRow(stringResource(R.string.trial_network), source?.networkName),
            StreamSignalRow(stringResource(R.string.trial_mux), source?.muxName),
            StreamSignalRow(stringResource(R.string.trial_adapter), source?.adapterName),
        ),
        reception = listOf(
            StreamSignalRow(stringResource(R.string.trial_signal), frontend?.relativeSignalPercent?.let { stringResource(R.string.trial_percent, String.format(locale, "%.0f", it)) }
                ?: frontend?.absoluteSignalDbm?.let { stringResource(R.string.trial_dbm, String.format(locale, "%.1f", it)) }),
            StreamSignalRow(stringResource(R.string.trial_snr), frontend?.relativeSnrPercent?.let { stringResource(R.string.trial_percent, String.format(locale, "%.0f", it)) }
                ?: frontend?.absoluteSnrDecibels?.let { stringResource(R.string.trial_db, String.format(locale, "%.1f", it)) }),
            StreamSignalRow(stringResource(R.string.trial_ber), frontend?.bitErrorRateRaw?.toString()),
            StreamSignalRow(stringResource(R.string.trial_unc), frontend?.uncorrectedBlockCount?.toString()),
        ),
        health = listOf(
            StreamSignalRow(stringResource(R.string.trial_queue), live?.queue?.mediaSpanMicroseconds?.let { stringResource(R.string.trial_ms, (it / 1000).toString()) }),
            StreamSignalRow(stringResource(R.string.trial_dropped), live?.queue?.let { (it.droppedIFrameCount + it.droppedPFrameCount + it.droppedBFrameCount).toString() }),
        ),
        liveFrontend = frontend != null,
    )
}
