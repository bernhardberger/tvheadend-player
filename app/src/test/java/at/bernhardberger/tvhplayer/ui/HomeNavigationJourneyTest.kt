@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import at.bernhardberger.tvhplayer.testutil.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import kotlin.time.Instant
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeNavigationJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = NavigationJourneyFixture(compose, ServerSettings(host = javaClass.simpleName))
    @After fun after() = fixture.close()

    @Test fun restoringGuideWithPendingGuideEntryKeepsTheRestoredGraph() {
        fixture.app(guideEntry = true)
        fixture.restoreWithPendingGuide()
        compose.onNodeWithTag("epg-screen").assertExists()
        compose.onNodeWithTag("home-header").assertDoesNotExist()
        compose.runOnIdle { assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value) }
    }

    @Test fun restoringConnectionSettingsDropsPendingGuideWithoutReplacingTheGraph() {
        fixture.app(emptyChannels = true)
        compose.onNodeWithText("Connection settings").performSemanticsAction(SemanticsActions.OnClick)
        fixture.settle()
        compose.onNodeWithText("Add connection", useUnmergedTree = true).assertExists()
        fixture.restoreWithPendingGuide()
        compose.onNodeWithText("Add connection", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("epg-screen").assertDoesNotExist()
        compose.onNodeWithTag("home-header").assertDoesNotExist()
        compose.runOnIdle { assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value) }
    }

    @Test fun guideEntryDuringEnteringAutoplayLeavesFullScreenWithoutAStaleCompletion() {
        compose.mainClock.autoAdvance = false
        fixture.app(autoStartPlayback = true)
        compose.runOnIdle {
            assertTrue(fixture.launchRequests.state.value is ApplianceLaunchState.Entering)
            fixture.launchRequests.request(at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.GUIDE)
        }
        fixture.settle()
        compose.onNodeWithTag("epg-screen").assertExists()
        compose.onNodeWithTag("main-startup-root").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(fixture.playerVisible)
            assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value)
        }
    }

    @Test fun coldGuideEntryWithNoChannelsShowsTheGuideEmptyState() {
        fixture.app(autoStartPlayback = true, guideEntry = true, emptyChannels = true)
        compose.onNodeWithTag("guide-empty-state").assertExists()
        compose.runOnIdle {
            assertFalse(fixture.playerVisible)
            assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value)
        }
    }

    @Test fun coldGuideEntryOverridesAutoplayAndKeepsNormalGuideBack() {
        fixture.app(autoStartPlayback = true, guideEntry = true)
        compose.runOnIdle {
            assertFalse(fixture.playerVisible)
            assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value)
        }
        compose.onNodeWithTag("main-startup-root").assertDoesNotExist()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-home").assertIsFocused()
    }

    @Test fun guideEntryInTheSameFrameAsPlaybackBecomesVisibleOpensFullGuide() {
        compose.mainClock.autoAdvance = false
        fixture.app(autoStartPlayback = true)
        compose.runOnIdle {
            val entering = fixture.launchRequests.state.value as ApplianceLaunchState.Entering
            fixture.launchRequests.completePlayerVisibility(entering.target, entering.target.channelId, entering.target.channelName)
            fixture.launchRequests.request(at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.GUIDE)
        }
        fixture.settle()
        compose.onNodeWithTag("epg-screen").assertExists()
        compose.onNodeWithTag("channel-schedule-panel").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(fixture.playerVisible)
            assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value)
        }
    }

    @Test fun warmGuideEntryTogglesScheduleWithoutLeavingLivePlayer() {
        compose.mainClock.autoAdvance = false
        fixture.app(autoStartPlayback = true)
        compose.runOnIdle {
            assertTrue(fixture.playerVisible)
            val entering = fixture.launchRequests.state.value as ApplianceLaunchState.Entering
            fixture.launchRequests.completePlayerVisibility(entering.target, entering.target.channelId, entering.target.channelName)
        }
        fixture.settle()
        compose.runOnIdle {
            fixture.launchRequests.request(at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.GUIDE)
        }
        fixture.settle()
        compose.onNodeWithTag("channel-schedule-panel").assertExists()
        compose.onNodeWithTag("epg-screen").assertDoesNotExist()
        compose.runOnIdle { assertTrue(fixture.playerVisible) }
        compose.runOnIdle {
            fixture.launchRequests.request(at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.GUIDE)
        }
        fixture.settle()
        compose.onNodeWithTag("channel-schedule-panel").assertDoesNotExist()
        compose.onNodeWithTag("epg-screen").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(fixture.playerVisible)
            assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value)
        }
    }

    @Test fun warmGuideEntryWithOptionsOpenKeepsOptionsAndDoesNotNavigate() {
        compose.mainClock.autoAdvance = false
        fixture.app(autoStartPlayback = true)
        compose.runOnIdle {
            val entering = fixture.launchRequests.state.value as ApplianceLaunchState.Entering
            fixture.launchRequests.completePlayerVisibility(entering.target, entering.target.channelId, entering.target.channelName)
        }
        fixture.settle()
        fixture.key(Key.Menu)
        compose.onNodeWithTag("playback-options-title").assertExists()
        compose.runOnIdle {
            fixture.launchRequests.request(at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.GUIDE)
        }
        fixture.settle()
        compose.onNodeWithTag("playback-options-title").assertExists()
        compose.onNodeWithTag("channel-schedule-panel").assertDoesNotExist()
        compose.onNodeWithTag("epg-screen").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(fixture.playerVisible)
            assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value)
        }
    }

    @Test fun guideEntryFromChannelsOpensFullGuideAndRepeatingItKeepsFocusAndPosition() {
        fixture.app()
        compose.runOnIdle {
            fixture.launchRequests.request(at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.GUIDE)
        }
        fixture.settle()
        compose.onNodeWithTag("epg-screen").assertExists()
        fixture.focus("guide-filter-button")
        val position = fixture.guidePosition.position.value
        compose.runOnIdle {
            fixture.launchRequests.request(at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.GUIDE)
        }
        fixture.settle()
        compose.onNodeWithTag("guide-filter-button").assertIsFocused()
        compose.runOnIdle {
            assertFalse(fixture.playerVisible)
            assertEquals(position, fixture.guidePosition.position.value)
            assertEquals(ApplianceLaunchState.Idle, fixture.launchRequests.state.value)
        }
    }

    @Test fun autostartLiveWithoutRecordingsReturnsToExposedHomeAndReleasesStartup() {
        compose.mainClock.autoAdvance = false
        fixture.app(autoStartPlayback = true)
        compose.runOnIdle {
            assertTrue(fixture.playerVisible)
            // Keep the offline stream pending rather than letting an empty fake stream fail.
            // Supply the launch completion normally sent by the player's first-picture callback.
            val entering = fixture.launchRequests.state.value as ApplianceLaunchState.Entering
            assertTrue(fixture.launchRequests.completePlayerVisibility(entering.target, entering.target.channelId, entering.target.channelName))
        }
        fixture.settle()
        compose.onNodeWithTag("home-header").assertDoesNotExist()
        repeat(3) {
            if (fixture.playerVisible) {
                compose.runOnIdle { fixture.back.onBackPressed() }
                fixture.settle()
            }
        }
        compose.mainClock.advanceTimeUntil(15_000) {
            compose.onAllNodes(hasTestTag("home-header")).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasTestTag("home-onnow-0")).fetchSemanticsNodes().isNotEmpty()
        }
        fixture.settle()
        compose.onNodeWithTag("main-startup-root").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(fixture.playerVisible)
            assertEquals(MainStartupKeyMode.Inactive, fixture.startupKeyMode)
        }
        // Home's scroll-away header may be above the viewport when On now owns focus,
        // but both must remain exposed to the merged accessibility/automation tree.
        compose.onNodeWithTag("home-header").assertExists()
        compose.onNodeWithTag("home-onnow-0").assertIsDisplayed()
        compose.onNodeWithTag("nav-home").assertExists()
    }

    @Test fun emptyHomeBrowseChannelsUsesTheChannelsDestination() {
        fixture.app(emptyEpg = true)
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithText("Browse channels").assertIsFocused()
        fixture.key(Key.DirectionUp)
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        fixture.key(Key.DirectionDown)
        compose.onNodeWithText("Browse channels").assertIsFocused()
        fixture.key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithText("Browse channels").assertIsFocused()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("channel-row-1").assertIsFocused()
        compose.onNodeWithTag("home-view-button").assertDoesNotExist()
    }

    @Test fun appRootTwoBackPressesFromGuideReachHomeRailThenRightRestoresTheGenreCard() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-genre-0")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-genre-1").assertIsFocused()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("guide-filter-button").assertExists()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        compose.onNodeWithTag("home-genre-1").assertDoesNotExist()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-genre-1").assertIsFocused()
    }

    @Test fun appRootPlayerBackRestoresTheHomeCardAndViewport() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-onnow-0")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        val before = compose.onNodeWithTag("home-onnow-1").getUnclippedBoundsInRoot()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("home-view-button").assertDoesNotExist()
        repeat(3) {
            if (compose.onAllNodes(hasTestTag("home-view-button")).fetchSemanticsNodes().isEmpty()) {
                compose.runOnIdle { fixture.back.onBackPressed() }
                fixture.settle()
            }
        }
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        val after = compose.onNodeWithTag("home-onnow-1").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(before.left.value, after.left.value, 2f)
        org.junit.Assert.assertEquals(before.top.value, after.top.value, 2f)
    }

    @Test fun aViewRefocusWithNothingFocusedEntersTheRememberedCardNotTheRail() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-onnow-0")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        fixture.viewRefocusesWithNothingFocused()
        compose.onNodeWithTag("nav-home").assertIsNotFocused()
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        // The viewer's own way to the rail is untouched.
        fixture.key(Key.DirectionLeft)
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
        fixture.key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
    }

    @Test fun realBackKeyCycleFromThePlayerReturnsToTheOnNowCardWithTheRailClosed() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-onnow-0")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("home-view-button").assertDoesNotExist()
        fixture.backKeyCycle()
        compose.onNodeWithTag("home-view-button").assertExists()
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        compose.onNodeWithTag("global-drawer-wordmark").assertDoesNotExist()
    }

    @Test fun realBackKeyCycleFromThePlayerReturnsToTheRecentChannelCardWithTheRailClosed() {
        // Playback is not confirmed offline, so seed what a confirmed first picture records.
        // The real preferences DataStore performs these setup writes on its IO worker.
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val profileId = ServerProfileId.generate()
            context.dataStore.edit { it[stringPreferencesKey("activeServerProfileId")] = profileId.value }
            LastPlayedChannelStore(context).run {
                setChannelId(profileId, ChannelId(3)); setChannelId(profileId, ChannelId(2))
            }
        }
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-recent-0")
        fixture.key(Key.Enter)
        compose.onNodeWithTag("home-view-button").assertDoesNotExist()
        fixture.backKeyCycle()
        compose.onNodeWithTag("home-view-button").assertExists()
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        compose.onNodeWithTag("global-drawer-wordmark").assertDoesNotExist()
    }

    @Test fun realRailRightRestoresTheCardLeftBehindInsteadOfUpcoming() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
    }

    @Test fun upcomingOpensItsScheduleRowAndBackRestoresTheCard() {
        fixture.app(scheduled = (1L..10L).map { id ->
            DvrEntry.create(DvrEntryId(id), title = "Scheduled programme $id", state = DvrEntryState.SCHEDULED,
                start = Instant.fromEpochSeconds(1_900_000_000L + id * 3600),
                stop = Instant.fromEpochSeconds(1_900_000_000L + id * 3600 + 1800))
        })
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-upcoming-0")
        repeat(8) { fixture.key(Key.DirectionRight) }
        compose.onNodeWithTag("home-upcoming-8").assertIsFocused()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("recordings-schedule-list").assertExists()
        compose.onNodeWithTag("recording-list-entry-9").assertIsFocused().assertIsDisplayed()
        // Re-enter through the rail, matching the TV journey rather than Home's deep link.
        fixture.focus("nav-schedule")
        fixture.key(Key.Enter)
        fixture.key(Key.DirectionRight)
        fixture.focus("recording-list-entry-9")
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            repeat(8) {
                compose.onNodeWithTag("nav-home").assertIsNotFocused()
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            compose.onNodeWithTag("nav-schedule").assertIsFocused()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        fixture.key(Key.DirectionRight)
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-schedule").assertIsFocused().assertIsSelected()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-upcoming-8").assertIsFocused()
    }

    @Test fun allScheduledBackRestoresTheEndCard() {
        val scheduled = DvrEntry.create(DvrEntryId(1), title = "Scheduled programme",
            state = DvrEntryState.SCHEDULED, start = Instant.fromEpochSeconds(1_900_000_000),
            stop = Instant.fromEpochSeconds(1_900_001_800))
        fixture.app(scheduled = listOf(scheduled))
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-upcoming-0")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-all-scheduled").assertIsFocused()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("recordings-schedule-list").assertExists()
        fixture.publishRecordings(listOf(scheduled, DvrEntry.create(DvrEntryId(2), title = "New scheduled programme",
            state = DvrEntryState.SCHEDULED, start = Instant.fromEpochSeconds(1_900_003_600),
            stop = Instant.fromEpochSeconds(1_900_005_400))))
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-all-scheduled").assertIsFocused()
    }
}
