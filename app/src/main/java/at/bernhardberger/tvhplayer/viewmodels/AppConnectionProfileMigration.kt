package at.bernhardberger.tvhplayer.viewmodels

import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.core.StreamProfileSelectionOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

internal suspend fun collectReadyStreamProfileMigrations(
    states: Flow<SessionState>,
    currentState: () -> SessionState,
    discover: suspend () -> StreamProfilesResult,
    migrate: suspend (List<StreamProfileSelectionOption>) -> Unit,
) {
    states.collectLatest { ready ->
        if (ready !is SessionState.Ready) return@collectLatest
        val profiles = discover()
        if (currentState() !== ready || profiles !is StreamProfilesResult.Available) {
            return@collectLatest
        }
        migrate(
            profiles.profiles.map { profile ->
                StreamProfileSelectionOption(profile.id.value, profile.name)
            },
        )
    }
}
