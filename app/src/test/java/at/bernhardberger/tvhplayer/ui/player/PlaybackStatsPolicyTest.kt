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
        assertEquals(listOf("Tuner: No lock • 75% signal • 75% SNR", "Tvheadend: 250.0 ms queue"),
            liveStatsRows(noLock, StatsWindow(), PlayerStatsLevel.STREAM_PATH))
        assertEquals(listOf("Tvheadend: 250.0 ms queue"),
            liveStatsRows(statsLiveDiagnostics(tuner = false), StatsWindow(), PlayerStatsLevel.STREAM_PATH))
        assertEquals(emptyList<String>(), liveStatsRows(noLock.copy(live = null), StatsWindow(), PlayerStatsLevel.DEBUG))
        assertEquals(emptyList<String>(), liveStatsRows(noLock, StatsWindow(), PlayerStatsLevel.OFF))
        val debug = liveStatsRows(noLock, StatsWindow(), PlayerStatsLevel.DEBUG)
        assertEquals(listOf("Tuner", "Tvheadend", "Frontend", "SNR", "BER / uncorrected", "Queue", "Server drops", "Client drops"),
            debug.map { it.substringBefore(":") })
    }

    @Test fun countersGrowOnlyInsideTheWindowAndResetsAreNotNegativeGrowth() {
        var now = 0L
        val history = PlaybackStatsHistory { now }
        history.update("channel", statsLiveDiagnostics(uncorrected = 10, serverDrops = 2))
        now = 1_000
        val growth = history.update("channel", statsLiveDiagnostics(uncorrected = 13, serverDrops = 5))
        assertEquals(3L, growth.uncorrected)
        assertEquals(3L, growth.serverDrops)
        now = 2_000
        assertEquals(3L, history.update("channel", statsLiveDiagnostics(uncorrected = 1)).uncorrected)
        now = 61_000
        assertEquals(0L, history.update("channel", statsLiveDiagnostics(uncorrected = 1)).uncorrected)
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
): AppPlaybackDiagnostics {
    var live = LiveSubscriptionDiagnostics.update(null, SubscriptionEvent.Started(
        source = LiveSubscriptionSource.create(adapterName = if (tuner) "DVB-T adapter" else null,
            muxName = "North ridge multiplex", networkName = "Ridge network",
            providerName = "Ridge", serviceName = service),
        streams = null, codecMetadata = null))
    if (tuner) live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Signal(
        relativeSnr = 49_152, absoluteSnr = 31_250, relativeSignal = 49_152, absoluteSignal = -63_500,
        bitErrorRate = 0, uncorrectedBlockCount = uncorrected, frontendStatusReported = true,
        frontendState = LiveFrontendState.create(signalDetected = true, partiallySynchronized = true, locked = locked)))
    live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Queue(
        packetCount = 1_500, byteCount = 2_000_000, delay = 250_000,
        bFrameDropCount = serverDrops, pFrameDropCount = 0, iFrameDropCount = 0))
    return AppPlaybackDiagnostics(source = AppPlaybackSource.LIVE_TV, state = AppPlaybackState.Playing,
        isPlaying = true, bufferedMs = 5_000,
        video = AppPlaybackFormatDiagnostics(codec = "H.264", resolution = "1920×1080", frameRate = 50f),
        audio = AppPlaybackFormatDiagnostics(codec = "E-AC-3", language = "English", channelCount = 6, sampleRateHz = 48_000),
        videoDecoderCounters = AppVideoDecoderCounters(12_000, 0, 2), live = live)
}
