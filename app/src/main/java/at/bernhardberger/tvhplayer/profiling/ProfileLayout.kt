package at.bernhardberger.tvhplayer.profiling

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.layout
import at.bernhardberger.tvhplayer.BuildConfig

/** Committed composition lifetime, distinct from remeasurement of an existing tree. */
@Composable
internal fun ProfileCompositionLifetime(region: String) {
    if (!BuildConfig.PROFILE_TRACE) return
    DisposableEffect(region) {
        profileTrace("P46:enter:$region") { }
        onDispose { profileTrace("P46:dispose:$region") { } }
    }
}

/** Diagnostic subtree ownership; absent entirely from ordinary build modifiers. */
internal fun Modifier.profileLayout(region: String): Modifier {
    if (!BuildConfig.PROFILE_TRACE) return this
    return layout { measurable, constraints ->
        val child = profileTrace("P44:measure:$region") { measurable.measure(constraints) }
        layout(child.width, child.height) {
            profileTrace("P44:place:$region") { child.placeRelative(0, 0) }
        }
    }
}
