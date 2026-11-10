package at.bernhardberger.tvhplayer.testutil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import at.bernhardberger.tvhplayer.ui.components.LocalBrowsePreparationContext
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkSamplingContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Keeps preparation on the clock-driven effect context unless a test supplies a held dispatcher. */
internal fun ComposeContentTestRule.setContentWithBrowsePreparation(
    preparationContext: CoroutineContext = EmptyCoroutineContext,
    content: @Composable () -> Unit,
) {
    setContent {
        CompositionLocalProvider(LocalBrowsePreparationContext provides preparationContext, content = content)
    }
}

/** Keeps palette sampling and glow baking on the effect clock, or a deliberately held dispatcher. */
internal fun ComposeContentTestRule.setContentWithArtworkSampling(
    samplingContext: CoroutineContext = EmptyCoroutineContext,
    content: @Composable () -> Unit,
) {
    setContent {
        CompositionLocalProvider(LocalArtworkSamplingContext provides samplingContext, content = content)
    }
}
