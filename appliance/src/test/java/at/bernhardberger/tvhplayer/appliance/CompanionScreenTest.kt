package at.bernhardberger.tvhplayer.appliance

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.appliance.update.UpdateResult
import at.bernhardberger.tvhplayer.appliance.update.UpdateScreenState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Production-composable evidence for the setup screen, English at font scale 1.0. The captures prove
 * only these compositions; the SurfaceView, overscan and remote feel stay physical-TV gates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val activated = mutableListOf<CompanionRow>()

    private val ready = CompanionScreenState(
        playerVersion = "0.4.2",
        accessibilityStatus = CompanionServiceStatus.ON,
        installAllowed = true,
        defaultHome = true,
        hasSystemHome = true,
        update = UpdateScreenState(result = UpdateResult.UP_TO_DATE),
    )
    private val toFix = ready.copy(accessibilityStatus = CompanionServiceStatus.OFF, installAllowed = false, defaultHome = false)
    private val playerMissing = toFix.copy(playerVersion = null)
    private val available = ready.copy(
        update = UpdateScreenState(latestVersion = "0.5.0", result = UpdateResult.WAITING_STANDBY))
    private val installing = ready.copy(
        update = UpdateScreenState(latestVersion = "0.5.0", result = UpdateResult.INSTALLING))
    private val failed = ready.copy(update = UpdateScreenState(result = UpdateResult.NO_NETWORK))

    private fun show(initial: CompanionScreenState): (CompanionScreenState) -> Unit {
        var state by mutableStateOf(initial)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    CompanionScreen(
                        state = state,
                        onRow = { activated += it },
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
        compose.waitForIdle()
        return { state = it }
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/outputs/companion-captures/$name.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    private fun focused(text: String) = compose.onNode(
        androidx.compose.ui.test.isFocused() and hasText(text)).assertIsFocused()

    @Test fun allSet() {
        show(ready)
        focused("Tvheadend Player")
        save("en-font1.0-all-set")
    }

    @Test fun thingsToFix() {
        show(toFix.copy(playerVersion = "0.4.2"))
        compose.onNodeWithText("2 things to fix").assertExists()
        focused("Accessibility service")
        save("en-font1.0-things-to-fix")
    }

    @Test fun playerMissing() {
        show(playerMissing)
        compose.onNodeWithText("Player not installed").assertExists()
        focused("Tvheadend Player")
        save("en-font1.0-player-missing")
    }

    @Test fun updateAvailable() {
        show(available)
        compose.onNodeWithText("New").assertExists()
        focused("Update now")
        save("en-font1.0-update-available")
    }

    @Test fun updateInstalling() {
        show(installing)
        compose.onNodeWithText("Updating").assertExists()
        focused("Updating to 0.5.0")
        save("en-font1.0-updating")
    }

    @Test fun updateError() {
        show(failed)
        focused("Tvheadend Player")
        // The message lives in the update row, below the fold; walk to it as the remote would.
        repeat(6) { press(Key.DirectionDown) }
        focused("Couldn't check for updates")
        save("en-font1.0-update-error")
    }

    @Test fun automaticUpdatesFocused() {
        show(ready)
        repeat(5) { press(Key.DirectionDown) }
        focused("Automatic updates")
        save("en-font1.0-automatic-updates-focused")
    }

    @Test fun updateUpToDate() {
        show(ready)
        repeat(6) { press(Key.DirectionDown) }
        focused("Check for updates")
        save("en-font1.0-update-up-to-date-focused")
        // Focus back up keeps the list scrolled, so the resting update row shows in full.
        press(Key.DirectionUp)
        focused("Automatic updates")
        save("en-font1.0-update-up-to-date")
    }

    @Test fun updateChecking() {
        show(ready.copy(update = UpdateScreenState(result = UpdateResult.CHECKING)))
        focused("Checking…")
        save("en-font1.0-checking")
    }

    @Test fun updateAvailableWithAnotherRowFocused() {
        show(available)
        press(Key.DirectionUp)
        focused("Automatic updates")
        save("en-font1.0-update-available-other-focus")
    }

    @Test fun downReachesEveryRowInOrderAndUpComesBack() {
        show(toFix.copy(playerVersion = "0.4.2", installAllowed = true))
        focused("Accessibility service")
        for (title in listOf("HOME app", "Install unknown apps", "Automatic updates", "Check for updates")) {
            press(Key.DirectionDown)
            focused(title)
        }
        press(Key.DirectionUp)
        focused("Automatic updates")
    }

    @Test fun googleTvHomeRowAppearsOnlyAsTheDefaultHome() {
        val set = show(ready)
        compose.onNodeWithText("Google TV home").assertExists()
        set(ready.copy(defaultHome = false))
        compose.waitForIdle()
        compose.onAllNodesWithText("Google TV home").assertCountEquals(0)
    }

    @Test fun focusStaysOnTheRowWhenItsStateChanges() {
        val set = show(toFix.copy(playerVersion = "0.4.2"))
        focused("Accessibility service")
        // Returning from Android settings with the service now on.
        set(toFix.copy(playerVersion = "0.4.2", accessibilityStatus = CompanionServiceStatus.ON))
        compose.waitForIdle()
        focused("Accessibility service")
    }

    @Test fun centerReportsTheFocusedRow() {
        show(toFix.copy(playerVersion = "0.4.2"))
        press(Key.DirectionCenter)
        assertEquals(listOf(CompanionRow.ACCESSIBILITY), activated)
    }

    @Test fun automaticUpdatesIsASwitchWithItsState() {
        val set = show(ready)
        fun node() = compose.onNodeWithText("Automatic updates").fetchSemanticsNode().config
        assertEquals(Role.Switch, node()[SemanticsProperties.Role])
        assertEquals(ToggleableState.On, node()[SemanticsProperties.ToggleableState])
        set(ready.copy(update = ready.update.copy(automatic = false)))
        compose.waitForIdle()
        assertEquals(ToggleableState.Off, node()[SemanticsProperties.ToggleableState])
    }

    @Test fun aBusyUpdateRowKeepsFocusAnnouncesAndIgnoresTheClick() {
        show(installing)
        focused("Updating to 0.5.0")
        val config = compose.onNodeWithText("Updating to 0.5.0").fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
        assertTrue(SemanticsProperties.Disabled in config)
        press(Key.DirectionCenter)
        assertTrue(activated.isEmpty())
        focused("Updating to 0.5.0")
    }

    @Test fun anIdleUpdateRowReportsTheClick() {
        show(ready.copy(hasSystemHome = false))
        repeat(5) { press(Key.DirectionDown) }
        focused("Check for updates")
        press(Key.DirectionCenter)
        assertEquals(listOf(CompanionRow.UPDATE), activated)
    }
}
