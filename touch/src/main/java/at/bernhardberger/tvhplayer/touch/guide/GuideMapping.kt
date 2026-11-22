package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvhplayer.touch.ui.ruleSchedule
import at.bernhardberger.tvhplayer.ui.screens.isScheduled

import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgCreditRole
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.browseAppError
import at.bernhardberger.tvhplayer.core.forEmptyChannelPresentation
import at.bernhardberger.tvhplayer.core.otherAirings
import at.bernhardberger.tvhplayer.core.programmeAgeRating
import at.bernhardberger.tvhplayer.core.shouldPresentEmptyTag
import at.bernhardberger.tvhplayer.touch.channels.ChannelTag
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A null day follows today; a null jump follows Now. A jump belongs to one instant, not a tab. */
internal data class GuideSelection(
    val day: LocalDate? = null,
    val jump: Long? = null,
    val detailsId: String? = null,
    val channelId: String? = null,
    val view: GuideView = GuideView.Overview,
) {
    fun dateIn(days: List<LocalDate>): LocalDate = day?.takeIf { it in days } ?: days.first()
}

/** The time the Guide is drawn at. */
internal data class GuideClock(val nowSec: Long, val zone: ZoneId) {
    val today: LocalDate get() = dateAt(nowSec)
    fun dateAt(seconds: Long): LocalDate = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
}

/**
 * The words the mapping needs from resources and the locale, so the mapping stays plain Kotlin:
 * the shared formatters' clock, range, duration, date, episode and genre texts.
 */
internal class GuideTexts(
    /** "8:00 PM". */
    val clock: (epochSec: Long) -> String,
    /** "8:00–9:00 PM". */
    val timeRange: (startSec: Long, stopSec: Long) -> String,
    /** "1h 27m". */
    val duration: (seconds: Long) -> String,
    /** A day tab: "Today", "Tomorrow", "Nov 17". */
    val day: (date: LocalDate, today: LocalDate) -> String,
    /** The selected day written out: "Sunday, November 15". */
    val date: (LocalDate) -> String,
    /** Another airing's time: "Tomorrow 7:50 PM". */
    val airing: (epochSec: Long, today: LocalDate) -> String,
    /** "S1 E6". */
    val number: (EpgEvent) -> String?,
    /** The episode line under the title. */
    val episode: (EpgEvent) -> String?,
    val genre: (EpgEvent) -> String?,
    val firstAired: (epochMillis: Long) -> String,
    /** A metadata number, "2 of 3" with the [of] count the mapping found to cover it. */
    val count: (number: Long, of: Long?) -> String,
    /** Broadcast: "New" or "Repeat". */
    val broadcast: (isNew: Boolean) -> String,
)

/**
 * The Guide destination from the shared channel scope and the session's guide: the overview from
 * the day's jump time, By channel's day, the day tabs and the opened program's Details.
 *
 * With [artwork] channels carry their logos' and programs their pictures' image models; the program
 * on now on the [playing] channel is the one watched.
 */
@Suppress("LongParameterList")
internal fun guideState(
    scope: ChannelScopeState,
    connection: ConnectionUiState,
    degraded: Boolean,
    observation: SessionObservation,
    selection: GuideSelection,
    dayLoading: Boolean,
    clock: GuideClock,
    texts: GuideTexts,
    artwork: ArtworkModels? = null,
    playing: String? = null,
    overviewRows: GuideOverviewRows = GuideOverviewRows(),
    checkpoint: () -> Unit = {},
): GuideState {
    val browsing = scope.scope
    val current = scope.channelCatalogCurrent
    val tags = browsing.tags.map { ChannelTag(it.id.value.toString(), it.name.orEmpty()) }
    val selectedTagId = browsing.activeTagId?.value?.toString()
    val today = clock.today
    val empty = GuideState(emptyList(), tags, selectedTagId, days = emptyList(), date = texts.date(today))
    val snapshot = observation.epgSnapshotForDisplay
    val unavailable = { error: ConnectionUiState ->
        empty.copy(availability = error.browseAppError()?.let(GuideAvailability::Failed) ?: GuideAvailability.Loading)
    }
    return when {
        connection == ConnectionUiState.CredentialUnavailable ->
            empty.copy(availability = GuideAvailability.SignInNeeded)
        !scope.settingsLoaded -> empty.copy(availability = GuideAvailability.Loading)
        browsing.allChannels.isEmpty() -> {
            val shown = connection.forEmptyChannelPresentation(current)
            if (shown == ConnectionUiState.Ready) empty else unavailable(shown)
        }
        browsing.visibleChannels.isEmpty() ->
            if (shouldPresentEmptyTag(current, connection, true, browsing.activeTagId != null)) {
                empty
            } else {
                unavailable(connection)
            }
        snapshot == null -> unavailable(connection)
        else -> GuideMapper(
            browsing.visibleChannels, observation, snapshot.events, clock, today, texts, artwork, playing,
        )
            .state(selection, tags, selectedTagId, overviewRows, checkpoint)
            .copy(
                dayLoading = dayLoading,
                availability = if (degraded) GuideAvailability.Offline else GuideAvailability.Current,
            )
    }
}

