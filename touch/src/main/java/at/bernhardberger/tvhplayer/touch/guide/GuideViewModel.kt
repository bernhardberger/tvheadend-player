package at.bernhardberger.tvhplayer.touch.guide

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.touch.channels.sameProgrammeAuthority
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.epgChangeCounts
import at.bernhardberger.tvhplayer.touch.ui.traceArtworkMapping
import at.bernhardberger.tvhplayer.ui.screens.GuideCoverageRequestOwner
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/**
 * The Guide's selection and its guide coverage requests. A request covers the shown channels
 * through the end of the selected day; a new day or channel set cancels the overlapping older
 * request, and clearing the model cancels all of them.
 *
 * [acquisitionContext] keeps the SDK call off Main, as the TV's Guide does.
 */
internal class GuideViewModel(
    private val session: TvheadendSession,
    private val acquisitionContext: CoroutineContext = Dispatchers.Default,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : ViewModel() {
    val observation: StateFlow<SessionObservation> = session.observation
    private val mutableSelection = MutableStateFlow(GuideSelection())
    val selection: StateFlow<GuideSelection> = mutableSelection.asStateFlow()
    private val mutablePending = MutableStateFlow(0L)

    /** Changes whenever a request starts or ends; read [isPending] after it. */
    val pendingVersion: StateFlow<Long> = mutablePending.asStateFlow()
    private val requests = GuideCoverageRequestOwner(viewModelScope, COVERAGE_TIMEOUT_MS) {
        mutablePending.update { it + 1 }
    }
    private var coverageSession: SessionGenerationIdentity? = null
    private val mutableMapped = MutableStateFlow<GuideMappingResult?>(null)
    val overviewViewport = MutableStateFlow<List<String>>(emptyList())
    val mapped = mutableMapped.asStateFlow()
    private var mappingInput: GuideMappingInput? = null
    private var mappingJob: Job? = null

    init {
        viewModelScope.launch {
            observation.collect { updateCoverageSession(it.currentSession?.generationIdentity) }
        }
    }

    fun selectDay(day: LocalDate?) = mutableSelection.update { it.copy(day = day, jump = null) }

    fun selectPage(view: GuideView, channelId: String? = null) =
        mutableSelection.update { it.copy(view = view, channelId = channelId ?: it.channelId) }

    fun selectJump(jump: Long?) = mutableSelection.update { it.copy(jump = jump) }

    fun reconcileSelection(expected: GuideSelection, available: GuideSelection) {
        mutableSelection.compareAndSet(expected, available)
    }

    // Retain Details across configuration changes, but start closed after process death.
    fun openDetails(programId: String?) = mutableSelection.update { it.copy(detailsId = programId) }

    fun map(context: Context, input: GuideMappingInput) {
        val changes = input.changesFrom(mappingInput)
        if (changes.isEmpty()) return
        mutableMapped.value?.let {
            if (it.days.isNotEmpty() && !it.input.sameDayOwner(input)) mutableMapped.value = it.copy(days = emptyMap())
        }
        mappingInput = input
        // Like the TV's browse preparation: finish the captured projection, then take
        // the newest EPG update. Metadata churn must not starve the first display.
        if (mappingJob?.isActive == true && changes.all { it in GUIDE_STREAM_CHANGES }) return
        mappingJob?.cancel()
        val app = context.applicationContext
        mappingJob = viewModelScope.launch {
            yield()
            while (true) {
                val captured = checkNotNull(mappingInput)
                val result = prepareGuide(app, captured, mutableMapped.value)
                ensureActive()
                mutableMapped.value = result
                if (mappingInput === captured) break
            }
        }
    }

    private suspend fun prepareGuide(context: Context, input: GuideMappingInput, previous: GuideMappingResult?) =
        withContext(mappingDispatcher) {
            val sincePublished = input.changesFrom(previous?.input)
            val metadataOnly = sincePublished.all { it == "epg" || it == "dvr" }
            val projection = when {
                !metadataOnly -> null
                "epg" in sincePublished -> input.epgProjection()
                else -> previous?.projection
            }
            if (metadataOnly && previous != null && previous.metadataUnchanged(input, sincePublished, projection)) {
                return@withContext previous.copy(input = input, projection = projection)
            }
            val counts = if ("epg" in sincePublished) {
                context.epgChangeCounts(previous?.input?.observation, input.observation)
            } else ""
            val rows = GuideOverviewRows(input.viewport.toSet(), previous?.reusableRows(input))
            val state = traceArtworkMapping(
                context, "Guide", sincePublished.joinToString("+") + counts, rows = { rows.formattedCount },
            ) {
                val localized = context.createConfigurationContext(input.configuration)
                val texts = guideTexts(
                    localized, input.configuration.locales[0], input.clock.zone, input.uses24Hour,
                )
                val state = guideState(
                    input.scope, input.connection, input.degraded, input.observation, input.selection, false,
                    input.clock, texts, input.artwork, input.playingId, rows, ::ensureActive,
                )
                ensureActive()
                state
            }
            GuideMappingResult(input, state, projection, rows, input.rememberDay(state, rows, previous))
        }

    /** Asks the server for the [coverage] window's guide unless that request is already running. */
    fun request(coverage: GuideCoverage) {
        val capability = observation.value.currentSession
        updateCoverageSession(capability?.generationIdentity)
        if (capability == null) return
        val through = Instant.fromEpochSeconds(coverage.throughSec)
        requests.request(coverage.channelIds, coverage.startSec) { ids ->
            withContext(acquisitionContext) { session.epgRepository.acquireCoverageBatch(capability, ids, through) }
        }
    }

    fun isPending(coverage: GuideCoverage): Boolean =
        coverageSession === observation.value.currentSession?.generationIdentity &&
            coverage.channelIds.any { requests.isPending(it, coverage.startSec) }

    private fun updateCoverageSession(session: SessionGenerationIdentity?) {
        if (coverageSession === session) return
        coverageSession = session
        requests.cancelAll()
    }

    override fun onCleared() {
        requests.dispose()
    }
}

internal class GuideMappingInput(
    val scope: ChannelScopeState,
    val connection: ConnectionUiState,
    val degraded: Boolean,
    val observation: SessionObservation,
    val selection: GuideSelection,
    val clock: GuideClock,
    val configuration: Configuration,
    val uses24Hour: Boolean,
    val artwork: ArtworkModels?,
    val playingId: String?,
    val viewport: List<String> = emptyList(),
) {
    val dayChannel get() = scope.scope.visibleChannels.let { channels ->
        val id = selection.channelId?.toLongOrNull()
        (id?.let { channels.firstOrNull { channel -> channel.id.value == id } } ?: channels.firstOrNull())?.id
    }

    fun sameDayOwner(next: GuideMappingInput): Boolean =
        scope === next.scope && dayChannel == next.dayChannel &&
            observation.currentSession?.generationIdentity == next.observation.currentSession?.generationIdentity

    fun changesFrom(previous: GuideMappingInput?): List<String> {
        if (previous == null) return listOf("initial")
        return buildList {
            if (scope !== previous.scope) add("scope")
            if (connection != previous.connection || degraded != previous.degraded) add("connection")
            addObservationChanges(observation, previous.observation)
            if (selection != previous.selection) add("selection")
            if (clock != previous.clock) add("clock")
            if (configuration != previous.configuration || uses24Hour != previous.uses24Hour) add("format")
            if (artwork !== previous.artwork) add("artwork")
            if (playingId != previous.playingId) add("playing")
            if (viewport != previous.viewport) add("viewport")
        }
    }
}

private fun MutableList<String>.addObservationChanges(current: SessionObservation, previous: SessionObservation) {
    // Identity is a cheap trigger for the worker, not a claim that the visible window changed.
    if (current.epgSnapshotForDisplay?.events !== previous.epgSnapshotForDisplay?.events) add("epg")
    if (current.dvrSnapshotForDisplay !== previous.dvrSnapshotForDisplay) add("dvr")
    if (current.channelCatalogForDisplay !== previous.channelCatalogForDisplay) add("catalog")
    if (!current.sameProgrammeAuthority(previous)) add("authority")
}

internal data class GuideMappingResult(
    val input: GuideMappingInput,
    val state: GuideState,
    val projection: GuideEpgProjection? = null,
    val rows: GuideOverviewRows,
    val days: Map<LocalDate, GuideCachedDay> = emptyMap(),
) {
    fun metadataUnchanged(
        next: GuideMappingInput, changes: List<String>, nextProjection: GuideEpgProjection?,
    ): Boolean = ("epg" !in changes || nextProjection == (projection ?: input.epgProjection())) &&
            ("dvr" !in changes || dvrMarksUnchanged(next))

    private fun dvrMarksUnchanged(next: GuideMappingInput): Boolean {
        if (!rows.marksUnchanged(next)) return false
        return state.details?.let { details ->
            val ids = (details.otherAirings.map { it.id } + details.id).toHashSet()
            input.observation.epgSnapshotForDisplay?.events.orEmpty().asSequence()
                .filter { it.id.value.toString() in ids }
                .all { event ->
                    input.observation.overviewMarks(event, input.clock.nowSec, input.artwork) ==
                        next.observation.overviewMarks(event, next.clock.nowSec, next.artwork)
                }
        } ?: true
    }

    fun reusableRows(next: GuideMappingInput): GuideOverviewRows? {
        val date = next.selection.day ?: next.clock.today
        val cached = days[date]?.takeIf { next.selection.view == GuideView.ByChannel && input.sameDayOwner(next) }
        if (cached?.canReuseRows(next, date) == true) return cached.rows
        return rows.takeIf {
            input.scope.profileId == next.scope.profileId &&
                input.clock.zone == next.clock.zone && input.configuration == next.configuration &&
                input.uses24Hour == next.uses24Hour && input.artwork === next.artwork &&
                input.playingId == next.playingId
        }
    }
}

internal data class GuideCachedDay(
    val programs: List<GuideProgram>,
    val rows: GuideOverviewRows,
    private val clock: GuideClock,
    private val playingId: String?,
    private val configuration: Configuration,
    private val uses24Hour: Boolean,
    private val artwork: ArtworkModels?,
) {
    fun canReuseRows(next: GuideMappingInput, date: LocalDate): Boolean =
        clock.zone == next.clock.zone && date >= next.clock.today &&
            playingId == next.playingId && configuration == next.configuration &&
            uses24Hour == next.uses24Hour && artwork === next.artwork
}

private fun GuideMappingInput.rememberDay(
    state: GuideState, rows: GuideOverviewRows, previous: GuideMappingResult?,
): Map<LocalDate, GuideCachedDay> {
    val retained = previous?.takeIf { it.input.sameDayOwner(this) }?.days.orEmpty()
        .filterKeys { it in state.dayDates }
    val date = state.dayDates.getOrNull(state.selectedDay)
    val channelId = dayChannel?.value?.toString()
    val day = state.channels.firstOrNull { it.id == channelId }?.day
    return if (selection.view == GuideView.ByChannel && date != null && day != null) {
        retained + (date to GuideCachedDay(day, rows, clock, playingId, configuration, uses24Hour, artwork))
    } else retained
}

/** The shown channels and the selected day's window a coverage request asks for. */
internal data class GuideCoverage(val channelIds: List<ChannelId>, val startSec: Long, val throughSec: Long)

private const val COVERAGE_TIMEOUT_MS = 30_000L
private val GUIDE_STREAM_CHANGES = setOf("epg", "dvr", "viewport")
