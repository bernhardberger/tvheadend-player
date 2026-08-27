package at.bernhardberger.tvhplayer.data

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.EpgCoverageRequestResult
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability as SdkProgressCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class TvheadendDataRuntimeTest {
    @Test
    fun frontierRequestsEveryVisibleChannelBeforeAggregatingTheResult() {
        val requested = mutableListOf<Int>()

        val result = requestAllEpgCoverage(listOf(11, 22, 33)) { channelId ->
            requested += channelId
            if (channelId == 11) EpgCoverageRequestResult.ACCEPTED
            else EpgCoverageRequestResult.INELIGIBLE
        }

        assertEquals(EpgFrontierResult.SETTLED, result)
        assertEquals(listOf(11, 22, 33), requested)
    }

    @Test
    fun progressCapabilityCombinesProtocolSupportWithCurrentWriteAccess() {
        assertEquals(
            RecordingProgressCapability.Disconnected,
            appRecordingProgressCapability(SdkProgressCapability.UNKNOWN, CapabilityAccess.ALLOWED),
        )
        assertEquals(
            RecordingProgressCapability.Unsupported,
            appRecordingProgressCapability(SdkProgressCapability.UNSUPPORTED, CapabilityAccess.ALLOWED),
        )
        assertEquals(
            RecordingProgressCapability.ReadOnly,
            appRecordingProgressCapability(SdkProgressCapability.SUPPORTED, CapabilityAccess.DENIED),
        )
        assertEquals(
            RecordingProgressCapability.Full,
            appRecordingProgressCapability(SdkProgressCapability.SUPPORTED, CapabilityAccess.ALLOWED),
        )
        assertEquals(
            RecordingProgressCapability.Disconnected,
            appRecordingProgressCapability(SdkProgressCapability.SUPPORTED, CapabilityAccess.UNKNOWN),
        )
    }
}
