package at.bernhardberger.tvhplayer.testutil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import at.bernhardberger.tvhplayer.ui.components.LocalBrowsePreparationContext
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
