@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.profiling

import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.AppRoot
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Exercises production root dispatch, not the journey fixture's extra BackHandler. */
@OptIn(ExperimentalTestApi::class)
class AppRootBackNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()

    @Test fun remoteBackUnwindsContentScopeAndDrawerBeforeExit() = checkBack(dispatcherOnly = false)
    @Test fun systemBackUnwindsContentScopeAndDrawerBeforeExit() = checkBack(dispatcherOnly = true)

    private fun checkBack(dispatcherOnly: Boolean) {
        compose.waitUntilAtLeastOneExists(hasText("All channels"), 15_000)
        val exits = AtomicInteger()
        val activityFallbacks = AtomicInteger()
        val activity = compose.activity
        val owner = activity.javaClass.getDeclaredField("runtimeOwner")
            .apply { isAccessible = true }.get(activity) as SdkRuntimeOwner
        val images = activity.javaClass.getDeclaredField("images")
            .apply { isAccessible = true }.get(activity) as ImageLoader
        val requests = ApplianceLaunchRequests()
        val server = ServerSettings(host = "offline.invalid")
        val dependencies = module {
            single<TvheadendSession> { owner.session }
            single { owner.playbackRuntime }
            single { owner.appProfileOwner }
            single { PlayerSettingsStore(activity) }
            single { ChannelTagSettingsStore(activity) }
            single { UiSettingsStore(activity) }
            single { LastPlayedChannelStore(activity) }
            single { ChannelSelectionStore() }
            single { GuidePositionStore() }
            single<ImageLoader> { images }
            viewModel { AppConnectionViewModel(get(), get()) }
            viewModel { ChannelsViewModel(get(), get()) }
        }
        compose.runOnIdle { activity.setContent {} }
        compose.waitForIdle()
        compose.runOnIdle {
            activity.viewModelStore.clear()
            // MainActivity has an enabled exit fallback beneath the Compose handlers.
            activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { activityFallbacks.incrementAndGet() }
            })
            activity.setContent {
                KoinApplication(application = { modules(dependencies) }) {
                    TVHeadendPlayerTheme {
                        AppRoot(
                            startupState = MainStartupState.Ready(server, autoStartPlayback = false),
                            runtimeServerSettings = server,
                            applianceLaunchRequests = requests,
                            onPlayerVisibilityChanged = {},
                            onRequestExit = { exits.incrementAndGet() },
                        )
                    }
                }
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("All channels"), 15_000)
        compose.waitForIdle()
        if (compose.onAllNodes(hasText("Channels") and isFocused()).fetchSemanticsNodes().isNotEmpty()) {
            key(Key.DirectionRight)
        }
        compose.onNodeWithText("All channels").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNode(hasText("1  Offline channel 1") and isFocused()).assertIsFocused()

        fun back() {
            if (dispatcherOnly) compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
            else InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
        }

        back()
        compose.onNodeWithText("All channels").assertIsFocused()
        assertEquals(0, exits.get() + activityFallbacks.get())
        back()
        assertEquals("Scope Back must open the drawer, not exit", 0, exits.get() + activityFallbacks.get())
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        back()
        assertEquals("Only a fresh Back from the Channels drawer exits", 1, exits.get())
        assertEquals("The browse root must own system Back", 0, activityFallbacks.get())
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
