package at.bernhardberger.tvhplayer.player.htsp

import at.bernhardberger.tvhplayer.htsp.HtspMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeshiftPtsRebaserTest {
    @Test
    fun backwardDiscontinuityRebasesPtsAndDtsAfterPriorOutput() {
        val rebaser = TimeshiftPtsRebaser(frameGapUs = 1L)
        val before = muxPacket(ptsUs = 1_000L, dtsUs = 900L)

        assertSame(before, rebaser.rebaseMuxPacket(before, isVideo = true))
        rebaser.markDiscontinuity(waitForVideoKeyframe = false)

        val after = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(ptsUs = 400L, dtsUs = 300L),
                isVideo = true,
            )
        )
        assertEquals(1_001L, after.long("pts"))
        assertEquals(901L, after.long("dts"))
        assertTrue(after.long("pts")!! > before.long("pts")!!)
    }

    @Test
    fun oneOffsetIsSharedAcrossStreamsAndRepeatedDiscontinuities() {
        val rebaser = TimeshiftPtsRebaser(frameGapUs = 1L)
        rebaser.rebaseMuxPacket(
            muxPacket(stream = 1, ptsUs = 2_000L, dtsUs = 1_900L),
            isVideo = true,
        )
        rebaser.markDiscontinuity(waitForVideoKeyframe = false)

        val video = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 1, ptsUs = 700L, dtsUs = 600L),
                isVideo = true,
            )
        )
        val audio = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 2, ptsUs = 750L, dtsUs = null),
                isVideo = false,
            )
        )
        assertEquals(1_301L, video.long("pts")!! - 700L)
        assertEquals(1_301L, audio.long("pts")!! - 750L)

        rebaser.markDiscontinuity(waitForVideoKeyframe = false)
        val repeated = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 1, ptsUs = 100L, dtsUs = 90L),
                isVideo = true,
            )
        )
        assertTrue(repeated.long("pts")!! > audio.long("pts")!!)
    }

    @Test
    fun packetWithoutTimestampsDoesNotConsumePendingDiscontinuity() {
        val rebaser = TimeshiftPtsRebaser(frameGapUs = 1L)
        rebaser.rebaseMuxPacket(muxPacket(ptsUs = 500L, dtsUs = 450L), isVideo = false)
        rebaser.markDiscontinuity(waitForVideoKeyframe = false)
        val withoutTimestamps = muxPacket(ptsUs = null, dtsUs = null)

        assertNull(rebaser.rebaseMuxPacket(withoutTimestamps, isVideo = false))

        val timestamped = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(ptsUs = 100L, dtsUs = null),
                isVideo = false,
            )
        )
        assertEquals(501L, timestamped.long("pts"))
        assertFalse(timestamped.fields.containsKey("dts"))
    }

    @Test
    fun nonMuxMessagesAndPreSeekPacketsRemainUnchanged() {
        val rebaser = TimeshiftPtsRebaser(frameGapUs = 1L)
        val control = HtspMessage(
            method = "timeshiftStatus",
            seq = null,
            fields = mapOf("pts" to 100L),
        )
        val mux = muxPacket(ptsUs = 200L, dtsUs = null)

        assertSame(control, rebaser.rebaseMuxPacket(control, isVideo = false))
        assertSame(mux, rebaser.rebaseMuxPacket(mux, isVideo = false))
    }

    @Test
    fun postSeekWaitsForVideoKeyframeAndDropsPacketsBelowPriorFloor() {
        val rebaser = TimeshiftPtsRebaser(frameGapUs = 1L)
        rebaser.rebaseMuxPacket(
            muxPacket(stream = 1, ptsUs = 2_000L, dtsUs = 1_900L),
            isVideo = true,
        )
        rebaser.markDiscontinuity(waitForVideoKeyframe = true)

        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 2, ptsUs = 650L, dtsUs = null),
                isVideo = false,
            )
        )
        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(
                    stream = 1,
                    ptsUs = 680L,
                    dtsUs = 600L,
                    frameType = 80,
                ),
                isVideo = true,
            )
        )

        val keyframe = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(
                    stream = 1,
                    ptsUs = 700L,
                    dtsUs = 620L,
                    frameType = 73,
                ),
                isVideo = true,
            )
        )
        assertEquals(2_001L, keyframe.long("pts"))
        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 2, ptsUs = 650L, dtsUs = null),
                isVideo = false,
            )
        )
        val audio = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 2, ptsUs = 750L, dtsUs = null),
                isVideo = false,
            )
        )
        assertTrue(audio.long("pts")!! > 2_000L)
    }

    @Test
    fun missingKeyframeFallsBackAfterBoundedPacketWait() {
        val events = mutableListOf<TimeshiftPtsRebaseEvent>()
        val rebaser = TimeshiftPtsRebaser(
            frameGapUs = 1L,
            maxKeyframeWaitPackets = 2,
            onEvent = events::add,
        )
        rebaser.rebaseMuxPacket(
            muxPacket(stream = 1, ptsUs = 2_000L, dtsUs = 1_900L),
            isVideo = true,
        )
        rebaser.markDiscontinuity(waitForVideoKeyframe = true)

        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 2, ptsUs = 600L, dtsUs = null),
                isVideo = false,
            )
        )
        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 1, ptsUs = 650L, dtsUs = 600L, frameType = 80),
                isVideo = true,
            )
        )
        val fallback = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 2, ptsUs = 700L, dtsUs = null),
                isVideo = false,
            )
        )

        assertEquals(2_001L, fallback.long("pts"))
        assertTrue(
            events.contains(
                TimeshiftPtsRebaseEvent.GateOpened(
                    usedFallback = true,
                    droppedPackets = 2,
                    offsetUs = 1_301L,
                )
            )
        )
    }

    @Test
    fun missingFrameTypeUsesBoundedFallbackInsteadOfClaimingKeyframe() {
        val rebaser = TimeshiftPtsRebaser(
            frameGapUs = 1L,
            maxKeyframeWaitPackets = 2,
        )
        rebaser.rebaseMuxPacket(
            muxPacket(stream = 1, ptsUs = 1_000L, dtsUs = 900L),
            isVideo = true,
        )
        rebaser.markDiscontinuity(waitForVideoKeyframe = true)

        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(
                    stream = 1,
                    ptsUs = 400L,
                    dtsUs = 350L,
                    frameType = null,
                ),
                isVideo = true,
            )
        )
        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(
                    stream = 1,
                    ptsUs = 450L,
                    dtsUs = 400L,
                    frameType = null,
                ),
                isVideo = true,
            )
        )
        val fallback = requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(
                    stream = 1,
                    ptsUs = 500L,
                    dtsUs = 450L,
                    frameType = null,
                ),
                isVideo = true,
            )
        )
        assertEquals(1_001L, fallback.long("pts"))
    }

    @Test
    fun repeatedDiscontinuityRearmsVideoKeyframeGate() {
        val rebaser = TimeshiftPtsRebaser(frameGapUs = 1L)
        rebaser.rebaseMuxPacket(
            muxPacket(stream = 1, ptsUs = 2_000L, dtsUs = 1_900L),
            isVideo = true,
        )
        rebaser.markDiscontinuity(waitForVideoKeyframe = true)
        requireNotNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 1, ptsUs = 700L, dtsUs = 650L, frameType = 73),
                isVideo = true,
            )
        )
        rebaser.markDiscontinuity(waitForVideoKeyframe = true)

        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 2, ptsUs = 300L, dtsUs = null),
                isVideo = false,
            )
        )
        assertNull(
            rebaser.rebaseMuxPacket(
                muxPacket(stream = 1, ptsUs = 350L, dtsUs = 320L, frameType = 80),
                isVideo = true,
            )
        )
        assertTrue(
            requireNotNull(
                rebaser.rebaseMuxPacket(
                    muxPacket(stream = 1, ptsUs = 400L, dtsUs = 360L, frameType = 73),
                    isVideo = true,
                )
            ).long("pts")!! > 2_000L
        )
    }

    private fun muxPacket(
        stream: Int = 1,
        ptsUs: Long?,
        dtsUs: Long?,
        frameType: Int? = 73,
    ): HtspMessage {
        val fields = mutableMapOf<String, Any?>(
            "subscriptionId" to 1,
            "stream" to stream,
            "payload" to byteArrayOf(1),
        )
        if (frameType != null) fields["frametype"] = frameType
        if (ptsUs != null) fields["pts"] = ptsUs
        if (dtsUs != null) fields["dts"] = dtsUs
        return HtspMessage(method = "muxpkt", seq = null, fields = fields)
    }
}
