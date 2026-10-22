package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.toAppError
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.startup.MainStartupScreen
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual evidence for the catalog alert at startup and in Channels without saved channels at the
 * 960×540 dp canvas, English, font scale 1.0, first button focused. Writes beside the player's error
 * captures in artifacts/player-chrome/errors (ignored).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
class BrowseErrorAlertCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Before fun television() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>().packageManager)
            .setSystemFeature("android.software.leanback", true)
    }

    @Test fun startupUnreachable() = capture("startup-101-unreachable") {
        MainStartupScreen(
            presentation = MainStartupPresentation.Actionable(AppErrorCondition.UNREACHABLE.toAppError()),
            contentPadding = TvFullScreenPadding,
        )
    }

    @Test fun channelsWithoutChannels() = capture("channels-108-no-channels") { channels(ConnectionUiState.Ready) }

    @Test fun channelsUnreachable() = capture("channels-101-unreachable") {
        channels(ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY))
    }

    @Composable
    private fun channels(connection: ConnectionUiState) {
        val observation = SessionObservation.create(
            sessionState = SessionState.Connecting,
            channelState = if (connection == ConnectionUiState.Ready) {
                ChannelRepositoryState.Current(ChannelCatalog.create(emptyList()))
            } else ChannelRepositoryState.Empty,
        )
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SideRail(currentRoute = AppDestination.CHANNELS, showEpgMenu = true, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                ChannelsScreenContent(
                    view = ChannelsView(ChannelsLayout.LIST),
                    contentPadding = padding,
                    initialFocusEnabled = !drawerActive,
                    channelScopeState = resolveChannelScopeState(observation.channelState, null),
                    observation = observation,
                    tagNotice = false,
                    selectedId = { null },
                    playingChannelId = null,
                    connectionUiState = connection,
                    onSelectChannel = {},
                    onSelectTag = {},
                    onDismissTagNotice = {},
                    onRetryConnection = {},
                    onOpenConnectionSettings = {},
                    onPlay = { _, _ -> },
                )
            }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                content()
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(1_000L)
        compose.waitForIdle()
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("../artifacts/player-chrome/errors").apply { mkdirs() }
        File(directory, "$name-en-font1.0.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
