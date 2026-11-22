package at.bernhardberger.tvhplayer.touch.guide

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
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
    val session: TvheadendSession,
    savedState: SavedStateHandle,
    val acquisitionContext: CoroutineContext = Dispatchers.Default,
    val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : ViewModel() {
    val observation: StateFlow<SessionObservation> = session.observation
    private val mutableSelection = MutableStateFlow(
        savedState.get<Bundle>(SELECTION_STATE).let { saved ->
            GuideSelection(
                day = saved?.getString("day")?.let(LocalDate::parse),
            )
        },
    )
    val selection: StateFlow<GuideSelection> = mutableSelection.asStateFlow()
    val pageStack = ProgramPageStack()
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
        // Save the latest browse position, not the transient jump or open Details.
        savedState.setSavedStateProvider(SELECTION_STATE) {
            val current = selection.value
            bundleOf("day" to current.day?.toString())
        }
        viewModelScope.launch {
            observation.collect { updateCoverageSession(it.currentSession?.generationIdentity) }
        }
    }

    fun selectDay(day: LocalDate?) = mutableSelection.update { it.copy(day = day, jump = null) }

    fun selectJump(jump: Long?) = mutableSelection.update { it.copy(jump = jump) }

    fun reconcileSelection(expected: GuideSelection, available: GuideSelection) {
        mutableSelection.compareAndSet(expected, available)
    }

    /** Null or a scope that hasn't caught up with the observation stops work, retaining the last result. */
    fun map(context: Context, input: GuideMappingInput?) {
        val current = input?.takeIf { it.hasMatchingGeneration }
        val changes = current?.changesFrom(mappingInput).orEmpty()
        if (current == null || changes.isEmpty()) {
            if (current == null) {
                mappingJob?.cancel()
                mappingJob = null
                mappingInput = mutableMapped.value?.input
            }
            return
        }
        mappingInput = current
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
                if (captured.sameBrowseOwner(checkNotNull(mappingInput)) &&
                    captured.generation === observation.value.currentSession?.generationIdentity
                ) {
                    mutableMapped.value = result
                }
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
                return@withContext previous.copy(
                    input = input, projection = projection,
                )
            }
            val counts = if ("epg" in sincePublished) {
                context.epgChangeCounts(previous?.input?.observation, input.observation)
            } else ""
            val rows = GuideOverviewRows(input.viewport.toSet(), previous?.reusableOverviewRows(input))
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
            GuideMappingResult(input, state, projection, rows)
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

    private companion object {
        const val SELECTION_STATE = "guideSelection"
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
    val generation get() = observation.currentSession?.generationIdentity
    val hasMatchingGeneration get() = scope.observation?.currentSession?.generationIdentity === generation

    fun sameBrowseOwner(next: GuideMappingInput): Boolean =
        hasMatchingGeneration && next.hasMatchingGeneration && generation === next.generation &&
            scope.profileId == next.scope.profileId && scope.scope.activeTagId == next.scope.scope.activeTagId

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
) {
    fun metadataUnchanged(
        next: GuideMappingInput, changes: List<String>, nextProjection: GuideEpgProjection?,
    ): Boolean = ("epg" !in changes || nextProjection == (projection ?: input.epgProjection())) &&
            ("dvr" !in changes || rows.marksUnchanged(next))

    fun reusableOverviewRows(next: GuideMappingInput): GuideOverviewRows? =
        rows.takeIf {
            input.sameBrowseOwner(next) && input.clock.today == next.clock.today &&
                state.dayDates.getOrNull(state.selectedDay) == (next.selection.day ?: next.clock.today) &&
                sameRowFormat(next)
        }

    private fun sameRowFormat(next: GuideMappingInput): Boolean =
        input.clock.zone == next.clock.zone && input.configuration == next.configuration &&
            input.uses24Hour == next.uses24Hour && input.artwork === next.artwork

}

/** The shown channels and the selected day's window a coverage request asks for. */
internal data class GuideCoverage(val channelIds: List<ChannelId>, val startSec: Long, val throughSec: Long)

internal const val COVERAGE_TIMEOUT_MS = 30_000L
private val GUIDE_STREAM_CHANGES = setOf("epg", "dvr", "viewport")
