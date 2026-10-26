@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.profiling

import androidx.media3.common.Format
import at.bernhardberger.tvhplayer.client.BuildConfig
import at.bernhardberger.tvhplayer.playback.PausedSeekNudgePhase
import at.bernhardberger.tvhplayer.playback.PlaybackStall
import at.bernhardberger.tvhplayer.playback.PlaybackTrace

/** Emits the playback markers the profiling tooling parses; the strings must not change. */
object ProfilePlaybackTrace : PlaybackTrace {
    override val enabled: Boolean get() = BuildConfig.PROFILE_TRACE

    override fun tuneAdmitted() {
        profileTrace("P44:tune:admitted") {}
    }

    override fun tuneBound(epoch: Long) {
        profileTrace("P44:tune:bound:$epoch") {}
    }

    override fun ready(epoch: Long?) {
        profileTrace("P44:ready:$epoch") {}
    }

    override fun firstVideoFrame(epoch: Long, format: Format?, adapterName: String?) {
        profileFirstVideoFrame(epoch, format, adapterName)
    }

    override fun videoCoverShown() {
        profileTrace("P44:cover:shown") {}
    }

    override fun videoPictureAdvancing(epoch: Long) {
        profileTrace("P44:picture:advancing:$epoch") {}
    }

    override fun videoCoverLifted(epoch: Long) {
        profileTrace("P44:cover:lifted:$epoch") {}
    }

    override fun stall(stall: PlaybackStall, epoch: Long) {
        profileTrace("P44:stall:${stall.name.lowercase()}:$epoch") {}
    }

    override fun freshDecoderPerZap() {
        profileTrace("P44:zap:fresh-decoder:on") {}
    }

    override fun pausedSeekNudge(phase: PausedSeekNudgePhase) {
        profileTrace("P44:seek:nudge:${phase.name.lowercase()}") {}
    }
}
