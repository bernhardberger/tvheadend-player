package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        transitionSpec = { (fadeIn() togetherWith fadeOut()).using(null) },
        modifier = modifier,
        label = "channel number",
    ) { displayedNumber ->
        // The outgoing content owns its digits until its background has faded out.
        if (displayedNumber.isNotEmpty()) {
            Surface(
                colors = SurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.78f),
                    contentColor = Color.White,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = displayedNumber,
                    fontSize = 56.sp,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
        }
    }
}
