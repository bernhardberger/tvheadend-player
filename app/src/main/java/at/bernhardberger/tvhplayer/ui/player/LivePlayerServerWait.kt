package at.bernhardberger.tvhplayer.ui.player

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import android.view.KeyEvent as AndroidKeyEvent
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R as AppR
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.WaitingConnectionFailure
import at.bernhardberger.tvhplayer.core.CHANNEL_START_CONNECTION_GRACE_MILLIS
import at.bernhardberger.tvhplayer.core.channelStartConnectionFailure
import at.bernhardberger.tvhplayer.core.toAppError
import at.bernhardberger.tvhplayer.core.toConnectionUiState
import at.bernhardberger.tvhplayer.core.waitingConnectionFailurePresentation
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryAction
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.ui.startup.ChannelStartupSurface
import kotlinx.coroutines.delay

/**
 * A live entry or foreground resume waits here while it has no session. The runtime owns
 * the start's lifetime; a connection lost after its first picture uses normal player recovery.
 * [connection] is AppRoot's profile-aware
 * state, so a missing or unavailable profile shows its recovery instead of connecting forever.
 */
@Stable
internal class LivePlayerServerWaitState(
    private val hasSession: State<Boolean>,
    private val observation: State<SessionObservation>,
    private val connection: State<ConnectionUiState>,
    private val close: PlayerClose,
    private val onReconnect: State<() -> Unit>,
    private val onOpenConnectionSettings: State<() -> Unit>,
    private val channelStartAtMillis: Long?,
    private val nowMillis: State<Long>,
    private val playerRecovery: State<Boolean>,
    private val sessionSeen: State<Boolean>,
) {
    /** The observation the wait shows its channel logo for: its profile names the saved copy. */
    val artworkObservation: SessionObservation get() = observation.value
    private var failures by mutableStateOf(WaitingConnectionFailure())
    val waitingForFirstSession: Boolean get() = !sessionSeen.value

    /** Passive while connecting, Actionable once it failed (its buttons are [failure]'s); null when not waiting. */
    val presentation: MainStartupPresentation?
        get() = when {
            playerRecovery.value -> null
            channelStartAtMillis != null && hasSession.value ->
                MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
            hasSession.value || channelStartAtMillis == null && sessionSeen.value -> null
            else -> failureState?.let(::waitingConnectionFailurePresentation)
                ?: MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
        }

    /**
     * The catalog error of the failure [presentation] shows, with the buttons the SDK's guidance
     * offers: no Retry while the connection retries by itself. Null while connecting or not waiting.
     */
    val failure: AppError?
        get() = if (hasSession.value || channelStartAtMillis == null && sessionSeen.value) null
            else failureState?.toAppError()

    private val failureState: ConnectionUiState?
        get() {
            val failure = failures.shown(connection.value)
            return if (channelStartAtMillis == null) failure
            else channelStartConnectionFailure(failure, channelStartAtMillis, nowMillis.value)
        }

    /** Applied after each composition. */
    fun observed() {
        failures = if (hasSession.value) WaitingConnectionFailure() else failures.observe(connection.value)
    }

    fun retry() {
        failures = failures.retry()
        onReconnect.value()
    }

    /** Connection settings leaves through [close], so nothing of this screen starts meanwhile. */
    fun openConnectionSettings() {
        close.close()
        onOpenConnectionSettings.value()
    }

    /**
     * Every non-system key but Back is consumed, down, repeats and up. A down opens a key
     * cycle, so the up of a key still held when the session arrives cannot activate the chrome.
     */
    fun consumesKey(event: KeyEvent, layerState: LivePlayerLayerState): Boolean {
        if (presentation !is MainStartupPresentation.Passive) return false
        val native = event.nativeKeyEvent
        if (native.keyCode == AndroidKeyEvent.KEYCODE_BACK ||
            at.bernhardberger.tvhplayer.ui.startup.startupSystemKey(native.keyCode)
        ) return false
        trackOpeningKeyCycle(native, layerState)
        return true
    }

    private fun trackOpeningKeyCycle(native: AndroidKeyEvent, layerState: LivePlayerLayerState) {
        when (native.action) {
            AndroidKeyEvent.ACTION_DOWN -> if (native.repeatCount == 0) layerState.core.beginOpeningKeyCycle(native.keyCode)
            AndroidKeyEvent.ACTION_UP -> layerState.core.endOpeningKeyCycle(native.keyCode)
        }
    }

}

