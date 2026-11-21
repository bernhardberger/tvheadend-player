package at.bernhardberger.tvhplayer.touch.guide

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
import at.bernhardberger.tvhplayer.core.runningRecordingForEvent
import at.bernhardberger.tvhplayer.core.shouldPresentEmptyTag
import at.bernhardberger.tvhplayer.touch.channels.ChannelTag
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.screens.isScheduled
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What the viewer picked in the Guide: the day (0 is today), the overview's jump time, opened Details. */
internal data class GuideSelection(val day: Int = 0, val jump: Int = 0, val detailsId: String? = null)

/** The time the Guide is drawn at. */
internal data class GuideClock(val nowSec: Long, val zone: ZoneId)

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
)

/**
 * The Guide destination from the shared channel scope and the session's guide: the overview from
 * the day's jump time, By channel's day, the day tabs and the opened program's Details.
 *
 * Programs carry no pictures and channels no logos (the loaders are not shared yet).
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
): GuideState {
    val browsing = scope.scope
    val current = scope.channelCatalogCurrent
    val tags = browsing.tags.map { ChannelTag(it.id.value.toString(), it.name.orEmpty()) }
    val selectedTagId = browsing.activeTagId?.value?.toString()
    val today = Instant.ofEpochSecond(clock.nowSec).atZone(clock.zone).toLocalDate()
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
        else -> GuideMapper(browsing.visibleChannels, observation, snapshot.events, clock, today, texts)
            .state(selection, tags, selectedTagId)
            .copy(
                dayLoading = dayLoading,
                availability = if (degraded) GuideAvailability.Offline else GuideAvailability.Current,
            )
    }
}

/** The jump times after Now, as drawn (section 7); which times to offer is an open design question. */
internal val GUIDE_JUMP_TIMES: List<LocalTime> = listOf(LocalTime.of(20, 15), LocalTime.of(22, 0))

/** The day tabs: today, then every later day the visible channels have programs on. */
internal fun guideDays(events: List<EpgEvent>, clock: GuideClock): List<LocalDate> {
    val today = Instant.ofEpochSecond(clock.nowSec).atZone(clock.zone).toLocalDate()
    val later = events.asSequence()
        .filter { it.stop.epochSeconds > clock.nowSec }
        .map { Instant.ofEpochSecond(it.start.epochSeconds).atZone(clock.zone).toLocalDate() }
        .filter { it.isAfter(today) }
    return (sequenceOf(today) + later).distinct().sorted().toList()
}

/** The guide the selected day needs: the visible channels through the end of that day. */
internal fun guideCoverage(
    scope: ChannelScopeState,
    observation: SessionObservation,
    selection: GuideSelection,
    clock: GuideClock,
): GuideCoverage? {
    val ids = scope.scope.visibleChannels.map { it.id }.takeIf { it.isNotEmpty() } ?: return null
    val shown = ids.toSet()
    val events = observation.epgSnapshotForDisplay?.events.orEmpty().filter { it.channelId in shown }
    val days = guideDays(events, clock)
    val window = guideWindow(days[selection.day.coerceIn(days.indices)], null, clock)
    return GuideCoverage(ids, window.daySec.first, window.daySec.last + 1)
}

/** The selected day's window and the overview's anchor in it. */
internal data class GuideWindow(val daySec: LongRange, val anchorSec: Long)

/**
 * Today from now, another day from its start; a jump time moves the anchor to that time of the
 * selected day. The jumps already past today are not offered.
 */
internal fun guideWindow(date: LocalDate, jump: LocalTime?, clock: GuideClock): GuideWindow {
    val start = date.atStartOfDay(clock.zone).toEpochSecond()
    val end = date.plusDays(1).atStartOfDay(clock.zone).toEpochSecond()
    val anchor = jump?.let { date.atTime(it).atZone(clock.zone).toEpochSecond() } ?: maxOf(start, clock.nowSec)
    return GuideWindow(start until end, anchor)
}

