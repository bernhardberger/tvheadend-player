package at.bernhardberger.tvhplayer.ui.screens.settings

import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.rememberConnectionFormState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthKeyCycle
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupFlow
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.ui.screens.onboarding.discoverServers
import at.bernhardberger.tvhplayer.ui.screens.onboarding.rememberConnectionSetupState
import org.koin.compose.koinInject
import java.util.WeakHashMap

internal object ConnectionSecureWindow {
    private data class State(
        var leaseCount: Int,
        val clearOnFinalRelease: Boolean,
    )

    private val states = WeakHashMap<Window, State>()

    fun acquire(window: Window?): CredentialEditLease {
        if (window == null) return CredentialEditLease {}

        synchronized(states) {
            val existing = states[window]
            if (existing == null) {
                val wasSecure = window.hasSecureFlag()
                if (!wasSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                states[window] = State(leaseCount = 1, clearOnFinalRelease = !wasSecure)
            } else {
                existing.leaseCount += 1
                if (!window.hasSecureFlag()) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        return object : CredentialEditLease {
            private var active = true

            override fun release() = synchronized(states) {
                if (!active) return@synchronized
                active = false
                val state = states[window] ?: return@synchronized
                state.leaseCount -= 1
                if (state.leaseCount == 0) {
                    states.remove(window)
                    if (state.clearOnFinalRelease) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    private fun Window.hasSecureFlag(): Boolean =
        attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}

@Composable
internal fun SettingsConnection(
    initialFocusRequester: FocusRequester,
    settingsStore: ConnectionProfileEditor = koinInject<AppProfileOwner>(),
    session: TvheadendSession = koinInject(),
    restoreSaved: suspend () -> Unit = rememberSavedConnectionRestore(session),
    form: ConnectionFormState = rememberConnectionFormState(),
    onSaved: () -> Unit = {},
) {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val lease = ConnectionSecureWindow.acquire(window)
        onDispose(lease::release)
    }
    val context = LocalContext.current.applicationContext
    val state = rememberConnectionSetupState(
        session = session,
        profiles = settingsStore,
        discovery = { discoverServers(context) },
        mode = SetupMode.EDIT,
        restoreSaved = restoreSaved,
        credentialLease = { ConnectionSecureWindow.acquire(window) },
        onSaved = onSaved,
        form = form,
    )
    LaunchedEffect(state) { state.loadSaved() }
    DisposableEffect(state) { onDispose(state.form::clearCredentials) }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backCycle = remember { DepthKeyCycle() }
    // The depth host pops on an editor's unhandled Back key, so the key goes to the back
    // dispatcher here, where the wizard and an edited field take it first. Left remains local
    // to the full-screen wizard rather than popping a Settings level.
    Box(
        Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                when (native.keyCode) {
                    KeyEvent.KEYCODE_BACK -> dispatcher != null &&
                        backCycle.handle(native.keyCode, native.action == KeyEvent.ACTION_DOWN, native.repeatCount) {
                            dispatcher.onBackPressed()
                            true
                        }
                    KeyEvent.KEYCODE_DPAD_LEFT -> true
                    else -> false
                }
            }
            .focusRequester(initialFocusRequester)
            .focusGroup(),
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
