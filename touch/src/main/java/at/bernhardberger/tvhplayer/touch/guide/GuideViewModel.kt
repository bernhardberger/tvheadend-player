package at.bernhardberger.tvhplayer.touch.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.ui.screens.GuideCoverageRequestOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    init {
        viewModelScope.launch {
            observation.collect { updateCoverageSession(it.currentSession?.generationIdentity) }
        }
    }

    fun selectDay(day: LocalDate?) = mutableSelection.update { it.copy(day = day, jump = null) }

    fun selectJump(jump: Long?) = mutableSelection.update { it.copy(jump = jump) }

    fun reconcileSelection(expected: GuideSelection, available: GuideSelection) {
        mutableSelection.compareAndSet(expected, available)
    }

    // Retain Details across configuration changes, but start closed after process death.
    fun openDetails(programId: String?) = mutableSelection.update { it.copy(detailsId = programId) }

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

/** The shown channels and the selected day's window a coverage request asks for. */
internal data class GuideCoverage(val channelIds: List<ChannelId>, val startSec: Long, val throughSec: Long)

private const val COVERAGE_TIMEOUT_MS = 30_000L
