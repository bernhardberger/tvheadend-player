package at.bernhardberger.tvhplayer.ui.startup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** The light-only plate's resting glow; breathing lifts it to 30%. */
internal const val StartupBrandBackgroundRestingAlpha = 0.2f

// A half-cosine gives inhale and exhale matching, rounded turns without a hold.
private val BreathingEasing = Easing { fraction ->
    ((1.0 - cos(PI * fraction)) / 2.0).toFloat()
}

/** One off-main decode; the plate fades in to its resting glow, then only its opacity breathes. */
@Composable
internal fun StartupBrandBackground(enabled: Boolean) {
    if (!enabled) return
    val resources = LocalContext.current.applicationContext.resources
    val plate = produceState<ImageBitmap?>(null, resources) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(resources, R.drawable.startup_background_plate,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )?.asImageBitmap()
        }
    }
    val arrivalAlpha = animateFloatAsState(
        targetValue = if (plate.value != null) 1f else 0f,
        animationSpec = tween(300, easing = PlayerMotion.Standard),
        label = "startup-background-arrival",
    )
    val arrived by remember { derivedStateOf { arrivalAlpha.value == 1f } }
    // Breathe between 20–30% every four seconds, starting from the resting glow the
    // fade-in ends on so there is no jump.
    val ambientPulse = if (arrived) {
        rememberInfiniteTransition(label = "startup-ambient").animateFloat(
            initialValue = 1f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                tween(2_000, easing = BreathingEasing), RepeatMode.Reverse,
            ),
            label = "startup-ambient-opacity",
        )
    } else null
    Canvas(Modifier.fillMaxSize().testTag(if (plate.value != null) {
        "startup-background-prepared"
    } else "startup-background-pending")) {
        val image = plate.value ?: return@Canvas
        // Keep the accepted 12% overscan framing, centered.
        val scale = maxOf(size.width / image.width, size.height / image.height) * 1.12f
        val width = (image.width * scale).roundToInt()
        val height = (image.height * scale).roundToInt()
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(((size.width - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()),
            dstSize = IntSize(width, height),
            alpha = StartupBrandBackgroundRestingAlpha * arrivalAlpha.value * (ambientPulse?.value ?: 1f),
        )
    }
}
