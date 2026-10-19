package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import android.view.KeyEvent as AndroidKeyEvent
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.WaitingConnectionFailure
import at.bernhardberger.tvhplayer.core.toAppError
import at.bernhardberger.tvhplayer.core.toConnectionUiState
import at.bernhardberger.tvhplayer.core.waitingConnectionFailurePresentation
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryAction
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.ui.components.channelTitleText

/**
 * A live screen opened before its session was playable waits here for the server; its entry
 * start then tunes the channel. Only until the screen first sees a current session: after that
 * the player's own recovery owns a lost connection. [connection] is AppRoot's profile-aware
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
) {
    /** The observation the wait shows its channel logo for: its profile names the saved copy. */
    val artworkObservation: SessionObservation get() = observation.value
    private var sessionSeen by mutableStateOf(false)
    private var failures by mutableStateOf(WaitingConnectionFailure())

    /** Passive while connecting, Actionable once it failed (its buttons are [failure]'s); null when not waiting. */
    val presentation: MainStartupPresentation?
        get() = if (sessionSeen || hasSession.value) null else {
            failures.shown(connection.value)?.let(::waitingConnectionFailurePresentation)
                ?: MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
        }

    /**
     * The catalog error of the failure [presentation] shows, with the buttons the SDK's guidance
     * offers: no Retry while the connection retries by itself. Null while connecting or not waiting.
     */
    val failure: AppError?
        get() = if (sessionSeen || hasSession.value) null else failures.shown(connection.value)?.toAppError()

    /** Applied after each composition. */
    fun observed() {
        if (hasSession.value) sessionSeen = true else failures = failures.observe(connection.value)
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
     * While connecting, every key but Back is consumed, down, repeats and up. A down opens a key
     * cycle, so the up of a key still held when the session arrives cannot activate the chrome.
     */
    fun consumesKey(event: KeyEvent, layerState: LivePlayerLayerState): Boolean {
        if (presentation !is MainStartupPresentation.Passive) return false
        val native = event.nativeKeyEvent
        if (native.keyCode == AndroidKeyEvent.KEYCODE_BACK) return false
        when (native.action) {
            AndroidKeyEvent.ACTION_DOWN -> if (native.repeatCount == 0) layerState.beginOpeningKeyCycle(native.keyCode)
            AndroidKeyEvent.ACTION_UP -> layerState.endOpeningKeyCycle(native.keyCode)
        }
        return true
    }
}

@Composable
internal fun rememberLivePlayerServerWait(
    observation: SessionObservation,
    connectionUiState: ConnectionUiState?,
    close: PlayerClose,
    onReconnect: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
): LivePlayerServerWaitState {
    val hasSession = rememberUpdatedState(observation.currentSession != null)
    val latest = rememberUpdatedState(observation)
    val connection = rememberUpdatedState(connectionUiState ?: observation.sessionState.toConnectionUiState())
    val reconnect = rememberUpdatedState(onReconnect)
    val connectionSettings = rememberUpdatedState(onOpenConnectionSettings)
    val state = remember(close) { LivePlayerServerWaitState(hasSession, latest, connection, close, reconnect, connectionSettings) }
    SideEffect { state.observed() }
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
) {
    val presentation = state.presentation
    val channelNumber = channel?.visibleChannelNumber
    val picon = channel?.icon
    val connecting = presentation is MainStartupPresentation.Passive
    // Nothing here takes focus; the screen keeps it, so a key reaches no action behind the wait.
    LaunchedEffect(connecting) { if (connecting) runCatching { rootFocus.requestFocus() } }
    AnimatedVisibility(
        visible = connecting,
        enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
    ) {
        val title = channelTitleText(channelNumber, channelName)
        val connectingText = stringResource(R.string.connection_connecting)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(48.dp)
                .testTag(LivePlayerServerWaitTag)
                .clearAndSetSemantics {
                    contentDescription = "$title. $connectingText"
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            WaitPicon(state, picon)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp).widthIn(max = 680.dp),
            )
            PlayerBusyRing(Modifier.padding(top = 24.dp))
            Text(
                text = connectingText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp).widthIn(max = 680.dp),
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
        icon = painterResource(R.drawable.ic_error_outlined),
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

/** Only the saved logo of the observation's profile, as before a session; its own scope reads each emission. */
@Composable
private fun WaitPicon(state: LivePlayerServerWaitState, picon: ArtworkId?) {
    ProvideArtworkSession(null, state.artworkObservation) { PiconBox(picon, Modifier.size(136.dp, 60.dp)) }
}
