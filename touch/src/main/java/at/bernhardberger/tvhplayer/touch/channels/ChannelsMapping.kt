package at.bernhardberger.tvhplayer.touch.channels

import at.bernhardberger.tvhplayer.touch.ui.programRecordControl
import at.bernhardberger.tvhplayer.touch.ui.ruleSchedule
import at.bernhardberger.tvhplayer.ui.screens.programmeRecordKeyAction
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.activeRecordingChannelIds
import at.bernhardberger.tvhplayer.core.browseAppError
import at.bernhardberger.tvhplayer.core.forEmptyChannelPresentation
import at.bernhardberger.tvhplayer.core.recordingWasRemoved
import at.bernhardberger.tvhplayer.core.runningRecordingForEvent
import at.bernhardberger.tvhplayer.core.shouldPresentEmptyTag
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.screens.isScheduled
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import kotlin.time.Instant

/**
 * The Channels destination from the shared channel scope and the session's guide, as the TV's
 * Channels screen reads them: the scope already holds the visible channels and tags.
 *
 * Every row on now carries its time range, time left and on-air progress; the row of [playingId]
 * is the channel the player plays, whose progress marks the viewer. Marks follow the TV: the
 * running program's own recording, else the channel's running recording; scheduled on the
 * programs ahead. With [artwork] rows carry the logo's and the running program's image models;
 * [ChannelList.hasLogos] keeps the tile in its place, the no-logo tile where a channel has none.
 */
@Suppress("LongParameterList")
internal fun channelsState(
    scope: ChannelScopeState,
    connection: ConnectionUiState,
    degraded: Boolean,
    observation: SessionObservation,
    now: Instant,
    texts: ChannelsTexts = ChannelsTexts.TitlesOnly,
    playingId: String? = null,
    artwork: ArtworkModels? = null,
    programs: List<ChannelRowPrograms>? = null,
    checkpoint: () -> Unit = {},
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
                shown == ConnectionUiState.Ready -> ChannelsState.Ready(ChannelList(emptyList()))
                else -> shown.browseAppError()?.let(ChannelsState::Failed) ?: ChannelsState.Loading
            }
        }
        browsing.visibleChannels.isEmpty() -> when {
            shouldPresentEmptyTag(current, connection, true, browsing.activeTagId != null) ->
                ChannelsState.Ready(ChannelList(emptyList(), tags, selectedTagId))
            else -> connection.browseAppError()?.let(ChannelsState::Failed) ?: ChannelsState.Loading
        }
        else -> {
            val mapper = RowMapper(observation, now, texts, artwork)
            val rows = browsing.visibleChannels.mapIndexed { index, channel ->
                checkpoint()
                mapper.row(channel, playing = channel.id.value.toString() == playingId, programs?.get(index))
            }
            val list = ChannelList(rows, tags, selectedTagId)
            if (degraded) ChannelsState.Offline(list) else ChannelsState.Ready(list)
        }
    }
}

/**
 * One channel's row outside the list, as the player's channel strip shows it; without [texts] it
 * carries the titles only.
 */
internal fun channelRow(
    channel: Channel,
    observation: SessionObservation,
    now: Instant,
    playing: Boolean = false,
    texts: ChannelsTexts = ChannelsTexts.TitlesOnly,
    artwork: ArtworkModels? = null,
): ChannelRow = RowMapper(observation, now, texts, artwork).row(channel, playing)

