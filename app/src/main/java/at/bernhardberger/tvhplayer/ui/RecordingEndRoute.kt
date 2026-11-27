package at.bernhardberger.tvhplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.RecordingEndOffer
import at.bernhardberger.tvhplayer.core.RecordingEndStart
import at.bernhardberger.tvhplayer.core.recordingEndWatchLive
import at.bernhardberger.tvhplayer.core.toPlaybackStart
import at.bernhardberger.tvhplayer.core.withCurrentAdmission
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.FinishedRecording
import at.bernhardberger.tvhplayer.playback.RecordingEndOutcome
import at.bernhardberger.tvhplayer.playback.RecordingPlayNextPress
import at.bernhardberger.tvhplayer.ui.player.RecordingEndScreen
import at.bernhardberger.tvhplayer.ui.player.recordingEndActionLabel
import at.bernhardberger.tvhplayer.ui.screens.ChannelContextMenu
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.home.HomeRecordingActions
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class RecordingEndSession(
    val route: RecordingPlayerKey,
    val session: CurrentSessionObservation,
    val ended: DvrEntry,
    val offers: List<RecordingEndOffer>,
    val finished: FinishedRecording,
)

@OptIn(ExperimentalTvheadendApi::class)
@Composable
internal fun RecordingEndRoute(
    end: RecordingEndSession,
    endState: MutableState<RecordingEndSession?>,
    startState: MutableState<Job?>,
    backStack: MutableList<AppNavKey>,
    observation: SessionObservation,
    session: TvheadendSession,
    runtime: AppPlaybackRuntime,
    scope: CoroutineScope,
    autoContinue: Boolean,
    shown: Boolean,
    suppressUntilUp: (Int) -> Unit,
    close: (RecordingEndSession) -> Unit,
    showGuide: (ChannelId, EpgEvent?) -> Unit,
) {
    var recordingEnd by endState
    var endStart by startState
    fun currentRecordings(observed: SessionObservation) = end.offers
        .filterIsInstance<RecordingEndOffer.Recording>().mapNotNull { offer ->
            observed.dvrEntry(offer.entry.id)?.let {
                offer.withCurrentAdmission(it, observed.recordingPlaybackAdmission(it))
            }
        }
    fun currentLive(observed: SessionObservation, now: Instant) =
        observed.dvrEntry(end.ended.id)?.let { ended ->
            recordingEndWatchLive(ended, end.finished.reachedAt, currentRecordings(observed),
                observed::event, ended.channelId?.let { observed.eventAt(it, now) }, now)
        }?.takeIf { observed.channel(it.channelId) != null }
    var now by remember(end.finished) { mutableStateOf(runtime.recordingOffersNow()) }
    LaunchedEffect(end.finished) {
        while (true) {
            delay(1_000L)
            now = runtime.recordingOffersNow()
        }
    }
    val recordings = currentRecordings(observation)
    val live = currentLive(observation, now)
    val offers = if (observation.currentSession !== end.session) emptyList()
        else recordings + listOfNotNull(live?.takeIf {
            end.offers.any { card -> card is RecordingEndOffer.WatchLive }
        })
    LaunchedEffect(end, offers) {
        if (offers.isNotEmpty() && offers != end.offers && recordingEnd === end) {
            recordingEnd = end.copy(offers = offers)
        }
    }
    val origin = recordingEndOriginTitle(backStack.getOrNull(backStack.lastIndex - 1))
    fun playOffer(offer: RecordingEndOffer, alternateStart: Boolean = false) {
        val opening = recordingEnd?.takeIf { it.route == end.route } ?: return
        if (endStart != null) return
        fun stillEligible(): Boolean {
            val current = session.observation.value
            if (current.currentSession !== end.session) return false
            return when (offer) {
                is RecordingEndOffer.WatchLive ->
                    currentLive(current, runtime.recordingOffersNow())?.channelId == offer.channelId
                is RecordingEndOffer.Recording -> {
                    val card = currentRecordings(current).firstOrNull { it.entry.id == offer.entry.id }
                    card != null && (alternateStart || card.start == offer.start)
                }
            }
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // The handoff holds the runtime's intent lock; do not call back into the runtime here.
                val outcome = runtime.playRecordingEndOffer(opening.finished, offer, ::stillEligible) presented@{
                    if (recordingEnd?.finished !== opening.finished || backStack.lastOrNull() != end.route) {
                        return@presented false
                    }
                    recordingEnd = null
                    backStack.popNavigation()
                    backStack.pushTransient(when (offer) {
                        is RecordingEndOffer.Recording -> recordingPlayerKey(offer.entry.id, offer.start.toPlaybackStart())
                        is RecordingEndOffer.WatchLive -> LivePlayerKey(offer.channelId.value,
                            session.observation.value.channel(offer.channelId)?.name.orEmpty())
                    })
                    true
                }
                val currentEnd = recordingEnd?.takeIf { it.finished === opening.finished } ?: return@launch
                if (backStack.lastOrNull() != end.route) return@launch
                when (outcome) {
                    RecordingEndOutcome.PRESENTED -> Unit
                    RecordingEndOutcome.STALE -> if (offer is RecordingEndOffer.WatchLive) {
                        recordingEnd = currentEnd.copy(offers = currentEnd.offers.filterNot { it is RecordingEndOffer.WatchLive })
                    }
                    RecordingEndOutcome.FAILED -> recordingEnd = currentEnd.copy(
                        offers = currentEnd.offers.filterNot {
                            when (offer) {
                                is RecordingEndOffer.Recording ->
                                    (it as? RecordingEndOffer.Recording)?.entry?.id == offer.entry.id
                                is RecordingEndOffer.WatchLive ->
                                    (it as? RecordingEndOffer.WatchLive)?.channelId == offer.channelId
                            }
                        },
                    )
                    RecordingEndOutcome.SUPERSEDED -> close(currentEnd)
                }
            } finally {
                if (endStart === coroutineContext[Job]) endStart = null
            }
        }
        endStart = job
        job.start()
    }
    val mutationInFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
    val nowSec = { now.epochSeconds }
    RecordingEndScreen(end.ended, offers, observation, stringResource(origin),
        autoContinue = autoContinue && !end.finished.playNextDismissed,
        shown = shown, starting = endStart != null,
        suppressUntilUp = suppressUntilUp,
        onClose = { close(end) },
        onPlay = { playOffer(it) },
        menu = { offer, dismiss ->
            when (offer) {
                is RecordingEndOffer.Recording -> HomeRecordingActions(
                    offer.entry, observation, contextMenu = true, nowSec = nowSec(),
                    onPlay = { _, start ->
                        dismiss()
                        playOffer(offer.copy(start = when (start) {
                            RecordingPlaybackStart.RESUME -> RecordingEndStart.Resume(
                                (offer.start as? RecordingEndStart.Resume)?.position ?: Duration.ZERO)
                            RecordingPlaybackStart.START_OVER -> RecordingEndStart.FromBeginning
                        }), alternateStart = true)
                    },
                    onWatchChannel = { id, _ -> dismiss(); playOffer(RecordingEndOffer.WatchLive(id, null)) },
                    onShowGuide = showGuide,
                    onReturnToRecording = { _, _ -> false },
                    onClose = dismiss, endStart = offer.start,
                    endHasContinuationTime = offer.displayContinuationTime != null,
                    endWatchSupport = recordingEndActionLabel(offer),
                    endWatchLiveEligible = live?.channelId == offer.entry.channelId && live != null,
                    onPlayEndDefault = { playOffer(offer) },
                )
                is RecordingEndOffer.WatchLive -> ChannelContextMenu(offer.channelId, observation,
                    nowSecProvider = nowSec, mutationScope = scope,
                    mutationInFlight = mutationInFlight,
                    onPlay = { _, _ -> dismiss(); playOffer(offer) },
                    onShowGuide = showGuide, onClose = dismiss)
            }
        },
    )
}

internal fun launchRecordingPlayNext(
    destination: RecordingPlayerKey,
    press: RecordingPlayNextPress,
    onAccepted: () -> Unit,
    startState: MutableState<Pair<RecordingPlayerKey, Job>?>,
    backStack: MutableList<AppNavKey>,
    runtime: AppPlaybackRuntime,
    scope: CoroutineScope,
    shouldStopOriginInBrowse: () -> Boolean,
) {
    var playNextStart by startState
    if (backStack.lastOrNull() !== destination || playNextStart != null) return
    val job = scope.launch(start = CoroutineStart.LAZY) {
        try {
            runtime.playRecordingNext(press, shouldStopOriginInBrowse, onAccepted) swap@{ offer ->
                val index = backStack.indexOfLast { it === destination }
                if (index < 0) return@swap false
                backStack[index] = recordingPlayerKey(offer.entry.id, offer.start.toPlaybackStart())
                true
            }
        } finally {
            if (playNextStart?.first === destination) playNextStart = null
        }
    }
    playNextStart = destination to job
    job.start()
}
