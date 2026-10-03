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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import at.bernhardberger.tvhplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

internal data class StartupBrandBackgroundFrame(val drift: Float, val alpha: Float)

// A half-cosine gives inhale and exhale matching, rounded turns without a hold.
private val BreathingEasing = Easing { fraction ->
    ((1.0 - cos(PI * fraction)) / 2.0).toFloat()
}

/** One light-only original plate, with a quiet opening and a gentle diagonal drift. */
internal fun startupBrandBackgroundFrame(millis: Float, enabled: Boolean = true): StartupBrandBackgroundFrame? {
    if (!enabled || !millis.isFinite() || millis < 0f) return null
    fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    return StartupBrandBackgroundFrame(
        drift = ((millis - 100f) / (StartupBrandDurationMillis - 100f)).coerceIn(0f, 1f),
        alpha = if (millis <= 950f) 0.5f * smooth((millis - 120f) / 830f)
            else 0.5f - 0.30f * smooth((millis - 950f) / (StartupBrandDurationMillis - 950f)),
    )
}

/** One off-main decode; settled waiting animates only the plate's opacity. */
@Composable
internal fun StartupBrandBackground(millis: () -> Float, enabled: Boolean) {
    if (!enabled) return
    val resources = LocalContext.current.applicationContext.resources
    val animateArrival = remember { millis() < StartupBrandDurationMillis }
    val latestMillis = rememberUpdatedState(millis)
    val plate = produceState<ImageBitmap?>(null, resources) {
        if (startupBrandBackgroundFrame(latestMillis.value()) == null) return@produceState
        val image = withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(resources, R.drawable.startup_background_plate,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )?.asImageBitmap()
        }
        value = image
    }
    // Only an animated entry fades a late decode. A static entry shows its held
    // glow immediately when prepared, without a second fade over the window transition.
    val arrivalAlpha = animateFloatAsState(
        targetValue = if (plate.value != null) 1f else 0f,
        animationSpec = tween(200),
        label = "startup-background-arrival",
    )
    val settled by remember {
        derivedStateOf { plate.value != null && latestMillis.value() >= StartupBrandDurationMillis }
    }
    // Start at the 20% resting glow, then breathe between 20–30% every four seconds.
    // Entering this animation only after assembly avoids a jump at the handoff.
    val ambientPulse = if (settled) {
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
        // Check the current owner clock even when asynchronous preparation finishes late.
        // Settled waits keep the glow; recovery/reduced motion never compose this layer.
        val now = latestMillis.value()
        val frame = startupBrandBackgroundFrame(now) ?: return@Canvas
        val image = plate.value ?: return@Canvas
        // 12% overscan leaves room for 4%-of-screen-width travel on both axes.
        val scale = maxOf(size.width / image.width, size.height / image.height) * 1.12f
        val width = (image.width * scale).roundToInt()
        val height = (image.height * scale).roundToInt()
        val drift = (frame.drift - 0.5f) * 0.04f * size.width
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(((size.width - width) / 2f + drift).roundToInt(), ((size.height - height) / 2f + drift).roundToInt()),
            dstSize = IntSize(width, height),
            alpha = frame.alpha * (if (animateArrival) arrivalAlpha.value else 1f) *
                (ambientPulse?.value ?: 1f),
        )
    }
}
