package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import kotlinx.coroutines.flow.StateFlow

/**
 * The session logos, programme pictures and their colours are loaded with: null before Ready, when
 * only the startup profile's saved copies show. The holder stays the same while the observation
 * changes; reading [currentSession] recomposes only when the session itself changes, never for a
 * guide or recording update.
 */
@Stable
class ArtworkContext(private val session: State<CurrentSessionObservation?>) {
    val currentSession: CurrentSessionObservation? get() = session.value
}

/** The app's artwork session. The default, for a composition outside the app, has none. */
val LocalArtworkContext = staticCompositionLocalOf { ArtworkContext(mutableStateOf(null)) }

/** Provides the current session of the latest [observation] as the artwork session of [content]. */
@Composable
fun ProvideAppArtwork(observation: StateFlow<SessionObservation>, content: @Composable () -> Unit) {
    val latest = observation.collectAsStateWithLifecycle()
    val context = remember(latest) { ArtworkContext(derivedStateOf { latest.value.currentSession }) }
    CompositionLocalProvider(LocalArtworkContext provides context, content = content)
}

/**
 * Keeps [currentSession] as the artwork session of [content], for a surface built from data older
 * than the latest observation: it shows its pictures with the session its data came from.
 */
@Composable
fun ProvideArtworkSession(currentSession: CurrentSessionObservation?, content: @Composable () -> Unit) {
    val context = remember(currentSession) { ArtworkContext(mutableStateOf(currentSession)) }
    CompositionLocalProvider(LocalArtworkContext provides context, content = content)
}
