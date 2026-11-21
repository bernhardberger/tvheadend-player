package at.bernhardberger.tvhplayer.touch.channels

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.browseAppError
import at.bernhardberger.tvhplayer.core.forEmptyChannelPresentation
import at.bernhardberger.tvhplayer.core.shouldPresentEmptyTag
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import kotlin.time.Instant

/**
 * The Channels destination from the shared channel scope and the session's guide, as the TV's
 * Channels screen reads them: the scope already holds the visible channels and tags.
 *
 * Left empty until their shared helpers exist outside the TV app: the clock texts (start, time
 * range, time left), the on-air progress, the episode line and the synopsis. Rows carry no logo;
 * [ChannelList.hasLogos] keeps the no-logo tile in its place.
 */
internal fun channelsState(
    scope: ChannelScopeState,
    connection: ConnectionUiState,
    degraded: Boolean,
    observation: SessionObservation,
    now: Instant,
): ChannelsState {
    val browsing = scope.scope
    val current = scope.channelCatalogCurrent
    val tags = browsing.tags.map { ChannelTag(it.id.value.toString(), it.name.orEmpty()) }
    val selectedTagId = browsing.activeTagId?.value?.toString()
    return when {
        connection == ConnectionUiState.CredentialUnavailable -> ChannelsState.SignInNeeded
        !scope.settingsLoaded -> ChannelsState.Loading
        browsing.allChannels.isEmpty() -> {
            val shown = connection.forEmptyChannelPresentation(current)
            when {
                shown == ConnectionUiState.Ready -> ChannelsState.Ready(ChannelList(emptyList(), hasLogos = true))
                else -> shown.browseAppError()?.let(ChannelsState::Failed) ?: ChannelsState.Loading
            }
        }
        browsing.visibleChannels.isEmpty() -> when {
            shouldPresentEmptyTag(current, connection, true, browsing.activeTagId != null) ->
                ChannelsState.Ready(ChannelList(emptyList(), tags, selectedTagId, hasLogos = true))
            else -> connection.browseAppError()?.let(ChannelsState::Failed) ?: ChannelsState.Loading
        }
        else -> {
            val rows = browsing.visibleChannels.map { channelRow(it, observation, now) }
            val list = ChannelList(rows, tags, selectedTagId, hasLogos = true)
            if (degraded) ChannelsState.Offline(list) else ChannelsState.Ready(list)
        }
    }
}

private fun channelRow(channel: Channel, observation: SessionObservation, now: Instant): ChannelRow {
    val next = observation.nextEvent(channel.id, now)
    // Each later program is the one after the program running at the previous one's start.
    val following = generateSequence(next) { observation.nextEvent(channel.id, it.start) }
        .drop(1)
        .take(FOLLOWING_LIMIT)
        .map { it.program() }
        .toList()
    return ChannelRow(
        id = channel.id.value.toString(),
        name = channel.name.orEmpty(),
        now = observation.eventAt(channel.id, now)?.program(),
        next = next?.program(),
        details = following.takeIf { it.isNotEmpty() }?.let { ChannelDetails(following = it) },
    )
}

private fun EpgEvent.program() = ChannelProgram(title = title.orEmpty(), start = null)

private const val FOLLOWING_LIMIT = 3
