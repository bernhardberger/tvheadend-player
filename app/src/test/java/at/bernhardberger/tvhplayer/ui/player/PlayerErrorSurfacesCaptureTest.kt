package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.LivePlayerAlert
import at.bernhardberger.tvhplayer.core.livePlayerAlert
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
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
 * Visual evidence for the live player's catalog error surfaces over the black player cover at the
 * 960×540 dp canvas, English, font scale 1.0, first button focused. Writes to
 * artifacts/player-chrome/errors (ignored).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
class PlayerErrorSurfacesCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Before fun television() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>().packageManager)
            .setSystemFeature("android.software.leanback", true)
    }

    @Test fun liveErrors() {
        val channel = listOf(AppErrorCondition.NO_FREE_ADAPTER, AppErrorCondition.SCRAMBLED, AppErrorCondition.STREAM_STOPPED,
            AppErrorCondition.CHANNEL_PLAYBACK_FAILED)
            .map { condition -> Scene("channel-${condition.number}", channel = condition) }
        val alerts = listOf(
            Scene("alert-101-unreachable", alert = connectionAlert(ConnectionFailureKind.UNREACHABLE)),
            Scene("alert-103-login", alert = connectionAlert(
                ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED)),
            Scene("alert-208-user-limit", alert = livePlayerAlert(ConnectionState.Connected, AppPlaybackState.Playing, AppErrorCondition.USER_LIMIT)),
            Scene("alert-110-reconnecting", alert = connectionAlert(
                ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)),
            Scene("alert-live-recovering", alert = LivePlayerAlert.Recovering(retryDelayMillis = 7_500L)),
        )
        val scene = mutableStateOf(channel.first())
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                // A key per scene so each starts fresh, as the player shows one surface at a time.
                key(scene.value.name) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        PlayerChannelMessage(
                            visible = scene.value.channel != null,
                            condition = scene.value.channel,
                            modifier = Modifier.align(Alignment.Center),
                            deviceFaultDetail = "DECODER_INIT_FAILED (4001)",
                        )
                        LivePlayerAlertOverlay(
                            visible = scene.value.alert != null,
                            alert = scene.value.alert,
                            onRetry = {},
                            onConnectionSettings = {},
                            onClose = {},
                        )
                    }
                }
            }
        }
        for (next in channel + alerts) {
            compose.runOnIdle { scene.value = next }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000L)
            compose.waitForIdle()
            if (next.alert != null) compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            val directory = File("../artifacts/player-chrome/errors").apply { mkdirs() }
            File(directory, "${next.name}-en-font1.0.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private class Scene(val name: String, val channel: AppErrorCondition? = null, val alert: LivePlayerAlert? = null)

    private fun connectionAlert(
        kind: ConnectionFailureKind,
        disposition: SessionRecoveryDisposition = SessionRecoveryDisposition.EXPLICIT_RETRY,
    ) = livePlayerAlert(
        ConnectionState.Error(kind, disposition),
        AppPlaybackState.Playing,
        null,
    )
}
