package at.bernhardberger.tvhplayer.touch.connect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

private const val Tablet = "en-rUS-w1280dp-h800dp-land-xhdpi"
private const val Upright = "en-rUS-w800dp-h1280dp-port-xhdpi"
private const val Phone = "en-rUS-w412dp-h892dp-port-xhdpi"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = Tablet)
    fun tabletFindServer() = findServer("tablet-setup", TouchCanvas.TABLET)

    @Test
    @Config(qualifiers = Upright)
    fun uprightFindServer() = findServer("tablet-upright-setup", TouchCanvas.TABLET_UPRIGHT)

    @Test
    @Config(qualifiers = Phone)
    fun phoneFindServerHandsOverPickedServer() {
        var picked: FoundServer? = null
        findServer("phone-setup", TouchCanvas.PHONE) { picked = it }
        compose.onNode(hasClickAction() and hasText(servers.last().name)).performClick()
        compose.runOnIdle { assertEquals(servers.last(), picked) }
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletAccount() = account("tablet-setup-connection", TouchCanvas.TABLET)

    @Test
    @Config(qualifiers = Phone)
    fun phoneAccountSubmitsEditedFields() {
        var submitted: ConnectionFields? = null
        account("phone-setup-connection", TouchCanvas.PHONE, onConnect = { submitted = it })
        field(R.string.connect_address).performTextReplacement(servers.last().address)
        compose.onNodeWithText(label(R.string.connect_submit)).performClick()
        compose.runOnIdle { assertEquals(fields.copy(address = servers.last().address), submitted) }
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletConnectingDisablesFieldsAndSubmit() {
        account("tablet-setup-connecting", TouchCanvas.TABLET, ConnectionPhase.Connecting(fields.address))
        listOf(R.string.connect_address, R.string.connect_port, R.string.connect_username, R.string.connect_password,
            R.string.connect_name).forEach { field(it).assertIsNotEnabled() }
        compose.onNodeWithText(compose.activity.getString(R.string.connect_connecting, fields.address))
            .assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = Phone)
    fun phoneConnecting() =
        account("phone-setup-connecting", TouchCanvas.PHONE, ConnectionPhase.Connecting(fields.address))

    @Test
    @Config(qualifiers = Tablet)
    fun tabletRefusedMarksBothSignInFields() {
        account("tablet-setup-rejected", TouchCanvas.TABLET, ConnectionPhase.Refused)
        assertRefused()
    }

    @Test
    @Config(qualifiers = Phone)
    fun phoneRefusedMarksBothSignInFields() {
        account("phone-setup-rejected", TouchCanvas.PHONE, ConnectionPhase.Refused)
        assertRefused()
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletEditor() = editor("tablet-connection-editor", TouchCanvas.TABLET)

    @Test
    @Config(qualifiers = Phone)
    fun phoneEditorSavesEditedFields() {
        var saved: ConnectionFields? = null
        editor("phone-connection-editor", TouchCanvas.PHONE, onSave = { saved = it })
        field(R.string.connect_name).performTextReplacement(servers.last().name)
        compose.onNodeWithText(label(R.string.connect_save)).performClick()
        compose.runOnIdle { assertEquals(editorFields.copy(name = servers.last().name), saved) }
    }

    @Test
    @Config(qualifiers = Phone)
    fun phoneEditorRefused() {
        editor("phone-connection-rejected", TouchCanvas.PHONE, ConnectionPhase.Refused)
        assertRefused()
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletConnections() = connections("tablet-settings-connection", TouchCanvas.TABLET)

    @Test
    @Config(qualifiers = Phone)
    fun phoneConnectionsOpensEditorForActiveConnection() {
        var editing: ActiveConnectionUiState? = null
        connections("phone-settings-connection", TouchCanvas.PHONE, onEdit = { editing = it })
        compose.onNodeWithText(label(R.string.connect_edit)).performClick()
        compose.runOnIdle { assertEquals(active, editing) }
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletRemoveConnection() = connections("tablet-connection-remove", TouchCanvas.TABLET, openRemove = true)

    @Test
    @Config(qualifiers = Phone)
    fun phoneRemoveConnectionRequiresConfirmation() {
        var removed: ActiveConnectionUiState? = null
        connections("phone-connection-remove", TouchCanvas.PHONE, openRemove = true, onRemove = { removed = it })
        compose.runOnIdle { assertNull(removed) }
        compose.onNodeWithText(label(R.string.connect_cancel)).performClick()
        compose.runOnIdle { assertNull(removed) }
        openRemove()
        compose.onNodeWithText(label(R.string.connect_remove)).performClick()
        compose.runOnIdle { assertEquals(active, removed) }
    }

    private fun findServer(name: String, canvas: TouchCanvas, onPick: (FoundServer) -> Unit = {}) {
        show { FindServerScreen(ServerSearchUiState(servers), onPick, {}, {}) }
        capture(name, canvas)
    }

    private fun account(
        name: String,
        canvas: TouchCanvas,
        phase: ConnectionPhase = ConnectionPhase.Editing,
        onConnect: (ConnectionFields) -> Unit = {},
    ) {
        show {
            var current by remember(phase) {
                mutableStateOf(if (phase == ConnectionPhase.Editing) fields else signedInFields)
            }
            ConnectionAccountScreen(ConnectionUiState(current, phase), { current = it }, onConnect, {})
        }
        capture(name, canvas)
    }

    private fun editor(
        name: String,
        canvas: TouchCanvas,
        phase: ConnectionPhase = ConnectionPhase.Editing,
        onSave: (ConnectionFields) -> Unit = {},
    ) {
        var open by mutableStateOf(false)
        show {
            var current by remember { mutableStateOf(editorFields) }
            ConnectionsScreen(active, {}, {}, {})
            if (open) {
                ConnectionEditor(
                    ConnectionUiState(current, phase), editorFields, { current = it }, onSave, { open = false },
                )
            }
        }
        capture(name, canvas)
        compose.runOnIdle { open = true }
        captureDialogOver(name, canvas, fullScreen = canvas == TouchCanvas.PHONE)
    }

    private fun connections(
        name: String,
        canvas: TouchCanvas,
        openRemove: Boolean = false,
        onEdit: (ActiveConnectionUiState) -> Unit = {},
        onRemove: (ActiveConnectionUiState) -> Unit = {},
    ) {
        show { ConnectionsScreen(active, {}, onEdit, onRemove) }
        capture(name, canvas)
        if (openRemove) {
            openRemove()
            captureDialogOver(name, canvas)
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    // The activity and native dialogs have separate Compose roots in Robolectric.
    // Keep captureTouch's full canvas, and composite the real Material dialog over it.
    private fun captureDialogOver(name: String, canvas: TouchCanvas, fullScreen: Boolean = false) {
        compose.waitForIdle()
        val window = checkNotNull(ShadowDialog.getLatestDialog().window)
        if (fullScreen) {
            compose.runOnIdle {
                val root = window.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                ViewCompat.dispatchApplyWindowInsets(
                    root,
                    WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, canvas.statusBarDp * 2, 0, 0))
                        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                        .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                        .build(),
                )
            }
        }
        val modal = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val output = File(checkNotNull(System.getProperty("touch.captureDir")), "slice1/$name.png")
        val frame = BitmapFactory.decodeFile(output.path).copy(Bitmap.Config.ARGB_8888, true)
        val dim = (window.attributes.dimAmount * 255).toInt()
        Canvas(frame).apply {
            drawColor(Color.argb(dim, 0, 0, 0))
            drawBitmap(modal, (width - modal.width) / 2f, (height - modal.height) / 2f, null)
        }
        output.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        frame.recycle()
    }

    private fun openRemove() =
        compose.onNodeWithContentDescription(label(R.string.connect_remove_description)).performClick()

    private fun assertRefused() {
        val message = label(R.string.connect_refused)
        val error = SemanticsMatcher.expectValue(SemanticsProperties.Error, message)
        field(R.string.connect_username).assert(error)
        field(R.string.connect_password).assert(error)
        // Both fields carry the sentence as their error; it is written out once for the pair.
        compose.onAllNodes(hasText(message), useUnmergedTree = true).assertCountEquals(1)
    }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice1", name, canvas)

    private fun field(@StringRes id: Int) =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasText(label(id)))

    private fun label(@StringRes id: Int): String = compose.activity.getString(id)

    private val servers = listOf(
        FoundServer("Tvheadend", "192.168.1.20", 9982),
        FoundServer("tvh-test", "192.168.1.31", 9982),
    )
    private val active = ActiveConnectionUiState("Home server", servers.first().address, 9982, "alex")
    private val fields = ConnectionFields(address = servers.first().address)
    private val signedInFields = fields.copy(
        username = active.username,
        password = "•".repeat(8), // Mask-only fixture; not a credential.
    )
    private val editorFields = signedInFields.copy(name = active.name)
}
