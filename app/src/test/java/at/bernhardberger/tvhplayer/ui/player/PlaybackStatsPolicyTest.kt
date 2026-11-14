package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.playback.LiveFrontendState
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionDiagnostics
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionSource
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi
import at.bernhardberger.tvhplayer.di.PlayerStatsLevel
import at.bernhardberger.tvhplayer.playback.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PlaybackStatsPolicyTest {
    @Test fun sourceEvidenceChoosesThePathNotTheNames() {
        assertEquals(StreamPathStep.entries, streamPath(statsLiveDiagnostics(), StatsWindow(), null, null).steps)
        val iptv = statsLiveDiagnostics(tuner = false)
        assertEquals(StreamPathStep.entries.drop(1), streamPath(iptv, StatsWindow(), null, null).steps)
        assertEquals(StreamPathStep.entries.drop(2), streamPath(
            iptv.copy(source = AppPlaybackSource.RECORDING), StatsWindow(), null, null).steps)
    }

    @Test fun onlySupportedFactsFlagStepsAndTheFirstFlagFollowsThePath() {
        val lowMeasurements = statsLiveDiagnostics().copy(bufferedMs = 0)
        assertTrue(streamPath(lowMeasurements, StatsWindow(signalChange = -70.0, snrChange = -60.0,
            bufferChangeMs = -10_000, queueChangeUs = 999_000), null, null).flagged.isEmpty())
        val flagged = streamPath(statsLiveDiagnostics(locked = false),
            StatsWindow(serverDrops = 1, clientDrops = 1, decoderDrops = 1), "60", "50")
        assertEquals(StreamPathStep.entries.toSet(), flagged.flagged)
        assertEquals(StreamPathStep.TUNER, flagged.firstFlagged)
        assertEquals(setOf(StreamPathStep.TUNER),
            streamPath(lowMeasurements, StatsWindow(uncorrected = 1, bitErrors = 100), "50", "50").flagged)
    }

    @Test fun theStreamPathNamesTheCodecFamilyAndOneRateWhereBothReadTheSame() {
        assertEquals("HEVC", videoCodecFamily("hvc1.2.4.L153.B0", null))
        assertEquals("H.264", videoCodecFamily("avc1.640028", "video/avc"))
        assertEquals("MPEG-2", videoCodecFamily(null, "video/mpeg2"))
        assertEquals("AV1", videoCodecFamily("av01.0.08M.08", null))
        assertEquals("VP9", videoCodecFamily("vp09.00.40.08", null))
        assertEquals("mp4a.40.2", videoCodecFamily("mp4a.40.2", "video/unknown"))
        assertNull(videoCodecFamily(null, null))
        assertEquals("59.94/50 fps", frameRateText("59.94", "50"))
        assertEquals("50 fps", frameRateText("50", "50"))
        assertEquals("50 fps", frameRateText(null, "50"))
        assertNull(frameRateText(null, null))
    }

    @Test fun theChannelMessageCarriesTheTunerAndServerRowsWithDataAndDebugAddsTheirGroups() {
        val noLock = statsLiveDiagnostics(locked = false)
        assertEquals(listOf("Tuner: No lock • 75% signal", "Tvheadend: 250 ms queue"),
            liveStatsRows(noLock, StatsWindow(), PlayerStatsLevel.STREAM_PATH))
        assertEquals(listOf("Tvheadend: 250 ms queue"),
            liveStatsRows(statsLiveDiagnostics(tuner = false), StatsWindow(), PlayerStatsLevel.STREAM_PATH))
        assertEquals(emptyList<String>(), liveStatsRows(noLock.copy(live = null), StatsWindow(), PlayerStatsLevel.DEBUG))
        assertEquals(emptyList<String>(), liveStatsRows(noLock, StatsWindow(), PlayerStatsLevel.OFF))
        val debug = liveStatsRows(noLock, StatsWindow(), PlayerStatsLevel.DEBUG)
        assertEquals(listOf("Tuner", "Tvheadend", "Frontend", "SNR", "BER / uncorrected", "Queue", "Server drops", "Client drops"),
            debug.map { it.substringBefore(":") })
        val one = liveStatsRows(statsLiveDiagnostics(locked = false, packetCount = 1, clientDrops = 1), StatsWindow(),
            PlayerStatsLevel.DEBUG)
        assertTrue(one.toString(), one.any { it.startsWith("Queue: 1 packet • ") })
        assertTrue(one.toString(), "Client drops: 1 packet" in one)
        assertTrue(debug.toString(), debug.any { it.startsWith("Queue: 1,500 packets • ") })
    }

    @Test fun theAudioLineNamesTheLanguageAsTheTrackRowsAndFallsBackToTheBadgeCodec() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val d = statsLiveDiagnostics()
            assertEquals("E-AC-3 • English • 6 ch • 48,000 Hz", audioSummary(d, "Unknown language", "Multiple languages"))
            val htsp = d.copy(audio = d.audio?.copy(codec = null, language = "de", sampleMimeType = "audio/ac3"))
            assertEquals("DD • German • 6 ch • 48,000 Hz", audioSummary(htsp, "Unknown language", "Multiple languages"))
            assertEquals("DD • Unknown language • 6 ch • 48,000 Hz",
                audioSummary(htsp.copy(audio = htsp.audio?.copy(language = "und")), "Unknown language", "Multiple languages"))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test fun countersGrowOnlyInsideTheWindowAndResetsAreNotNegativeGrowth() {
        var now = 0L
        val history = PlaybackStatsHistory { now }
        history.update("channel", statsLiveDiagnostics(uncorrected = 10, serverDrops = 2))
        now = 1_000
        val growth = history.update("channel", statsLiveDiagnostics(uncorrected = 13, serverDrops = 5))
        assertEquals(3L, growth.uncorrected)
        assertEquals(3L, growth.serverDrops)
        assertEquals(61_000L, growth.growthExpiresAtMillis)
        now = 2_000
        val reset = history.update("channel", statsLiveDiagnostics(uncorrected = 1))
        assertEquals(3L, reset.uncorrected)
        assertEquals("a reset adds no growth of its own", 61_000L, reset.growthExpiresAtMillis)
        now = 61_000
        val expired = history.update("channel", statsLiveDiagnostics(uncorrected = 1))
        assertEquals(0L, expired.uncorrected)
        assertNull(expired.growthExpiresAtMillis)
    }

    @Test fun aRowWithGrowthShowsThatFactAndOneRelatedValueInCompactCounts() {
        val d = statsLiveDiagnostics()
        fun row(step: StreamPathStep, w: StatsWindow, diagnostics: AppPlaybackDiagnostics = d) =
            stepValue(step, diagnostics, w, output = null, requested = null, measuredRate = "59.94", declaredRate = "50", timeshift = null)
        assertEquals("+9999 uncorrected • 75% SNR", row(StreamPathStep.TUNER, StatsWindow(uncorrected = 9_999)))
        assertEquals("lock is the normal state and goes unsaid", "75% signal • 75% SNR", row(StreamPathStep.TUNER, StatsWindow()))
        assertEquals("No lock • 75% signal", row(StreamPathStep.TUNER, StatsWindow(), statsLiveDiagnostics(locked = false)))
        assertEquals("No lock • +9999 uncorrected",
            row(StreamPathStep.TUNER, StatsWindow(uncorrected = 9_999), statsLiveDiagnostics(locked = false)))
        assertEquals("+12k drops • 250 ms queue", row(StreamPathStep.SERVER, StatsWindow(serverDrops = 12_345)))
        assertEquals("250 ms queue", row(StreamPathStep.SERVER, StatsWindow(queueChangeUs = 100_000)))
        assertEquals("+1.2M drops • 5.0 s buffer", row(StreamPathStep.APP, StatsWindow(clientDrops = 1_299_999)))
        assertEquals("+9999 drops • 59.94/50 fps", row(StreamPathStep.DECODER, StatsWindow(decoderDrops = 9_999)))
        assertEquals("a second rate takes the family's room", "1080 • 59.94/50 fps", row(StreamPathStep.DECODER, StatsWindow()))
        assertEquals("the declared scan letter of the same height", "1080i • 59.94/50 fps",
            stepValue(StreamPathStep.DECODER, d, StatsWindow(), null, null, "59.94", "50", null, raster = "1080i"))
        listOf("50", null).forEach { measured ->
            assertEquals("one rate keeps the family", "H.264 • 1080i • 50 fps",
                stepValue(StreamPathStep.DECODER, d, StatsWindow(), null, null, measured, "50", null, raster = "1080i"))
        }
        assertEquals("a measurement without a declared rate is a second rate", "1080 • 50/— fps",
            stepValue(StreamPathStep.DECODER, d, StatsWindow(), null, null, "50", null, null))
        assertEquals("a stale raster of another height", "1080", videoSizeLabel(d.video, raster = "720p"))
        assertEquals("1440×1088", videoSizeLabel(d.video?.copy(resolution = "1440×1088", width = 1440, height = 1088), null))
        // A non-standard size takes the codec family's room.
        listOf(3840 to 1600, 1920 to 1088, 1280 to 800).forEach { (width, height) ->
            val odd = d.copy(video = d.video?.copy(resolution = "$width×$height", width = width, height = height))
            assertEquals("$width×$height • 59.94/50 fps", row(StreamPathStep.DECODER, StatsWindow(), odd))
        }
        val output = DisplayOutput(1920, 1080, "23.98", "", matchFrameRate = null)
        assertEquals("23.98 Hz • requested 59.94 Hz",
            stepValue(StreamPathStep.SCREEN, d, StatsWindow(), output, "59.94", null, null, null))
        assertEquals("1920×1080 @ 23.98 Hz", stepValue(StreamPathStep.SCREEN, d, StatsWindow(), output, "23.98", null, null, null))
        val recording = d.copy(source = AppPlaybackSource.RECORDING, live = null, positionMs = 10_799_000, durationMs = 10_800_000)
        assertEquals("5.0 s buffer • 2:59:59/3:00:00", row(StreamPathStep.APP, StatsWindow(), recording))
        assertEquals("an hour-long duration gives the position its hours", "5.0 s buffer • 0:00:04/2:14:59",
            row(StreamPathStep.APP, StatsWindow(), recording.copy(positionMs = 4_000, durationMs = 8_099_000)))
        assertEquals("5.0 s buffer • 00:04/59:59",
            row(StreamPathStep.APP, StatsWindow(), recording.copy(positionMs = 4_000, durationMs = 3_599_000)))
        assertEquals(0L, pathGrowth(StreamPathStep.SCREEN, StatsWindow(uncorrected = 1, serverDrops = 1)))
        assertEquals(listOf("9999", "10k", "999k", "1.0M", "12.3M"),
            listOf(9_999L, 10_000L, 999_999L, 1_000_000L, 12_399_999L).map(::compactCount))
    }

    @Test fun newTargetSourceOrAWindowWithoutObservationsStartsABaseline() {
        var now = 0L
        val history = PlaybackStatsHistory { now }
        history.update("a", statsLiveDiagnostics(uncorrected = 1))
        now = 1_000
        assertEquals(0L, history.update("b", statsLiveDiagnostics(uncorrected = 20)).uncorrected)
        now = 2_000
        assertEquals(0L, history.update("b", statsLiveDiagnostics(uncorrected = 40, service = "Another service")).uncorrected)
        now = 63_000
        assertEquals(0L, history.update("b", statsLiveDiagnostics(uncorrected = 90, service = "Another service")).uncorrected)
    }

    @Test fun measurementChangesKeepTheirSignButDisappearAtDisplayPrecision() {
        var now = 0L
        val history = PlaybackStatsHistory { now }
        history.update(1, statsLiveDiagnostics().copy(bufferedMs = 5_000))
        now = 1_000
        assertEquals(-1_000L, history.update(1, statsLiveDiagnostics().copy(bufferedMs = 4_000)).bufferChangeMs)
        assertNull(displayedStatsChange(0.04, 1))
        assertNull(displayedStatsChange(-0.04, 1))
        assertEquals(-0.1, displayedStatsChange(-0.06, 1))
    }
}

