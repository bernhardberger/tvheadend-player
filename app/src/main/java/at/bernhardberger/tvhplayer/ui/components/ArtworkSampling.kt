package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/** Tests can keep artwork calculations on their clock-driven effect context. */
internal val LocalArtworkSamplingContext = staticCompositionLocalOf<CoroutineContext> { Dispatchers.Default }
