package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.playback.AppLiveTargetPresentation
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.Channel

/** Display only, including the brief Ready-to-resolved handoff; never admits a playback target. */
internal fun mainStartupDisplayChannel(
    readiness: CurrentChannelReadiness,
    persistedId: ChannelId?,
): Channel? {
    val channels = when (readiness) {
        is CurrentChannelReadiness.Browsable -> readiness.channels
        is CurrentChannelReadiness.Ready -> readiness.channels
        else -> return null
    }
    val id = LastPlayedChannelPolicy.resolve(channels.map { it.id }, persistedId)
    return channels.firstOrNull { it.id == id }
}

enum class MainStartupPlaybackOutcome { PRESENTED, ALREADY_PLAYING, RECOVERY }

/** Prior live identity is captured before autoplay; a recording is never live resume evidence. */
internal fun mainStartupReturningToPlayback(
    priorLiveChannelId: ChannelId?,
    resolvedTarget: ApplianceLaunchTarget?,
): Boolean = priorLiveChannelId != null && priorLiveChannelId == resolvedTarget?.channelId

/** Presentation-only ownership survives launch completion, but never profile replacement. */
internal data class MainStartupReveal(val target: ApplianceLaunchTarget, val profileGeneration: Long) {
    fun targetFor(profileGeneration: Long): ApplianceLaunchTarget? = target.takeIf { this.profileGeneration == profileGeneration }
}

/** The player supplies its own admission/recovery outcome and a session/intent-fenced runtime signal. */
internal fun mainStartupPlaybackOutcome(
    expectedEpoch: Long?,
    presentation: AppLiveTargetPresentation?,
    recovery: Boolean,
    adoptedPlaying: Boolean,
): MainStartupPlaybackOutcome? = when {
    recovery -> MainStartupPlaybackOutcome.RECOVERY
    expectedEpoch == null || presentation?.epoch != expectedEpoch -> null
    !presentation.visible && !(presentation.playing && presentation.audioOnly) -> null
    adoptedPlaying && presentation.playing -> MainStartupPlaybackOutcome.ALREADY_PLAYING
    else -> MainStartupPlaybackOutcome.PRESENTED
}
