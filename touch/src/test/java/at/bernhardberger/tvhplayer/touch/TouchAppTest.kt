package at.bernhardberger.tvhplayer.touch

import android.content.pm.ApplicationInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.robolectric.shadows.ShadowLog

import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryTestFactory
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.ContentGenre
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStoreCall
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.di.touchModule
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.channelRow
import at.bernhardberger.tvhplayer.touch.channels.rememberChannelsTexts
import at.bernhardberger.tvhplayer.touch.channels.channelsFixture
import at.bernhardberger.tvhplayer.touch.guide.GuideClock
import at.bernhardberger.tvhplayer.touch.player.LivePlayerState
import at.bernhardberger.tvhplayer.touch.player.LiveTimeline
import at.bernhardberger.tvhplayer.touch.player.PlayerControl
import at.bernhardberger.tvhplayer.touch.player.PlayerDvrConfirmation
import at.bernhardberger.tvhplayer.touch.player.PlayerScreen
import at.bernhardberger.tvhplayer.touch.player.ShownPrograms
import at.bernhardberger.tvhplayer.touch.player.choose
import at.bernhardberger.tvhplayer.touch.player.liveRecordOffer
import at.bernhardberger.tvhplayer.touch.player.playerSchedule
import at.bernhardberger.tvhplayer.touch.player.withShownPrograms
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
import at.bernhardberger.tvhplayer.touch.testutil.FakeConnection
import at.bernhardberger.tvhplayer.touch.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.touch.testutil.MemoryPreferences
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.ui.ConfirmDvrChange
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import at.bernhardberger.tvhplayer.touch.ui.TouchDarkColors
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import java.time.ZoneId
import kotlin.time.toJavaInstant
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

