package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.android.TvheadendArtwork
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.AppArtworkSource

/**
 * The session logos, programme pictures and their colours are loaded with: null before Ready, when
 * only saved logos show, those of the profile of the [observation] the surface draws from. The
 * holder stays the same while the observation changes; reading [currentSession] recomposes only
 * when the session itself changes, never for a guide or recording update.
 */
@Stable
class ArtworkContext(
    internal val tvheadend: TvheadendSession?,
    private val session: State<CurrentSessionObservation?>,
    private val observation: State<SessionObservation?>,
) {
    val currentSession: CurrentSessionObservation? get() = session.value

    /**
     * The model of logo [id]: with the current session, else the saved copy of the observation's
     * profile. A saved copy is the SDK's model, equal for every observation of the same profile, so
     * a retained update leaves the logo alone while another profile's observation replaces it.
     */
    @OptIn(ExperimentalTvheadendApi::class)
    internal fun logo(id: ArtworkId): AppArtworkSource {
        val current = session.value
        val saved = if (current != null) null else tvheadend?.let { tvheadend ->
            observation.value?.let { TvheadendArtwork.cached(tvheadend, it, id) }
        }
        return AppArtworkSource(current, id, saved)
    }
}

/** The app's artwork session. The default, for a composition outside the app, has none. */
val LocalArtworkContext = staticCompositionLocalOf { ArtworkContext(null, mutableStateOf(null), mutableStateOf(null)) }

/** Provides the current session of the latest observation of [session] as the artwork session of [content]. */
@Composable
fun ProvideAppArtwork(session: TvheadendSession, content: @Composable () -> Unit) {
    val latest = session.observation.collectAsStateWithLifecycle()
    val context = remember(session, latest) { ArtworkContext(session, derivedStateOf { latest.value.currentSession }, latest) }
    CompositionLocalProvider(LocalArtworkContext provides context, content = content)
}

/**
 * Keeps [currentSession] as the artwork session of [content], for a surface built from data older
 * than the latest observation: it shows its pictures with the session its data came from. Without
 * one, saved logos are those of the profile of [observation], the one the surface's data came
 * from; without that, none.
 */
@Composable
fun ProvideArtworkSession(
    currentSession: CurrentSessionObservation?,
    observation: SessionObservation? = null,
    content: @Composable () -> Unit,
) {
    val tvheadend = LocalArtworkContext.current.tvheadend
    val named = rememberUpdatedState(observation)
    val context = remember(tvheadend, currentSession) { ArtworkContext(tvheadend, mutableStateOf(currentSession), named) }
    CompositionLocalProvider(LocalArtworkContext provides context, content = content)
}
