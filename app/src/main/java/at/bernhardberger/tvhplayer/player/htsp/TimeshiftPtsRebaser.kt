package at.bernhardberger.tvhplayer.player.htsp

import at.bernhardberger.tvhplayer.htsp.HtspMessage

internal const val TIMESHIFT_DISCONTINUITY_METHOD = "tvhplayerTimeshiftDiscontinuity"

internal val TIMESHIFT_DISCONTINUITY_MESSAGE = HtspMessage(
    method = TIMESHIFT_DISCONTINUITY_METHOD,
    seq = null,
    fields = emptyMap(),
)

internal sealed interface TimeshiftPtsRebaseEvent {
    data class GateOpened(
        val usedFallback: Boolean,
        val droppedPackets: Int,
        val offsetUs: Long,
    ) : TimeshiftPtsRebaseEvent

    data class PacketDroppedBelowFloor(
        val droppedPackets: Int,
    ) : TimeshiftPtsRebaseEvent
}

/** Keeps Media3 sample timestamps monotonic across a server-side HTSP seek. */
internal class TimeshiftPtsRebaser(
    private val frameGapUs: Long = 1L,
    private val maxKeyframeWaitPackets: Int = 256,
    private val onEvent: (TimeshiftPtsRebaseEvent) -> Unit = {},
) {
    private val lock = Any()
    private var lastOutputUs: Long? = null
    private var offsetUs = 0L
    private var discontinuityPending = false
    private var waitForVideoKeyframe = false
    private var postDiscontinuityFloorUs: Long? = null
    private var keyframeWaitDroppedPackets = 0
    private var floorDroppedPackets = 0

    init {
        require(frameGapUs > 0L)
        require(maxKeyframeWaitPackets > 0)
    }

    fun markDiscontinuity(waitForVideoKeyframe: Boolean) = synchronized(lock) {
        discontinuityPending = true
        this.waitForVideoKeyframe = waitForVideoKeyframe
        keyframeWaitDroppedPackets = 0
        floorDroppedPackets = 0
    }

    fun reset() = synchronized(lock) {
        lastOutputUs = null
        offsetUs = 0L
        discontinuityPending = false
        waitForVideoKeyframe = false
        postDiscontinuityFloorUs = null
        keyframeWaitDroppedPackets = 0
        floorDroppedPackets = 0
    }

    fun rebaseMuxPacket(
        message: HtspMessage,
        isVideo: Boolean,
    ): HtspMessage? = synchronized(lock) {
        if (message.method != "muxpkt") return@synchronized message
        val ptsUs = message.long("pts")
            ?: return@synchronized if (
                discontinuityPending || postDiscontinuityFloorUs != null
            ) null else message
        val dtsUs = message.long("dts")

        if (discontinuityPending) {
            val frameType = message.int("frametype") ?: -1
            val isVideoKeyframe = isVideo && frameType == 73
            var usedFallback = false
            if (waitForVideoKeyframe && !isVideoKeyframe) {
                if (keyframeWaitDroppedPackets < maxKeyframeWaitPackets) {
                    keyframeWaitDroppedPackets++
                    return@synchronized null
                }
                usedFallback = true
            }
            postDiscontinuityFloorUs = lastOutputUs
            offsetUs = lastOutputUs?.let { previous ->
                previous + frameGapUs - ptsUs
            } ?: 0L
            discontinuityPending = false
            waitForVideoKeyframe = false
            onEvent(
                TimeshiftPtsRebaseEvent.GateOpened(
                    usedFallback = usedFallback,
                    droppedPackets = keyframeWaitDroppedPackets,
                    offsetUs = offsetUs,
                )
            )
        }

        val rebasedPtsUs = ptsUs + offsetUs
        val rebasedDtsUs = dtsUs?.plus(offsetUs)
        val floorUs = postDiscontinuityFloorUs
        if (floorUs != null && rebasedPtsUs <= floorUs) {
            floorDroppedPackets++
            onEvent(
                TimeshiftPtsRebaseEvent.PacketDroppedBelowFloor(floorDroppedPackets)
            )
            return@synchronized null
        }
        lastOutputUs = maxOf(lastOutputUs ?: Long.MIN_VALUE, rebasedPtsUs)
        if (offsetUs == 0L) {
            return@synchronized message
        }

        val rebasedFields = message.fields.toMutableMap()
        rebasedFields["pts"] = rebasedPtsUs
        if (dtsUs != null) rebasedFields["dts"] = rebasedDtsUs
        message.copy(fields = rebasedFields)
    }
}
