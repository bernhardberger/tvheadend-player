package at.bernhardberger.tvhplayer.ui.screens.settings

import android.view.KeyEvent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.ui.components.backKeyCycleFocus
import at.bernhardberger.tvhplayer.ui.components.rememberBackKeyCycle
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.rememberConnectionFormState
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupFlow
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.ui.screens.onboarding.discoverServers
import at.bernhardberger.tvhplayer.ui.screens.onboarding.rememberConnectionSetupState
import kotlinx.coroutines.sync.Mutex
import org.koin.compose.koinInject

@Composable
internal fun SettingsConnection(
    settingsStore: ConnectionProfileEditor = koinInject<AppProfileOwner>(),
    session: TvheadendSession = koinInject(),
    restoreSaved: suspend () -> Unit = rememberSavedConnectionRestore(session),
    form: ConnectionFormState = rememberConnectionFormState(),
    onSaved: () -> Unit = {},
    mode: SetupMode = SetupMode.EDIT,
    /** The stored server's name while its password is entered again. */
    profileName: String? = null,
    interruptsPlayback: () -> Boolean = { false },
    existingProfile: (host: String, port: Int, username: String) -> Boolean = { _, _, _ -> false },
    onAddName: (() -> Unit)? = null,
    beforeTest: suspend () -> Unit = {},
    sessionLock: Mutex = (settingsStore as? AppProfileOwner)?.connectionChanges ?: remember { Mutex() },
    connectionGeneration: () -> Long = { (settingsStore as? AppProfileOwner)?.configurationGeneration?.value ?: 0L },
    confirmSwitch: (@Composable (form: ConnectionFormState, onSwitch: () -> Unit, onKeep: () -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val state = rememberConnectionSetupState(
        session = session,
        profiles = settingsStore,
        discovery = { discoverServers(context) },
        mode = mode,
        restoreSaved = restoreSaved,
        onSaved = onSaved,
        form = form,
        interruptsPlayback = interruptsPlayback,
        existingProfile = existingProfile,
        onAddName = onAddName,
        beforeTest = { (settingsStore as? AppProfileOwner)?.setSetupTrialConnection(true); beforeTest() },
        sessionLock = sessionLock,
        connectionGeneration = connectionGeneration,
    )
    LaunchedEffect(state) { state.loadSaved() }
    DisposableEffect(state) { onDispose(state.form::clearCredentials) }
    SettingsSetupFrame { ConnectionSetupFlow(state, profileName, confirmSwitch) }
}

/**
 * A full-screen step over Settings. The depth host pops on an editor's unhandled Back key, so the
 * key goes to the back dispatcher here, where the step and an edited field take it first. Left
 * remains local to the step rather than popping a Settings level.
 */
@Composable
internal fun SettingsSetupFrame(content: @Composable () -> Unit) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backCycle = rememberBackKeyCycle(dispatcher != null)
    Box(
        Modifier
            .fillMaxSize()
            .backKeyCycleFocus(backCycle)
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                when (native.keyCode) {
                    KeyEvent.KEYCODE_BACK -> if (dispatcher == null) {
                        backCycle.reset()
                        false
                    } else
                        backCycle.handle(native.action == KeyEvent.ACTION_DOWN, native.repeatCount, native.isCanceled) {
                            dispatcher.onBackPressed()
                        }
                    KeyEvent.KEYCODE_DPAD_LEFT -> true
                    else -> false
                }
            },
    ) {
        content()
    }
}

/**
 * Reconnects the saved connection after the editor's test interrupted it; with no server in use the
 * test's connection is closed instead.
 */
@Composable
internal fun rememberSavedConnectionRestore(
    session: TvheadendSession,
    owner: AppProfileOwner = koinInject(),
): suspend () -> Unit = remember(session, owner) {
    {
        val active = owner.serverProfile.value as? ServerProfileReadResult.Available
        if (active != null) session.connect(active.profile) else session.disconnect()
        owner.setSetupTrialConnection(false)
    }
}
