package at.bernhardberger.tvhplayer.viewmodels

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.core.StreamProfileSelectionOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

internal suspend fun collectReadyStreamProfileMigrations(
    observations: Flow<SessionObservation>,
    currentObservation: () -> SessionObservation,
    discover: suspend (CurrentSessionObservation) -> StreamProfilesResult,
    migrate: suspend (List<StreamProfileSelectionOption>) -> Unit,
) {
    observations.collectLatest { observation ->
        val currentSession = observation.currentSession ?: return@collectLatest
        val profiles = discover(currentSession)
        if (
            currentObservation().currentSession !== currentSession ||
            profiles !is StreamProfilesResult.Available
        ) {
            return@collectLatest
        }
        migrate(
            profiles.profiles.map { profile ->
                StreamProfileSelectionOption(profile.id.value, profile.name)
            },
        )
    }
}
