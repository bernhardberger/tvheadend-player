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
import org.koin.compose.koinInject

@Composable
internal fun SettingsConnection(
    settingsStore: ConnectionProfileEditor = koinInject<AppProfileOwner>(),
    session: TvheadendSession = koinInject(),
    restoreSaved: suspend () -> Unit = rememberSavedConnectionRestore(session),
    form: ConnectionFormState = rememberConnectionFormState(),
    onSaved: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val state = rememberConnectionSetupState(
        session = session,
        profiles = settingsStore,
        discovery = { discoverServers(context) },
        mode = SetupMode.EDIT,
        restoreSaved = restoreSaved,
        onSaved = onSaved,
        form = form,
    )
    LaunchedEffect(state) { state.loadSaved() }
    DisposableEffect(state) { onDispose(state.form::clearCredentials) }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backCycle = rememberBackKeyCycle(dispatcher != null)
    // The depth host pops on an editor's unhandled Back key, so the key goes to the back
    // dispatcher here, where the wizard and an edited field take it first. Left remains local
    // to the full-screen wizard rather than popping a Settings level.
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
        ConnectionSetupFlow(state)
    }
}

/** Reconnects the saved connection after the editor's test interrupted it. */
@Composable
private fun rememberSavedConnectionRestore(
    session: TvheadendSession,
    owner: AppProfileOwner = koinInject(),
): suspend () -> Unit = remember(session, owner) {
    {
        (owner.serverProfile.value as? ServerProfileReadResult.Available)?.let { session.connect(it.profile) }
    }
}