/** Shared synthetic stream evidence for policy tests and the existing offline capture lane. */
@OptIn(SubscriptionInfrastructureApi::class)
internal fun statsLiveDiagnostics(
    tuner: Boolean = true,
    locked: Boolean = true,
    uncorrected: Long = 0,
    serverDrops: Long = 0,
    service: String = "Ridge Earth",
    signal: Long = 49_152,
    snr: Long = 49_152,
    queueDelayUs: Long = 250_000,
    packetCount: Long = 1_500,
    clientDrops: Long = 0,
): AppPlaybackDiagnostics {
    var live = LiveSubscriptionDiagnostics.update(null, SubscriptionEvent.Started(
        source = LiveSubscriptionSource.create(adapterName = if (tuner) "DVB-T adapter" else null,
            muxName = "North ridge multiplex", networkName = "Ridge network",
            providerName = "Ridge", serviceName = service),
        streams = null, codecMetadata = null))
    if (tuner) live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Signal(
        relativeSnr = snr, absoluteSnr = 31_250, relativeSignal = signal, absoluteSignal = -63_500,
        bitErrorRate = 0, uncorrectedBlockCount = uncorrected, frontendStatusReported = true,
        frontendState = LiveFrontendState.create(signalDetected = true, partiallySynchronized = true, locked = locked)))
    live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Queue(
        packetCount = packetCount, byteCount = 2_000_000, delay = queueDelayUs,
        bFrameDropCount = serverDrops, pFrameDropCount = 0, iFrameDropCount = 0))
    if (clientDrops > 0) live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Dropped(clientDrops))
    return AppPlaybackDiagnostics(source = AppPlaybackSource.LIVE_TV, state = AppPlaybackState.Playing,
        isPlaying = true, bufferedMs = 5_000,
        video = AppPlaybackFormatDiagnostics(codec = "H.264", resolution = "1920×1080", frameRate = 50f, width = 1920, height = 1080),
        audio = AppPlaybackFormatDiagnostics(codec = "E-AC-3", language = "en", channelCount = 6, sampleRateHz = 48_000),
        videoDecoderCounters = AppVideoDecoderCounters(12_000, 0, 2), live = live)
}
