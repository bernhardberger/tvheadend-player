package at.bernhardberger.tvhplayer.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Page motion belongs to the two browse hosts, never to individual screen bodies. */
internal object BrowseMotionPolicy {
    const val slideFraction = 1f / 3f

    fun <T> animation(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun tabTransform(direction: Int?): ContentTransform {
        if (direction == null) return EnterTransition.None togetherWith ExitTransition.None
        return (slideInHorizontally(animation()) { (it * slideFraction * direction).toInt() } +
            fadeIn(animation())) togetherWith
            (slideOutHorizontally(animation()) { (-it * slideFraction * direction).toInt() } +
                fadeOut(animation()))
    }
}
