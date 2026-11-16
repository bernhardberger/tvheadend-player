@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class, at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)

package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.input.key.nativeKeyCode
import org.junit.Assert.assertFalse
import at.bernhardberger.tvhplayer.testutil.pressKey
import android.app.Application
import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.datastore.core.DataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
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
import at.bernhardberger.tvhplayer.testutil.awaitScheduledWork
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.ui.player.LivePlayerServerWaitTag
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOpener
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionChannelId
import kotlinx.coroutines.awaitCancellation
import java.io.IOException

/**
 * Startup and play before the session is ready. OK on a saved channel row opens the player for
 * that channel at once; it waits without an action, tunes that channel on Ready, offers the catalog's
 * recovery when connecting fails, and Back returns to the row without tuning anything later.
 * Without saved channels a cold start shows the startup screen, readable, until channels arrive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class StartupOfflineTest {
    @get:Rule val compose = createComposeRule()
    private val startupKeys = MainStartupActivityKeyDispatcher(
        at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner())
    private var startupKeyContract = MainStartupActivityKeyContract(
        at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode.Inactive)
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val profileDispatcher = StandardTestDispatcher()
    private val catalog = ChannelCatalog.create((1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) })
    private lateinit var player: ExoPlayer
    private lateinit var session: FakeTvheadendSession
    private lateinit var runtime: AppPlaybackRuntime
    private lateinit var playerData: InMemoryData
    private lateinit var launchRequests: ApplianceLaunchRequests
    private val activeSubscriptions = java.util.concurrent.ConcurrentHashMap.newKeySet<SubscriptionChannelId>()

    @After fun after() {
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    @Test fun okBeforeReadyTunesThatChannelWithoutAnAppStartupSurface() {
        app(holdLive = true)
        confirmRow(1)
        // The saved row opens its channel wait, not the app's startup screen.
        compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true).assertExists()
        assertEquals(listOf("1 Name 1"), compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        assertEquals(0, compose.onAllNodes(hasClickAction() and isFocused(), useUnmergedTree = false).fetchSemanticsNodes().size)
        // Confirm is consumed by the wait rather than reaching an action behind it.
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true).assertExists()
        assertEquals(0, binds())

        // The first session installs the requested channel once; in-app entry has no Starting phase.
        session.publish(ready(catalog))
        settle()
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        settle()
        assertEquals(1, binds())
        compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun failedConnectingThatRetriesByItselfOffersConnectionSettingsFocusedAndNoRetry() {
        app()
        confirmRow(1)
        session.publish(SessionObservation.create(
            sessionState = SessionState.Unavailable(SessionFailure.TRANSPORT_UNAVAILABLE),
            channelState = ChannelRepositoryState.Stale(catalog),
        ))
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasText("Retry", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        session.publish(connecting(catalog))
        settle()
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun connectionSettingsFromTheFailedWaitLeavesThePlayerForConnectionSettings() {
        app()
        confirmRow(1)
        session.publish(SessionObservation.create(
            sessionState = SessionState.Unavailable(SessionFailure.synchronizationFailed(TvheadendFailure.MALFORMED_RESPONSE)),
            channelState = ChannelRepositoryState.Stale(catalog),
        ))
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.pressKey(Key.DirectionRight)
        compose.onNode(hasTestTag("tv-recovery-secondary") and hasText("Connection settings", substring = true)).assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        settle()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Add connection"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-overlay")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, binds())
    }

    @Test fun backLeavesTheWaitToItsRowAndCancelsItsTune() {
        app()
        confirmRow(2)
        compose.pressKey(Key.Back)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("channel-row-2").assertIsFocused()
        session.publish(ready(catalog))
        settle()
        assertEquals(0, binds())
        assertEquals(null, runtime.activeTarget.value)
    }

    @Test fun aReplacedPendingTargetTunesOnlyTheSecondChannel() {
        app()
        confirmRow(1)
        compose.pressKey(Key.Back)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-1") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        confirmRow(3)
        session.publish(ready(catalog))
        settle()
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(3)) }
        settle()
        assertEquals(1, binds())
    }

    @Test fun aChannelGoneAfterSyncShowsTheUnavailableState() {
        app()
        confirmRow(3)
        session.publish(ready(ChannelCatalog.create(catalog.channels.take(2))))
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("player-channel-unavailable")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, binds())
    }

    @Test fun savedChannelsShowTheShellWithoutTheStartupScreen() {
        app()
        assertEquals(0, compose.onAllNodes(hasTestTag("main-startup-root")).fetchSemanticsNodes().size)
    }

    @Test fun cachedColdLandingDrawsHomeBeforeReleasingStartupWithoutTuning() {
        coldApp(connecting(catalog))
        advance(1_000)
        compose.onNodeWithTag("home-header").fetchSemanticsNode()
        assertEquals(0, startupScreens())
        assertEquals(0, binds())
        assertEquals(0, compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().size)
    }

    @Test fun cachedColdLandingHoldsStartupAndHidesHomeAndRailUntilSettingsArrive() {
        val gate = CompletableDeferred<Unit>()
        val tags = InMemoryData().apply { readGate = gate }
        app(ready(catalog), awaitRows = false, tagData = tags)
        advance(1_000)
        assertEquals(1, startupScreens())
        val landingOrRail = SemanticsMatcher("Home or navigation rail") { node ->
            val tag = node.config.getOrElse(SemanticsProperties.TestTag) { "" }
            tag.startsWith("home-") || tag.startsWith("nav-")
        }
        // clearAndSetSemantics hides the landing tree from accessibility's merged view.
        assertEquals(0, compose.onAllNodes(landingOrRail, useUnmergedTree = false).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().size)

        gate.complete(Unit)
        advance(1_000)
        assertEquals(0, startupScreens())
        compose.onNodeWithTag("home-header").fetchSemanticsNode()
        compose.onNodeWithTag("home-recovery").assertIsFocused()
        assertEquals(0, binds())
    }

    @Test fun failedChannelSettingsReleaseColdStartupToRetryThenHome() {
        val tags = InMemoryData().apply { failNextRead = true }
        app(ready(catalog), awaitRows = false, tagData = tags)
        advance(1_000)
        assertEquals(0, startupScreens())
        compose.onNode(hasText("Retry") and isFocused()).assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        advance(1_000)
        compose.onNodeWithTag("home-header").fetchSemanticsNode()
        compose.onNodeWithTag("home-recovery").assertIsFocused()
        assertEquals(0, startupScreens())
        assertEquals(0, binds())
    }

    @Test fun withoutSavedChannelsTheStaticBrandShowsAtOnceAndStaysReadable() {
        coldApp(SessionObservation.create(sessionState = SessionState.Connecting))
        advance(200)
        // The one grace: the screen is laid out with its brand still transparent, no stage text yet.
        assertEquals(1, startupScreens())
        assertEquals(0, statusTexts())
        compose.onNodeWithTag("main-startup-mark", useUnmergedTree = true).fetchSemanticsNode()
        compose.onNode(hasText("Tvheadend Player"), useUnmergedTree = true).fetchSemanticsNode()
        // From the stage text on, readiness keeps the shown screen readable before it leaves.
        // (Its 500ms minimum is checked in ColdStartPresentationTest: the glow's frames also
        // move Robolectric's Handler clock, so a fixed wait here would measure the harness.)
        repeat(20) { if (statusTexts() == 0) advance(50) }
        assertEquals(1, statusTexts())
        session.publish(ready(catalog))
        advance(50)
        assertEquals("shown screens stay readable", 1, startupScreens())
        compose.mainClock.advanceTimeBy(300)
        assertEquals("the settled stage has one status", 1, statusTexts())
        advance(600)
        assertEquals(0, startupScreens())
        openChannels()
        advance(300)
        compose.onNodeWithTag("channel-row-1").fetchSemanticsNode()
    }

    @Test fun savedChannelsWithinTheGraceLeadStraightToTheShell() {
        coldApp(SessionObservation.create(sessionState = SessionState.Connecting))
        advance(200)
        session.publish(connecting(catalog))
        advance(100)
        assertEquals(0, startupScreens())
        compose.onNodeWithTag("home-header").fetchSemanticsNode()
        openChannels()
        advance(300)
        compose.onNodeWithTag("channel-row-1").fetchSemanticsNode()
        advance(1_000)
        assertEquals(0, startupScreens() + statusTexts())
    }

    @Test fun aFailedColdStartKeepsConnectionSettingsFocusedThroughAutomaticAttempts() {
        coldApp(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.TRANSPORT_UNAVAILABLE)))
        advance(1_000)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        // 110: the session retries by itself, so the startup alert offers Connection settings and no Retry.
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-secondary")).fetchSemanticsNodes().size)
        assertEquals(1, startupScreens())
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        advance(500)
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
    }

    @Test fun unavailableProfileOffersConnectionSettingsInsteadOfConnecting() =
        profileRecovery(ServerProfileReadResult.Unavailable)

    @Test fun missingProfileOffersConnectionSettingsInsteadOfConnecting() =
        profileRecovery(ServerProfileReadResult.Missing)

    private fun profileRecovery(profile: ServerProfileReadResult) {
        app(profile = profile)
        confirmRow(1, awaitWait = false)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun okWithAReadySessionStartsThatChannelAndBackLeavesItPlaying() {
        app(ready(catalog), holdLive = true)
        confirmRow(2, awaitWait = false)
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        assertEquals(1, binds())
        assertEquals("a viewer selection uses the player's opening state", 0,
            compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.pressKey(Key.Back) // Ordinary player chrome handles the first Back.
        settle()
        compose.pressKey(Key.Back)
        advance(300)
        compose.awaitScheduledWork(profileDispatcher) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("playback stays warm behind browse", AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun autoplayKeepsStartupPendingUntilItsFirstFrame() {
        app(ready(catalog), awaitRows = false, holdLive = true, autoplay = true)
        settle()
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value != null }
        advance(500)
        assertEquals("passive Starting is not a recovery outcome", 1, startupScreens())
        compose.awaitScheduledWork(profileDispatcher) { SubscriptionChannelId(1) in activeSubscriptions }
        compose.runOnIdle {
            player.pause()
            playerListeners.toList().forEach { it.onPlaybackStateChanged(androidx.media3.common.Player.STATE_READY) }
            playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        }
        advance(800)
        assertEquals("the presented target completes startup", 0, startupScreens())
    }

    @Test fun freshAutoplayKeepsBrandUntilItsSavedChannelCanTune() {
        startAutoplay(connecting(catalog), ChannelId(2))
        compose.onNodeWithText("1 Name 1", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("2 Name 2", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("main-startup-wordmark", useUnmergedTree = true).assertExists()
        assertEquals("Connecting", compose.onNodeWithTag("main-startup-status", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
        assertEquals("the saved choice must not tune", 0, binds())
        assertTrue("cold autoplay retains the branded background",
            compose.onAllNodes(hasTestTag("startup-background-pending") or hasTestTag("startup-background-prepared"),
                useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.pressKey(Key.ChannelUp)
        settle()
        assertEquals("Connecting consumes a zap without tuning", 0, binds())
        session.publish(ready(catalog))
        settle()
        compose.onNodeWithText("2 Name 2", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun autoplayFallsBackWhenTheSavedChannelIsMissing() {
        startAutoplay(connecting(catalog), ChannelId(2))
        assertEquals(0, binds())
        val replacement = Channel.create(ChannelId(3), name = "Replacement", number = 3)
        session.publish(ready(ChannelCatalog.create(listOf(replacement))))
        settle()
        compose.onNodeWithText("3 Replacement", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(AppPlaybackTarget.Live(replacement.id), runtime.activeTarget.value)
        assertEquals(1, binds())
    }

    @Test fun autoplayWithoutSavedDataKeepsBrandUntilReady() {
        startAutoplay(SessionObservation.create(sessionState = SessionState.Connecting), null)
        assertEquals(0, binds())
        session.publish(ready(catalog))
        settle()
        compose.onNodeWithText("1 Name 1", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("main-startup-wordmark", useUnmergedTree = true).assertExists()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun readyWaitsForTheInitialSavedChoice() {
        val gate = CompletableDeferred<Unit>()
        startAutoplay(connecting(catalog), ChannelId(2), gate)
        session.publish(ready(catalog))
        settle()
        compose.onNodeWithTag("main-startup-wordmark", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("1 Name 1", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, binds())
        playerData.readGate = null
        gate.complete(Unit)
        settle()
        compose.onNodeWithText("2 Name 2", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    private lateinit var bootstrapOwner: at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel
    private var bootstrapGeneration by androidx.compose.runtime.mutableStateOf(0)

    @Test fun stoppedBootstrapRestoresDarkWithoutWithdrawingAutoplay() {
        val gate = CompletableDeferred<Unit>()
        val saved = androidx.lifecycle.SavedStateHandle()
        app(awaitRows = false, bootstrapHandle = saved, bootstrapReadGate = gate, bootstrapAutoplay = true)
        settle()
        compose.runOnIdle {
            bootstrapOwner.finishFreshStart()
            assertTrue(saved.get<Boolean>("startup.requestCreationHandled") != true)
            assertEquals(at.bernhardberger.tvhplayer.core.ApplianceLaunchState.Idle,
                bootstrapOwner.applianceLaunchRequests.state.value)
            // Cancel the old owner's held bootstrap before creating its replacement from the same saved state.
            bootstrapOwner.viewModelScope.cancel()
            bootstrapGeneration++
        }
        settle()
        advance(1_500)
        assertEquals(MainStartupState.ResolvingLocal, bootstrapOwner.state.value)
        assertTrue("recreated Preparing must remember the completed freshness", startupBackgroundCount() == 0)
        gate.complete(Unit)
        settle()
        advance(500)
        assertTrue(bootstrapOwner.state.value is MainStartupState.Ready)
        assertTrue(bootstrapOwner.applianceLaunchRequests.state.value is
            at.bernhardberger.tvhplayer.core.ApplianceLaunchState.Pending)
        assertTrue("restored Ready remains dark while autoplay waits", startupBackgroundCount() == 0)
        assertEquals(0, binds())
    }

    @Test fun coldInitialLiveIntentKeepsGlowWhenBootstrapCoalesces() {
        val gate = CompletableDeferred<Unit>()
        app(awaitRows = false, bootstrapHandle = androidx.lifecycle.SavedStateHandle(),
            bootstrapReadGate = gate, earlyBootstrapLive = true, bootstrapAutoplay = true)
        settle()
        assertEquals("the read gate keeps bootstrap unfinished when LIVE arrives",
            MainStartupState.ResolvingLocal, bootstrapOwner.state.value)
        val earlyRequest = bootstrapOwner.applianceLaunchRequests.state.value
        assertTrue("LIVE is pending before bootstrap finishes",
            earlyRequest is at.bernhardberger.tvhplayer.core.ApplianceLaunchState.Pending)
        gate.complete(Unit)
        settle()
        assertTrue(bootstrapOwner.state.value is MainStartupState.Ready)
        assertEquals("bootstrap autoplay coalesces with the earlier LIVE request",
            earlyRequest, bootstrapOwner.applianceLaunchRequests.state.value)
        advance(500)
        assertTrue("a cold initial LIVE intent keeps glow after bootstrap", startupBackgroundCount() > 0)
        assertEquals(0, binds())
    }

    @Test fun leavingEndsGlowEvenWhileTheColdRequestRemainsPending() {
        val gate = CompletableDeferred<Unit>()
        app(initial = SessionObservation.create(sessionState = SessionState.Connecting), awaitRows = false,
            bootstrapHandle = androidx.lifecycle.SavedStateHandle(), bootstrapReadGate = gate, bootstrapAutoplay = true)
        settle()
        gate.complete(Unit)
        settle()
        advance(500)
        assertTrue(startupBackgroundCount() > 0)
        val pending = bootstrapOwner.applianceLaunchRequests.state.value
        compose.runOnIdle {
            bootstrapOwner.finishFreshStart()
            bootstrapOwner.applianceLaunchRequests.requestEntry(
                at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.LIVE, false)
        }
        settle()
        assertTrue("a return to the same pending request is dark", startupBackgroundCount() == 0)
        assertEquals(pending, bootstrapOwner.applianceLaunchRequests.state.value)
        assertEquals(0, binds())
    }

    @Test fun restoredBootstrapStaysDarkBeforeAndAfterLocalSettingsArrive() {
        val gate = CompletableDeferred<Unit>()
        app(awaitRows = false, holdLive = true,
            bootstrapHandle = androidx.lifecycle.SavedStateHandle(mapOf(
                "startup.requestCreationHandled" to true, "startup.retainedRequestId" to 7L)),
            bootstrapReadGate = gate)
        settle()
        advance(1_500)
        compose.onNodeWithTag("main-startup-status", useUnmergedTree = true).assertExists()
        assertTrue("restored Preparing must not flash the brand", startupBackgroundCount() == 0)
        gate.complete(Unit)
        settle()
        advance(500)
        compose.onNodeWithText("Connecting", useUnmergedTree = true).assertExists()
        assertTrue("restored Ready remains dark", startupBackgroundCount() == 0)
        compose.onNodeWithTag("main-startup-wordmark", useUnmergedTree = true).assertExists()
        assertEquals(0, binds())
    }

    @Test fun coldBootstrapKeepsTheBrandedPreparingScreen() {
        val gate = CompletableDeferred<Unit>()
        app(awaitRows = false, bootstrapHandle = androidx.lifecycle.SavedStateHandle(), bootstrapReadGate = gate)
        settle()
        advance(1_500)
        assertTrue("cold Preparing retains its brand", startupBackgroundCount() > 0)
        gate.complete(Unit)
        settle()
        assertTrue("cold Ready retains its brand", startupBackgroundCount() > 0)
    }

    private fun startupBackgroundCount() =
        compose.onAllNodes(hasTestTag("startup-background-pending") or hasTestTag("startup-background-prepared"),
            useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun savedPendingLiveUsesTheChannelStartGrace() = pendingLiveGrace(connecting(catalog))

    @Test fun unsavedPendingLiveUsesTheSameChannelStartGrace() =
        pendingLiveGrace(SessionObservation.create(sessionState = SessionState.Connecting))

    @Test fun sessionLostBeforeThePictureKeepsTheOriginalGraceThroughRecoveryHandoff() {
        app(connecting(catalog), awaitRows = false, holdLive = true, autoplay = true)
        settle()
        advance(5_000)
        session.publish(ready(catalog))
        settle()
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value != null }
        session.publish(SessionObservation.create(
            sessionState = SessionState.Unavailable(SessionFailure.TRANSPORT_UNAVAILABLE),
            channelState = ChannelRepositoryState.Stale(catalog),
        ))
        // This crosses T0 + 15 s, but not the later runtime start's own deadline.
        advance(10_000)
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        assertEquals("RECOVERY releases the startup owner", at.bernhardberger.tvhplayer.core.ApplianceLaunchState.Idle,
            launchRequests.state.value)
        compose.settleFrames(4)
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        compose.onNodeWithText("Connecting", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun pendingLiveGrace(initial: SessionObservation) {
        app(initial, awaitRows = false, holdLive = true, autoplay = true)
        settle()
        session.publish(SessionObservation.create(
            sessionState = SessionState.Unavailable(SessionFailure.TRANSPORT_UNAVAILABLE),
            channelState = initial.channelState,
        ))
        advance(1_000)
        compose.onNodeWithTag("tv-recovery-primary").assertDoesNotExist()
        assertEquals(0, binds())
        advance(15_000)
        compose.onNodeWithTag("tv-recovery-primary").assertExists()
        compose.onNodeWithTag("tv-recovery-secondary").assertDoesNotExist()
        assertEquals(0, binds())
    }

    private fun startAutoplay(
        initial: SessionObservation, persisted: ChannelId?, gate: CompletableDeferred<Unit>? = null,
    ) {
        app(initial, awaitRows = false, holdLive = true, autoplay = true,
            persistedChannel = persisted, initialReadGate = gate)
        settle()
        advance(500)
        compose.onNodeWithTag("main-startup-wordmark", useUnmergedTree = true).assertExists()
    }

    @Test fun startupKeysLeaveTheOwnerAndItsTargetUntilBack() {
        app(ready(catalog), awaitRows = false, holdLive = true, autoplay = true)
        settle()
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value != null }
        val target = runtime.activeTarget.value
        val count = binds()
        val playing = player.playWhenReady
        for (key in listOf(Key.DirectionCenter, Key.Info, Key.Menu, Key.ChannelUp, Key.Two, Key.MediaPlayPause)) {
            for ((action, repeat) in listOf(0 to 0, 0 to 1, 1 to 0)) {
                assertTrue("$key is consumed by the Activity contract",
                    startupKeys.dispatch(startupKeyContract, key.nativeKeyCode, action, repeat))
            }
            assertEquals("$key keeps startup", 1, startupScreens())
            assertEquals("$key retains the target", target, runtime.activeTarget.value)
            assertEquals("$key starts no subscription", count, binds())
            assertEquals("$key does not control playback", playing, player.playWhenReady)
            compose.onNodeWithTag("playback-options-title").assertDoesNotExist()
            compose.onNodeWithTag("player-actions").assertDoesNotExist()
        }
        assertFalse(startupKeys.dispatch(startupKeyContract, android.view.KeyEvent.KEYCODE_BACK, 0))
        // Android dispatches unclaimed Back to this owner, not to an invisible player root.
        @Suppress("UNCHECKED_CAST")
        val activityRule = compose as androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, androidx.activity.ComponentActivity>
        compose.runOnIdle { activityRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertEquals(0, startupScreens())
        compose.onNodeWithTag("home-header").assertExists()
    }

    @Test fun aServerRestartWhileBrowsingRetunesTheWarmChannelOnce() {
        app(ready(catalog), holdLive = true)
        confirmRow(2, awaitWait = false)
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        // A player that is not failed shows its banner: the first Back hides it, the second leaves.
        compose.pressKey(Key.Back)
        compose.pressKey(Key.Back)
        settle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        session.publish(connecting(catalog))
        settle()
        session.publish(ready(catalog))
        settle()
        compose.awaitScheduledWork(profileDispatcher) { binds() == 2 && runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        // Returning to the player adopts the retuned channel.
        compose.pressKey(Key.DirectionCenter)
        settle()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("channel-row-2")).fetchSemanticsNodes().isEmpty() }
        settle()
        assertEquals(2, binds())
    }

    @Test fun reopeningThePlayerAsTheServerReturnsStillRetunesTheWarmChannelOnce() {
        app(ready(catalog), holdLive = true)
        confirmRow(2, awaitWait = false)
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        // A player that is not failed shows its banner: the first Back hides it, the second leaves.
        compose.pressKey(Key.Back)
        compose.pressKey(Key.Back)
        settle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        session.publish(connecting(catalog))
        settle()
        // The retune waits for its settings while OK opens the player: browse leaves before it is done.
        val gate = CompletableDeferred<Unit>().also { playerData.readGate = it }
        session.publish(ready(catalog))
        settle()
        assertEquals(1, binds())
        compose.pressKey(Key.DirectionCenter)
        settle()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("channel-row-2")).fetchSemanticsNodes().isEmpty() }
        playerData.readGate = null
        gate.complete(Unit)
        settle()
        compose.awaitScheduledWork(profileDispatcher) { binds() == 2 && runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertEquals(2, binds())
    }

    @Test fun twoOksInOneBurstOpenOnlyTheSecondChannel() {
        app()
        // Both confirms run before either request's coroutine pushes its player.
        val confirms = listOf(1L, 2L).map { id ->
            requireNotNull(compose.onNodeWithTag("channel-row-$id").fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        }
        compose.runOnUiThread { confirms.forEach { it() } }
        settle()
        compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true).assertExists()
        assertEquals(listOf("2 Name 2"), compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        session.publish(ready(catalog))
        settle()
        compose.awaitScheduledWork(profileDispatcher) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        assertEquals(1, binds())
        // Ordinary player chrome handles the first Back; the second leaves the one player.
        compose.pressKey(Key.Back)
        settle()
        compose.pressKey(Key.Back)
        settle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun startupScreens() = compose.onAllNodes(hasTestTag("main-startup-root")).fetchSemanticsNodes().size

    private fun statusTexts() =
        compose.onAllNodes(hasTestTag("main-startup-status"), useUnmergedTree = true).fetchSemanticsNodes().size

    /** Both clocks: frames, and the Handler clock startup's deadlines use. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun advance(millis: Long) {
        repeat((millis / 50).toInt()) {
            profileDispatcher.scheduler.runCurrent()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            compose.mainClock.advanceTimeBy(50)
        }
        compose.waitForIdle()
    }

    /** A cold start without saved channels. */
    private fun coldApp(initial: SessionObservation) {
        app(initial, awaitRows = false)
    }

    /** Home is the landing destination; these journeys browse the channel rows. */
    private fun openChannels() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("nav-channels")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-channels")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun confirmRow(id: Long, awaitWait: Boolean = true, confirm: Boolean = true) {
        repeat(10) {
            val focusedRow = (1..3L).firstOrNull { row ->
                compose.onAllNodes(hasTestTag("channel-row-$row") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            when {
                focusedRow == id -> return@repeat
                focusedRow != null && focusedRow > id -> compose.pressKey(Key.DirectionUp)
                else -> compose.pressKey(Key.DirectionDown)
            }
        }
        compose.onNodeWithTag("channel-row-$id").assertIsFocused()
        if (!confirm) return
        compose.pressKey(Key.DirectionCenter)
        settle()
        if (!awaitWait) return
        compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true).assertExists()
        settle()
    }

    private fun binds() = session.calls.count { it == FakeSessionCall.BIND_LIVE_PLAYBACK }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun settle() {
        repeat(10) {
            profileDispatcher.scheduler.runCurrent()
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private fun connecting(channels: ChannelCatalog) = SessionObservation.create(
        sessionState = SessionState.Connecting,
        channelState = ChannelRepositoryState.Stale(channels),
    )

    private fun ready(channels: ChannelCatalog) = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(channels),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    /** By default saved channels before the session is ready: the warm start of D26. */
    private fun app(
        initial: SessionObservation = connecting(catalog),
        awaitRows: Boolean = true,
        profile: ServerProfileReadResult = ServerProfileReadResult.anonymous(at.bernhardberger.tvheadend.sdk.core.ServerProfileId.generate(), "offline.invalid", 9982),
        tagData: InMemoryData = InMemoryData(),
        holdLive: Boolean = false,
        autoplay: Boolean = false,
        persistedChannel: ChannelId? = null,
        initialReadGate: CompletableDeferred<Unit>? = null,
        bootstrapHandle: androidx.lifecycle.SavedStateHandle? = null,
        bootstrapReadGate: CompletableDeferred<Unit>? = null,
        earlyBootstrapLive: Boolean = false,
        bootstrapAutoplay: Boolean = false,
    ) {
        session = FakeTvheadendSession(initial).apply {
            // The fake's stream otherwise fails at once, and a failed player is not a warm channel.
            if (holdLive) scriptLivePlaybackSuccess(SubscriptionOpener { channel, _, _ ->
                activeSubscriptions.add(channel)
                try {
                    awaitCancellation()
                } finally {
                    activeSubscriptions.remove(channel)
                }
            })
            else scriptLivePlaybackSuccess()
        }
        val data = InMemoryData().also { playerData = it }
        val settings = PlayerSettingsStore(data)
        val profiles = AppProfileOwner(session, FakeServerProfileStore(profile),
            settings, profileDispatcher, readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        // Establish the active profile before setChannelId; without it the store does not write.
        profileDispatcher.scheduler.runCurrent()
        val lastPlayed = LastPlayedChannelStore(data)
        if (persistedChannel != null) runBlocking { lastPlayed.setChannelId(persistedChannel) }
        data.readGate = initialReadGate
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val controlled = at.bernhardberger.tvhplayer.testutil.observePlayerListeners(player, playerListeners)
        runtime = AppPlaybackRuntime(controlled, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings(),
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val uiData = InMemoryData()
        val uiStore = UiSettingsStore(uiData).apply {
            runBlocking { setChannelsView(ChannelsView(ChannelsLayout.LIST)) }
            runBlocking { setAutoStartPlayback(bootstrapAutoplay) }
        }
        uiData.readGate = bootstrapReadGate
        val dependencies = module {
            includes(at.bernhardberger.tvhplayer.testutil.playerStatsTestModule())
            single<CoroutineScope>(qualifier = org.koin.core.qualifier.named("application")) { scope }
            single<TvheadendSession> { session }
            single { runtime }
            single { profiles }
            single { settings }
            single { ChannelTagSettingsStore(tagData) }
            // These journeys walk the list's rows.
            single { uiStore }
            viewModel {
                at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel(
                    get(), get(), bootstrapHandle ?: androidx.lifecycle.SavedStateHandle())
            }
            single { lastPlayed }
            single { ChannelSelectionStore() }
            single { GuidePositionStore() }
            single { notices }
            single { ConnectionNoticeSource(session.observation, profiles.configurationGeneration, notices) }
            viewModel { AppConnectionViewModel(get(), get(), get()) }
            viewModel { ChannelsViewModel(get(), get(), get<AppProfileOwner>().liveProfile) }
            viewModel { VideoPlayerViewModel(get(), get()) }
            viewModel { SettingsStorageViewModel(session.cache, get(), ChannelAccentStore()) {} }
            viewModel { SettingsPlayerViewModel(get(), get(), get(), get(), get()) }
        }
        val server = ServerSettings(host = "offline.invalid")
        launchRequests = ApplianceLaunchRequests().apply { requestStartup(autoplay) }
        val fixtureFreshStart = androidx.compose.runtime.mutableStateOf(true)
        // Startup timing is checked against the frame and Handler clocks.
        if (!awaitRows) compose.mainClock.autoAdvance = false
        compose.setContent {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    val bootstrap = if (bootstrapHandle != null)
                        org.koin.androidx.compose.koinViewModel<at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel>(
                            key = "bootstrap-$bootstrapGeneration")
                        else null
                    androidx.compose.runtime.LaunchedEffect(bootstrap) {
                        if (bootstrap != null) {
                            bootstrapOwner = bootstrap
                            if (earlyBootstrapLive) bootstrap.applianceLaunchRequests.requestEntry(
                                at.bernhardberger.tvhplayer.core.ApplianceEntryDestination.LIVE, false)
                        }
                    }
                    AppRoot(
                        registerActivityKeyContract = { registered ->
                            startupKeyContract = registered
                            { startupKeyContract = MainStartupActivityKeyContract(
                                at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode.Inactive) }
                        },
                        startupState = bootstrap?.state?.collectAsStateWithLifecycle()?.value
                            ?: MainStartupState.Ready(server, autoStartPlayback = autoplay),
                        runtimeServerSettings = server,
                        applianceLaunchRequests = bootstrap?.applianceLaunchRequests ?: launchRequests,
                        freshStart = bootstrap?.freshStart?.collectAsStateWithLifecycle()?.value
                            ?: fixtureFreshStart.value,
                        onStartupFinished = {
                            if (bootstrap != null) bootstrap.finishFreshStart()
                            else fixtureFreshStart.value = false
                        },
                        onPlayerVisibilityChanged = {},
                        onRequestExit = {},
                    )
                }
            }
        }
        if (!awaitRows) return
        openChannels()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().isNotEmpty()
        }
        settle()
        // These journeys exercise channel selection, not Home's initial focus or rail traversal.
        compose.onNodeWithTag("channel-row-1")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(hasTestTag("nav-channels") and isFocused()).fetchSemanticsNodes().isNotEmpty()) {
            compose.pressKey(Key.DirectionRight)
        }
    }
    private val playerListeners = mutableListOf<androidx.media3.common.Player.Listener>()

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        /** Holds every read that starts while set, as a slow store would. */
        var readGate: CompletableDeferred<Unit>? = null
        var failNextRead = false
        override val data: Flow<Preferences> get() = flow {
            readGate?.await()
            if (failNextRead) {
                failNextRead = false
                throw IOException("Settings read failed")
            }
            emitAll(state)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }
}
