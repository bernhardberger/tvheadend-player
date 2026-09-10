package at.bernhardberger.tvhplayer.profiling

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import at.bernhardberger.tvhplayer.BuildConfig

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
