package at.bernhardberger.tvhplayer.touch.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Material Components for Android's mtrl_transition_shared_axis_slide_distance.
private val PageShift = 30.dp

@Composable
internal fun pageMove(forward: Boolean): ContentTransform {
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val shift = with(LocalDensity.current) { PageShift.roundToPx() }
    val offset = if (forward == ltr) shift else -shift
    val motion = MaterialTheme.motionScheme
    val enter = slideInHorizontally(motion.defaultSpatialSpec()) { offset } + fadeIn(motion.defaultEffectsSpec())
    val exit = slideOutHorizontally(motion.defaultSpatialSpec()) { -offset } + fadeOut(motion.fastEffectsSpec())
    return enter togetherWith exit
}