/** The jump times after Now, as drawn (section 7); which times to offer is an open design question. */
internal val GUIDE_JUMP_TIMES: List<LocalTime> = listOf(LocalTime.of(20, 15), LocalTime.of(22, 0))

/** Raw inputs to the visible schedule, including the tabs and the separately opened Details. */
internal data class GuideEpgProjection(
    val days: List<LocalDate>,
    val events: Map<ChannelId?, List<EpgEvent>>,
    val details: EpgEvent?,
    val airings: List<EpgEvent>,
)

internal fun GuideMappingInput.epgProjection(): GuideEpgProjection? {
    val snapshot = observation.epgSnapshotForDisplay ?: return null
    val visible = scope.scope.visibleChannels.mapTo(HashSet()) { it.id }
    val upcoming = snapshot.events.filter {
        it.channelId in visible && it.stop > it.start && it.stop.epochSeconds > clock.nowSec
    }
    val days = guideDays(upcoming, clock)
    val date = selection.dateIn(days)
    val jump = selection.jump?.takeIf { second ->
        second > clock.nowSec && GUIDE_JUMP_TIMES.any { date.atTime(it).atZone(clock.zone).toEpochSecond() == second }
    }
    val window = guideWindow(date, jump, clock)
    val byChannel = selection.view == GuideView.ByChannel
    val selectedChannel = dayChannel
    val end = if (byChannel) window.daySec.last + 1
    else maxOf(window.daySec.last + 1, window.anchorSec + OVERVIEW_SPAN_SEC)
    val events = upcoming.asSequence()
        .filter { !byChannel || it.channelId == selectedChannel }
        .filter { it.stop.epochSeconds > window.daySec.first && it.start.epochSeconds < end }
        .groupBy { it.channelId }
    val details = selection.detailsId?.toLongOrNull()?.let { id -> snapshot.events.firstOrNull { it.id.value == id } }
    val airings = details?.let { otherAirings(it, snapshot.events, clock.nowSec, AIRINGS_LIMIT) }.orEmpty()
    return GuideEpgProjection(days, events, details, airings)
}

/** The day tabs: today, then every later day the visible channels have programs on. */
internal fun guideDays(events: List<EpgEvent>, clock: GuideClock): List<LocalDate> {
    val today = clock.today
    val later = events.asSequence()
        .filter { it.stop > it.start && it.stop.epochSeconds > clock.nowSec }
        .flatMap {
            val first = maxOf(clock.dateAt(it.start.epochSeconds), today.plusDays(1))
            // Event stops are exclusive: an event ending exactly at midnight is not on the next day.
            val last = clock.dateAt(it.stop.epochSeconds - 1)
            generateSequence(first) { date -> date.plusDays(1) }.takeWhile { date -> date <= last }
        }
    return (sequenceOf(today) + later).distinct().sorted().toList()
}

/** The guide the selected day needs: the visible channels through the end of that day. */
internal fun guideCoverage(
    scope: ChannelScopeState,
    observation: SessionObservation,
    selection: GuideSelection,
    clock: GuideClock,
): GuideCoverage? {
    val channels = scope.scope.visibleChannels
    return if (selection.view == GuideView.Overview) {
        guideCoverage(channels.map { it.id }, observation, selection, clock)
    } else {
        val requestedId = selection.channelId?.toLongOrNull()
        val channel = requestedId?.let { id -> channels.firstOrNull { it.id.value == id } } ?: channels.firstOrNull()
        val window = guideWindow(maxOf(selection.day ?: clock.today, clock.today), null, clock)
        channel?.let { GuideCoverage(listOf(it.id), window.daySec.first, window.daySec.last + 1) }
    }
}

