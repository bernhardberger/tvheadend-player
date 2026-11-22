package at.bernhardberger.tvhplayer.touch.guide

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.channelContextActions
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.watchWays
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.screens.GuideCoverageRequestOwner
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

internal data class ChannelPageState(
    val channel: GuideChannel? = null,
    val onNow: GuideProgram? = null,
    val days: Map<LocalDate, String> = emptyMap(),
    val selectedDay: LocalDate? = null,
    val retained: Map<LocalDate, List<GuideProgram>> = emptyMap(),
    val loading: Boolean = true,
    val watchWays: List<BrowseContextAction> = emptyList(),
    val watchRecordingId: String? = null,
)

internal data class ChannelPageInput(
    val scope: ChannelScopeState,
    val observation: SessionObservation,
    val clock: GuideClock,
    val configuration: Configuration,
    val uses24Hour: Boolean,
    val artwork: ArtworkModels?,
    val playingId: String?,
    val behindLive: Boolean = false,
) {
    val generation get() = observation.currentSession?.generationIdentity
    val matchingGeneration get() = scope.observation?.currentSession?.generationIdentity === generation

    fun sameDays(other: ChannelPageInput): Boolean =
        matchingGeneration && other.matchingGeneration && generation === other.generation &&
            scope.profileId == other.scope.profileId && scope.scope.activeTagId == other.scope.scope.activeTagId &&
            clock.today == other.clock.today && clock.zone == other.clock.zone &&
            configuration == other.configuration && uses24Hour == other.uses24Hour &&
            artwork === other.artwork && playingId == other.playingId
}

/** One host's channel, day selection and visited rows. A covered page does no preparation. */
internal class ChannelPageModel(
    private val session: TvheadendSession,
    private val acquisitionContext: CoroutineContext,
    private val mappingDispatcher: CoroutineDispatcher,
    private val channelId: ChannelId,
    initialDay: LocalDate?,
) : ViewModel() {
    private var day = initialDay
    private var input: ChannelPageInput? = null
    private var context: Context? = null
    private var preparedFor: ChannelPageInput? = null
    private var coverageKey: Triple<SessionGenerationIdentity?, LocalDate, LocalDate>? = null
    private var mapping: Job? = null
    private var work: DayWork? = null
    private val mutableState = MutableStateFlow<Pair<ChannelPageInput, ChannelPageState>?>(null)
    val state = mutableState.asStateFlow()
    private val requests = GuideCoverageRequestOwner(viewModelScope, COVERAGE_TIMEOUT_MS) {
        // Let request() finish registering the new window before reconsidering the selection.
        viewModelScope.launch { yield(); updateMapping() }
    }

    fun visibleState(current: ChannelPageInput): ChannelPageState =
        mutableState.value?.takeIf { (prepared, _) -> prepared.sameDays(current) }?.second ?: ChannelPageState()

    fun selectDay(date: LocalDate) {
        if (date == day) return
        day = date
        val shown = mutableState.value
        if (shown != null && date in shown.second.days) {
            mutableState.value = shown.first to shown.second.copy(selectedDay = date, loading = false)
        }
        updateMapping()
    }

    fun submit(context: Context, next: ChannelPageInput) {
        this.context = context.applicationContext
        input = next.takeIf { it.matchingGeneration && it.scope.settingsLoaded }
        if (input == null) pause() else updateMapping()
    }

    fun pause() {
        input = null
        mapping?.cancel()
        mapping = null
        coverageKey = null
        requests.cancelAll()
    }

    private fun updateMapping() {
        val current = input ?: return
        if (day?.isBefore(current.clock.today) == true) day = null
        val date = day ?: current.clock.today
        request(current, date)
        val window = guideWindow(date, null, current.clock)
        val next = DayWork(current, date, requests.isPending(channelId, window.daySec.first))
        if (!needsMapping(next)) return
        mapping?.cancel()
        work = next
        mapping = viewModelScope.launch { mapDays(next) }
    }

    private fun needsMapping(next: DayWork): Boolean {
        val old = work
        if (mapping == null || old == null || !old.sameSelection(next)) return true
        return mapping?.isActive != true && next.input != preparedFor
    }

    private suspend fun mapDays(work: DayWork) {
        var current = work.input
        do {
            val keep = visibleState(current).retained
            val result = withContext(mappingDispatcher) {
                val local = checkNotNull(context).createConfigurationContext(current.configuration)
                prepareChannelPage(current, channelId, work.date, work.pending, local, keep, ::ensureActive)
            }
            val latest = input
            val published = session.observation.value
            if (latest == null || !current.sameDays(latest) ||
                current.generation !== published.currentSession?.generationIdentity
            ) return
            preparedFor = current
            mutableState.value = current to result
            if (latest == current) {
                // Coverage may settle in the same turn in which the SDK publishes the new EPG.
                val superseded = published != current.observation
                if (!superseded && !result.loading && result.selectedDay != work.date) day = null
                return
            }
            current = latest
        } while (true)
    }

    private fun request(current: ChannelPageInput, date: LocalDate) {
        val capability = current.observation.currentSession ?: return
        val key = Triple(current.generation, current.clock.today, date)
        if (coverageKey == key || current.channel(channelId) == null) return
        if (coverageKey?.first !== current.generation) requests.cancelAll()
        coverageKey = key
        val window = guideWindow(date, null, current.clock)
        val through = Instant.fromEpochSeconds(window.daySec.last + 1)
        requests.request(listOf(channelId), window.daySec.first) { ids ->
            withContext(acquisitionContext) { session.epgRepository.acquireCoverageBatch(capability, ids, through) }
        }
    }

    override fun onCleared() {
        requests.dispose()
    }

    private data class DayWork(val input: ChannelPageInput, val date: LocalDate, val pending: Boolean) {
        fun sameSelection(other: DayWork) =
            date == other.date && pending == other.pending && input.clock == other.input.clock &&
                input.sameDays(other.input)
    }
}

