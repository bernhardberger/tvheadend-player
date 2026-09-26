package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

@Composable
internal fun ChannelNumberOverlay(number: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = number,
        contentKey = { it.isNotEmpty() },
        // The box fades; the digits themselves change without motion.
        transitionSpec = {
            (fadeIn(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardDecelerate)) togetherWith
                fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate))).using(null)
        },
        modifier = modifier,
        label = "channel number",
    ) { displayedNumber ->
        // The outgoing content owns its digits until its background has faded out.
        if (displayedNumber.isNotEmpty()) {
            Surface(
                colors = SurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.78f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                // Tabular digits over an invisible three-digit template keep the box
                // from resizing while digits are typed.
                val style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum")
                Box(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = CHANNEL_NUMBER_WIDTH_TEMPLATE,
                        fontSize = 56.sp,
                        style = style,
                        modifier = Modifier.alpha(0f).clearAndSetSemantics { },
                    )
                    Text(
                        text = displayedNumber,
                        fontSize = 56.sp,
                        style = style,
                    )
                }
            }
        }
    }
}

private const val CHANNEL_NUMBER_WIDTH_TEMPLATE = "000"
