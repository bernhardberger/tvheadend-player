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
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.ui.ListMessage
import at.bernhardberger.tvhplayer.touch.ui.MessageKind
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupAction
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupFailure
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.touch.ui.TouchDarkColors
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
        var picked: DiscoveredTvheadendServer? = null
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
        field(SharedR.string.onboarding_server_address).performTextReplacement(servers.last().host)
        compose.onNodeWithText(label(SharedR.string.onboarding_connect)).performClick()
        compose.runOnIdle { assertEquals(fields.copy(address = servers.last().host), submitted) }
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletConnectingDisablesFieldsAndSubmit() {
        account("tablet-setup-connecting", TouchCanvas.TABLET, ConnectionPhase.Connecting(fields.address))
        listOf(SharedR.string.onboarding_server_address, R.string.connect_port, SharedR.string.username, SharedR.string.password,
            SharedR.string.server_name_field).forEach { field(it).assertIsNotEnabled() }
        compose.onNodeWithText(compose.activity.getString(SharedR.string.onboarding_connecting_title, fields.address))
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
        field(SharedR.string.server_name_field).performTextReplacement(servers.last().name)
        compose.onNodeWithText(label(SharedR.string.server_name_save)).performClick()
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
        compose.onNodeWithText(label(SharedR.string.setup_connection)).performClick()
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
        compose.onNodeWithText(label(SharedR.string.cancel)).performClick()
        compose.runOnIdle { assertNull(removed) }
        openRemove()
        compose.onNodeWithText(label(R.string.connect_remove)).performClick()
        compose.runOnIdle { assertEquals(active, removed) }
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletFailedDialog() = failureDialog("tablet-setup-failed-dialog", TouchCanvas.TABLET, SetupFailure.TIMEOUT)

    @Test
    @Config(qualifiers = Phone)
    fun phoneFailedDialogTriesAgain() {
        var chosen: SetupAction? = null
        failureDialog("phone-setup-failed-dialog", TouchCanvas.PHONE, SetupFailure.TIMEOUT) { chosen = it }
        compose.onNodeWithText(label(SharedR.string.connection_try_again)).performClick()
        compose.runOnIdle { assertEquals(SetupAction.TRY_AGAIN, chosen) }
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletUnreachableDialogContinuesAnyway() {
        var chosen: SetupAction? = null
        failureDialog("tablet-setup-unreachable-dialog", TouchCanvas.TABLET, SetupFailure.UNREACHABLE) { chosen = it }
        compose.onNodeWithText(label(SharedR.string.connection_try_again)).assertDoesNotExist()
        compose.onNodeWithText(label(SharedR.string.onboarding_continue_anyway)).performClick()
        compose.runOnIdle { assertEquals(SetupAction.CONTINUE_ANYWAY, chosen) }
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletNotTvheadendMarksTheAddressPair() {
        account("tablet-setup-not-tvheadend", TouchCanvas.TABLET, ConnectionPhase.NotTvheadend)
        assertPairError(R.string.connect_not_tvheadend, SharedR.string.onboarding_server_address, R.string.connect_port)
    }

    @Test
    @Config(qualifiers = Phone)
    fun phoneNotTvheadend() = account("phone-setup-not-tvheadend", TouchCanvas.PHONE, ConnectionPhase.NotTvheadend)

    @Test
    @Config(qualifiers = Tablet)
    fun tabletConnectionStateConnecting() {
        val status = ConnectionStatus(ConnectionStatus.Kind.Working, SharedR.string.connection_overview_connecting)
        connections("tablet-connection-state-connecting", TouchCanvas.TABLET, active.copy(status = status))
        compose.onNodeWithText(label(SharedR.string.connection_overview_connecting)).assertExists()
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletConnectionStateFailed() {
        val status = ConnectionStatus(ConnectionStatus.Kind.Failed, SharedR.string.error_101_title)
        connections("tablet-connection-state-failed", TouchCanvas.TABLET, active.copy(status = status))
        compose.onNodeWithText(label(SharedR.string.error_101_title)).assertExists()
        compose.onNodeWithText(label(SharedR.string.connection_overview_connected)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletEditorSavedPassword() =
        editor("tablet-connection-editor-saved-password", TouchCanvas.TABLET, passwordSaved = true)

    @Test
    @Config(qualifiers = Phone)
    fun phoneEditorSavedPasswordLeavesWhenTyping() {
        editor("phone-connection-editor-saved-password", TouchCanvas.PHONE, passwordSaved = true)
        val saved = label(SharedR.string.onboarding_password_saved)
        compose.onNodeWithText(saved).assertExists()
        compose.onNodeWithContentDescription(label(SharedR.string.show_password)).assertDoesNotExist()
        field(SharedR.string.password).performTextReplacement("•".repeat(4)) // Mask-only fixture.
        compose.onNodeWithText(saved).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(SharedR.string.show_password)).assertExists()
    }

    @Test
    @Config(qualifiers = Tablet)
    fun tabletSignInNeeded() = signInNeeded("tablet-sign-in-needed", TouchCanvas.TABLET)

    @Test
    @Config(qualifiers = Phone)
    fun phoneSignInNeeded() = signInNeeded("phone-sign-in-needed", TouchCanvas.PHONE)

    private fun findServer(name: String, canvas: TouchCanvas, onPick: (DiscoveredTvheadendServer) -> Unit = {}) {
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
        passwordSaved: Boolean = false,
    ) {
        var open by mutableStateOf(false)
        val loaded = if (passwordSaved) editorFields.copy(password = "") else editorFields
        show {
            var current by remember { mutableStateOf(loaded) }
            ConnectionsScreen(active, {}, {}, {})
            if (open) {
                ConnectionEditor(
                    ConnectionUiState(current, phase, passwordSaved = passwordSaved), loaded, { current = it }, onSave,
                    { open = false },
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
        shown: ActiveConnectionUiState = active,
        openRemove: Boolean = false,
        onEdit: (ActiveConnectionUiState) -> Unit = {},
        onRemove: (ActiveConnectionUiState) -> Unit = {},
    ) {
        show { ConnectionsScreen(shown, {}, onEdit, onRemove) }
        capture(name, canvas)
        if (openRemove) {
            openRemove()
            captureDialogOver(name, canvas)
        }
    }

    private fun failureDialog(
        name: String,
        canvas: TouchCanvas,
        failure: SetupFailure,
        onAction: (SetupAction) -> Unit = {},
    ) {
        show {
            ConnectionAccountScreen(ConnectionUiState(signedInFields), {}, {}, {})
            ConnectionFailureDialog(failure, SetupMode.FIRST_RUN, onCancel = {}, onAction = onAction)
        }
        capture(name, canvas)
        captureDialogOver(name, canvas)
    }

    private fun signInNeeded(name: String, canvas: TouchCanvas) {
        show {
            TouchFrame(content = { ListMessage(MessageKind.SignInNeeded, onSettings = {}) })
        }
        capture(name, canvas)
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
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
        compose.onNodeWithContentDescription(label(SharedR.string.server_remove)).performClick()

    // Both fields carry the sentence as their error; it is written out once for the pair.
    private fun assertRefused() =
        assertPairError(R.string.connect_refused, SharedR.string.username, SharedR.string.password)

    private fun assertPairError(@StringRes message: Int, @StringRes vararg fields: Int) {
        val text = label(message)
        val error = SemanticsMatcher.expectValue(SemanticsProperties.Error, text)
        fields.forEach { field(it).assert(error) }
        compose.onAllNodes(hasText(text), useUnmergedTree = true).assertCountEquals(1)
    }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice1", name, canvas)

    private fun field(@StringRes id: Int) =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasText(label(id)))

    private fun label(@StringRes id: Int): String = compose.activity.getString(id)

    private val servers = listOf(
        DiscoveredTvheadendServer("Tvheadend", "192.168.1.20", 9982),
        DiscoveredTvheadendServer("tvh-test", "192.168.1.31", 9982),
    )
    private val active = ActiveConnectionUiState("Home server", servers.first().host, 9982, "alex")
    private val fields = ConnectionFields(address = servers.first().host)
    private val signedInFields = fields.copy(
        username = active.username,
        password = "•".repeat(8), // Mask-only fixture; not a credential.
    )
    private val editorFields = signedInFields.copy(name = active.name)
}