private class GuideMapper(
    private val channels: List<Channel>,
    private val observation: SessionObservation,
    private val events: List<EpgEvent>,
    private val clock: GuideClock,
    private val today: LocalDate,
    private val texts: GuideTexts,
) {
    private val byChannel: Map<ChannelId?, List<EpgEvent>> = events.asSequence()
        .filter { it.stop > it.start && it.stop.epochSeconds > clock.nowSec }
        .distinctBy { it.id }
        .sortedBy { it.start }
        .groupBy { it.channelId }
    private val names = channels.associate { it.id to it.name.orEmpty() }

    fun state(selection: GuideSelection, tags: List<ChannelTag>, selectedTagId: String?): GuideState {
        val days = guideDays(channels.flatMap { byChannel[it.id].orEmpty() }, clock)
        val dayIndex = selection.day.coerceIn(days.indices)
        val date = days[dayIndex]
        val jumps = GUIDE_JUMP_TIMES.filter { date.atTime(it).atZone(clock.zone).toEpochSecond() > clock.nowSec }
        val jumpIndex = selection.jump.coerceIn(0, jumps.size)
        val window = guideWindow(date, jumps.getOrNull(jumpIndex - 1), clock)
        val guideChannels = channels.map { channel(it, window) }
        val details = selection.detailsId?.let { id -> events.find { it.id.value.toString() == id } }?.let(::details)
        return GuideState(
            channels = guideChannels,
            tags = tags,
            selectedTagId = selectedTagId,
            days = days.map { texts.day(it, today) },
            selectedDay = dayIndex,
            date = texts.date(date),
            jumps = jumps.map { texts.clock(date.atTime(it).atZone(clock.zone).toEpochSecond()) },
            selectedJump = jumpIndex,
            details = details?.let { mapOf(it.id to it) }.orEmpty(),
        )
    }

    private fun channel(channel: Channel, window: GuideWindow): GuideChannel {
        val schedule = byChannel[channel.id].orEmpty()
        val overviewEnd = maxOf(window.daySec.last + 1, window.anchorSec + OVERVIEW_SPAN_SEC)
        val overview = mutableListOf<EpgEvent>()
        var gap: GuideGap? = null
        val shown = schedule.filter { it.stop.epochSeconds > window.anchorSec && it.start.epochSeconds < overviewEnd }
        for (event in shown) {
            val previous = overview.lastOrNull()
            if (previous != null && event.start > previous.stop) {
                gap = GuideGap(texts.clock(previous.stop.epochSeconds), texts.clock(event.start.epochSeconds))
                break
            }
            overview += event
        }
        val day = schedule.filter {
            it.stop.epochSeconds > window.daySec.first && it.start.epochSeconds <= window.daySec.last
        }
        return GuideChannel(
            id = channel.id.value.toString(),
            name = channel.name.orEmpty(),
            programs = overview.map(::program),
            gap = gap,
            day = day.map(::program),
        )
    }

    private fun program(event: EpgEvent): GuideProgram {
        val running = event.isRunning(clock.nowSec)
        return GuideProgram(
            id = event.id.value.toString(),
            channelId = event.channelId?.value?.toString().orEmpty(),
            title = event.title.orEmpty(),
            start = texts.clock(event.start.epochSeconds),
            number = texts.number(event),
            length = texts.duration(event.stop.epochSeconds - event.start.epochSeconds),
            year = event.copyrightYear?.toString(),
            genre = texts.genre(event),
            timeRange = texts.timeRange(event.start.epochSeconds, event.stop.epochSeconds),
            episode = texts.episode(event),
            synopsis = event.synopsis(),
            progress = event.progress(clock.nowSec).takeIf { running },
            timeLeft = texts.duration(event.stop.epochSeconds - clock.nowSec).takeIf { running },
            recording = observation.runningRecordingForEvent(event) != null,
            scheduled = observation.isScheduled(event),
        )
    }

    private fun details(event: EpgEvent): ProgramDetails {
        val program = program(event)
        val episode = event.episode
        return ProgramDetails(
            id = program.id,
            channelId = program.channelId,
            channel = names[event.channelId].orEmpty(),
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
            synopsis = program.synopsis,
            otherAirings = otherAirings(event, events, clock.nowSec, AIRINGS_LIMIT).map(::airing),
            credits = credits(event),
            facts = listOfNotNull(
                program.genre?.let { ProgramFact(FactKey.Genre, it) },
                episode?.seasonNumber?.let { ProgramFact(FactKey.Season, it.toString()) },
                episode?.episodeNumber?.let { ProgramFact(FactKey.Episode, it.toString()) },
                program.year?.let { ProgramFact(FactKey.Year, it) },
                programmeAgeRating(event.rating?.label, event.rating?.age)?.let { ProgramFact(FactKey.AgeRating, it) },
                event.firstAired?.let { ProgramFact(FactKey.FirstAired, texts.firstAired(it.toEpochMilliseconds())) },
                event.categories?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() }
                    ?.let { ProgramFact(FactKey.Categories, it.joinToString(" • ")) },
            ),
        )
    }

    private fun airing(event: EpgEvent) = ProgramAiring(
        id = event.id.value.toString(),
        time = texts.airing(event.start.epochSeconds, today),
        line = listOfNotNull(names[event.channelId], texts.number(event), event.subtitle?.takeIf(String::isNotBlank)),
        scheduled = observation.isScheduled(event),
    )

    @OptIn(ExperimentalTvheadendApi::class)
    private fun credits(event: EpgEvent): List<ProgramCredit> {
        // Guests and presenters have no section of their own in the design.
        val roles = listOf(
            CreditRole.Cast to EpgCreditRole.ACTOR,
            CreditRole.Director to EpgCreditRole.DIRECTOR,
            CreditRole.Writer to EpgCreditRole.WRITER,
        )
        val credits = event.credits.orEmpty()
        return roles.mapNotNull { (role, kind) ->
            credits.filter { it.role == kind }.map { it.name }.filter(String::isNotBlank)
                .takeIf { it.isNotEmpty() }?.let { ProgramCredit(role, it) }
        }
    }
}

/** The description, else the summary; Tvheadend fills one or both. */
private fun EpgEvent.synopsis(): String? =
    description?.takeIf(String::isNotBlank) ?: summary?.takeIf(String::isNotBlank)

private const val OVERVIEW_SPAN_SEC = 3 * 3600L
private const val AIRINGS_LIMIT = 5
