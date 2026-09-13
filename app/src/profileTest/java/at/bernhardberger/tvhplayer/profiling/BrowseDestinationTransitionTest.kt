package at.bernhardberger.tvhplayer.profiling

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import at.bernhardberger.tvhplayer.ui.components.BrowsePreparationPending
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData
import androidx.tv.material3.Text
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
        var previousDestination = AppDestination.CHANNELS
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
                // Sample the spring after the destination decorators have attached,
                // while both pages are still visibly present.
                compose.mainClock.advanceTimeBy(96)
                compose.waitForIdle()
                val middle = pixel()
                assertTrue("$destination must fade from $previous, got $middle", differs(middle, previous))
                assertTrue("$destination must not jump to $expected, got $middle", differs(middle, expected))
                val currentDestination = when (destination) {
                    ChannelsKey -> AppDestination.CHANNELS
                    GuideKey -> AppDestination.GUIDE
                    RecordingsKey -> AppDestination.RECORDINGS
                    else -> AppDestination.SETTINGS
                }
                val top = compose.onNodeWithTag("destination-$currentDestination")
                    .getUnclippedBoundsInRoot().top.value
                val direction = currentDestination.ordinal.compareTo(previousDestination.ordinal)
                if (direction != 0) {
                    assertTrue("$destination content must slide on the drawer axis, top=$top", top * direction > 1f)
                }
                compose.onNodeWithTag("navigation-owner").assertIsFocused()
                compose.mainClock.advanceTimeBy(1000)
                compose.waitForIdle()
                assertTrue("$destination must finish at $expected, got ${pixel()}", !differs(pixel(), expected))
                assertTrue(abs(compose.onNodeWithTag("destination-$currentDestination")
                    .getUnclippedBoundsInRoot().top.value) < 0.5f)
                previous = expected
                previousDestination = currentDestination
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
                if (it is SettingsKey) {
                    assertTrue("The interrupted Recordings page must finish its exit, not disappear",
                        pixel().blue > 0.01f)
                }
            }
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            assertTrue("Latest Settings must be fully visible", !differs(pixel(), Color.Yellow))
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @OptIn(InternalComposeTracingApi::class)
    @Test fun inFlightMotionDoesNotReexecuteDestinationSlotCompositions() {
        showScene()
        compose.mainClock.autoAdvance = false
        var slots = 0
        compose.runOnIdle {
            Composer.setTracer(object : CompositionTracer {
                override fun isTraceInProgress() = true
                override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                    if (info.substringBefore(" (") == "at.bernhardberger.tvhplayer.ui.SidebarVisitDestination") slots++
                }
                override fun traceEventEnd() = Unit
            })
        }
        try {
            compose.runOnIdle { backStack.navigateTopLevel(RecordingsKey) }
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            val before = pixel()
            compose.runOnIdle {
                assertTrue("The observer must see the actual destination slots before checking isolation", slots > 0)
                slots = 0
            }
            compose.mainClock.advanceTimeBy(48)
            compose.waitForIdle()
            assertTrue("Content must continue animating", differs(pixel(), before))
            compose.runOnIdle { assertEquals("Frame-rate motion must stay out of slot composition", 0, slots) }
        } finally {
            compose.runOnIdle { Composer.setTracer(null) }
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun pendingGuidePreparationDoesNotBlockNewerMainDestinations() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            showScene {
                started.countDown()
                assertTrue(release.await(10, TimeUnit.SECONDS))
            }
            compose.runOnIdle { backStack.navigateTopLevel(GuideKey) }
            compose.waitUntil(5_000) { started.count == 0L }
            compose.runOnIdle { backStack.navigateTopLevel(RecordingsKey) }
            compose.runOnIdle { backStack.navigateTopLevel(SettingsKey(SettingsSection.GENERAL)) }
            compose.onNodeWithTag("destination-SETTINGS").assertExists()
            compose.onNodeWithTag("navigation-owner").assertIsFocused()
            release.countDown()
            compose.waitUntilAtLeastOneExists(hasText("Settings prepared"), 5_000)
            compose.runOnIdle { assertEquals(SettingsKey(SettingsSection.GENERAL), backStack.last()) }
            compose.onNodeWithTag("navigation-owner").assertIsFocused()
        } finally {
            release.countDown()
        }
    }

    private fun showScene(prepareGuide: (() -> Unit)? = null) {
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
                                Box(Modifier.fillMaxSize().background(Color.Red).testTag("destination-CHANNELS"))
                            }
                            entry<GuideKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.GUIDE)) {
                                if (prepareGuide != null) {
                                    val prepared = rememberPreparedBrowseData(GuideKey, Unit) { prepareGuide() }
                                    BrowsePreparationPending(PaddingValues(), false, ready = prepared != null)
                                }
                                Box(Modifier.fillMaxSize().background(Color.Green).testTag("destination-GUIDE"))
                            }
                            entry<RecordingsKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.RECORDINGS)) {
                                Box(Modifier.fillMaxSize().background(Color.Blue).testTag("destination-RECORDINGS"))
                            }
                            entry<SettingsKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.SETTINGS)) {
                                val color = if (it.section == SettingsSection.GENERAL) Color.Yellow else Color.Cyan
                                Box(Modifier.fillMaxSize().background(color).testTag("destination-SETTINGS"))
                                if (prepareGuide != null) {
                                    val prepared = rememberPreparedBrowseData(SettingsSection.GENERAL, Unit) { Unit }
                                    if (prepared != null) Text("Settings prepared")
                                }
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
