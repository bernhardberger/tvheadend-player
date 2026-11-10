@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ContentGenre
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import at.bernhardberger.tvhplayer.ui.components.awaitBrowsePreparation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * The Guide's genre choice lasts while its entry stays in the back stack: choosing Sport, visiting
 * Channels through the navigation rail and returning shows the same filter, on the Filter button
 * and checked in its panel. The choice is not persisted across launches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideGenreSurvivesNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val fixture = NavigationJourneyFixture(compose, ServerSettings(host = javaClass.simpleName))
    private val back get() = fixture.back
    private val guidePosition get() = fixture.guidePosition

    @Test fun guidePickedGenreSurvivesPlayerBack() {
        app()
        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        focus("guide-filter-button")
        key(Key.Enter)
        focus("guide-genre-sport")
        key(Key.Enter)
        focus("guide-date-button")
        key(Key.DirectionDown)
        key(Key.Enter)
        focus("details-watch")
        key(Key.Enter)
        compose.onNodeWithTag("guide-date-button").assertDoesNotExist()
        repeat(3) {
            if (compose.onAllNodes(hasTestTag("guide-date-button")).fetchSemanticsNodes().isEmpty()) {
                compose.runOnIdle { back.onBackPressed() }
                settle()
            }
        }
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(
            context.getString(R.string.header_button_value_description, context.getString(R.string.guide_filter),
                context.getString(R.string.programme_category_sport)))
    }

    @Test fun homeGenreSurvivesPlayerBackAndAnotherHomeGenreRepositionsTheRetainedGuide() {
        app()
        openDestination("nav-channels", Key.DirectionUp, "nav-home")
        focus("home-genre-0")
        key(Key.Enter)
        val sport = context.getString(R.string.header_button_value_description,
            context.getString(R.string.guide_filter), context.getString(R.string.programme_category_sport))
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(sport)
        org.junit.Assert.assertEquals(ChannelId(1), guidePosition.position.value?.channelId)
        focus("guide-date-button")
        key(Key.DirectionDown)
        key(Key.Enter)
        focus("details-watch")
        key(Key.Enter)
        compose.onNodeWithTag("guide-date-button").assertDoesNotExist()
        repeat(3) {
            if (compose.onAllNodes(hasTestTag("guide-date-button")).fetchSemanticsNodes().isEmpty()) {
                compose.runOnIdle { back.onBackPressed() }
                settle()
            }
        }
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(sport)
        focus("nav-epg")
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        key(Key.Enter)
        key(Key.DirectionRight)
        focus("home-genre-0")
        key(Key.DirectionRight)
        compose.onNodeWithTag("home-genre-1").assertIsFocused()
        key(Key.Enter)
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(
            context.getString(R.string.header_button_value_description, context.getString(R.string.guide_filter),
                context.getString(R.string.programme_category_children)))
        org.junit.Assert.assertEquals(ChannelId(2), guidePosition.position.value?.channelId)
    }

    @Test fun recordingPlayerBackRestoresTheGuideProgrammeWindowAndViewport() {
        app(withRecording = true)
        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        focus("guide-date-button")
        repeat(6) { key(Key.DirectionDown) }
        val nowWindow = checkNotNull(guidePosition.position.value).windowStartSec
        key(Key.DirectionLeft)
        val left = checkNotNull(guidePosition.position.value)
        org.junit.Assert.assertEquals(ChannelId(6), left.channelId)
        org.junit.Assert.assertEquals(EventId(66), left.eventId)
        org.junit.Assert.assertTrue(left.windowStartSec < nowWindow)
        org.junit.Assert.assertTrue(left.firstVisibleColumn > 0)
        key(Key.Enter)
        focus("details-play")
        key(Key.Enter)
        compose.onNodeWithTag("guide-date-button").assertDoesNotExist()
        // Back first dismisses player chrome if it is still visible, then closes the player.
        repeat(3) {
            if (compose.onAllNodes(hasTestTag("guide-date-button")).fetchSemanticsNodes().isEmpty()) {
                compose.runOnIdle { back.onBackPressed() }
                settle()
            }
        }
        compose.onNodeWithTag("guide-date-button").assertExists()
        // Returning from the full-screen player, unlike changing destinations, retains the exact spot.
        org.junit.Assert.assertEquals(left, guidePosition.position.value)
        focus("guide-date-button")
        key(Key.DirectionDown)
        compose.onNode(hasText("Recorded programme") and isFocused()).assertExists()
        org.junit.Assert.assertEquals(left, guidePosition.position.value)
    }

    @Test fun leavingForChannelsWithTheRailOpenReentersGuideAtNow() {
        app()
        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        focus("guide-date-button")
        key(Key.DirectionDown)
        val nowWindow = checkNotNull(guidePosition.position.value).windowStartSec
        focus("guide-date-button")
        key(Key.Enter)
        focus("guide-time-day-1")
        key(Key.Enter)
        key(Key.DirectionDown)
        org.junit.Assert.assertTrue(checkNotNull(guidePosition.position.value).windowStartSec > nowWindow)
        openDestination("nav-epg", Key.DirectionUp, "nav-channels")
        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        focus("guide-date-button")
        key(Key.DirectionDown)
        org.junit.Assert.assertEquals(nowWindow, checkNotNull(guidePosition.position.value).windowStartSec)
    }

    @After fun after() {
        fixture.close()
    }

    @Test fun aChosenGenreSurvivesAVisitToChannelsAndBack() {
        app()
        val sportFilter = context.getString(R.string.header_button_value_description, context.getString(R.string.guide_filter), "Sport")

        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        // Browse preparation runs on Dispatchers.Default, outside the Compose clock.
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("guide-filter-button")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(sportFilter).assertDoesNotExist()

        focus("guide-filter-button")
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertIsDisplayed()
        focus("guide-genre-all")
        repeat(4) { key(Key.DirectionDown) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("guide-genre-sport") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertDoesNotExist()
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(sportFilter)
        // The header is named by its pane title; the genre lives on its button.
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, context.getString(R.string.epg_title))).assertExists()

        openDestination("nav-epg", Key.DirectionUp, "nav-channels")
        // Channels browse preparation runs on Dispatchers.Default, outside the Compose clock.
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(sportFilter).assertDoesNotExist()

        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        // Guide browse preparation runs on Dispatchers.Default, outside the Compose clock.
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("guide-filter-button")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(sportFilter)
        focus("guide-filter-button")
        key(Key.Enter)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("guide-genre-sport") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Moves focus to [tag] and settles; the rail's items are reached the way a remote lands on them. */
    private fun focus(tag: String) {
        fixture.focus(tag)
    }

    /** Enters the rail on the current destination's item, steps to its neighbour and opens it. */
    private fun openDestination(from: String, step: Key, to: String) {
        fixture.openDestination(from, step, to)
    }

    private fun app(withRecording: Boolean = false) {
        fixture.app(withRecording)
    }

    private fun key(key: Key) {
        fixture.key(key)
    }

    // Background preparation takes real time, which the advanced clock does not see.
    private fun settle() {
        fixture.settle()
    }
}