private class RowMapper(
    private val observation: SessionObservation,
    private val now: Instant,
    private val texts: ChannelsTexts,
    private val artwork: ArtworkModels?,
) {
    private val recordingChannels by lazy {
        val entries = observation.dvrSnapshotForDisplay?.entries.orEmpty().filterNot(::recordingWasRemoved)
        activeRecordingChannelIds(entries, now.epochSeconds)
    }

    fun row(channel: Channel, playing: Boolean, projected: ChannelRowPrograms? = null): ChannelRow {
        val programs = projected ?: observation.channelRowPrograms(channel.id, now)
        val running = programs.running
        val next = programs.ahead.firstOrNull()
        val following = programs.ahead.drop(1).map { it.program() }
        val details = ChannelDetails(
            artworkModel = running?.let { artwork?.picture(programmeArtwork(it), observation) },
            episode = running?.let { texts.episode(it) },
            metadata = running?.metadata(),
            synopsis = running?.synopsis(),
            following = following,
        )
        val programRecording = observation.runningRecordingForEvent(running) != null
        return ChannelRow(
            id = channel.id.value.toString(),
            name = channel.name.orEmpty(),
            logoModel = artwork?.logo(channel.icon, observation),
            now = running?.program(),
            next = next?.program(),
            playback = if (playing) ChannelPlayback(running?.progress(now.epochSeconds)) else null,
            recording = when {
                programRecording -> ChannelRecording.Program
                channel.id in recordingChannels -> ChannelRecording.Channel
                else -> null
            },
            details = details.takeIf { it != ChannelDetails() },
        )
    }

    /** A running program gives its range, time left and progress; one ahead gives its start. */
    private fun EpgEvent.program(): ChannelProgram {
        val nowSec = now.epochSeconds
        val running = isRunning(nowSec)
        return ChannelProgram(
            title = title.orEmpty(),
            start = texts.clock(start.epochSeconds),
            timeRange = if (running) texts.timeRange(start.epochSeconds, stop.epochSeconds) else null,
            progress = progress(nowSec).takeIf { running },
            timeLeft = if (running) texts.duration(stop.epochSeconds - nowSec) else null,
            scheduled = !running && observation.isScheduled(this),
            ruleSchedule = observation.ruleSchedule(this),
            record = programRecordControl(programmeRecordKeyAction(this, observation, nowSec)),
            eventId = id,
            airing = (start.epochSeconds until stop.epochSeconds).takeIf { running },
        )
    }

    /** The opened row's line as drawn: genre, year, length. */
    private fun EpgEvent.metadata(): String? =
        listOfNotNull(
            texts.genre(this), copyrightYear?.toString(), texts.duration(stop.epochSeconds - start.epochSeconds),
        )
            .joinToString(" • ")
            .takeIf(String::isNotEmpty)

    /**
     * The TV's rule (`programmeSynopsis`, app ProgrammePane.kt:280): the first summary or
     * description that doesn't repeat the title or subtitle.
     */
    private fun EpgEvent.synopsis(): String? =
        sequenceOf(summary, description)
            .mapNotNull { it?.replace("\\r\\n", "\n")?.replace("\\n", "\n")?.trim() }
            .firstOrNull { it.isNotBlank() && it != title?.trim() && it != subtitle?.trim() }
}

internal data class ChannelRowPrograms(
    val running: EpgEvent?,
    val ahead: List<EpgEvent>,
) {
    fun nextBoundary(now: Instant): Long = sequenceOf(running?.stop, ahead.firstOrNull()?.start)
        .filterNotNull()
        .filter { it > now }
        .minOfOrNull { it.epochSeconds + if (it.nanosecondsOfSecond == 0) 0 else 1 }
        ?: Long.MAX_VALUE
}

internal fun SessionObservation.channelRowPrograms(id: ChannelId, now: Instant): ChannelRowPrograms =
    ChannelRowPrograms(
        eventAt(id, now),
        generateSequence(nextEvent(id, now)) { nextEvent(id, it.start) }.take(FOLLOWING_LIMIT + 1).toList(),
    )

internal fun SessionObservation.sameProgrammeAuthority(previous: SessionObservation): Boolean {
    if (currentSession !== previous.currentSession) return false
    val write = (sessionState as? SessionState.Ready)?.capabilities?.dvrWrite
    val previousWrite = (previous.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite
    return write == previousWrite && recordingProgressCapability == previous.recordingProgressCapability &&
        (dvrState is DvrRepositoryState.Current) == (previous.dvrState is DvrRepositoryState.Current)
}

private const val FOLLOWING_LIMIT = 3
