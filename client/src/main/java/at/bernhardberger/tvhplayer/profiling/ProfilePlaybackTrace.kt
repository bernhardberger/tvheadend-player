@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.profiling

import androidx.media3.common.Format
import at.bernhardberger.tvhplayer.client.BuildConfig
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
}