private fun ChannelPageInput.channel(id: ChannelId): Channel? = scope.scope.allChannels.firstOrNull { it.id == id }

@Suppress("LongParameterList") // The worker's schedule snapshot, selection and previously shown days.
private fun prepareChannelPage(
    input: ChannelPageInput,
    id: ChannelId,
    requested: LocalDate,
    pending: Boolean,
    context: Context,
    retained: Map<LocalDate, List<GuideProgram>>,
    checkpoint: () -> Unit,
): ChannelPageState {
    val channel = input.channel(id) ?: return ChannelPageState(loading = !input.scope.channelCatalogCurrent)
    val head = GuideChannel(
        id.value.toString(), channel.name.orEmpty(), logoModel = input.artwork?.logo(channel.icon, input.observation),
    )
    val clock = input.clock
    val snapshot = input.observation.epgSnapshotForDisplay
    val events = snapshot?.events.orEmpty().asSequence().filter { it.channelId == id }
        .filter { it.stop > it.start && it.stop.epochSeconds > clock.nowSec }
        .distinctBy { it.id }.sortedBy { it.start }.toList()
    val dates = guideDays(events, clock).takeIf { events.isNotEmpty() }.orEmpty()
    val waiting = pending && requested !in dates || snapshot == null
    val selected = requested.takeIf { waiting || it in dates } ?: clock.today
    val texts = guideTexts(context, input.configuration.locales[0], clock.zone, input.uses24Hour)
    val mapper = GuideMapper(
        listOf(channel), input.observation, emptyList(), clock, clock.today, texts, input.artwork, input.playingId,
    )
    val now = events.firstOrNull { it.isRunning(clock.nowSec) }
    val shownDates = if (waiting && requested in retained) (dates + requested).distinct().sorted() else dates
    val tabs = channelDayLabels(shownDates, clock.today, context)
    val kept = retained.filterKeys { it in tabs }
    val rows = if (waiting) kept else kept + (selected to mapDayRows(events, clock, selected, mapper, checkpoint))
    val state = ChannelPageState(
        head, now?.let(mapper::program), tabs, selected, rows, loading = waiting && selected !in kept,
    )
    return state.withWatch(input, now, head.id)
}

@OptIn(ExperimentalTvheadendApi::class)
private fun ChannelPageState.withWatch(input: ChannelPageInput, event: EpgEvent?, channelId: String): ChannelPageState {
    val observation = input.observation
    val recording = event?.let(observation::dvrEntryForEvent)
    val canModify = observation.currentSession != null && observation.dvrState is DvrRepositoryState.Current &&
        (observation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    return copy(
        watchWays = watchWays(channelContextActions(
            event, input.clock.nowSec, recording, recording?.let(observation::recordingPlaybackAdmission), canModify,
            playing = input.playingId == channelId, behindLive = input.behindLive,
        )),
        watchRecordingId = recording?.id?.value?.toString(),
    )
}

internal fun channelDayLabels(dates: List<LocalDate>, today: LocalDate, context: Context): Map<LocalDate, String> {
    val resources = context.resources
    val locale = resources.configuration.locales[0]
    val weekdayDay = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEd"), locale)
    return dates.associateWith { date ->
        when (date) {
            today -> resources.getString(SharedR.string.channel_schedule_today)
            today.plusDays(1) -> resources.getString(SharedR.string.channel_schedule_tomorrow)
            else -> weekdayDay.format(date)
        }
    }
}

private fun mapDayRows(
    events: List<EpgEvent>,
    clock: GuideClock,
    selected: LocalDate,
    mapper: GuideMapper,
    checkpoint: () -> Unit,
): List<GuideProgram> {
    val window = guideWindow(selected, null, clock).daySec
    return events.asSequence().filter {
        it.stop.epochSeconds > window.first && it.start.epochSeconds <= window.last &&
            (selected != clock.today || it.start.epochSeconds > clock.nowSec)
    }.map { checkpoint(); mapper.program(it) }.toList()
}
