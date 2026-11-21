package at.bernhardberger.tvhplayer.touch

import android.os.Looper
import android.view.ViewGroup
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryTestFactory
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStoreCall
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.testutil.FakeConnection
import at.bernhardberger.tvhplayer.touch.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.common.AppClock
import java.time.ZoneId
import kotlin.time.toJavaInstant
import kotlin.time.Clock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The app's pages on the SDK's fake session and profile store: first run, the frame and Connections. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
@OptIn(TvheadendTestingApi::class)
class TouchAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val images = ImageLoaderRule()

    private var connection = FakeConnection()
    private val server = DiscoveredTvheadendServer("Tvheadend", "192.168.1.20", 9982)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @After fun close() = connection.close()

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
        compose.onNodeWithText(guide.first.name!!).assertIsDisplayed()
        compose.onNodeWithText(guide.second.name!!).assertIsDisplayed()
        // The phone row writes what is left of the program on now beside its title.
        compose.onNodeWithText(guide.programs[0], substring = true).assertIsDisplayed()

        // The open row offers nothing to play or record yet.
        compose.onNodeWithText(label(R.string.nav_epg)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.nav_recordings)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.channels_expand, guide.first.name))
            .performClick()
        compose.onNodeWithText(guide.programs[2]).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText(label(SharedR.string.stop_recording))).assertIsDisplayed()
        listOf(R.string.channels_watch, R.string.channels_record, R.string.channels_details, R.string.channels_show_in_guide)
            .forEach { compose.onNodeWithText(label(it)).assertDoesNotExist() }
        // The logo and the open program's picture come from the server through the image loader.
        val session = connection.session.observation.value.currentSession
        val logo = AppArtworkSource(session, (guide.logo as ChannelIcon.ImageCache).artworkId)
        assertTrue(images.requested.toString(), logo in images.requested)
        assertTrue(images.requested.toString(), AppArtworkSource(session, guide.picture) in images.requested)
    }

    @Test
    fun aTagFiltersTheChannels() {
        val guide = savedConnectionWithGuide()
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_all))).performClick()
        compose.onNode(hasClickAction() and hasText(guide.news.name!!)).performClick()

        compose.onNodeWithText(guide.first.name!!).assertIsDisplayed()
        compose.onNodeWithText(guide.second.name!!).assertDoesNotExist()
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
    fun recordOnAChannelRecordsWhatIsOnNow() {
        val guide = savedConnectionWithGuide()
        connection.session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(WEATHER_ENTRY)))
        connection.session.publish(guide.connected(guide.dvr.entries - guide.recordingNow))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.channels_expand, guide.first.name))
            .performClick()
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_record))).performClick()
        compose.waitForIdle()
        assertTrue(FakeSessionCall.DVR_SCHEDULE_ENTRY in connection.session.calls)
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
    fun aViewOptionChangesTheRecordings() {
        val guide = savedConnectionWithGuide()
        openRecordings()
        compose.onNodeWithContentDescription(label(R.string.recordings_view_options)).performClick()
        compose.onNodeWithText(label(R.string.recordings_show)).performClick()
        compose.onNodeWithText(label(R.string.recordings_show_unwatched)).performClick()

        compose.onNodeWithText(guide.diary.title!!).assertDoesNotExist()
        compose.onNodeWithText(guide.film.title!!).assertExists()
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

        // A tap opens the program in place; only Details has something behind it.
        compose.onNodeWithText(guide.programs[0]).performClick()
        listOf(R.string.channels_watch, R.string.channels_record)
            .forEach { compose.onNodeWithText(label(it)).assertDoesNotExist() }
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_details))).performClick()

        compose.onNodeWithText("About ${guide.programs[0]}").assertIsDisplayed()
        listOf(R.string.channels_watch, R.string.channels_record, R.string.channels_show_in_guide)
            .forEach { compose.onNodeWithText(label(it)).assertDoesNotExist() }
    }

    @Test
    fun guideWaitsForTheServersGuideInTheFirstLoadForm() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        connection.session.publish(
            SessionObservation.create(
                sessionState = ready().sessionState,
                channelState = ChannelRepositoryState.Current(ServerGuide(Clock.System.now()).catalog),
            ),
        )
        openGuide()

        compose.onNodeWithText(label(SharedR.string.epg_loading)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.guide_overview)).assertDoesNotExist()
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
        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        advance(16)
        compose.onNode(heading and hasText(label(SharedR.string.server_list_title))).assertIsDisplayed()

        val failed = Notice.DvrActionFailed(DvrMutationKind.DELETE, DvrMutationFeedback.REJECTED)
        val next = Notice.RecordUnavailable(null)
        post(failed)
        post(next)
        val headline = NoticeFormatter(compose.activity).format(failed).headline
        notice(headline).assertIsDisplayed()
        compose.captureTouch("slice1", "tablet-connections-notice", TouchCanvas.TABLET)

        val active = connection.noticeCenter.state.value.active
        back()
        advance(16)
        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        notice(headline).assertIsDisplayed() // A single visible live region, with the original display time.
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

    private fun savedConnectionWithGuide(): ServerGuide {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        val guide = ServerGuide(Clock.System.now())
        connection.session.publish(guide.connected())
        compose.waitForIdle()
        return guide
    }

    private fun signInToFoundServer() {
        show()
        compose.onNode(hasClickAction() and hasText(server.name)).performClick()
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()
        compose.waitForIdle()
    }

    private var content: @Composable () -> Unit = {}

    private fun show() {
        connection.start()
        val presentation = connection.presentation()
        val channels = connection.channels()
        val recordings = connection.recordings()
        val guide = connection.guide()
        content = {
            MaterialTheme(colorScheme = MobileDarkColors) {
                TouchApp(
                    connection.session, connection.owner, presentation, channels, recordings, guide,
                    connection.noticeCenter,
                ) {
                    flowOf(TvheadendDiscoveryTestFactory.discovering(listOf(server)))
                }
            }
        }
        compose.setContent(content)
    }

    /** A configuration change: the activity is rebuilt, and its view models stay. */
    private fun recreate() {
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
        const val FAILURE_MILLIS = 6_000L
        const val INFO_MILLIS = 4_000L
        const val KEYBOARD_PX = 600
        const val WEATHER_ENTRY = 15L

        /** The Guide overview lists the later programs only in the tablet's columns. */
        const val TABLET = "en-rUS-w1280dp-h800dp-land-xhdpi"
    }
}
