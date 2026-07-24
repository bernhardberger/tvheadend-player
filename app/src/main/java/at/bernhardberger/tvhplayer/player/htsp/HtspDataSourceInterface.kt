package at.bernhardberger.tvhplayer.player.htsp

import at.bernhardberger.tvhplayer.core.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.TimeshiftState
import kotlinx.coroutines.flow.StateFlow

interface HtspDataSourceInterface {

    val timeshiftState: StateFlow<TimeshiftState>

    val timeshiftOffsetPts: Long

    val timeshiftStartTime: Long

    val timeshiftStartPts: Long

    fun setSpeed(tvhSpeed: Int)

    fun resume()

    fun pause()

    fun seekTimeshift(deltaMs: Long): TimeshiftSeekDecision

    fun goLive(): TimeshiftSeekDecision

    fun getResponseHeaders(): Map<String, List<String>>?
}
