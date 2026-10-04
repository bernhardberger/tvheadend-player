package at.bernhardberger.tvhplayer.notices

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Startup/profile failures belong to recovery surfaces, not transient feedback. */
class ConnectionNoticeSource(
    private val observations: StateFlow<SessionObservation>,
    private val profileGeneration: StateFlow<Long>,
    private val notices: NoticeCenter,
) {
    suspend fun run(): Unit = coroutineScope {
        var profile = profileGeneration.value
        var ready: SessionGenerationIdentity? = null
        var retired: SessionGenerationIdentity? = null
        var lost = false
        var loginRejected = false
        var lossJob: Job? = null
        combine(profileGeneration, observations) { generation, observation -> generation to observation }
            .collect { (generation, observation) ->
                notices.prune()
                // combine can already have queued a pair when either live input advances.
                if (generation != profileGeneration.value || observation !== observations.value) return@collect
                val context = NoticeContext(generation, observation.currentSession?.generationIdentity)
                if (generation != profile) {
                    retired = ready
                    profile = generation
                    ready = null
                    lost = false
                    loginRejected = false
                    lossJob?.cancel()
                    lossJob = null
                }
                val state = observation.sessionState
                val identity = observation.currentSession?.generationIdentity
                when {
                    state is SessionState.Ready && identity != null && identity !== retired -> {
                        lossJob?.cancel()
                        lossJob = null
                        if (lost && identity !== ready) {
                            notices.post(Notice.Connection(ConnectionNoticeKind.RESTORED), context)
                        }
                        ready = identity
                        lost = false
                        loginRejected = false
                    }
                    ready == null -> Unit
                    state is SessionState.Unavailable && state.reason == SessionFailure.AuthenticationRejected -> {
                        lossJob?.cancel()
                        lossJob = null
                        if (!loginRejected) {
                            loginRejected = notices.post(Notice.Connection(ConnectionNoticeKind.LOGIN_REJECTED), context)
                        }
                    }
                    state == SessionState.Connecting || (state is SessionState.Unavailable &&
                        state.reason.recoveryDisposition == SessionRecoveryDisposition.AUTOMATIC_BACKOFF) -> {
                        if (!lost && lossJob == null) {
                            val expectedProfile = profile
                            lossJob = launch {
                                delay(3_000)
                                lossJob = null
                                val current = observations.value
                                val currentState = current.sessionState
                                val reconnecting = currentState == SessionState.Connecting ||
                                    currentState == SessionState.Synchronizing ||
                                    (currentState is SessionState.Unavailable &&
                                        currentState.reason.recoveryDisposition == SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
                                val currentContext = NoticeContext(expectedProfile, current.currentSession?.generationIdentity)
                                if (profileGeneration.value == expectedProfile && reconnecting) {
                                    lost = notices.post(Notice.Connection(ConnectionNoticeKind.LOST), currentContext)
                                }
                            }
                        }
                    }
                    state == SessionState.Synchronizing -> Unit // Keep a reconnect's debounce running through sync.
                    else -> {
                        lossJob?.cancel()
                        lossJob = null
                    }
                }
            }
    }
}