@Composable
internal fun rememberLivePlayerServerWait(
    observation: SessionObservation,
    connectionUiState: ConnectionUiState?,
    close: PlayerClose,
    onReconnect: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    channelStartAtMillis: Long?,
    playerRecovery: Boolean = false,
): LivePlayerServerWaitState {
    val hasSession = rememberUpdatedState(observation.currentSession != null)
    // Ordinary entries wait only for their first session, never again for a later loss.
    val sessionSeen = remember(close) { mutableStateOf(observation.currentSession != null) }
    val latest = rememberUpdatedState(observation)
    val connection = rememberUpdatedState(connectionUiState ?: observation.sessionState.toConnectionUiState())
    val reconnect = rememberUpdatedState(onReconnect)
    val connectionSettings = rememberUpdatedState(onOpenConnectionSettings)
    val now = remember(channelStartAtMillis) { mutableStateOf(SystemClock.elapsedRealtime()) }
    val recovery = rememberUpdatedState(playerRecovery)
    LaunchedEffect(channelStartAtMillis) {
        if (channelStartAtMillis != null) {
            delay((channelStartAtMillis + CHANNEL_START_CONNECTION_GRACE_MILLIS - now.value).coerceAtLeast(0L))
            now.value = channelStartAtMillis + CHANNEL_START_CONNECTION_GRACE_MILLIS
        }
    }
    val state = remember(close, channelStartAtMillis) {
        LivePlayerServerWaitState(
            hasSession, latest, connection, close, reconnect, connectionSettings,
            channelStartAtMillis, now, recovery, sessionSeen,
        )
    }
    SideEffect {
        if (hasSession.value) sessionSeen.value = true
        state.observed()
    }
    return state
}

/**
 * The wait of [state]: the channel, the connecting text and the player's busy ring, with no
 * action, then the player's recovery overlay with the catalog's actions once connecting failed.
 */
@Composable
internal fun LivePlayerServerWait(
    state: LivePlayerServerWaitState,
    channel: Channel?,
    channelName: String,
    rootFocus: FocusRequester,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    announceStatus: Boolean = true,
    freshStart: Boolean = false,
) {
    val presentation = state.presentation
    val connecting = presentation is MainStartupPresentation.Passive
    // Nothing here takes focus; the screen keeps it, so a key reaches no action behind the wait.
    LaunchedEffect(connecting) { if (connecting) runCatching { rootFocus.requestFocus() } }
    AnimatedVisibility(
        visible = connecting,
        enter = fadeIn(tween(if (motionEnabled) PlayerMotion.MediumMs else 0, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(
            if (motionEnabled) PlayerMotion.MediumMs else 0, easing = PlayerMotion.StandardAccelerate,
        )),
    ) {
        ProvideArtworkSession(state.artworkObservation.currentSession, state.artworkObservation) {
            ChannelStartupSurface(
                channel = channel,
                channelName = channelName,
                status = stringResource(if ((presentation as? MainStartupPresentation.Passive)?.messageKind ==
                    MainStartupMessageKind.STARTING_TELEVISION) R.string.main_startup_message_starting
                    else R.string.main_startup_message_connecting),
                modifier = Modifier.testTag(LivePlayerServerWaitTag),
                motionEnabled = motionEnabled,
                announceStatus = announceStatus,
                freshStart = freshStart,
                tuningChannel = channel?.takeIf { freshStart &&
                    (presentation as? MainStartupPresentation.Passive)?.messageKind ==
                    MainStartupMessageKind.STARTING_TELEVISION },
            )
        }
    }
    val current = state.failure?.takeIf { presentation is MainStartupPresentation.Actionable }
    // The exit keeps the failure it showed, so its actions do not turn into a spinner while it fades.
    val failure = rememberLastShown(current)
    val context = LocalContext.current
    val text = remember(context) { AppErrorText(context) }
    TvRecoveryOverlay(
        visible = current != null,
        message = failure?.let { text.title(it.condition) }.orEmpty(),
        detail = failure?.let { text.paragraph(it.condition) },
        icon = painterResource(AppR.drawable.ic_error_outlined),
        actions = failure?.actions.orEmpty().mapNotNull { action ->
            when (action) {
                AppErrorAction.RETRY -> state::retry
                AppErrorAction.CONNECTION_SETTINGS -> state::openConnectionSettings
                // The wait's errors are not closable; Back closes the screen.
                AppErrorAction.CLOSE -> null
            }?.let { TvRecoveryAction(text.label(action), it) }
        },
    )
}

internal const val LivePlayerServerWaitTag = "player-server-wait"