/** The app's pages on the SDK's fake session and profile store: first run, the frame and Connections. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34], qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi",
    instrumentedPackages = [
        "at.bernhardberger.tvhplayer.touch.TouchAppKt",
        "at.bernhardberger.tvhplayer.ui.common.TimeConverterKt",
    ],
)
@OptIn(TvheadendTestingApi::class)
class TouchAppTest {
    private val motionScale = object : MotionDurationScale { override var scaleFactor = 1f }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = motionScale)
    @get:Rule val images = ImageLoaderRule(
        Interceptor { chain ->
            val id = (chain.request.data as? AppArtworkSource)?.id
            val art = when (id) {
                captureLogo -> TouchFixtureArt.picon("northline-news")
                capturePicture -> TouchFixtureArt.art("northline-tonight")
                else -> null
            }
            if (art != null) SuccessResult(art.asImage(), chain.request, DataSource.MEMORY)
            else ErrorResult(null, chain.request, IllegalStateException("No fixture art for this model"))
        },
    )

    private var connection = FakeConnection()
    private val server = DiscoveredTvheadendServer("Tvheadend", "192.168.1.20", 9982)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Before fun setClock() {
        assertTrue(SystemClock.setCurrentTimeMillis(FIXTURE_NOW.toEpochMilliseconds()))
    }

    @After fun close() = connection.close()

    @Test
    fun playerAndBrowseShareTheSelectedChannelScope() {
        val guide = ServerGuide(FIXTURE_NOW)
        val dependencies = module {
            single<TvheadendSession> { connection.session }
            single { connection.owner }
            single { connection.channelTagSettings() }
        }
        val graph = koinApplication(createEagerInstances = false) { modules(touchModule, dependencies) }
        try {
            runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
            connection.start()
            val browse = compose.runOnIdle { graph.koin.get<ChannelsViewModel>() }
            val player = compose.runOnIdle { graph.koin.get<ChannelsViewModel>() }
            compose.runOnIdle { connection.session.publish(guide.connected()) }
            compose.runOnIdle { browse.selectTag(guide.news.id) }
            compose.runOnIdle {
                assertEquals(guide.news.id, player.scope.value.scope.activeTagId)
                assertEquals(listOf(guide.first.id), player.scope.value.scope.visibleChannels.map { it.id })
                player.selectTag(null)
                assertEquals(null, browse.scope.value.scope.activeTagId)
            }
        } finally {
            compose.runOnIdle { graph.close() }
        }
    }

    @Test
    fun firstRunSavesThePassedConnectionAndOpensChannels() {
        signInToFoundServer()
        connection.session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        connection.session.publish(ready())

        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        val saved = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf(server.host), saved.profiles.map { it.host })
        assertEquals(saved.profiles.single().id, saved.activeId)
        // Nothing stays behind the frame: Back leaves the app.
        back()
        assertTrue(compose.activity.isFinishing)
    }

    @Test
    fun refusedSignInMarksTheAccountFields() {
        signInToFoundServer()
        connection.session.publish(
            SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AUTHENTICATION_REJECTED)),
        )

        val refused = SemanticsMatcher.expectValue(SemanticsProperties.Error, label(R.string.connect_refused))
        field(SharedR.string.username).assert(refused)
        field(SharedR.string.password).assert(refused)
        val stored = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertTrue(stored.profiles.isEmpty())
    }

    @Test
    fun removingTheConnectionInUseReturnsToFirstRun() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        compose.onNodeWithContentDescription(label(SharedR.string.server_remove)).performClick()
        compose.onNodeWithText(label(R.string.connect_remove)).performClick()

        compose.onNode(heading and hasText(label(SharedR.string.onboarding_find_title))).assertIsDisplayed()
        connectToFoundServer()
        connection.session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        connection.session.publish(ready())
        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertDoesNotExist()
        back()
        assertTrue(compose.activity.isFinishing)
    }

    @Test
    fun aFailedRemovalKeepsTheConnection() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        connection.store.scriptMutationUnavailable()
        connection.writesHeld.value = true
        compose.onNodeWithContentDescription(label(SharedR.string.server_remove)).performClick()
        compose.onNodeWithText(label(R.string.connect_remove)).performClick()
        compose.waitForIdle()
        connection.writesHeld.value = false

        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()
        compose.onNodeWithText("Home server").assertIsDisplayed()
        val stored = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf(server.host), stored.profiles.map { it.host })
    }

    @Test
    fun dialogFailureRunsTheTestAgain() {
        signInToFoundServer()
        unavailable(SessionFailure.NETWORK_UNAVAILABLE)
        compose.onNode(isDialog() and hasAnyDescendant(hasText(label(R.string.connect_network_title)))).assertExists()

        compose.onNodeWithText(label(SharedR.string.connection_try_again)).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        // The retry's own attempt: the earlier outcome is not counted again.
        connection.session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        connection.session.publish(ready())

        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        val saved = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf(server.host), saved.profiles.map { it.host })
    }

    @Test
    fun aFailedSaveSaysSoAndConnectTriesAgain() {
        connection.store.scriptMutationUnavailable()
        show()
        compose.onNode(hasClickAction() and hasText(server.name)).performClick()
        field(SharedR.string.server_name_field).performTextInput("Living room")
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()
        connection.session.publish(ready())

        compose.onNodeWithText(label(SharedR.string.connection_save_failed)).assertIsDisplayed()
        // Nothing of the failed save is kept, neither the connection nor its name.
        connection.store.scriptMutationSuccess()
        val failed = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertTrue(failed.profiles.isEmpty())
        val retried = connection.store.calls.size
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()

        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        val saved = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf(server.host to "Living room"), saved.profiles.map { it.host to it.label })
        // The retry writes the connection with its name at once: one mutation, no rename after it.
        assertEquals(
            listOf(FakeServerProfileStoreCall.ADD_ANONYMOUS),
            connection.store.calls.drop(retried).filter { it in writes },
        )
    }

    @Test
    fun recreationDuringTheFirstSaveStillAppliesTheName() {
        show()
        compose.onNode(hasClickAction() and hasText(server.name)).performClick()
        field(SharedR.string.server_name_field).performTextInput("Living room")
        connection.writesHeld.value = true
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()
        connection.session.publish(ready())
        compose.waitForIdle()

        recreate()
        connection.writesHeld.value = false

        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        val saved = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf("Living room"), saved.profiles.map { it.label })
        // The name goes with the connection in its one write.
        assertEquals(listOf(FakeServerProfileStoreCall.ADD_ANONYMOUS), connection.store.calls.filter { it in writes })
    }

    @Test
    fun anUnreadListAfterTheEditorsWriteKeepsTheEditorForTheRetry() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        val id = (runBlocking { connection.store.profiles() } as ServerProfileListResult.Available).activeId
        show()
        openEditor()
        field(SharedR.string.onboarding_server_address).performTextReplacement("192.168.1.21")
        connection.failReadAfterNextWrite()
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()
        connection.session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        connection.session.publish(ready())

        compose.onNodeWithText(label(SharedR.string.connection_save_failed)).assertIsDisplayed()
        val retried = connection.store.calls.size
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()

        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()
        compose.onNodeWithText("192.168.1.21").assertIsDisplayed()
        assertEquals(
            listOf(FakeServerProfileStoreCall.UPDATE_ANONYMOUS),
            connection.store.calls.drop(retried).filter { it in writes },
        )
        val saved = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf(id to "192.168.1.21"), saved.profiles.map { it.id to it.host })
    }

    @Test
    fun firstRunInANewActivityCompletesAConnectionStoredButNotReadBack() {
        show()
        compose.onNode(hasClickAction() and hasText(server.name)).performClick()
        connection.failReadAfterNextWrite()
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()
        connection.session.publish(ready())
        compose.onNodeWithText(label(SharedR.string.connection_save_failed)).assertIsDisplayed()

        // The activity finishes and opens again while the process lives: a new first run, nothing it knew.
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        recreate()
        val replaced = connection.store.calls.size
        compose.onNode(hasClickAction() and hasText(server.name)).performClick()
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()
        connection.session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        connection.session.publish(ready())

        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        val saved = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf(server.host), saved.profiles.map { it.host })
        assertEquals(
            listOf(FakeServerProfileStoreCall.UPDATE_ANONYMOUS),
            connection.store.calls.drop(replaced).filter { it in writes },
        )
    }

    @Test
    fun unreachableContinueAnywaySavesTheUntestedConnection() {
        signInToFoundServer()
        unavailable(SessionFailure.SERVER_UNREACHABLE)
        connection.store.scriptMutationUnavailable()
        compose.onNodeWithText(label(SharedR.string.onboarding_continue_anyway)).performClick()

        // A failed write says so on the form, without the network question again.
        compose.onNodeWithText(label(SharedR.string.connection_save_failed)).assertIsDisplayed()
        compose.onNode(isDialog()).assertDoesNotExist()
        connection.store.scriptMutationSuccess()
        // Connect writes again; a new test would wait for the server, which says nothing more here.
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()

        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        val saved = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf(server.host), saved.profiles.map { it.host })
    }

    @Test
    fun notTvheadendMarksTheAddressPair() {
        signInToFoundServer()
        unavailable(SessionFailure.INCOMPATIBLE_SERVER)

        val error = SemanticsMatcher.expectValue(SemanticsProperties.Error, label(R.string.connect_not_tvheadend))
        field(SharedR.string.onboarding_server_address).assert(error)
        field(R.string.connect_port).assert(error)
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun connectionsCardFollowsTheConnection() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        compose.onNodeWithText(label(SharedR.string.connection_overview_connecting)).assertExists()

        unavailable(SessionFailure.SERVER_UNREACHABLE)
        compose.onNodeWithText(label(SharedR.string.error_101_title)).assertExists()
        compose.onNodeWithText(label(SharedR.string.connection_overview_connecting)).assertDoesNotExist()
    }

    @Test
    fun signInNeededOpensTheEditorWithThePasswordRequired() {
        openSignInNeeded(forgetUsername = false)

        field(SharedR.string.password).assertIsFocused()
        field(SharedR.string.username).assert(hasText("alex"))
        compose.onNodeWithText(label(SharedR.string.onboarding_password_saved)).assertDoesNotExist()
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()
        val required = SemanticsMatcher.expectValue(SemanticsProperties.Error, label(SharedR.string.onboarding_sign_in_pair))
        field(SharedR.string.password).assert(required)
        // Cancel leaves the Connections page with the editor.
        compose.onNodeWithContentDescription(label(SharedR.string.close)).performClick()
        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
    }

    @Test
    fun signInNeededWithoutTheUsernameDoesNotSaveWithoutSignIn() {
        // Every way into the editor: Edit on the Connections page, then the sign-in action.
        val id = unreadableSignIn(forgetUsername = true)
        openEditor()
        refusesToSaveWithoutSignIn(id)
        compose.onNodeWithContentDescription(label(SharedR.string.close)).performClick()
        back()
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_connection_settings))).performClick()
        refusesToSaveWithoutSignIn(id)
    }

    private fun refusesToSaveWithoutSignIn(id: ServerProfileId) {
        field(SharedR.string.username).assert(hasText(""))
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()

        val required = SemanticsMatcher.expectValue(SemanticsProperties.Error, label(SharedR.string.onboarding_sign_in_pair))
        field(SharedR.string.username).assert(required)
        compose.onNodeWithText(compose.activity.getString(SharedR.string.onboarding_connecting_title, server.host))
            .assertDoesNotExist()
        val stored = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(ServerProfileAuthenticationMode.PASSWORD, stored.profiles.single { it.id == id }.authenticationMode)
    }

    @Test
    fun aDiscardedSignInIsGoneWhenTheEditorOpensAgain() {
        openSignInNeeded(forgetUsername = false)
        field(SharedR.string.password).performTextInput("draft")
        discard()

        compose.onNode(hasClickAction() and hasText(label(R.string.channels_connection_settings))).performClick()
        field(SharedR.string.username).assert(hasText("alex"))
        compose.onNodeWithContentDescription(label(SharedR.string.show_password)).assertDoesNotExist()
    }

    @Test
    fun aTestRunningWhenTheEditorIsDiscardedSavesNothing() {
        openSignInNeeded(forgetUsername = false)
        val seeded = connection.store.calls.size
        field(SharedR.string.password).performTextInput("secret")
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()
        discard()

        connection.session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        connection.session.publish(ready())
        compose.waitForIdle()
        assertTrue(connection.store.calls.drop(seeded).none { it in writes })
    }

    @Test
    fun theEditorKeepsATypedSignInThroughRecreation() {
        // The fake's editing read gives the saved username but never a saved password.
        connection = FakeConnection(readForEditing = ServerProfileEditReadResult.Unavailable)
        runBlocking { connection.store.addPassword(server.host, server.port, "alex", "old", "Home server", true) }
        show()
        openEditor()
        field(SharedR.string.username).performTextReplacement("sam")
        field(SharedR.string.password).performTextInput("new")

        recreate()
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()
        connection.session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        connection.session.publish(ready())

        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()
        assertTrue("The typed sign-in was not the one saved", connection.savedSignIn("sam", "new"))
    }

    @Test
    fun aRunningSaveCannotBeDiscarded() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        openEditor()
        field(SharedR.string.onboarding_server_address).performTextReplacement("192.168.1.21")
        connection.writesHeld.value = true
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()
        connection.session.publish(ready())
        compose.waitForIdle()

        compose.onNodeWithContentDescription(label(SharedR.string.close)).assertIsNotEnabled().performClick()
        compose.onNodeWithText(label(SharedR.string.autorec_editor_discard)).assertDoesNotExist()
        connection.writesHeld.value = false

        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()
        compose.onNodeWithText("192.168.1.21").assertIsDisplayed()
    }

    @Test
    fun aSaveThatStartsUnderTheDiscardPromptKeepsTheEditor() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        openEditor()
        field(SharedR.string.onboarding_server_address).performTextReplacement("192.168.1.21")
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()
        compose.onNodeWithContentDescription(label(SharedR.string.close)).performClick()
        val confirm = compose.onNode(hasClickAction() and hasText(label(SharedR.string.autorec_editor_discard)))
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        connection.writesHeld.value = true

        // The test passes and its save starts; a tap on the prompt lands before the next frame removes it.
        connection.session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        compose.mainClock.autoAdvance = false
        connection.session.publish(ready())
        compose.runOnUiThread {
            shadowOf(Looper.getMainLooper()).idle()
            confirm()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        // The editor stays, with the change being written, until the save ends.
        field(SharedR.string.onboarding_server_address).assertExists()
        connection.writesHeld.value = false
        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()
        compose.onNodeWithText("192.168.1.21").assertIsDisplayed()
    }

    @Test
    fun channelsAppearAfterConnectingWithoutUnwiredControls() {
        val guide = savedConnectionWithGuide()
        val next = compose.onNodeWithText(guide.programs[1], substring = true)
        compose.onNodeWithText(guide.first.name!!).assertIsDisplayed()
        compose.onNodeWithText(guide.second.name!!).assertIsDisplayed()
        compose.onNodeWithText(guide.programs[0]).assertIsDisplayed()
        next.assertIsDisplayed()

        compose.onNodeWithText(label(R.string.nav_epg)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.nav_recordings)).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText(guide.first.name!!)).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.channels_watch_named, guide.first.name),
        ).assertDoesNotExist()
        // Browse asks for the channel logo but not the current program's artwork.
        val session = connection.session.observation.value.currentSession
        val logo = AppArtworkSource(session, (guide.logo as ChannelIcon.ImageCache).artworkId)
        assertTrue(images.requested.toString(), logo in images.requested)
        assertTrue(AppArtworkSource(session, guide.picture) !in images.requested)

        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        recreate()
        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()
        back()
        next.assertIsDisplayed()

        val connections = compose.onNode(heading and hasText(label(SharedR.string.server_list_title)))
        val openConnections = compose.onNodeWithContentDescription(label(R.string.channels_connection_settings))
        compose.mainClock.autoAdvance = false
        openConnections.performClick()
        advance(64)
        connections.assertExists()
        next.assertExists()
        back()
        compose.mainClock.autoAdvance = true
        connections.assertDoesNotExist()
        next.assertIsDisplayed()
        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
    }

    @Test
    fun channelsIgnoreEpgUpdatesOutsideTheirRowsButShowAChangedCurrentProgram() {
        val info = compose.activity.applicationInfo
        val oldFlags = info.flags
        info.flags = oldFlags or ApplicationInfo.FLAG_DEBUGGABLE
        ShadowLog.clear()
        fun mappings() = ShadowLog.getLogsForTag("TouchArtwork").count { it.msg.startsWith("mapped Channels ") }
        try {
            val now = Instant.parse("2027-01-16T12:00:05Z")
            assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds()))
            val guide = savedConnectionWithGuide(now)
            compose.onNodeWithText(guide.programs[0], substring = true).assertIsDisplayed()
            val initial = mappings()
            assertTrue(initial > 0)
            compose.runOnIdle { connection.session.publish(guide.connected(epg = guide.refreshedEpg())) }
            compose.runOnIdle { assertEquals("No map for an event outside the shown rows", initial, mappings()) }
            compose.runOnIdle {
                connection.session.publish(guide.connected(epg = guide.refreshedEpg("Updated News")))
            }
            compose.onNodeWithText("Updated News", substring = true).assertIsDisplayed()
            compose.runOnIdle { assertEquals("One mapping for one visible publication", initial + 1, mappings()) }

            openGuide()
            openRecordings()
            back()
            compose.runOnIdle { assertEquals("An unchanged return reuses the last mapping", initial + 1, mappings()) }

            openGuide()
            compose.runOnIdle { connection.session.publish(guide.connected(epg = guide.refreshedEpg("Hidden News"))) }
            openRecordings()
            compose.runOnIdle {
                connection.session.publish(guide.connected(epg = guide.refreshedEpg("Latest News")))
            }
            compose.runOnIdle { assertEquals("Neither hidden destination starts a mapping", initial + 1, mappings()) }
            back()
            compose.onNodeWithText("Latest News", substring = true).assertIsDisplayed()
            compose.runOnIdle { assertEquals("Only the latest hidden update is mapped", initial + 2, mappings()) }
        } finally {
            info.flags = oldFlags
        }
    }

    @Test
    fun channelsMapAtAMinuteAndAProgrammeBoundaryButNotBetween() {
        val info = compose.activity.applicationInfo
        val oldFlags = info.flags
        info.flags = oldFlags or ApplicationInfo.FLAG_DEBUGGABLE
        ShadowLog.clear()
        fun mappings() = ShadowLog.getLogsForTag("TouchArtwork").filter { it.msg.startsWith("mapped Channels ") }
        val now = Instant.parse("2027-01-16T12:00:50Z")
        try {
            assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds()))
            // Evening News ends at 12:01:07, not on the minute.
            val guide = savedConnectionWithGuide(Instant.parse("2027-01-16T11:31:07Z"))
            compose.onNodeWithText(guide.programs[0], substring = true).assertIsDisplayed()
            val initial = mappings().size
            fun advance(seconds: Long, framesMs: Long) {
                assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds() + seconds * 1000))
                compose.mainClock.advanceTimeBy(framesMs)
            }
            advance(5, 5_016)
            compose.runOnIdle { assertEquals("A progress tick is not a mapping", initial, mappings().size) }
            advance(10, 5_016)
            compose.runOnIdle {
                assertEquals("The minute maps once", initial + 1, mappings().size)
                assertTrue(mappings().last().msg.contains("reason=minute"))
            }
            advance(17, 7_016)
            compose.onNodeWithText(guide.programs[0], substring = true).assertDoesNotExist()
            compose.onNodeWithText(guide.programs[1], substring = true).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("The off-minute boundary maps once", initial + 2, mappings().size)
                assertTrue(mappings().last().msg.contains("reason=boundary"))
            }

            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            advance(77, 60_016)
            compose.runOnIdle {
                assertEquals("The stopped activity must not map at a clock wake", initial + 2, mappings().size)
            }
            connection.session.publish(guide.connected(epg = guide.refreshedEpg()))
            connection.session.publish(guide.connected(emptyList()))
            compose.runOnIdle {
                assertEquals("The stopped activity must not map a publication", initial + 2, mappings().size)
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle { assertEquals("Resume combines clock and data into one pass", initial + 3, mappings().size) }
        } finally {
            info.flags = oldFlags
        }
    }

    @Test
    fun channelsShowsKeptRowsBeforeMappingCanRunAgain() {
        val dispatcher = mutableStateOf<CoroutineDispatcher>(Dispatchers.Main.immediate)
        val guide = savedConnectionWithGuide(mappingDispatcher = { dispatcher.value })
        val held = StandardTestDispatcher()
        fun holdMapping() = compose.runOnIdle { dispatcher.value = held }
        fun releaseMapping() = compose.runOnIdle { dispatcher.value = Dispatchers.Main.immediate }
        val loading = compose.onNodeWithText(label(R.string.channels_loading))
        val original = compose.onNodeWithText(guide.programs[0], substring = true)
        val mappedAfterReturn = compose.onNodeWithText("Back to Channels", substring = true)
        original.assertIsDisplayed()

        openRecordings()
        holdMapping()
        connection.session.publish(guide.connected(epg = guide.refreshedEpg("Back to Channels")))
        back()
        loading.assertDoesNotExist()
        original.assertIsDisplayed()
        releaseMapping()
        mappedAfterReturn.assertIsDisplayed()

        holdMapping()
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        // Re-enter with a publication queued while stopped and no mapping dispatcher available.
        connection.session.publish(guide.connected(epg = guide.refreshedEpg("After Stop")))
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        loading.assertDoesNotExist()
        mappedAfterReturn.assertIsDisplayed()
        releaseMapping()
        val mappedAfterStop = compose.onNodeWithText("After Stop", substring = true)
        mappedAfterStop.assertIsDisplayed()

        holdMapping()
        recreate()
        loading.assertDoesNotExist()
        mappedAfterStop.assertIsDisplayed()

        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()
        back()
        loading.assertDoesNotExist()
        mappedAfterStop.assertIsDisplayed()

        openGuide()
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_all))).performClick()
        compose.onNode(hasClickAction() and hasText(guide.news.name!!)).performClick()
        back()
        compose.onNodeWithText(guide.second.name!!).assertDoesNotExist()
        loading.assertIsDisplayed()
        releaseMapping()
        mappedAfterStop.assertIsDisplayed()
        compose.onNodeWithText(guide.second.name!!).assertDoesNotExist()
    }

    @Test
    fun channelsHideKeptRowsWhenTheSessionIsReplaced() {
        val dispatcher = mutableStateOf<CoroutineDispatcher>(Dispatchers.Main.immediate)
        val guide = savedConnectionWithGuide(mappingDispatcher = { dispatcher.value })
        val channel = compose.onNodeWithText(guide.first.name!!)
        channel.assertIsDisplayed()

        val held = StandardTestDispatcher()
        compose.runOnIdle { dispatcher.value = held }
        compose.runOnIdle { connection.session.replaceGeneration(guide.connected()) }
        channel.assertDoesNotExist()
        compose.onNodeWithText(label(R.string.channels_loading)).assertIsDisplayed()

        compose.runOnIdle { dispatcher.value = Dispatchers.Main.immediate }
        channel.assertIsDisplayed()
    }

    @Test
    fun aTagFiltersTheChannels() {
        val guide = savedConnectionWithGuide()
        compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.channels_filter, label(R.string.channels_all)),
        ).performClick()
        compose.onNode(hasClickAction() and hasText(guide.news.name!!)).performClick()

        compose.onNodeWithText(guide.first.name!!).assertIsDisplayed()
        compose.onNodeWithText(guide.second.name!!).assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun channelRowAndWatchButtonDoDifferentThingsOnPhone() {
        val watches = mutableListOf<String>()
        watchChannel = { watches += it }
        watchWay = { id, way, _ ->
            assertEquals(BrowseContextAction.WATCH, way)
            watches += id
        }
        val guide = savedConnectionWithGuide(withChannelArt = true)
        val name = guide.first.name!!
        val row = compose.onNode(hasClickAction() and hasText(name))
        val program = compose.onNode(hasClickAction() and hasText(label(R.string.channels_details)))
        val navigation = compose.onNode(hasClickAction() and hasText(label(R.string.nav_epg)))

        compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.channels_watch_named, name),
        ).performClick()
        assertEquals(listOf(guide.first.id.value.toString()), watches)
        row.performClick()
        compose.onNode(heading and hasText(name)).assertIsDisplayed()
        assertEquals("Opening the row does not tune", 1, watches.size)
        navigation.assertDoesNotExist()
        compose.captureTouch("channels-step", "phone-channel-page", TouchCanvas.PHONE)
        compose.onNodeWithContentDescription(label(SharedR.string.context_watch)).performClick()
        assertEquals(listOf(guide.first.id.value.toString(), guide.first.id.value.toString()), watches)

        val pageBack = compose.onNodeWithContentDescription(label(R.string.recordings_back))
        for (canvas in listOf(TouchCanvas.PHONE_LANDSCAPE, TouchCanvas.PHONE)) {
            recreate(canvas)
            pageBack.assertIsDisplayed()
            compose.onNode(heading and hasText(name)).assertIsDisplayed()
            row.assertDoesNotExist()
            if (canvas == TouchCanvas.PHONE) navigation.assertDoesNotExist() else navigation.assertIsDisplayed()
        }

        program.performClick()
        compose.onNodeWithText("About ${guide.programs[0]}").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.channels_show_in_guide)).assertDoesNotExist()
        back()
        program.assertIsDisplayed()
        pageBack.performClick()
        row.assertIsDisplayed()
        navigation.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun channelDetailsCoverTheWidePaneAndBackRestoresIt() {
        watchWay = { _, _, _ -> }
        val minute = Instant.parse("2027-01-16T11:31:00Z")
        assertTrue(SystemClock.setCurrentTimeMillis(minute.toEpochMilliseconds() + 7_000))
        val guide = savedConnectionWithGuide(minute, withChannelArt = true)
        val name = guide.first.name!!
        val paneHeading = compose.onNode(heading and hasText(name))
        paneHeading.assertIsDisplayed()
        val timeLeft = compose.onAllNodes(hasText("left", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        val rowTimeLeft = timeLeft.single { it.startsWith("•") }.removePrefix("•").trim()
        assertEquals("The pane shows the same time left as the picked row", rowTimeLeft,
            timeLeft.single { !it.startsWith("•") })
        compose.captureTouch("channels-step", "tablet-channels-pane", TouchCanvas.TABLET)
        val detailsBack = compose.onNodeWithContentDescription(label(R.string.recordings_back))
        val program = compose.onNode(hasClickAction() and hasText(label(R.string.channels_details)))
        program.performClick()
        detailsBack.assertIsDisplayed()
        compose.captureTouch("channels-step", "tablet-channel-details-over-pane", TouchCanvas.TABLET)
        back()
        paneHeading.assertIsDisplayed()
        detailsBack.assertDoesNotExist()

        program.performClick()
        compose.onNode(hasClickAction() and hasText(label(R.string.nav_channels))).performClick()
        paneHeading.assertIsDisplayed()
        detailsBack.assertDoesNotExist()

        compose.runOnIdle {
            playingChannel.value = guide.first.id.value.toString()
            behindLive.value = playingChannel.value
        }
        val menu = compose.onNodeWithContentDescription(label(R.string.watch_options))
        val live = compose.onNode(hasClickAction() and hasText(label(SharedR.string.context_watch_live)))
        menu.performClick()
        live.assertIsDisplayed()
        compose.captureTouch("channels-step", "tablet-channels-pane-menu-open", TouchCanvas.TABLET)
        compose.onNode(hasClickAction() and hasText(guide.second.name!!)).performClick()
        compose.onNode(heading and hasText(guide.second.name!!)).assertIsDisplayed()
        val row = compose.onNode(hasClickAction() and hasText(name))
        row.performClick()
        menu.assertIsDisplayed()
        live.assertDoesNotExist()

        val details = compose.onNodeWithText("About ${guide.programs[0]}")
        program.performSemanticsAction(SemanticsActions.OnClick)
        details.assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            recreate(TouchCanvas.TABLET_UPRIGHT)
            details.assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        back()
        paneHeading.assertIsDisplayed()
        row.assertDoesNotExist() // Back returns to the same channel, now drawn as a page.

        program.performSemanticsAction(SemanticsActions.OnClick)
        details.assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            recreate(TouchCanvas.TABLET)
            details.assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        val otherTag = ChannelTag.create(ChannelTagId(8), name = "Other channels")
        val other = Channel.create(guide.second.id, name = guide.second.name, tagIds = listOf(otherTag.id))
        val observation = guide.connected()
        compose.runOnIdle {
            connection.session.publish(
                SessionObservation.create(
                    sessionState = observation.sessionState,
                    channelState = ChannelRepositoryState.Current(
                        ChannelCatalog.create(listOf(guide.first, other), listOf(guide.news, otherTag)),
                    ),
                    epgState = observation.epgState,
                    dvrState = observation.dvrState,
                ),
            )
            browseChannels.selectTag(otherTag.id)
        }
        details.assertIsDisplayed()
        back()
        val otherRow = compose.onNode(hasClickAction() and hasText(other.name!!))
        otherRow.assertIsSelected()
        paneHeading.assertDoesNotExist()
        row.assertDoesNotExist()

        // Removing the filter must not resurrect the pick that left the list.
        compose.runOnIdle { browseChannels.selectTag(null) }
        recreate(TouchCanvas.TABLET_UPRIGHT)
        row.assertIsDisplayed()
        paneHeading.assertDoesNotExist()
        recreate(TouchCanvas.TABLET)
        otherRow.performSemanticsAction(SemanticsActions.OnClick)
        otherRow.assertIsSelected()
        compose.onNode(hasClickAction() and hasText(label(R.string.nav_channels)))
            .performSemanticsAction(SemanticsActions.OnClick)
        otherRow.assertIsSelected()
        back()
        assertTrue("With no Details above the picked pane, Back leaves the app", compose.activity.isFinishing)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun thePlayingChannelPageReturnsToPlayback() {
        playingChannel = mutableStateOf("1")
        val returned = mutableListOf<Pair<String, BrowseContextAction>>()
        watchWay = { id, way, _ -> returned += id to way }
        val guide = savedConnectionWithGuide(withChannelArt = true)
        val id = guide.first.id.value.toString()
        val row = compose.onNode(hasClickAction() and hasText(guide.first.name!!))
        val channel = compose.onNode(heading and hasText(guide.first.name!!))
        row.performClick()
        compose.captureTouch("channels-step", "phone-channel-page-playing", TouchCanvas.PHONE)
        compose.onNodeWithContentDescription(label(SharedR.string.player_continue_watching)).performClick()
        assertEquals(listOf(id to BrowseContextAction.CONTINUE_WATCHING), returned)

        compose.runOnIdle { behindLive.value = id }
        val menu = compose.onNodeWithContentDescription(label(R.string.watch_options))
        val live = compose.onNode(hasClickAction() and hasText(label(SharedR.string.context_watch_live)))
        menu.performClick()
        compose.captureTouch("channels-step", "phone-channel-page-menu-open", TouchCanvas.PHONE)
        live.performClick()
        assertEquals(id to BrowseContextAction.WATCH_LIVE, returned.last())

        menu.performClick()
        live.assertIsDisplayed()
        back()
        live.assertDoesNotExist()
        channel.assertIsDisplayed()
        back()
        channel.assertDoesNotExist()
        row.assertIsDisplayed()
    }

    @Test
    fun returningFromAChannelKeepsTheListUntilChannelsIsTappedAgain() {
        val guide = savedConnectionWithGuide()
        val extras = (3L..24L).map { id -> Channel.create(ChannelId(id), name = "Extra channel $id") }
        val observation = guide.connected()
        connection.session.publish(
            SessionObservation.create(
                sessionState = observation.sessionState,
                channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                    listOf(guide.first, guide.second) + extras, listOf(guide.news),
                )),
                epgState = observation.epgState,
            ),
        )
        compose.waitForIdle()
        val first = compose.onNode(hasClickAction() and hasText(guide.first.name!!))
        val name = extras.last().name!!
        val row = compose.onNode(hasClickAction() and hasText(name))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(extras.size + 1)
        row.performClick()
        compose.onNode(heading and hasText(name)).assertIsDisplayed()
        back()
        row.assertIsDisplayed()
        first.assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText(label(R.string.nav_channels))).performClick()
        first.assertIsDisplayed()

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(extras.size + 1)
        row.performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { browseChannels.selectTag(guide.news.id) }
        first.assertIsDisplayed()
        compose.onNode(heading and hasText(name)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun anUprightTabletOpensAChannelPageInsteadOfAPane() {
        val watches = mutableListOf<String>()
        watchChannel = { watches += it }
        watchWay = { _, _, _ -> }
        val guide = savedConnectionWithGuide(withChannelArt = true)
        val channel = compose.onNode(heading and hasText(guide.first.name!!))
        val row = compose.onNode(hasClickAction() and hasText(guide.first.name!!))
        channel.assertDoesNotExist()
        row.performClick()
        channel.assertIsDisplayed()
        row.assertDoesNotExist()
        compose.captureTouch("channels-step", "tablet-upright-channel-page", TouchCanvas.TABLET_UPRIGHT)

        val guideTab = compose.onNode(hasClickAction() and hasText(label(R.string.nav_epg)))
        compose.mainClock.autoAdvance = false
        try {
            guideTab.performSemanticsAction(SemanticsActions.OnClick)
            compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            guideTab.assertIsSelected()
            channel.assertIsDisplayed()
            back()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNode(hasClickAction() and hasText(label(R.string.nav_channels))).assertIsSelected()
        channel.assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.recordings_back)).performClick()
        row.assertIsDisplayed()
        channel.assertDoesNotExist()

        recreate(TouchCanvas.TABLET)
        row.assertIsSelected()
        channel.assertIsDisplayed()

        recreate(TouchCanvas.TABLET_UPRIGHT)
        row.assertIsDisplayed()
        channel.assertDoesNotExist()
        row.performSemanticsAction(SemanticsActions.OnClick)
        val pageBack = compose.onNodeWithContentDescription(label(R.string.recordings_back))
        pageBack.assertIsDisplayed()

        val zone = ZoneId.systemDefault()
        val start = GuideClock(guide.now.epochSeconds, zone).today.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val tomorrowPrograms = List(48) { index ->
            val begins = start + index * 1800L
            EpgEvent.create(
                EventId(800L + index), guide.first.id, Instant.fromEpochSeconds(begins),
                Instant.fromEpochSeconds(begins + 1800L), title = "Tomorrow programme $index",
            )
        }
        connection.session.publish(guide.connected(epg = EpgSnapshot.create(guide.snapshot.events + tomorrowPrograms)))
        val today = compose.onNodeWithText(label(SharedR.string.channel_schedule_today))
        val tomorrow = compose.onNodeWithText(label(SharedR.string.channel_schedule_tomorrow))
        val onNow = compose.onNodeWithText(label(R.string.channels_on_now))
        val outer = compose.onNodeWithTag("channel-page-scroll")
        val schedule = compose.onNodeWithTag("channel-program-rows")
        fun assertTomorrowAt(index: Int) {
            tomorrow.assertIsSelected()
            onNow.assertIsNotDisplayed()
            compose.onNodeWithText(tomorrowPrograms[index].title!!).assertIsDisplayed()
        }
        fun scrollPositions() = listOf(outer, schedule).map {
            it.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        }
        tomorrow.performSemanticsAction(SemanticsActions.OnClick)
        outer.performScrollToIndex(1)
        schedule.performScrollToIndex(12)
        assertTomorrowAt(12)
        val uprightPositions = scrollPositions()

        compose.mainClock.autoAdvance = false
        try {
            recreate(TouchCanvas.TABLET)
            row.assertIsSelected()
            pageBack.assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertTomorrowAt(12)
        assertEquals(uprightPositions, scrollPositions())
        schedule.performScrollToIndex(24)
        assertTomorrowAt(24)
        val widePositions = scrollPositions()
        recreate(TouchCanvas.TABLET_UPRIGHT)
        pageBack.assertIsDisplayed()
        assertTomorrowAt(24)
        assertEquals(widePositions, scrollPositions())

        back()
        row.performSemanticsAction(SemanticsActions.OnClick)
        today.assertIsSelected()
        onNow.assertIsDisplayed()
        connection.session.publish(guide.connected())
        recreate(TouchCanvas.TABLET)

        val other = compose.onNode(hasClickAction() and hasText(guide.second.name!!))
        other.performSemanticsAction(SemanticsActions.OnClick)
        other.assertIsSelected()
        recreate(TouchCanvas.TABLET_UPRIGHT)
        compose.onNode(heading and hasText(guide.second.name!!)).assertIsDisplayed()
        pageBack.assertIsDisplayed()
        back()
        other.assertIsDisplayed()
        recreate(TouchCanvas.TABLET)
        row.assertIsSelected()
        channel.assertIsDisplayed()

        val channelsTab = compose.onNode(hasClickAction() and hasText(label(R.string.nav_channels)))
        for ((canvas, width) in listOf(
            TouchCanvas.PHONE_LANDSCAPE_MEDIUM to "medium",
            TouchCanvas.PHONE_LANDSCAPE to "wide",
        )) {
            recreate(canvas)
            guideTab.performSemanticsAction(SemanticsActions.OnClick)
            guideTab.assertIsSelected()
            compose.onNodeWithTag("browse-app-mark").assertIsDisplayed()
            val channelsBounds = channelsTab.fetchSemanticsNode().boundsInRoot
            val guideBounds = guideTab.fetchSemanticsNode().boundsInRoot
            assertEquals("Rail items share a column", channelsBounds.left, guideBounds.left)
            assertTrue("Guide follows Channels down the rail", guideBounds.top > channelsBounds.bottom)
            channelsTab.performSemanticsAction(SemanticsActions.OnClick)
            row.assertIsDisplayed()
            channel.assertDoesNotExist()
            compose.captureTouch("channels-step", "phone-sideways-$width-channels", canvas)

            val watch = compose.onNodeWithContentDescription(
                compose.activity.getString(R.string.channels_watch_named, guide.first.name!!),
            )
            watch.performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(guide.first.id.value.toString(), watches.single())
            watches.clear()
            row.performSemanticsAction(SemanticsActions.OnClick)
            assertTrue("Opening a row does not tune", watches.isEmpty())
            pageBack.assertIsDisplayed()
            channel.assertIsDisplayed()
            row.assertDoesNotExist()
            guideTab.assertIsDisplayed()
            compose.captureTouch("channels-step", "phone-sideways-$width-channel-page", canvas)
            pageBack.performSemanticsAction(SemanticsActions.OnClick)
            row.assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = TABLET)
    fun resizingOpenChannelToOnePaneDoesNotSlideThePage() {
        val windowSize = mutableStateOf(DpSize(1280.dp, 800.dp))
        val guide = savedConnectionWithGuide(
            configurationOverride = { DeviceConfigurationOverride.WindowSize(windowSize.value) },
        )
        val row = compose.onNode(hasClickAction() and hasText(guide.first.name!!))
        val pageBack = compose.onNodeWithContentDescription(label(R.string.recordings_back))
        row.performSemanticsAction(SemanticsActions.OnClick)
        row.assertIsSelected()
        pageBack.assertDoesNotExist()

        val midResize = try {
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread {
                windowSize.value = DpSize(800.dp, 800.dp)
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(64)
            pageBack.assertIsDisplayed()
            pageBack.fetchSemanticsNode().positionInRoot.x
        } finally {
            compose.mainClock.autoAdvance = true
        }
        val settled = pageBack.fetchSemanticsNode().positionInRoot.x
        assertEquals("A resize draws the page without a horizontal slide", settled, midResize)
        row.assertDoesNotExist()
    }

    @Test
    fun offlineKeepsTheLastListUnderTheBanner() {
        val guide = savedConnectionWithGuide()
        connection.session.publish(
            SessionObservation.create(
                sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE),
                channelState = ChannelRepositoryState.Stale(guide.catalog),
            ),
        )

        compose.onNodeWithText(label(R.string.channels_saved)).assertIsDisplayed()
        compose.onNodeWithText(guide.first.name!!).assertIsDisplayed()
        compose.onNodeWithText(guide.second.name!!).assertIsDisplayed()
    }

    @Test
    fun recordingsDoNotRemapWhenOnlyTheGuideChanges() {
        val info = compose.activity.applicationInfo
        val oldFlags = info.flags
        info.flags = oldFlags or ApplicationInfo.FLAG_DEBUGGABLE
        ShadowLog.clear()
        fun mappings() = ShadowLog.getLogsForTag("TouchArtwork").count { it.msg.startsWith("mapped Recordings ") }
        try {
            val guide = savedConnectionWithGuide()
            openRecordings()
            val initial = mappings()
            assertTrue(initial > 0)
            compose.runOnIdle { connection.session.publish(guide.connected(epg = guide.refreshedEpg())) }
            compose.runOnIdle { assertEquals("An EPG-only update leaves Recordings unchanged", initial, mappings()) }
        } finally {
            info.flags = oldFlags
        }
    }

    @Test
    fun recordingsShowTheServersRecordingsWithoutUnwiredControls() {
        val guide = savedConnectionWithGuide()
        openRecordings()
        listOf(guide.recordingNow, guide.diary, guide.film).forEach {
            compose.onNodeWithText(it.title!!).assertIsDisplayed()
        }
        compose.onNodeWithText(guide.scheduled.title!!).assertDoesNotExist()

        // A picked recording shows its details but nothing to play or change yet.
        compose.onNodeWithText(guide.film.title!!).performClick()
        listOf(R.string.recordings_watch, R.string.recordings_resume, R.string.recordings_watch_from_beginning)
            .forEach { compose.onNodeWithText(label(it)).assertDoesNotExist() }
        compose.onNodeWithContentDescription(label(R.string.recordings_more)).assertDoesNotExist()
    }

    @Test
    fun deletingARecordingAsksFirstAndRemovesItsRow() {
        val guide = savedConnectionWithGuide()
        connection.session.dvrRepository.scriptDeleteEntry(DvrMutationResult.Confirmed(Unit))
        openRecordings()
        compose.onNodeWithText(guide.film.title!!).performClick()
        compose.onNodeWithContentDescription(label(SharedR.string.context_delete)).performClick()

        compose.onNodeWithText(label(SharedR.string.delete_recording_confirm_title)).assertIsDisplayed()
        assertTrue(FakeSessionCall.DVR_DELETE_ENTRY !in connection.session.calls)
        inDialog(SharedR.string.context_delete).performClick()
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_DELETE_ENTRY in connection.session.calls)

        connection.session.publish(guide.connected(listOf(guide.recordingNow, guide.diary, guide.scheduled)))
        compose.waitForIdle()
        compose.onNodeWithText(label(SharedR.string.delete_recording_confirm_title)).assertDoesNotExist()
        compose.onNodeWithText(guide.film.title!!).assertDoesNotExist()
    }

    @Test
    fun removeEntryDismissesWhenTheRecordingGainsAFile() {
        // Failed entries without files are not in the Archive destination. Drive its actual confirmation host.
        val guide = ServerGuide(FIXTURE_NOW)
        val withoutFile = guide.connected(listOf(guide.filmWithErrors(hasFile = false)))
        val withFile = guide.connected(listOf(guide.filmWithErrors(hasFile = true)))
        lateinit var dvr: DvrControls
        connection.session.publish(withoutFile)
        compose.setContent {
            val controls = viewModel { DvrControls(connection.browseSession, connection.noticeCenter) }
            SideEffect { dvr = controls }
            ConfirmDvrChange(controls)
        }
        compose.runOnIdle { dvr.delete(guide.film.id.value.toString()) }
        compose.onNodeWithText(label(SharedR.string.remove_entry_confirm_title)).assertIsDisplayed()
        connection.session.publish(withFile)
        compose.onNode(isDialog()).assertDoesNotExist()

        connection.session.publish(withoutFile)
        compose.runOnIdle { dvr.delete(guide.film.id.value.toString()) }
        val confirm = inDialog(SharedR.string.remove_entry).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!

        // Even a tap arriving before Compose sees the update must not delete the newly reported file.
        compose.runOnIdle {
            connection.session.publish(withFile)
            confirm()
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(FakeSessionCall.DVR_DELETE_ENTRY !in connection.session.calls)
    }

    @Test
    fun aConfirmedDeleteFailureStillShowsAfterRecreation() {
        val guide = savedConnectionWithGuide()
        connection.session.dvrRepository.scriptDeleteEntry(
            DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false),
        )
        connection.dvrRepliesHeld.value = true
        openRecordings()
        compose.onNodeWithText(guide.film.title!!).performClick()
        compose.onNodeWithContentDescription(label(SharedR.string.context_delete)).performClick()
        recreate()
        inDialog(SharedR.string.context_delete).performClick()
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_DELETE_ENTRY in connection.session.calls)
        recreate()
        connection.dvrRepliesHeld.value = false
        compose.waitForIdle()

        val failed = Notice.DvrActionFailed(DvrMutationKind.DELETE, DvrMutationFeedback.PERMISSION_DENIED)
        notice(NoticeFormatter(compose.activity).format(failed).headline).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun aPendingConfirmationWaitsForTheInFlightCommand() {
        val guide = savedConnectionWithGuide()
        connection.dvrRepliesHeld.value = true
        connection.session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(WEATHER_ENTRY)))
        openGuide()
        compose.onNodeWithText(guide.programs[1]).performClick()
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_record))).performClick()
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_SCHEDULE_ENTRY in connection.session.calls)
        openRecordings()
        compose.onNodeWithText(guide.film.title!!).performClick()
        compose.onNodeWithContentDescription(label(SharedR.string.context_delete)).performClick()
        inDialog(SharedR.string.context_delete).assertIsNotEnabled()
        assertTrue(FakeSessionCall.DVR_DELETE_ENTRY !in connection.session.calls)
        connection.dvrRepliesHeld.value = false
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_DELETE_ENTRY !in connection.session.calls)
        inDialog(SharedR.string.context_delete).performClick()
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_DELETE_ENTRY in connection.session.calls)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun recordOnALaterProgramSchedulesItAtOnce() {
        val guide = savedConnectionWithGuide()
        connection.session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(WEATHER_ENTRY)))
        openGuide()
        compose.onNodeWithText(guide.programs[1]).performClick()
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_record))).performClick()
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_SCHEDULE_ENTRY in connection.session.calls)

        // No dialog and no notice: the program's scheduled mark is the answer.
        assertEquals(null, connection.noticeCenter.state.value.active)
        connection.session.publish(guide.connected(guide.dvr.entries + guide.scheduling(WEATHER_ENTRY, event = 2)))
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNode(hasText(guide.programs[1]) and hasContentDescription(label(R.string.channels_scheduled)))
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun aProgramAnAutomaticRecordingScheduledSaysSoInsteadOfOfferingRecord() {
        val guide = savedConnectionWithGuide()
        val bySeries = guide.scheduling(WEATHER_ENTRY, event = 2, rule = AutorecRuleId("weather"))
        connection.session.publish(guide.connected(guide.dvr.entries + bySeries))
        openGuide()
        compose.onNodeWithText(guide.programs[1]).performClick()
        compose.onNodeWithText(label(SharedR.string.recording_state_scheduled)).assertIsDisplayed()
        listOf(label(R.string.channels_record), label(SharedR.string.cancel_recording)).forEach {
            compose.onAllNodes(hasClickAction() and hasText(it)).assertCountEquals(0)
        }
        val skipped = guide.scheduling(WEATHER_ENTRY, event = 2, rule = AutorecRuleId("weather"), enabled = false)
        connection.session.publish(guide.connected(guide.dvr.entries + skipped))
        compose.onNodeWithText(label(SharedR.string.autorec_editor_skipped)).assertIsDisplayed()
        compose.onNode(hasText(guide.programs[1]) and hasContentDescription(label(R.string.channels_scheduled)))
            .assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun cancellingAScheduledProgramAsksFirst() {
        val guide = savedConnectionWithGuide()
        connection.session.dvrRepository.scriptCancelEntry(DvrMutationResult.Confirmed(Unit))
        openGuide()
        val scheduled = hasText(guide.programs[2]) and hasContentDescription(label(R.string.channels_scheduled))
        compose.onNode(scheduled).assertIsDisplayed()
        compose.onNodeWithText(guide.programs[2]).performClick()
        val cancel = hasClickAction() and hasText(label(SharedR.string.cancel_recording))

        // Keep recording changes nothing.
        compose.onNode(cancel).performClick()
        compose.onNodeWithText(label(SharedR.string.cancel_recording_confirm_title)).assertIsDisplayed()
        assertEquals(WindowInsets.Type.systemBars(), dialogRequestedBars())
        inDialog(SharedR.string.keep_recording).performClick()
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(FakeSessionCall.DVR_CANCEL_ENTRY !in connection.session.calls)

        compose.onNode(cancel).performClick()
        inDialog(SharedR.string.cancel_recording).performClick()
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_CANCEL_ENTRY in connection.session.calls)
        connection.session.publish(guide.connected(guide.dvr.entries - guide.scheduled))
        compose.waitForIdle()
        compose.onNode(scheduled).assertDoesNotExist()
        compose.onNodeWithText(guide.programs[2]).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun stoppingARunningProgrammeAsksBeforeChangingIt() {
        val guide = savedConnectionWithGuide()
        connection.session.dvrRepository.scriptStopEntry(DvrMutationResult.Confirmed(Unit))
        openGuide()
        compose.onNodeWithText(guide.programs[0]).performClick()
        val stop = hasClickAction() and hasText(label(SharedR.string.stop_recording))

        compose.onNode(stop).performClick()
        compose.onNodeWithText(label(SharedR.string.stop_recording_confirm_title)).assertIsDisplayed()
        inDialog(SharedR.string.keep_recording).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(FakeSessionCall.DVR_STOP_ENTRY !in connection.session.calls)

        compose.onNode(stop).performClick()
        inDialog(SharedR.string.stop_recording).performClick()
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_STOP_ENTRY in connection.session.calls)
        compose.onNode(stop).assertIsDisplayed()
        connection.session.publish(guide.connected(guide.dvr.entries - guide.recordingNow))
        compose.onNode(stop).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun playerProgramLineUsesTheGenreOfTheDisplayedEvent() {
        val guide = ServerGuide(
            Instant.fromEpochSeconds(1_800_000_000),
            listOf(ContentGenre.fromContentType(0x10), ContentGenre.fromContentType(0x90)),
        )
        connection.session.publish(guide.connected())
        val observation = connection.session.observation.value
        val wall = guide.now + 1.hours
        val live = observation.event(EventId(2))
        val shown = mutableStateOf(live)
        val artwork = ArtworkModels(null)
        val onAir = channelRow(guide.first, observation, wall, artwork = artwork)
        val channel = mutableStateOf(onAir)
        val timeline = mutableStateOf<LiveTimeline?>(null)
        compose.setContent {
            val face = onAir.withShownPrograms(
                ShownPrograms(shown.value, null), wall, rememberChannelsTexts(), artwork, observation,
            )
            SideEffect { channel.value = face }
            MaterialTheme(colorScheme = TouchDarkColors) {
                PlayerScreen(
                    LivePlayerState(face, ChannelsState.Loading, null, timeline),
                    {}, {}, {}, {}, {},
                )
            }
        }
        fun assertLine(genre: Int?) {
            val time = checkNotNull(channel.value.now?.timeRange)
            val expected = genre?.let { compose.activity.getString(R.string.player_time_and_genre, time, label(it)) }
                ?: time
            compose.onNodeWithText(expected).assertIsDisplayed()
        }
        compose.waitForIdle()
        assertLine(SharedR.string.programme_category_education_factual)
        assertEquals(null, channel.value.details?.artworkModel)
        compose.runOnIdle { shown.value = observation.event(EventId(1)) }
        compose.waitForIdle()
        assertLine(SharedR.string.programme_category_film_drama)
        assertTrue(AppArtworkSource(observation.currentSession, guide.picture) in images.requested)
        compose.runOnIdle { shown.value = observation.event(EventId(3)) }
        compose.waitForIdle()
        assertLine(null)
        assertEquals(null, channel.value.details?.artworkModel)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun recordFromPlayerInfoSchedulesTheChosenProgrammeSilently() {
        val guide = ServerGuide(Instant.fromEpochSeconds(1_800_000_000))
        connection.session.publish(guide.connected())
        connection.session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(WEATHER_ENTRY)))
        connection.dvrRepliesHeld.value = true
        showLiveDvr(guide)
        playerButton(R.string.player_info).performClick()
        compose.onNodeWithText(guide.programs[1]).performClick()
        playerButton(R.string.channels_record).performClick()
        compose.waitForIdle()
        assertEquals(DvrSchedule.Programme(EventId(2)), connection.scheduleRequests.single().schedule)
        connection.dvrRepliesHeld.value = false
        compose.waitForIdle()
        assertEquals(null, connection.noticeCenter.state.value.active)
        assertTrue(connection.noticeCenter.state.value.pending.isEmpty())
    }

    @Test
    @Config(qualifiers = TABLET)
    fun cancelFromPlayerDetailsAsksFirstAndIsDismissedInTheWindow() {
        val guide = ServerGuide(Instant.fromEpochSeconds(1_800_000_000))
        connection.session.publish(guide.connected())
        val inWindow = mutableStateOf(false)
        showLiveDvr(guide, inWindow = { inWindow.value })
        playerButton(R.string.player_info).performClick()
        compose.onNodeWithText(guide.programs[2]).performClick()
        playerButton(R.string.channels_details).performClick()
        playerButton(SharedR.string.cancel_recording).performClick()
        inDialog(SharedR.string.cancel_recording).assertIsDisplayed()
        assertEquals("The landscape player dialog should request hidden system bars", 0, dialogRequestedBars())
        assertTrue(FakeSessionCall.DVR_CANCEL_ENTRY !in connection.session.calls)
        compose.runOnIdle { inWindow.value = true }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { inWindow.value = false }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(FakeSessionCall.DVR_CANCEL_ENTRY !in connection.session.calls)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun aStalePlayerRecordTapDoesNotRecordTheNewDisplayedProgramme() {
        val guide = ServerGuide(Instant.fromEpochSeconds(1_800_000_000))
        connection.session.publish(guide.connected(emptyList()))
        val displayed = mutableStateOf(EventId(1))
        showLiveDvr(guide, displayed = { displayed.value })
        val record = playerButton(R.string.player_record)
        val staleTap = record.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle {
            displayed.value = EventId(2)
            staleTap()
        }
        compose.waitForIdle()
        assertTrue(connection.scheduleRequests.isEmpty())
        connection.session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(WEATHER_ENTRY)))
        record.performClick()
        compose.waitForIdle()
        assertEquals(DvrSchedule.Programme(EventId(2)), connection.scheduleRequests.single().schedule)
    }

    private fun showLiveDvr(
        guide: ServerGuide,
        inWindow: () -> Boolean = { false },
        displayed: () -> EventId = { EventId(1) },
    ) {
        val rows = channelsFixture(playing = true)
        val state = LivePlayerState(rows.rows.first(), ChannelsState.Ready(rows), null, mutableStateOf(null))
        val observation = connection.session.observation.value
        fun programs() = ShownPrograms(observation.event(displayed()), null)
        fun offer() = liveRecordOffer(guide.first.id, programs(), connection.session.observation.value, guide.now.epochSeconds)
        val clock = GuideClock(guide.now.epochSeconds, ZoneId.systemDefault())
        compose.setContent {
            val dvr = viewModel { DvrControls(connection.browseSession, connection.noticeCenter) { guide.now.epochSeconds } }
            MaterialTheme(colorScheme = TouchDarkColors) {
                PlayerScreen(
                    state.copy(
                        schedule = playerSchedule(
                            guide.first.id, listOf(guide.first), observation, programs(), clock, guide.texts,
                        ),
                        liveRecord = offer(), offered = setOf(PlayerControl.Info, PlayerControl.Record),
                    ),
                    {}, {}, {}, {}, { dvr.choose(it, ::offer) },
                    video = {}, inWindow = inWindow(), confirmationOpen = dvr.pending != null,
                )
                PlayerDvrConfirmation(dvr, inWindow())
            }
        }
    }

    private fun playerButton(@StringRes id: Int) =
        compose.onNode(hasClickAction() and (hasText(label(id)) or hasContentDescription(label(id))))

    private fun dialogRequestedBars(): Int = compose.runOnIdle {
        val decor = WindowInspector.getGlobalWindowViews().single {
            it.isAttachedToWindow && it !== compose.activity.window.decorView
        }
        val controller = checkNotNull(decor.windowInsetsController)
        // The platform's requested mask, not its reported/visible insets (which Robolectric doesn't render).
        ReflectionHelpers.callInstanceMethod<Int>(controller, "getRequestedVisibleTypes") and
            WindowInsets.Type.systemBars()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun aFailedRecordShowsTheSharedFailureNotice() {
        val guide = savedConnectionWithGuide()
        connection.session.dvrRepository.scriptScheduleEntry(
            DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false),
        )
        openGuide()
        compose.onNodeWithText(guide.programs[1]).performClick()
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_record))).performClick()
        compose.waitForIdle()

        val failed = Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED)
        notice(NoticeFormatter(compose.activity).format(failed).headline).assertIsDisplayed()
    }

    private fun inDialog(@StringRes id: Int) =
        compose.onNode(hasClickAction() and hasText(label(id)) and hasAnyAncestor(isDialog()))

    @Test
    fun recordingsWriteClockTimesByTheDevicesSetting() {
        val uses24Hour = AppClock.systemUses24Hour
        AppClock.systemUses24Hour = true
        try {
            val guide = savedConnectionWithGuide()
            openRecordings()
            val until = checkNotNull(guide.recordingNow.stop).toJavaInstant().atZone(ZoneId.systemDefault())
            val expected = "%02d:%02d".format(until.hour, until.minute)
            compose.onNodeWithText(compose.activity.getString(R.string.recordings_until, expected), substring = true).assertExists()
        } finally {
            AppClock.systemUses24Hour = uses24Hour
        }
    }

    @Test
    fun viewOptionsApplyImmediatelyAndRestoreOnTheNextLaunch() {
        val memory = MemoryPreferences()
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        connection = FakeConnection(preferences = object : DataStore<Preferences> by memory {
            override val data = flow {
                readGate?.await()
                emitAll(memory.data)
            }

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                writeGate?.await()
                return memory.updateData(transform)
            }
        })
        val guide = savedConnectionWithGuide()
        openRecordings()
        fun options() = compose.onNodeWithContentDescription(label(R.string.recordings_view_options))
        fun choose(@StringRes page: Int, @StringRes choice: Int) {
            compose.onNodeWithText(label(page)).performClick()
            compose.onNodeWithText(label(choice)).performClick()
        }
        options().performClick()
        val holdWrite = CompletableDeferred<Unit>()
        compose.runOnIdle { writeGate = holdWrite }
        choose(R.string.recordings_show, R.string.recordings_show_unwatched)

        compose.onNodeWithText(guide.diary.title!!).assertDoesNotExist()
        compose.onNodeWithText(guide.film.title!!).assertExists()
        holdWrite.complete(Unit)
        choose(R.string.recordings_sort, R.string.recordings_sort_oldest)
        choose(R.string.recordings_group_by, R.string.recordings_grouping_folders)
        compose.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("Films").assertIsDisplayed()
        compose.onNodeWithText("Docs").assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText(label(R.string.nav_channels))).performClick()

        val holdRead = CompletableDeferred<Unit>()
        compose.runOnIdle {
            readGate = holdRead
            relaunchedRecordings.value = connection.recordings()
        }
        openRecordings()
        val showedFlatList = compose.onAllNodes(hasText(guide.film.title!!)).fetchSemanticsNodes().isNotEmpty()
        val showedLoading = compose.onAllNodes(hasText(label(SharedR.string.recordings_loading)))
            .fetchSemanticsNodes().isNotEmpty()
        holdRead.complete(Unit)
        compose.onNodeWithText("Films").assertIsDisplayed()
        assertFalse("Recordings showed the default flat list before the saved view loaded", showedFlatList)
        assertTrue("Recordings should show first load while waiting for the saved view", showedLoading)
        compose.onNodeWithText("Docs").assertDoesNotExist()
        options().performClick()
        compose.onNodeWithText(label(R.string.recordings_sort_oldest)).assertIsDisplayed()
    }

    /** A password connection whose saved sign-in can no longer be read; the frame asks for it. */
    private fun unreadableSignIn(forgetUsername: Boolean): ServerProfileId {
        connection = FakeConnection(readForEditing = ServerProfileEditReadResult.Unavailable)
        val id = runBlocking {
            val id = (connection.store.addPassword(server.host, server.port, "alex", "•", "Home server", true)
                as ServerProfileWriteResult.Applied).id
            connection.store.scriptCredentialsUnavailable(id, forgetUsername)
            id
        }
        show()
        compose.onNode(heading and hasText(label(SharedR.string.error_106_title))).assertIsDisplayed()
        return id
    }

    private fun openSignInNeeded(forgetUsername: Boolean): ServerProfileId = unreadableSignIn(forgetUsername).also {
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_connection_settings))).performClick()
    }

    private fun discard() {
        compose.onNodeWithContentDescription(label(SharedR.string.close)).performClick()
        compose.onNode(hasClickAction() and hasText(label(SharedR.string.autorec_editor_discard))).performClick()
        compose.waitForIdle()
    }

    private fun openEditor() {
        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        compose.onNode(hasClickAction() and hasText(label(SharedR.string.setup_connection))).performClick()
        compose.waitForIdle()
    }

    @Test
    fun guideShowsTheServersProgramsWithoutUnwiredControls() {
        val guide = savedConnectionWithGuide()
        openGuide()
        compose.onNodeWithText(guide.programs[0]).assertIsDisplayed()

        unavailable(SessionFailure.NETWORK_UNAVAILABLE)
        compose.mainClock.autoAdvance = false
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_connection_settings))).performClick()
        advance(64)
        val connections = compose.onNode(heading and hasText(label(SharedR.string.server_list_title)))
        connections.assertExists()
        compose.onNode(heading and hasText(label(R.string.nav_epg))).assertExists()
        back()
        compose.mainClock.autoAdvance = true
        connections.assertDoesNotExist()
        connection.session.publish(guide.connected())
        compose.onNode(heading and hasText(label(R.string.nav_epg))).assertIsDisplayed()
        compose.onNodeWithText(guide.programs[0]).assertIsDisplayed()

        // A tap opens the program in place; only Details has something behind it.
        compose.onNodeWithText(guide.programs[0]).performClick()
        listOf(R.string.channels_watch, R.string.channels_record)
            .forEach { compose.onNodeWithText(label(it)).assertDoesNotExist() }
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_details))).performClick()

        compose.onNodeWithText("About ${guide.programs[0]}").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.channels_show_in_guide)).assertIsDisplayed()
        listOf(R.string.channels_watch, R.string.channels_record)
            .forEach { compose.onNodeWithText(label(it)).assertDoesNotExist() }
    }

    @Test
    fun guideWaitsForTheServersGuideInTheFirstLoadForm() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        connection.session.publish(
            SessionObservation.create(
                sessionState = ready().sessionState,
                channelState = ChannelRepositoryState.Current(ServerGuide(FIXTURE_NOW).catalog),
            ),
        )
        openGuide()

        compose.onNodeWithText(label(SharedR.string.epg_loading)).assertIsDisplayed()
    }

    @Test
    fun postedNoticesShowOneAfterAnotherAndLeaveAfterTheirTime() {
        val failed = Notice.DvrActionFailed(DvrMutationKind.CANCEL, DvrMutationFeedback.CONNECTION_LIMIT)
        val next = Notice.RecordUnavailable(null)
        showSavedConnectionForNotices()
        post(failed)
        post(next)

        val failure = NoticeFormatter(compose.activity).format(failed)
        notice(failure.headline).assertIsDisplayed()
            .assert(hasText(checkNotNull(failure.detail)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        val second = NoticeFormatter(compose.activity).format(next).headline
        notice(second).assertDoesNotExist()

        // A failure stays its six seconds, and only then the next one comes.
        advance(FAILURE_MILLIS - 1_000)
        notice(failure.headline).assertIsDisplayed()
        advance(1_100)
        notice(failure.headline).assertDoesNotExist()
        notice(second).assertIsDisplayed()
        advance(INFO_MILLIS + 100)
        notice(second).assertDoesNotExist()
    }

    @Test
    fun postedNoticeShowsOnFirstRun() {
        connection.noticeClock = { compose.mainClock.currentTime }
        show()
        compose.onNode(heading and hasText(label(SharedR.string.onboarding_find_title))).assertIsDisplayed()
        compose.mainClock.autoAdvance = false

        val failed = Notice.DvrActionFailed(DvrMutationKind.DELETE, DvrMutationFeedback.REJECTED)
        post(failed)
        advance(16)
        notice(NoticeFormatter(compose.activity).format(failed).headline).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun postedNoticeShowsOnConnectionsAndContinuesInTheFrame() {
        showSavedConnectionForNotices()
        motionScale.scaleFactor = 0f
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()
        compose.mainClock.autoAdvance = false

        val failed = Notice.DvrActionFailed(DvrMutationKind.DELETE, DvrMutationFeedback.REJECTED)
        val next = Notice.RecordUnavailable(null)
        post(failed)
        post(next)
        val headline = NoticeFormatter(compose.activity).format(failed).headline
        notice(headline).assertIsDisplayed()
        compose.captureTouch("slice1", "tablet-connections-notice", TouchCanvas.TABLET)

        val active = connection.noticeCenter.state.value.active
        compose.mainClock.autoAdvance = true
        back()
        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        notice(headline).assertIsDisplayed() // A single visible live region, with the original display time.
        compose.mainClock.autoAdvance = false
        assertEquals(active, connection.noticeCenter.state.value.active)
        advance(connection.noticeCenter.remaining(checkNotNull(active).expiresAt) + 32)
        notice(headline).assertDoesNotExist()
        notice(NoticeFormatter(compose.activity).format(next).headline).assertIsDisplayed()
    }

    @Test
    fun noticesWaitWhileTheKeyboardIsUpOrTheAppIsAwayAndGetNoFreshTime() {
        val failed = Notice.DvrActionFailed(DvrMutationKind.DELETE, DvrMutationFeedback.REJECTED)
        showSavedConnectionForNotices()
        val headline = NoticeFormatter(compose.activity).format(failed).headline
        keyboard(shown = true)
        post(failed)
        advance(2_000)
        notice(headline).assertDoesNotExist()

        // Waiting cost it nothing: it starts its whole time once the keyboard is gone.
        keyboard(shown = false)
        advance(FAILURE_MILLIS - 2_000)
        notice(headline).assertIsDisplayed()

        // Away, its time runs on; back, it shows for what is left and no longer.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        advance(1_000)
        notice(headline).assertDoesNotExist()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        advance(100)
        notice(headline).assertIsDisplayed()
        advance(1_000)
        notice(headline).assertDoesNotExist()
    }

    private fun showSavedConnectionForNotices() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        connection.noticeClock = { compose.mainClock.currentTime }
        show()
        connection.session.publish(ready())
        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
    }

    private fun post(notice: Notice) {
        compose.runOnIdle { assertTrue(connection.noticeCenter.post(notice, connection.noticeCenter.context())) }
        advance(16)
    }

    private fun notice(headline: String) = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion) and hasText(headline),
    )

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    /** The keyboard's insets as the system sends them when it opens or closes. */
    private fun keyboard(shown: Boolean) {
        compose.runOnIdle {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            ViewCompat.dispatchApplyWindowInsets(
                content,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, if (shown) KEYBOARD_PX else 0))
                    .setVisible(WindowInsetsCompat.Type.ime(), shown)
                    .build(),
            )
        }
        advance(16)
    }

    private fun openGuide() {
        compose.onNode(hasClickAction() and hasText(label(R.string.nav_epg))).performClick()
        compose.waitForIdle()
    }

    private fun openRecordings() {
        compose.onNode(hasClickAction() and hasText(label(R.string.nav_recordings))).performClick()
        compose.waitForIdle()
    }

    private fun savedConnectionWithGuide(
        now: Instant = FIXTURE_NOW,
        withChannelArt: Boolean = false,
        mappingDispatcher: () -> CoroutineDispatcher = { Dispatchers.Main.immediate },
        configurationOverride: (@Composable () -> DeviceConfigurationOverride)? = null,
    ): ServerGuide {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        val guide = ServerGuide(now)
        if (withChannelArt) {
            captureLogo = (guide.logo as ChannelIcon.ImageCache).artworkId
            capturePicture = guide.picture
        }
        show(mappingDispatcher, configurationOverride)
        connection.session.publish(guide.connected())
        compose.waitForIdle()
        return guide
    }

    private fun signInToFoundServer() {
        show()
        connectToFoundServer()
    }

    private fun connectToFoundServer() {
        compose.onNode(hasClickAction() and hasText(server.name)).performClick()
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()
        compose.waitForIdle()
    }

    private var content: @Composable () -> Unit = {}
    private val relaunchedRecordings = mutableStateOf<RecordingsViewModel?>(null)
    private var watchChannel: ((String) -> Unit)? = null
    private var watchWay: ((String, BrowseContextAction, String?) -> Unit)? = null
    private var playingChannel = mutableStateOf<String?>(null)
    private val behindLive = mutableStateOf<String?>(null)
    private var captureLogo: ArtworkId? = null
    private var capturePicture: ArtworkId? = null
    private lateinit var browseChannels: ChannelsViewModel

    private fun show(
        mappingDispatcher: () -> CoroutineDispatcher = { Dispatchers.Main.immediate },
        configurationOverride: (@Composable () -> DeviceConfigurationOverride)? = null,
    ) {
        connection.start()
        val presentation = connection.presentation()
        val channels = connection.channels().also { browseChannels = it }
        val recordings = connection.recordings()
        val guide = connection.guide()
        val app: @Composable () -> Unit = {
            MaterialTheme(colorScheme = TouchDarkColors) {
                TouchApp(
                    connection.browseSession, connection.owner, presentation, channels,
                    relaunchedRecordings.value ?: recordings, guide,
                    connection.noticeCenter, playingId = playingChannel.value, onWatch = watchChannel,
                    onChannelWatch = watchWay, behindLiveId = behindLive.value,
                    channelsDispatcher = mappingDispatcher(),
                ) {
                    flowOf(TvheadendDiscoveryTestFactory.discovering(listOf(server)))
                }
            }
        }
        content = if (configurationOverride == null) app else { { DeviceConfigurationOverride(configurationOverride(), app) } }
        compose.setContent(content)
    }

    /** A configuration change: the activity is rebuilt, and its view models stay. */
    private fun recreate(canvas: TouchCanvas? = null) {
        if (canvas != null) compose.runOnIdle { RuntimeEnvironment.setQualifiers(canvas.qualifiers) }
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { it.setContent(content = content) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun field(@StringRes id: Int) =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasText(label(id)))

    private fun label(@StringRes id: Int): String = compose.activity.getString(id)

    private fun unavailable(failure: SessionFailure) {
        connection.session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(failure)))
        compose.waitForIdle()
    }

    private val writes = setOf(
        FakeServerProfileStoreCall.ADD_ANONYMOUS, FakeServerProfileStoreCall.ADD_PASSWORD,
        FakeServerProfileStoreCall.UPDATE_ANONYMOUS, FakeServerProfileStoreCall.UPDATE_PASSWORD,
        FakeServerProfileStoreCall.UPDATE_LABEL, FakeServerProfileStoreCall.SET_ACTIVE,
        FakeServerProfileStoreCall.REMOVE,
    )

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
        ),
    )

    private companion object {
        val FIXTURE_NOW = Instant.parse("2027-01-16T11:31:07Z")
        const val FAILURE_MILLIS = 6_000L
        const val INFO_MILLIS = 4_000L
        const val KEYBOARD_PX = 600
        const val WEATHER_ENTRY = 15L

        /** The Guide overview lists the later programs only in the tablet's columns. */
        const val TABLET = "en-rUS-w1280dp-h800dp-land-xhdpi"
    }
}
