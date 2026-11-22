package at.bernhardberger.tvhplayer.touch.channels

import at.bernhardberger.tvhplayer.touch.ui.programRecordControl
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import at.bernhardberger.tvhplayer.touch.ui.RuleSchedule
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
 * Browse needs only the logo and what's on now and next. The player list still asks for
 * [includeDetails] (including the image, controls, time range and programs after next).
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
    previousRows: Map<ChannelId, MappedChannelRow> = emptyMap(),
    mappedRows: MutableMap<ChannelId, MappedChannelRow>? = null,
    includeDetails: Boolean = true,
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
            val mapper = RowMapper(observation, now, texts, artwork, includeDetails)
            val rows = browsing.visibleChannels.mapIndexed { index, channel ->
                checkpoint()
                val row = mapper.map(
                    channel, channel.id.value.toString() == playingId, programs?.get(index), previousRows[channel.id],
                )
                mappedRows?.put(channel.id, row)
                row.row
            }
            val list = ChannelList(rows, tags, selectedTagId)
            if (degraded) ChannelsState.Offline(list) else ChannelsState.Ready(list)
        }
    }
}

internal class MappedChannelRow(val row: ChannelRow, internal val inputs: ChannelRowInputs)

internal data class ChannelRowInputs(
    val programs: ChannelRowPrograms,
    val name: String,
    val logo: Any?,
    val picture: Any?,
    val playing: Boolean,
    val recording: ChannelRecording?,
    val runningMarks: ChannelProgramMarks?,
    val aheadMarks: List<ChannelProgramMarks>,
    val left: String?,
    // The browse UI draws live progress from airing bounds. Refresh its fallback on the minute wake.
    val progressMinute: Long?,
)

internal data class ChannelProgramMarks(
    val scheduled: Boolean,
    val rule: RuleSchedule?,
    val record: ProgramRecordControl?,
)

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
): ChannelRow = RowMapper(observation, now, texts, artwork).map(channel, playing).row

private class RowMapper(
    private val observation: SessionObservation,
    private val now: Instant,
    private val texts: ChannelsTexts,
    private val artwork: ArtworkModels?,
    private val includeDetails: Boolean = true,
) {
    private val remainingLabels = mutableMapOf<Long, String?>()
    // Match the Guide clock for the label only. Programme selection and progress use the actual instant.
    private val labelMinute = Math.floorDiv(now.epochSeconds, SECONDS_PER_MINUTE)
    private val labelNowSec = labelMinute * SECONDS_PER_MINUTE
    private val recordingChannels by lazy {
        val entries = observation.dvrSnapshotForDisplay?.entries.orEmpty().filterNot(::recordingWasRemoved)
        activeRecordingChannelIds(entries, now.epochSeconds)
    }

    fun map(
        channel: Channel,
        playing: Boolean,
        projected: ChannelRowPrograms? = null,
        previous: MappedChannelRow? = null,
    ): MappedChannelRow {
        val programs = projected ?: observation.channelRowPrograms(channel.id, now, includeDetails)
        val running = programs.running
        val recording = when {
            observation.runningRecordingForEvent(running) != null -> ChannelRecording.Program
            channel.id in recordingChannels -> ChannelRecording.Channel
            else -> null
        }
        val inputs = ChannelRowInputs(
            programs, channel.name.orEmpty(), artwork?.logo(channel.icon, observation),
            if (includeDetails) running?.let { artwork?.picture(programmeArtwork(it), observation) } else null,
            playing, recording, if (includeDetails) running?.marks() else null, programs.ahead.map { it.marks() },
            running?.let { remainingLabel(it.stop.epochSeconds - labelNowSec) },
            running?.let { labelMinute },
        )
        return if (previous?.inputs == inputs) previous
        else MappedChannelRow(buildRow(channel.id, inputs), inputs)
    }

    private fun buildRow(id: ChannelId, inputs: ChannelRowInputs): ChannelRow = with(inputs) {
        val running = programs.running
        val ahead = programs.ahead.mapIndexed { index, event -> event.program(aheadMarks[index]) }
        val details = if (includeDetails) {
            ChannelDetails(
                artworkModel = picture,
                episode = running?.let { texts.episode(it) },
                metadata = running?.metadata(),
                synopsis = running?.synopsis(),
                following = ahead.drop(1),
            ).takeIf { it != ChannelDetails() }
        } else null
        ChannelRow(
            id = id.value.toString(),
            name = name,
            logoModel = logo,
            now = running?.program(runningMarks),
            next = ahead.firstOrNull(),
            playback = if (playing) ChannelPlayback(running?.progress(now.epochSeconds)) else null,
            recording = recording,
            details = details,
        )
    }

    private fun remainingLabel(seconds: Long): String? {
        if (seconds !in remainingLabels) remainingLabels[seconds] = texts.duration(seconds)
        return remainingLabels[seconds]
    }

    private fun EpgEvent.marks() = ChannelProgramMarks(
        scheduled = !isRunning(now.epochSeconds) && observation.isScheduled(this),
        rule = observation.ruleSchedule(this),
        record = if (includeDetails) {
            programRecordControl(programmeRecordKeyAction(this, observation, now.epochSeconds))
        } else null,
    )

    /** A running program gives its range, time left and progress; one ahead gives its start. */
    private fun EpgEvent.program(marks: ChannelProgramMarks?): ChannelProgram {
        val nowSec = now.epochSeconds
        val running = isRunning(nowSec)
        return ChannelProgram(
            title = title.orEmpty(),
            start = if (!running || includeDetails) texts.clock(start.epochSeconds) else null,
            timeRange = if (running && includeDetails) texts.timeRange(start.epochSeconds, stop.epochSeconds) else null,
            progress = progress(nowSec).takeIf { running },
            timeLeft = if (running) remainingLabel(stop.epochSeconds - labelNowSec) else null,
            scheduled = marks?.scheduled == true,
            ruleSchedule = marks?.rule,
            record = marks?.record,
            eventId = id.takeIf { includeDetails },
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

internal fun SessionObservation.channelRowPrograms(
    id: ChannelId,
    now: Instant,
    includeDetails: Boolean = true,
): ChannelRowPrograms =
    ChannelRowPrograms(
        eventAt(id, now),
        if (includeDetails) {
            generateSequence(nextEvent(id, now)) { nextEvent(id, it.start) }.take(FOLLOWING_LIMIT + 1).toList()
        } else listOfNotNull(nextEvent(id, now)),
    )

internal fun SessionObservation.sameProgrammeAuthority(previous: SessionObservation): Boolean {
    if (currentSession !== previous.currentSession) return false
    val write = (sessionState as? SessionState.Ready)?.capabilities?.dvrWrite
    val previousWrite = (previous.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite
    return write == previousWrite && recordingProgressCapability == previous.recordingProgressCapability &&
        (dvrState is DvrRepositoryState.Current) == (previous.dvrState is DvrRepositoryState.Current)
}

private const val FOLLOWING_LIMIT = 3
private const val SECONDS_PER_MINUTE = 60L