internal fun guideCoverage(
    ids: List<ChannelId>,
    observation: SessionObservation,
    selection: GuideSelection,
    clock: GuideClock,
): GuideCoverage? {
    if (ids.isEmpty()) return null
    val date = if (selection.day == null || selection.day == clock.today) {
        clock.today
    } else {
        val shown = ids.toSet()
        val events = observation.epgSnapshotForDisplay?.events.orEmpty().filter { it.channelId in shown }
        selection.dateIn(guideDays(events, clock))
    }
    val window = guideWindow(date, null, clock)
    return GuideCoverage(ids, window.daySec.first, window.daySec.last + 1)
}

/** The selected day's window and the overview's anchor in it. */
internal data class GuideWindow(val daySec: LongRange, val anchorSec: Long)

/**
 * Today from now, another day from its start; a jump time moves the anchor to that time of the
 * selected day. The jumps already past today are not offered.
 */
internal fun guideWindow(date: LocalDate, jump: Long?, clock: GuideClock): GuideWindow {
    val start = date.atStartOfDay(clock.zone).toEpochSecond()
    val end = date.plusDays(1).atStartOfDay(clock.zone).toEpochSecond()
    val anchor = jump ?: maxOf(start, clock.nowSec)
    return GuideWindow(start until end, anchor)
}

