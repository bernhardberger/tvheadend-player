package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal const val StripIdentityScale = 7f / 12f
internal const val StripArrival = 0.875f
internal const val StripHandoffWindow = 0.0625f

internal val RailLogoTravel get() = (NowPlayingPlateWidth - ChipWidth) / 2

internal fun railTextTravel(hasPicon: Boolean) = NowPlayingPlateWidth + NowPlayingPlateGap -
    if (hasPicon) ChipWidth + IdentityTextGap else 0.dp

internal fun Modifier.travelToStrip(rail: QuickZapRailState?, dx: Dp, logo: Boolean = false): Modifier {
    if (rail == null) return this
    return layout { measurable, constraints ->
        val rtl = layoutDirection == LayoutDirection.Rtl
        val part = measurable.measure(constraints)
        val pivotY = if (logo) part.height / 2f else part[FirstBaseline].toFloat()
        layout(part.width, part.height) {
            if (!logo) rail.identityBaselineFromCenter = pivotY - part.height / 2f
            part.placeRelativeWithLayer(0, 0) {
                val e = rail.expansion.value
                val travelling = rail.morphIdentity
                railIdentityTransform(rail, dx, pivotY, logo, fromStrip = false, rtl = rtl)
                val fadeStart = StripArrival + StripHandoffWindow
                alpha = if (travelling) ((1f - e) / (1f - fadeStart)).coerceIn(0f, 1f) else 1f - e
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
        }
    }
}

internal fun GraphicsLayerScope.railIdentityTransform(
    rail: QuickZapRailState,
    dx: Dp,
    pivotY: Float,
    logo: Boolean,
    fromStrip: Boolean,
    rtl: Boolean,
) {
    if (!rail.morphIdentity) return
    // Arrive before the crossfade, so two displaced names never compete.
    val e = rail.expansion.value
    val travel = if (e <= 0.75f) e
        else (0.75f + (e - 0.75f) * 0.25f / (StripArrival - 0.75f)).coerceAtMost(1f)
    val distance = if (fromStrip) travel - 1f else travel
    val scale = 1f - (1f - StripIdentityScale) * travel
    transformOrigin = TransformOrigin(if (logo) 0.5f else if (rtl) 1f else 0f, pivotY / size.height.coerceAtLeast(1f))
    translationX = dx.toPx() * if (rtl) -distance else distance
    if (!logo) translationY = (rail.stripBaselineFromCenter - rail.identityBaselineFromCenter) * distance
    scaleX = if (fromStrip) scale / StripIdentityScale else scale
    scaleY = scaleX
}
