package at.bernhardberger.tvhplayer.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Tab body motion belongs to the shared TabContent, for both pages and sections. The player's
 * player page uses a linear clock with per-element motion windows.
 */
internal object BrowseMotionPolicy {
    const val slideFraction = 1f / 3f
    fun <T> stepAnimation() = tween<T>(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    const val pageDownMs = 500
    val pageDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val pageAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val pageEmphasized = CubicBezierEasing(0.3f, 0f, 0f, 1f)
    val pageStandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    fun <T> pageProgress(open: Boolean) = tween<T>(if (open) pageDownMs else 400, easing = LinearEasing)

    fun <T> animation(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun tabTransform(direction: Int?): ContentTransform {
        if (direction == null) return EnterTransition.None togetherWith ExitTransition.None
        return (slideInHorizontally(stepAnimation()) { (it * slideFraction * direction).toInt() } +
            fadeIn(stepAnimation())) togetherWith
            (slideOutHorizontally(stepAnimation()) { (-it * slideFraction * direction).toInt() } +
                fadeOut(stepAnimation()))
    }
}
