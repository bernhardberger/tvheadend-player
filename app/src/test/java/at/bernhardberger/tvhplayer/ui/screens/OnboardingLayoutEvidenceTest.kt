package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingLayoutEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test
    fun introductionKeepsItsKeylineAndInitialFocus() {
        showIntroduction()
        val title = textBounds(R.string.onboarding_title)
        val action = textBounds(R.string.onboarding_continue)
        assertEquals(58f, title.left, .1f)
        assertEquals(902f, action.right, .1f)
        compose.onNodeWithText(label(R.string.onboarding_continue)).assertIsFocused()
    }

    @Test
    fun blankConnectionKeepsItsKeylineAndInitialFocus() {
        showConnection()
        val title = textBounds(R.string.onboarding_connection_title)
        assertEquals(58f, title.left, .1f)
        assertEquals(28f, title.top, .1f)
        val focusedField = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val hostLabel = compose.onNodeWithText(label(R.string.host), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Initial focus belongs to the blank host field", focusedField.contains(hostLabel.center))
        compose.onNodeWithText(label(R.string.save_and_continue)).assertIsNotEnabled()
    }

    @Test
    @Category(VisualCapture::class)
    fun captureIntroduction() {
        showIntroduction()
        capture("en-font1.0-introduction", textBounds(R.string.onboarding_title),
            textBounds(R.string.onboarding_continue), "Continue")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureBlankConnection() {
        showConnection()
        capture("en-font1.0-connection", textBounds(R.string.onboarding_connection_title),
            textBounds(R.string.save_and_continue), "blank host field")
    }

    private fun showIntroduction() = show { OnboardingIntroduction {} }

    private fun showConnection() = show {
        OnboardingConnection(
            settingsStore = object : ConnectionProfileEditor {
                override val serverSettings = emptyFlow<ServerSettings>()
                override suspend fun loadServerForEditing(applyAvailable: (String, Int, String, String) -> Unit) =
                    error("No profile access in layout evidence")
                override suspend fun saveServer(host: String, htspPort: Int) =
                    error("No profile access in layout evidence")
                override suspend fun savePasswordServer(
                    host: String, htspPort: Int, username: String, password: String,
                    credentialLease: CredentialEditLease,
                ) = error("No profile access in layout evidence")
                override suspend fun clearProfile() = error("No profile access in layout evidence")
            },
            onBack = {},
            form = ConnectionFormState(),
        )
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private fun textBounds(id: Int) = compose.onNodeWithText(label(id)).fetchSemanticsNode().boundsInRoot

    private fun capture(name: String, title: Rect, action: Rect, focus: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/onboarding-captures").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(directory, "$name.txt").writeText(
                "canvas=960x540dp; density=1; locale=en; fontScale=1.0; focus=$focus\n" +
                    "title=$title; action=$action\nproduction onboarding; blank form; offline static composition only\n",
            )
            bitmap.recycle()
        }
    }
}
