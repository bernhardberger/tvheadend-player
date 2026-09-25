@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private val FIXED_LIVE_TIMESHIFT_PERIOD = 2.hours

internal fun requestedLiveTimeshiftPeriod(timeshiftEnabled: Boolean): Duration =
    if (timeshiftEnabled) FIXED_LIVE_TIMESHIFT_PERIOD else Duration.ZERO

internal class PlaybackPresentationEpoch {
    private val lock = Any()
    private var current = 0L

    fun begin(): Long = synchronized(lock) {
        check(current < Long.MAX_VALUE) { "Playback presentation epoch exhausted" }
        ++current
    }

    fun snapshot(): Long = synchronized(lock) { current }

    fun isCurrent(epoch: Long): Boolean = synchronized(lock) { epoch == current }

    fun beginIfCurrent(epoch: Long): Long? = synchronized(lock) {
        if (epoch != current) return@synchronized null
        check(current < Long.MAX_VALUE) { "Playback presentation epoch exhausted" }
        ++current
    }

    fun publishIfCurrent(epoch: Long, publish: () -> Unit): Boolean = synchronized(lock) {
        if (epoch != current) return@synchronized false
        publish()
        true
    }
}

fun LiveTimeshiftState.toAppPresentation(
    sample: at.bernhardberger.tvheadend.sdk.media3.TimeshiftPlaybackPosition =
        at.bernhardberger.tvheadend.sdk.media3.TimeshiftPlaybackPosition.Unavailable,
): AppTimeshiftState = when (this) {
    LiveTimeshiftState.Unavailable -> AppTimeshiftState()
    is LiveTimeshiftState.Available -> {
        val estimate =
            sample as? at.bernhardberger.tvheadend.sdk.media3.TimeshiftPlaybackPosition.Estimate
        // A sample taken from a stream segment that has since been replaced describes unrelated
        // content. Presenting it against this history would report a false distance behind live
        // and could authorise a seek on the successor derived from the predecessor's coordinate.
        val position = estimate
            ?.takeIf { it.timeline?.describesSameSegment(timeline) == true }
            ?.target
        AppTimeshiftState(
            available = true,
            paused = playbackPaused == true,
            bufferStartMs = timeline?.start?.inWholeMilliseconds ?: 0L,
            liveEdgeMs = timeline?.end?.inWholeMilliseconds ?: 0L,
            positionMs = position?.position?.inWholeMilliseconds ?: 0L,
            serverBehindLiveMs = positionBehindLive
                ?.takeIf { it.isFinite() && it >= Duration.ZERO }
                ?.inWholeMilliseconds,
            capacityMs = grantedPeriod.takeIf { it.isFinite() && it > Duration.ZERO }?.inWholeMilliseconds,
            // The latest status edge can lag a valid decoded-content coordinate.
            // Seekability still comes from the observed history, not this sample.
            timingKnown = timeline != null && position != null,
            timeline = timeline,
            playbackTarget = position,
            playbackSeek = estimate?.seek.takeIf { position != null },
        )
    }
}

internal fun measuredTimeshiftPresentation(
    bufferedDuration: Duration?,
    positionBehindLive: Duration?,
    serverPaused: Boolean?,
    grantedPeriod: Duration? = null,
): AppTimeshiftState {
    val buffered = bufferedDuration?.takeIf { it.isFinite() && it >= Duration.ZERO }
        ?.inWholeMilliseconds
    val behind = positionBehindLive?.takeIf { it.isFinite() && it >= Duration.ZERO }
        ?.inWholeMilliseconds
    return AppTimeshiftState(
        available = true,
        paused = serverPaused == true,
        bufferStartMs = -(buffered ?: 0L),
        positionMs = -(behind ?: 0L),
        serverBehindLiveMs = behind,
        capacityMs = grantedPeriod?.takeIf { it.isFinite() && it > Duration.ZERO }
            ?.inWholeMilliseconds,
        timingKnown = buffered != null && behind != null && behind <= buffered,
    )
}
