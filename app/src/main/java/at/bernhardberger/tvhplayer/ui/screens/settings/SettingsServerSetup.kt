package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import org.koin.compose.koinInject

@Composable
internal fun AddServer(
    owner: AppProfileOwner,
    interruption: () -> PlaybackInterruption,
    stopPlaying: suspend () -> Unit,
    showExisting: (ServerProfileId) -> Unit,
    onSaved: () -> Unit,
    existingProfile: (String, Int, String) -> Boolean,
    onAddName: () -> Unit,
    session: TvheadendSession = koinInject(),
) {
    val restore = rememberSavedConnectionRestore(session, owner)
    val editor = remember(restore) {
        ProfileSetupEditor(owner, target = null, activate = true, restoreActive = restore,
            onAlreadyExists = showExisting)
    }
    SettingsConnection(
        settingsStore = editor,
        session = session,
        restoreSaved = restore,
        mode = SetupMode.ADD,
        onSaved = onSaved,
        interruptsPlayback = { interruption() != PlaybackInterruption.NONE },
        existingProfile = existingProfile,
        onAddName = onAddName,
        beforeTest = stopPlaying,
        sessionLock = owner.connectionChanges,
        connectionGeneration = { owner.configurationGeneration.value },
        confirmSwitch = { form, onSwitch, onKeep ->
            val host = form.host.trim()
            val username = form.username.trim().ifBlank { null }
            SwitchServerDialog(
                name = username?.let { stringResource(R.string.server_user_on_host, it, host) } ?: host,
                username = username,
                interruption = interruption(),
                onSwitch = onSwitch,
                onKeep = onKeep,
            )
        },
    )
}

@Composable
internal fun EditServer(
    target: ServerProfileSummary,
    mode: SetupMode,
    owner: AppProfileOwner,
    activeId: ServerProfileId?,
    interruption: () -> PlaybackInterruption,
    stopPlaying: suspend () -> Unit,
    showExisting: (ServerProfileId) -> Unit,
    onSaved: () -> Unit,
    existingProfile: (String, Int, String) -> Boolean,
    session: TvheadendSession = koinInject(),
) {
    val restore = rememberSavedConnectionRestore(session, owner)
    val active = target.id == activeId
    val editor = remember(target.id, mode, restore) {
        ProfileSetupEditor(owner, target, activate = mode == SetupMode.PASSWORD, restoreActive = restore,
            onAlreadyExists = showExisting)
    }
    val name = serverProfileTitle(target)
    SettingsConnection(
        settingsStore = editor,
        session = session,
        restoreSaved = restore,
        mode = mode,
        profileName = name,
        onSaved = onSaved,
        interruptsPlayback = {
            mode == SetupMode.PASSWORD && !active && interruption() != PlaybackInterruption.NONE
        },
        // Testing a server that isn't in use stops playback without asking (owner decision).
        // Checked before the test, so a duplicate stops nothing; the save would refuse it anyway.
        existingProfile = existingProfile,
        beforeTest = { if (!active) stopPlaying() },
        sessionLock = owner.connectionChanges,
        connectionGeneration = { owner.configurationGeneration.value },
        confirmSwitch = { form, onSwitch, onKeep ->
            SwitchServerDialog(name, form.username.trim().ifBlank { null }, interruption(), onSwitch, onKeep)
        },
    )
}
