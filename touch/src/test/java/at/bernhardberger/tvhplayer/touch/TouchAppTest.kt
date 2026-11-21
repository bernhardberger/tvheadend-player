package at.bernhardberger.tvhplayer.touch

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryTestFactory
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.testutil.FakeConnection
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
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

    private val connection = FakeConnection()
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

    private fun signInToFoundServer() {
        show()
        compose.onNode(hasClickAction() and hasText(server.name)).performClick()
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()
        compose.waitForIdle()
    }

    private fun show() {
        connection.start()
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                TouchApp(connection.session, connection.owner) {
                    flowOf(TvheadendDiscoveryTestFactory.discovering(listOf(server)))
                }
            }
        }
    }

    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun field(@StringRes id: Int) =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasText(label(id)))

    private fun label(@StringRes id: Int): String = compose.activity.getString(id)

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
        ),
    )
}