@Suppress("LongParameterList")
internal class GuideMapper(
    private val channels: List<Channel>,
    private val observation: SessionObservation,
    private val events: List<EpgEvent>,
    private val clock: GuideClock,
    private val today: LocalDate,
    private val texts: GuideTexts,
    private val artwork: ArtworkModels?,
    private val playing: String?,
) {
    private fun upcoming(events: Sequence<EpgEvent>) = events
        .filter { it.stop > it.start && it.stop.epochSeconds > clock.nowSec }
        .distinctBy { it.id }
        .sortedBy { it.start }

    private val names by lazy { channels.associate { it.id to it.name.orEmpty() } }
    private val logos by lazy { channels.associate { it.id to artwork?.logo(it.icon, observation) } }

    fun state(
        selection: GuideSelection,
        tags: List<ChannelTag>,
        selectedTagId: String?,
        rows: GuideOverviewRows,
        checkpoint: () -> Unit,
    ): GuideState {
        checkpoint()
        val scheduleIndex = rows.schedule(channels, events, clock, playing)
        val days = scheduleIndex.days
        val date = selection.dateIn(days)
        val jumps = GUIDE_JUMP_TIMES.map { date.atTime(it).atZone(clock.zone).toEpochSecond() }
            .filter { it > clock.nowSec }
        val jumpIndex = jumps.indexOf(selection.jump) + 1
        val window = guideWindow(date, jumps.getOrNull(jumpIndex - 1), clock)
        val dayChannel = if (selection.view == GuideView.ByChannel) {
            channels.firstOrNull { it.id.value.toString() == selection.channelId } ?: channels.firstOrNull()
        } else null
        var phoneRows = 0
        val guideChannels = channels.mapIndexed { index, channel ->
            checkpoint()
            val schedule = scheduleIndex.byChannel[channel.id].orEmpty()
            val id = channel.id.value.toString()
            val next = schedule.firstOrNull { it.stop.epochSeconds > window.anchorSec }
            val hasLead = next != null && next.start.epochSeconds <= window.anchorSec
            val phoneIndex = if (hasLead) phoneRows++ else null
            val requested = dayChannel == null && rows.includes(index, phoneIndex, id)
            val pending = !requested && channel != dayChannel
            val logo = if (!pending || dayChannel != null) artwork?.logo(channel.icon, observation) else null
            val head = GuideChannel(
                id, channel.name.orEmpty(), logoModel = logo, overviewHasLead = hasLead, overviewPending = pending,
                hasUnmappedLogo = pending && artwork != null && channel.icon != null,
            )
            if (pending) head else overviewChannel(head, schedule, window, channel == dayChannel, rows)
        }
        val details = selection.detailsId?.let { details(it) }
        return GuideState(
            channels = guideChannels,
            tags = tags,
            selectedTagId = selectedTagId,
            days = days.map { texts.day(it, today) },
            selectedDay = days.indexOf(date),
            dayDates = days,
            date = texts.date(date),
            jumps = jumps.map(texts.clock),
            selectedJump = jumpIndex,
            jumpTimes = jumps,
            details = details,
        )
    }

    private fun overviewChannel(
        head: GuideChannel, schedule: List<EpgEvent>, window: GuideWindow, includeDay: Boolean, rows: GuideOverviewRows,
    ): GuideChannel {
        val overviewEnd = maxOf(window.daySec.last + 1, window.anchorSec + OVERVIEW_SPAN_SEC)
        val overview = mutableListOf<EpgEvent>()
        var gap: Pair<Long, Long>? = null
        val shown = schedule.asSequence()
            .filter { it.stop.epochSeconds > window.anchorSec && it.start.epochSeconds < overviewEnd }
        for (event in shown) {
            val coveredThrough = overview.lastOrNull()?.stop?.epochSeconds ?: window.anchorSec
            if (event.start.epochSeconds > coveredThrough) {
                gap = coveredThrough to event.start.epochSeconds
                break
            }
            overview += event
        }
        val day = if (includeDay) schedule.filter { it.stop.epochSeconds > clock.nowSec && it.overlaps(window.daySec) }
        else null
        val marks = (overview + day.orEmpty()).distinctBy { it.id }
            .associateWith { observation.overviewMarks(it, clock.nowSec, artwork) }
        val key = GuideOverviewRowKey(head, overview, gap, day, marks)
        return rows.getOrMap(key, texts.clock) { event, previous ->
            val currentMarks = marks.getValue(event)
            if (previous == null) program(event, marks = currentMarks)
            else previous.refresh(event, currentMarks, clock.nowSec, texts.duration, playing)
        }
    }

    /** The player can also be watching a past program, which belongs at the top of its Today list. */
    fun channelDay(id: ChannelId, date: LocalDate, watched: EpgEvent?): List<GuideProgram> {
        val schedule = upcoming(events.asSequence().filter { it.channelId == id })
        val playingEvent = watched?.takeIf { it.channelId == id && it.stop > it.start }
        val day = dayPrograms(schedule, guideWindow(date, null, clock).daySec, playingEvent)
        return if (playingEvent != null && date == today && day.none { it.watching }) {
            listOf(program(playingEvent, playingEvent)) + day
        } else {
            day
        }
    }

    private fun dayPrograms(schedule: Sequence<EpgEvent>, window: LongRange, watched: EpgEvent?): List<GuideProgram> =
        schedule.filter { it.overlaps(window) }.map { program(it, watched) }.toList()

    private fun EpgEvent.overlaps(window: LongRange): Boolean =
        stop.epochSeconds > window.first && start.epochSeconds <= window.last

    private fun program(
        event: EpgEvent, watched: EpgEvent? = null,
        marks: GuideOverviewMarks = observation.overviewMarks(event, clock.nowSec, artwork),
    ): GuideProgram {
        val running = event.isRunning(clock.nowSec)
        val channelId = event.channelId?.value?.toString().orEmpty()
        return GuideProgram(
            id = event.id.value.toString(),
            channelId = channelId,
            title = event.title.orEmpty(),
            start = texts.clock(event.start.epochSeconds),
            number = texts.number(event),
            length = texts.duration(event.stop.epochSeconds - event.start.epochSeconds),
            year = event.copyrightYear?.toString(),
            genre = texts.genre(event),
            timeRange = texts.timeRange(event.start.epochSeconds, event.stop.epochSeconds),
            episode = texts.episode(event),
            synopsis = event.synopsis(),
            stillModel = marks.picture,
            progress = event.progress(clock.nowSec).takeIf { running },
            timeLeft = texts.duration(event.stop.epochSeconds - clock.nowSec).takeIf { running },
            recording = marks.recording,
            scheduled = marks.scheduled,
            ruleSchedule = marks.rule,
            record = marks.control,
            watching = if (watched != null) event.id == watched.id else running && channelId == playing,
        )
    }


    fun details(id: String, watched: EpgEvent? = null): ProgramDetails? {
        val event = events.find { it.id.value.toString() == id }
            ?: watched?.takeIf { it.id.value.toString() == id } ?: return null
        val program = program(event, watched)
        val episode = event.episode
        return ProgramDetails(
            id = program.id,
            channelId = program.channelId,
            channel = names[event.channelId].orEmpty(),
            channelLogoModel = logos[event.channelId],
            title = program.title,
            episode = program.episode,
            length = program.length,
            year = program.year,
            genre = program.genre,
            ageRating = programmeAgeRating(event.rating?.label, event.rating?.age),
            timeRange = program.timeRange.orEmpty(),
            progress = program.progress,
            timeLeft = program.timeLeft,
            recording = program.recording,
            record = program.record,
            ruleSchedule = program.ruleSchedule,
            pictureModel = program.stillModel,
            watching = program.watching,
            synopsis = program.synopsis,
            otherAirings = otherAirings(event, events, clock.nowSec, AIRINGS_LIMIT).map(::airing),
            credits = credits(event),
            facts = facts(event, program),
        )
    }

    /**
     * The TV's facts in its order (app ProgrammeDetailsPage.kt `programmeAboutFacts`): a season,
     * episode or part number reads "2 of 3" when the server's count covers it, an episode without a
     * number its on-screen text; Broadcast says New or Repeat.
     */
    private fun facts(event: EpgEvent, program: GuideProgram): List<ProgramFact> {
        val episode = event.episode
        fun count(number: Long?, of: Long?) = number?.let { texts.count(it, of?.takeIf { total -> total >= it }) }
        fun list(values: List<String>?) =
            values?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()?.takeIf { it.isNotEmpty() }
                ?.joinToString(" • ")
        return listOf(
            FactKey.Genre to program.genre,
            FactKey.Season to count(episode?.seasonNumber, episode?.seasonCount),
            FactKey.Episode to (count(episode?.episodeNumber, episode?.episodeCount) ?: episode?.onscreen),
            FactKey.Part to count(episode?.partNumber, episode?.partCount),
            FactKey.Year to program.year,
            FactKey.AgeRating to programmeAgeRating(event.rating?.label, event.rating?.age),
            FactKey.Stars to event.rating?.stars?.let { texts.count(it, null) },
            FactKey.FirstAired to event.firstAired?.let { texts.firstAired(it.toEpochMilliseconds()) },
            FactKey.Broadcast to event.isNew?.let(texts.broadcast),
            FactKey.Categories to list(event.categories),
            FactKey.Keywords to list(event.keywords),
        ).mapNotNull { (key, value) -> value?.trim()?.takeIf(String::isNotEmpty)?.let { ProgramFact(key, it) } }
    }

    private fun airing(event: EpgEvent) = ProgramAiring(
        id = event.id.value.toString(),
        logoModel = logos[event.channelId],
        time = texts.airing(event.start.epochSeconds, today),
        line = listOfNotNull(names[event.channelId], texts.number(event), event.subtitle?.takeIf(String::isNotBlank)),
        scheduled = observation.isScheduled(event),
        ruleSchedule = observation.ruleSchedule(event),
    )

    @OptIn(ExperimentalTvheadendApi::class)
    private fun credits(event: EpgEvent): List<ProgramCredit> {
        // The TV's roles and order (app ProgrammeDetailsPage.kt `programmeCreditFacts`): one entry
        // per name and role, since the wire may repeat a pair.
        val roles = listOf(
            CreditRole.Cast to EpgCreditRole.ACTOR,
            CreditRole.Guests to EpgCreditRole.GUEST,
            CreditRole.Presenters to EpgCreditRole.PRESENTER,
            CreditRole.Director to EpgCreditRole.DIRECTOR,
            CreditRole.Writer to EpgCreditRole.WRITER,
        )
        val credits = event.credits.orEmpty()
        return roles.mapNotNull { (role, kind) ->
            credits.filter { it.role == kind }.map { it.name.trim() }.filter(String::isNotEmpty).distinct()
                .takeIf { it.isNotEmpty() }?.let { ProgramCredit(role, it) }
        }
    }
}

private fun GuideProgram.refresh(
    event: EpgEvent,
    marks: GuideOverviewMarks,
    nowSec: Long,
    duration: (Long) -> String,
    playing: String?,
): GuideProgram {
    val running = event.isRunning(nowSec)
    val current = copy(
        progress = event.progress(nowSec).takeIf { running },
        timeLeft = if (running) duration(event.stop.epochSeconds - nowSec) else null,
        stillModel = marks.picture,
        recording = marks.recording,
        scheduled = marks.scheduled,
        ruleSchedule = marks.rule,
        record = marks.control,
        watching = running && channelId == playing,
    )
    return if (this == current) this else current
}

/** The description, else the summary; Tvheadend fills one or both. */
private fun EpgEvent.synopsis(): String? =
    description?.takeIf(String::isNotBlank) ?: summary?.takeIf(String::isNotBlank)

private const val OVERVIEW_SPAN_SEC = 3 * 3600L
private const val AIRINGS_LIMIT = 5
