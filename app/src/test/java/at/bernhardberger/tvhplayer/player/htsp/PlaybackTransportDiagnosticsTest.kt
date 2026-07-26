package at.bernhardberger.tvhplayer.player.htsp

import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.player.PlaybackQueueDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackTransportDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackTunerDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackTransportDiagnosticsTest {
    @Test
    fun signalStatusMapsOnlyDocumentedFields() {
        val updated = updatePlaybackTransportDiagnostics(
            current = PlaybackTransportDiagnostics(),
            message = message(
                method = "signalStatus",
                "subscriptionId" to 7,
                "feStatus" to "LOCKED",
                "feAbsoluteSignal" to -52_750L,
                "feAbsoluteSNR" to 31_250L,
                "feBER" to 3L,
                "feUNC" to 1L,
            ),
            subscriptionId = 7,
        )

        assertEquals(
            PlaybackTunerDiagnostics(
                status = "LOCKED",
                signalPercent = null,
                signalMilliDbm = -52_750L,
                snrPercent = null,
                snrMilliDb = 31_250L,
                bitErrorRate = 3L,
                uncorrectedBlocks = 1L,
            ),
            updated.tuner,
        )
    }

    @Test
    fun queueStatusMergesWithoutDiscardingTheLatestSignalStatus() {
        val existingTuner = PlaybackTunerDiagnostics(status = "LOCKED")
        val updated = updatePlaybackTransportDiagnostics(
            current = PlaybackTransportDiagnostics(tuner = existingTuner),
            message = message(
                method = "queueStatus",
                "subscriptionId" to 7,
                "packets" to 12L,
                "bytes" to 48_000L,
                "delay" to 250_000L,
                "Bdrops" to 2L,
                "Pdrops" to 1L,
                "Idrops" to 0L,
            ),
            subscriptionId = 7,
        )

        assertSame(existingTuner, updated.tuner)
        assertEquals(
            PlaybackQueueDiagnostics(
                packets = 12L,
                bytes = 48_000L,
                delayMicros = 250_000L,
                bFrameDrops = 2L,
                pFrameDrops = 1L,
                iFrameDrops = 0L,
            ),
            updated.queue,
        )
    }

    @Test
    fun statusForAnotherSubscriptionIsIgnored() {
        val current = PlaybackTransportDiagnostics(
            tuner = PlaybackTunerDiagnostics(status = "LOCKED")
        )

        val updated = updatePlaybackTransportDiagnostics(
            current = current,
            message = message(
                method = "signalStatus",
                "subscriptionId" to 99,
                "feStatus" to "NO SIGNAL",
            ),
            subscriptionId = 7,
        )

        assertSame(current, updated)
    }

    private fun message(method: String, vararg fields: Pair<String, Any?>) = HtspMessage(
        method = method,
        seq = null,
        fields = mapOf(*fields),
    )
}
