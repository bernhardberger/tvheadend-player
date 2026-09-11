package at.bernhardberger.tvhplayer.profiling

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.drawWithContent
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

/** Root draw attribution only: child readiness and display presentation require separate evidence. */
internal fun Modifier.profileRouteDraw(route: String, drawerActive: Boolean): Modifier {
    if (!BuildConfig.PROFILE_TRACE) return this
    return drawWithContent {
        drawContent()
        profileTrace("P48:draw:$route:drawer:$drawerActive") { }
    }
}

/** Raw and ancestor-clipped geometry plus actual child draw; never writes layout state. */
internal inline fun Modifier.profileViewportItem(region: () -> String): Modifier {
    if (!BuildConfig.PROFILE_TRACE) return this
    val name = region()
    return onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInWindow()
        val clipped = coordinates.boundsInWindow()
        profileTrace(
            "P48:bounds:$name:${origin.x.toInt()},${origin.y.toInt()}," +
                "${coordinates.size.width},${coordinates.size.height}:" +
                "${clipped.left.toInt()},${clipped.top.toInt()}," +
                "${clipped.right.toInt()},${clipped.bottom.toInt()}",
        ) { }
    }.drawWithContent {
        drawContent()
        profileTrace("P48:itemDraw:$name") { }
    }
}
