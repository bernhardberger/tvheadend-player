package at.bernhardberger.tvhplayer.profiling

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.AppNavKey
import at.bernhardberger.tvhplayer.ui.ChannelsKey
import at.bernhardberger.tvhplayer.ui.GuideKey
import at.bernhardberger.tvhplayer.ui.RecordingsKey
import at.bernhardberger.tvhplayer.ui.SIDEBAR_SCENE_DESTINATION
import at.bernhardberger.tvhplayer.ui.SettingsKey
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.appDestinationContentTransform
import at.bernhardberger.tvhplayer.ui.navigateTopLevel
import at.bernhardberger.tvhplayer.ui.rememberAppNavBackStack
import at.bernhardberger.tvhplayer.ui.rememberSidebarGuideSceneStrategy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class BrowseDestinationTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()
    private lateinit var backStack: MutableList<AppNavKey>

    @Test fun everyBrowseEdgeAndSettingsCategoryHasAnIntermediateFrame() {
        showScene()
        var previous = Color.Red
        compose.mainClock.autoAdvance = false
        try {
            listOf(
                GuideKey to Color.Green,
                RecordingsKey to Color.Blue,
                SettingsKey(SettingsSection.GENERAL) to Color.Yellow,
                SettingsKey(SettingsSection.PLAYER) to Color.Cyan,
                RecordingsKey to Color.Blue,
                GuideKey to Color.Green,
                ChannelsKey to Color.Red,
            ).forEach { (destination, expected) ->
                compose.runOnIdle { backStack.navigateTopLevel(destination) }
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(48)
                compose.waitForIdle()
                val middle = pixel()
                assertTrue("$destination must fade from $previous, got $middle", differs(middle, previous))
                assertTrue("$destination must not jump to $expected, got $middle", differs(middle, expected))
                compose.onNodeWithTag("navigation-owner").assertIsFocused()
                compose.mainClock.advanceTimeBy(200)
                compose.waitForIdle()
                assertTrue("$destination must finish at $expected, got ${pixel()}", !differs(pixel(), expected))
                previous = expected
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun rapidRetargetingEndsOnLatestRootAndKeepsNavigationFocus() {
        showScene()
        compose.mainClock.autoAdvance = false
        try {
            listOf(GuideKey, RecordingsKey, SettingsKey(SettingsSection.GENERAL)).forEach {
                compose.runOnIdle { backStack.navigateTopLevel(it) }
                compose.mainClock.advanceTimeBy(32)
                compose.waitForIdle()
                compose.onNodeWithTag("navigation-owner").assertIsFocused()
            }
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
            assertTrue("Latest Settings must be fully visible", !differs(pixel(), Color.Yellow))
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun showScene() {
        compose.waitUntilAtLeastOneExists(hasText("Offline channel 1", substring = true), 15_000)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                backStack = rememberAppNavBackStack(ChannelsKey)
                val navigationFocus = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().background(Color.Black).testTag("scene-canvas")) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = {},
                        sceneStrategies = listOf(rememberSidebarGuideSceneStrategy(true, backStack.last())),
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<AppNavKey>()),
                        transitionSpec = { appDestinationContentTransform() },
                        popTransitionSpec = { appDestinationContentTransform() },
                        predictivePopTransitionSpec = { appDestinationContentTransform() },
                        entryProvider = entryProvider {
                            entry<ChannelsKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.CHANNELS)) {
                                Box(Modifier.fillMaxSize().background(Color.Red))
                            }
                            entry<GuideKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.GUIDE)) {
                                Box(Modifier.fillMaxSize().background(Color.Green))
                            }
                            entry<RecordingsKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.RECORDINGS)) {
                                Box(Modifier.fillMaxSize().background(Color.Blue))
                            }
                            entry<SettingsKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.SETTINGS)) {
                                val color = if (it.section == SettingsSection.GENERAL) Color.Yellow else Color.Cyan
                                Box(Modifier.fillMaxSize().background(color))
                            }
                        },
                    )
                    Box(Modifier.size(1.dp).testTag("navigation-owner").focusRequester(navigationFocus).focusable())
                    LaunchedEffect(Unit) { navigationFocus.requestFocus() }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("navigation-owner").assertIsFocused()
    }

    private fun pixel(): Color = compose.onNodeWithTag("scene-canvas").captureToImage().toPixelMap().let {
        it[it.width / 2, it.height / 2]
    }

    private fun differs(left: Color, right: Color): Boolean =
        abs(left.red - right.red) > 0.06f || abs(left.green - right.green) > 0.06f || abs(left.blue - right.blue) > 0.06f
}
