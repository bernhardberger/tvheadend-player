package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner

/**
 * Reconnects the saved connection after the editor's test interrupted it; with no server in use the
 * test's connection is closed instead.
 */
@Composable
fun rememberSavedConnectionRestore(
    session: TvheadendSession,
    owner: AppProfileOwner,
): suspend () -> Unit = remember(session, owner) {
    {
        val active = owner.serverProfile.value as? ServerProfileReadResult.Available
        if (active != null) session.connect(active.profile) else session.disconnect()
        owner.setSetupTrialConnection(false)
    }
}
