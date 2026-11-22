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
        mappingInput = input
        // Like the TV's browse preparation: finish the captured projection, then take
        // the newest EPG update. Metadata churn must not starve the first display.
        if (mappingJob?.isActive == true && changes == listOf("epg")) return
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
            val epgOnly = sincePublished.singleOrNull() == "epg"
            val projection = if (epgOnly) input.epgProjection() else null
            if (epgOnly && previous != null &&
                projection == (previous.projection ?: previous.input.epgProjection())
            ) {
                return@withContext GuideMappingResult(input, previous.state, projection)
            }
            val counts = if ("epg" in sincePublished) {
                context.epgChangeCounts(previous?.input?.observation, input.observation)
            } else ""
            val state = traceArtworkMapping(context, "Guide", sincePublished.joinToString("+") + counts) {
                val localized = context.createConfigurationContext(input.configuration)
                val texts = guideTexts(
                    localized, input.configuration.locales[0], input.clock.zone, input.uses24Hour,
                )
                val state = guideState(
                    input.scope, input.connection, input.degraded, input.observation, input.selection, false,
                    input.clock, texts, input.artwork, input.playingId, ::ensureActive,
                )
                ensureActive()
                state
            }
            GuideMappingResult(input, state, projection)
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
) {
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

internal class GuideMappingResult(
    val input: GuideMappingInput,
    val state: GuideState,
    val projection: GuideEpgProjection? = null,
)

/** The shown channels and the selected day's window a coverage request asks for. */
internal data class GuideCoverage(val channelIds: List<ChannelId>, val startSec: Long, val throughSec: Long)

private const val COVERAGE_TIMEOUT_MS = 30_000L
