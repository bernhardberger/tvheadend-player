package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.player.DebugVideoBackdrop
import at.bernhardberger.tvhplayer.ui.screens.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings pages that hold per-connection values name the connection once several are stored, and the
 * Connection list's state line stays readable on the focused row. 1920×1080, English, font scale 1.0;
 * production composables with fixture state. Offline composition only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
class SettingsConnectionScopeCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private val first = fixtureServer(1, "tvheadend.local", username = "anna")
    private val second = fixtureServer(2, "cabin.local", username = "fixture", credentialsAvailable = false)
    private val two = ServerProfileListResult.Available(listOf(first, second), first.id)
    private val one = ServerProfileListResult.Available(listOf(first), first.id)

    @Test fun channelGroupsWithTwoConnections() = channelGroups(two, "channel-groups-two-connections")
    @Test fun channelGroupsWithOneConnection() = channelGroups(one, "channel-groups-one-connection")
    @Test fun streamProfileWithTwoConnections() = streamProfile(two, "stream-profile-two-connections")
    @Test fun streamProfileWithOneConnection() = streamProfile(one, "stream-profile-one-connection")

    @Test fun focusedConnectionRows() {
        compose.setContent {
            Scene(SettingsSection.CONNECTION.name) {
                listOf(settingsRootLevel(connectionStatus = "Connected")) +
                    fixtureConnectionLevels("Connected", profiles = two)
            }
        }
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-${serverProfileRowId(first.id)}").assertIsFocused()
        capture("connection-list-in-use-focused")
        press(Key.DirectionDown)
        compose.onNodeWithTag("settings-row-${serverProfileRowId(second.id)}").assertIsFocused()
        capture("connection-list-password-needed-focused")
    }

    private fun channelGroups(profiles: ServerProfileListResult.Available, name: String) {
        val tags = listOf("Favorites", "Entertainment", "News").mapIndexed { index, tag ->
            ChannelTag.create(ChannelTagId(index + 1L), name = tag, channelIds = emptyList())
        }
        compose.setContent {
            val connection = connectionScopeName(profiles)
            Scene(SettingsSection.CHANNEL_TAGS.name) {
                listOf(settingsRootLevel(connectionStatus = "Connected"),
                    settingsChannelTagsLevel(tags, ChannelScopeVisibility(), loaded = true, failed = false,
                        onRetry = {}, onToggle = {}, connectionName = connection))
            }
        }
        press(Key.DirectionCenter)
        capture(name)
    }

    private fun streamProfile(profiles: ServerProfileListResult.Available, name: String) {
        val ui = connectedPlaybackUi()
        compose.setContent {
            val connection = connectionScopeName(profiles)
            Scene(SettingsSection.PLAYER.name) {
                listOf(settingsRootLevel(connectionStatus = "Connected"),
                    settingsPlayerLevel(ui, {}),
                    settingsStreamProfileLevel(ui, connectionName = connection) {})
            }
        }
        press(Key.DirectionCenter)
        repeat(7) { press(Key.DirectionDown) } // Past the Recording marks group to Profile.
        press(Key.DirectionCenter)
        capture(name)
    }

    /** A connected session offering four stream profiles, the first one chosen. */
    private fun connectedPlaybackUi(): SettingsPlayerUiState {
        val sdk = at.bernhardberger.tvheadend.sdk.core.SessionObservation.create(
            at.bernhardberger.tvheadend.sdk.core.SessionState.Ready(at.bernhardberger.tvheadend.sdk.core.ServerCapabilities.create(
                streaming = at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
                dvrWrite = at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED)),
            at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState.Current(at.bernhardberger.tvheadend.sdk.core.ChannelCatalog.create()),
            at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState.Current(at.bernhardberger.tvheadend.sdk.core.EpgSnapshot.create()),
            at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState.Current(at.bernhardberger.tvheadend.sdk.core.DvrSnapshot.create()))
        val profiles = listOf("htsp", "pass", "matroska", "webtv-h264-aac-mpegts").mapIndexed { index, name ->
            at.bernhardberger.tvheadend.sdk.core.StreamProfile(
                at.bernhardberger.tvheadend.sdk.core.StreamProfileId((index + 1).toString().padStart(32, '0')), name, "")
        }
        return SettingsPlayerUiState(connected = true, audioLanguages = listOf("en"),
            profiles = at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult.Available.create(profiles, requireNotNull(sdk.currentSession)),
            selectedProfileId = profiles.first().id)
    }

    @androidx.compose.runtime.Composable
    private fun Scene(section: String, levels: @androidx.compose.runtime.Composable () -> List<at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel>) {
        TVHeadendPlayerTheme {
            view = LocalView.current
            val navigation = rememberDepthNavigationState(SETTINGS_ROOT, section)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                DebugVideoBackdrop(true, Modifier.fillMaxSize())
                WarmPlaybackScrim()
                SideRail(currentRoute = AppDestination.SETTINGS, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                    SettingsScreenNavigation(navigation, levels(), initialFocusEnabled = !drawerActive, contentPadding = padding)
                }
            }
        }
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        assertEquals(1920, bitmap.width)
        assertEquals(1080, bitmap.height)
        val directory = File("build/outputs/connection-scope-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
