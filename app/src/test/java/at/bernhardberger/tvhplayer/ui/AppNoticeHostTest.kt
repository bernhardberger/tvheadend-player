package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.SessionCache
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.notifications.*
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsGeneralLevels
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppNoticeHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cacheResultAfterLeavingSettingsDeliversOnceAndDoesNotStealPlaybackFocus() {
        val release = CompletableDeferred<Unit>()
        val cache = object : SessionCache by FakeTvheadendSession().cache {
            override suspend fun clear() { release.await() }
        }
        val queue = AppNoticeQueue(SystemClock::elapsedRealtime, { Unit })
        val model = SettingsStorageViewModel(cache, queue)
        var playback by mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(MainStartupCompositionState(MainStartupPresentation.Inactive, ChannelsKey, true),
                    onBack = {}, onAction = {}, registerActivityKeyContract = { {} },
                    notices = { AppNoticeHost(queue, Unit) },
                    navigation = { _, _ ->
                        if (!playback) {
                            val clearState by model.clearState.collectAsState()
                            val statistics by cache.statistics.collectAsState()
                            val navigation = rememberDepthNavigationState(SettingsSection.GENERAL.name)
                            SettingsScreenNavigation(navigation, settingsGeneralLevels(UiSettings(), AppLanguage.SYSTEM,
                                statistics, clearState, {}, {}, model::clearCache))
                        } else {
                            val focus = remember { FocusRequester() }
                            Button(onClick = { clicks++ }, modifier = Modifier.focusRequester(focus)) { Text("Playback action") }
                            LaunchedEffect(Unit) { focus.requestFocus() }
                        }
                    })
            }
        }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { playback = true }
        compose.onNodeWithText("Playback action").assertIsFocused()
        compose.runOnIdle { release.complete(Unit) }
        compose.onNodeWithText("Cache cleared").assertIsDisplayed()
        compose.onNodeWithTag("app-notice").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithText("Playback action").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, clicks)
        assertNotNull(queue.state.value.active)
        assertEquals(0, queue.state.value.pending.size)
    }

    @Test fun visibleIdentityAndDeadlineSurviveNavigationAndRecreationWithoutReplay() {
        var time = 0L
        val queue = AppNoticeQueue({ time }, { Unit })
        var destination by mutableStateOf("Settings")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(MainStartupCompositionState(MainStartupPresentation.Inactive, ChannelsKey, true),
                    onBack = {}, onAction = {}, registerActivityKeyContract = { {} },
                    navigation = { _, _ -> Text(destination) }, notices = { AppNoticeHost(queue, Unit) })
            }
        }
        compose.runOnIdle { queue.post("cache", R.string.cache_notice_cleared, AppNoticeKind.SUCCESS, Unit) }
        compose.onNodeWithText("Cache cleared").assertIsDisplayed()
        val shown = queue.state.value.active!!
        compose.runOnIdle { time = 1_000; destination = "Playback" }
        compose.onNodeWithText("Cache cleared").assertIsDisplayed()
        assertEquals(shown, queue.state.value.active)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Cache cleared").assertIsDisplayed()
        assertEquals(shown, queue.state.value.active)
        compose.runOnIdle { time = 4_000; queue.prune() }
        compose.onNodeWithText("Cache cleared").assertDoesNotExist()
        compose.runOnIdle { destination = "Settings" }
        assertNull(queue.state.value.candidate)
    }

    @Test fun foregroundOnlyAndAccessibilityBudgetRemainIndependentOfPendingExpiry() {
        var time = 0L
        val queue = AppNoticeQueue({ time }, { Unit })
        lateinit var owner: LifecycleOwner
        val accessibility = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(originalTimeoutMillis: Long, containsIcons: Boolean,
                containsText: Boolean, containsControls: Boolean): Long {
                assertTrue(containsText)
                assertFalse(containsControls)
                return 60_000
            }
        }
        compose.setContent {
            owner = remember { object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }
            } }
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalAccessibilityManager provides accessibility) {
                TVHeadendPlayerTheme { AppNoticeHost(queue, Unit) }
            }
        }
        compose.runOnIdle { queue.post("cache", R.string.cache_notice_failed, AppNoticeKind.FAILURE, Unit) }
        compose.onNodeWithTag("app-notice").assertDoesNotExist()
        compose.runOnIdle { time = 29_000; (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("app-notice").assertIsDisplayed()
        val active = queue.state.value.active!!
        assertEquals(89_000L, active.expiresAt)
        compose.runOnIdle { time = 31_000; (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.STARTED }
        compose.onNodeWithTag("app-notice").assertDoesNotExist()
        compose.runOnIdle { time = 32_000; (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("app-notice").assertIsDisplayed()
        assertEquals(active, queue.state.value.active)
        compose.runOnIdle { time = 33_000; (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.STARTED }
        compose.onNodeWithTag("app-notice").assertDoesNotExist()
        // Simulate elapsedRealtime crossing the deadline during sleep without advancing
        // coroutine delay or manually pruning. The host must reconcile on resume.
        compose.runOnIdle { time = 90_000; (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("app-notice").assertDoesNotExist()
        compose.runOnIdle { assertNull(queue.state.value.active) }
    }
}
