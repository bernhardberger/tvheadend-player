package at.bernhardberger.tvhplayer.profiling

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsChannelTagsLevel
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
class ChannelSettingsEntryTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()

    @Test fun loadingContentEntryStaysOnCategoryThenReachesSettings() = checkEntry(failRead = false)

    @Test fun failedLoadContentEntryReachesRetry() = checkEntry(failRead = true)

    private fun checkEntry(failRead: Boolean) {
        compose.waitUntilAtLeastOneExists(hasText("All channels"), 15_000)
        val readGate = CompletableDeferred<Unit>()
        val store = object : DataStore<Preferences> {
            override val data = flow {
                readGate.await()
                if (failRead) throw IOException("test read failure")
                emit(emptyPreferences())
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("This focus test does not write preferences")
        }
        val contentFocus = FocusRequester()
        val categoryFocus = FocusRequester()
        compose.activityRule.scenario.onActivity { activity ->
            val model = ChannelsViewModel(FakeTvheadendSession(), ChannelTagSettingsStore(store))
            activity.viewModelStore.put("settings-entry-test", model)
            activity.setContent {
                TVHeadendPlayerTheme {
                    SettingsScreenNavigation(
                        rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CHANNEL_TAGS.name),
                        listOf(settingsRootLevel(), settingsChannelTagsLevel(model)),
                    )
                }
            }
        }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        // Loading owns a deterministic inert row in the active level.
        readGate.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithText(if (failRead) "Retry" else "All channels").assertIsFocused()
    }
}
