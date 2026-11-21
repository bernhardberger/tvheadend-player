package at.bernhardberger.tvhplayer.touch

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
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
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.testutil.FakeConnection
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
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
import org.robolectric.annotation.Config

/** The app's pages on the SDK's fake session and profile store: first run, the frame and Connections. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
@OptIn(TvheadendTestingApi::class)
class TouchAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        signInToFoundServer()
        connection.session.publish(ready())

        compose.onNodeWithText(label(SharedR.string.connection_save_failed)).assertIsDisplayed()
        connection.store.scriptMutationSuccess()
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()

        compose.onNode(heading and hasText(label(R.string.nav_channels))).assertIsDisplayed()
        val saved = runBlocking { connection.store.profiles() } as ServerProfileListResult.Available
        assertEquals(listOf(server.host), saved.profiles.map { it.host })
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
    }

    @Test
    fun unreachableContinueAnywaySavesTheUntestedConnection() {
        signInToFoundServer()
        unavailable(SessionFailure.SERVER_UNREACHABLE)
        compose.onNodeWithText(label(SharedR.string.onboarding_continue_anyway)).performClick()

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
        val id = openSignInNeeded(forgetUsername = true)
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
    fun theEditorKeepsATypedSignInThroughRecreation() {
        runBlocking { connection.store.addAnonymous(server.host, server.port, "Home server", true) }
        show()
        openEditor()
        field(SharedR.string.username).performTextInput("alex")
        field(SharedR.string.password).performTextInput("secret")

        recreate()

        field(SharedR.string.username).assert(hasText("alex"))
        compose.onNodeWithContentDescription(label(SharedR.string.show_password)).assertExists()
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
    fun channelsAppearAfterConnectingWithoutUnwiredControls() {
        val guide = savedConnectionWithGuide()
        compose.onNodeWithText(guide.first.name!!).assertIsDisplayed()
        compose.onNodeWithText(guide.second.name!!).assertIsDisplayed()
        compose.onNodeWithText(guide.programs[0]).assertIsDisplayed()

        // Guide is not wired yet, and the open row offers nothing to play or record.
        compose.onNodeWithText(label(R.string.nav_epg)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.nav_recordings)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.channels_expand, guide.first.name))
            .performClick()
        compose.onNodeWithText(guide.programs[2]).assertIsDisplayed()
        listOf(R.string.channels_watch, R.string.channels_record, R.string.channels_details, R.string.channels_show_in_guide)
            .forEach { compose.onNodeWithText(label(it)).assertDoesNotExist() }
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

        // A picked recording shows its details but nothing to play, delete or change yet.
        compose.onNodeWithText(guide.film.title!!).performClick()
        listOf(R.string.recordings_watch, R.string.recordings_resume, R.string.recordings_watch_from_beginning)
            .forEach { compose.onNodeWithText(label(it)).assertDoesNotExist() }
        listOf(R.string.recordings_delete, R.string.recordings_more)
            .forEach { compose.onNodeWithContentDescription(label(it)).assertDoesNotExist() }
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

    private fun openSignInNeeded(forgetUsername: Boolean): ServerProfileId {
        connection = FakeConnection(readForEditing = ServerProfileEditReadResult.Unavailable)
        val id = runBlocking {
            val id = (connection.store.addPassword(server.host, server.port, "alex", "•", "Home server", true)
                as ServerProfileWriteResult.Applied).id
            connection.store.scriptCredentialsUnavailable(id, forgetUsername)
            id
        }
        show()
        compose.onNode(heading and hasText(label(SharedR.string.error_106_title))).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText(label(R.string.channels_connection_settings))).performClick()
        return id
    }

    private fun openEditor() {
        compose.onNodeWithContentDescription(label(R.string.channels_connection_settings)).performClick()
        compose.onNode(hasClickAction() and hasText(label(SharedR.string.setup_connection))).performClick()
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
        connection.session.publish(
            SessionObservation.create(
                sessionState = ready().sessionState,
                channelState = ChannelRepositoryState.Current(guide.catalog),
                epgState = EpgRepositoryState.Current(guide.snapshot),
                dvrState = DvrRepositoryState.Current(guide.dvr),
            ),
        )
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
        content = {
            MaterialTheme(colorScheme = MobileDarkColors) {
                TouchApp(connection.session, connection.owner, presentation, channels, recordings) {
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

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
        ),
    )
}
