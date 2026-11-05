package at.bernhardberger.tvhplayer.appliance

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isFocusable
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
import org.junit.Assert.assertFalse
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
    private lateinit var backDispatcher: androidx.activity.OnBackPressedDispatcher
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

    @Test fun updateAvailableWithAutomaticUpdatesOffOffersAManualRetry() {
        show(available.copy(update = available.update.copy(automatic = false)))
        compose.onNodeWithText("Update ready. Choose Update now and keep this screen open, or turn on automatic updates.", substring = true)
            .assertExists()
        compose.onAllNodesWithText("installs while the TV is in standby", substring = true)
            .assertCountEquals(0)
        focused("Update now")
        save("en-font1.0-update-available-manual")
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
        repeat(9) { press(Key.DirectionDown) }
        focused("Couldn't check for updates")
        save("en-font1.0-update-error")
    }

    @Test fun automaticUpdatesFocused() {
        show(ready)
        repeat(8) { press(Key.DirectionDown) }
        focused("Automatic updates")
        save("en-font1.0-automatic-updates-focused")
    }

    @Test fun updateUpToDate() {
        show(ready)
        repeat(9) { press(Key.DirectionDown) }
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
        for (title in listOf("HOME app", "Open when the TV turns on", "Open live TV", "Open the Guide",
            "Install unknown apps", "Automatic updates", "Check for updates")) {
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
        repeat(8) { press(Key.DirectionDown) }
        focused("Check for updates")
        press(Key.DirectionCenter)
        assertEquals(listOf(CompanionRow.UPDATE), activated)
    }

    // ---- Functions: the section on the main screen, the function pages and the capture ----

    private var content by mutableStateOf(ready)
    private var capture by mutableStateOf<CaptureState>(CaptureState.Idle)
    private val capturesStarted = mutableListOf<CompanionFunction>()
    private var capturesCancelled = 0

    private fun withButtons(
        liveTv: ButtonBinding = ButtonBinding.Default,
        guide: ButtonBinding = ButtonBinding.Default,
        service: CompanionServiceStatus = CompanionServiceStatus.ON,
    ) = ready.copy(accessibilityStatus = service, functions = CompanionFunctions(true, liveTv, guide))

    /** The whole content, with the service and the store replaced by in-memory stand-ins. */
    private fun showContent(initial: CompanionScreenState) {
        content = initial
        capture = CaptureState.Idle
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                    CompanionContent(
                        state = content,
                        capture = capture,
                        onRow = { activated += it },
                        onStartCapture = {
                            capturesStarted += it
                            capture = CaptureState.Waiting(it, SystemClock.elapsedRealtime() + ButtonCapture.TIMEOUT_MILLIS)
                            true
                        },
                        onCancelCapture = {
                            capturesCancelled++
                            capture = CaptureState.Cancelled
                        },
                        onDismissCapture = {
                            if (capture !is CaptureState.Waiting) capture = CaptureState.Idle
                        },
                        onAssign = { function, binding ->
                            content.functions.assign(function, binding).also {
                                content = content.copy(functions = it.functions)
                            }
                        },
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Back reaches the screen through the activity's dispatcher, as the system delivers it. */
    private fun back() {
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun down(times: Int) = repeat(times) { press(Key.DirectionDown) }

    /** The remote's Down presses from the first row to the live TV or Guide row. */
    private fun openFunction(function: CompanionFunction, from: Int = 0) {
        down((if (function == CompanionFunction.LIVE_TV) 5 else 6) - from)
        focused(if (function == CompanionFunction.LIVE_TV) "Open live TV" else "Open the Guide")
        press(Key.DirectionCenter)
    }

    /** Shows [next] with the clock stopped, so the capture card's countdown starts at its deadline. */
    private fun setWhileFrozen(next: CaptureState) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            capture = next
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(48)
    }

    private fun nothingIsFocusable() = compose.onAllNodes(isFocusable()).assertCountEquals(0)

    @Test fun functionsAllSetTop() {
        showContent(withButtons())
        focused("Tvheadend Player")
        compose.onNodeWithText("This TV opens Tvheadend Player at startup, on HOME and with the TV and Guide buttons.")
            .assertExists()
        save("functions-01-all-set-top")
    }

    @Test fun functionsDefault() {
        showContent(withButtons())
        down(6)
        focused("Open the Guide")
        compose.onNodeWithText("Open when the TV turns on").assertExists()
        compose.onAllNodesWithText("Paused until the service is on").assertCountEquals(0)
        save("functions-02-functions-default")
    }

    @Test fun aBlockedAccessibilityServiceOffersAndroidSettingsAndAToggleBeforeAdb() {
        showContent(withButtons(service = CompanionServiceStatus.BLOCKED))
        compose.onNodeWithText("Blocked by this TV").assertExists()
        compose.onNodeWithText("Paused until the service can run").assertExists()
        focused("Accessibility service")
        compose.onNodeWithText("Blocked").assertExists()
        compose.onNodeWithText("On, but not running · Opens Android accessibility settings").assertExists()
        compose.onNodeWithText("Switch the service off and on in Android settings. If that does not help, this TV may need a one-time auto-start permission over ADB.")
            .assertExists()
        press(Key.DirectionCenter)
        assertEquals(listOf(CompanionRow.ACCESSIBILITY), activated)
        save("functions-03-accessibility-blocked")
    }

    @Test fun anOffServiceNamesTheFunctionsAsPaused() {
        showContent(withButtons(service = CompanionServiceStatus.OFF))
        compose.onNodeWithText("Paused until the service is on").assertExists()
    }

    @Test fun functionStatesShowAChangedButtonAndAFunctionThatIsOff() {
        showContent(withButtons(liveTv = ButtonBinding.Custom(4001), guide = ButtonBinding.NotSet))
        down(6)
        focused("Open the Guide")
        compose.onNodeWithText("TV button").assertExists()
        compose.onNodeWithText("Default: TV button").assertExists()
        compose.onNodeWithText("Function off").assertExists()
        assertEquals("Not set", compose.onNodeWithText("Open the Guide")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        save("functions-04-function-states")
    }

    @Test fun theStartupSwitchReportsItsRowAndKeepsItsState() {
        showContent(withButtons())
        down(4)
        focused("Open when the TV turns on")
        val config = compose.onNodeWithText("Open when the TV turns on").fetchSemanticsNode().config
        assertEquals(Role.Switch, config[SemanticsProperties.Role])
        assertEquals(ToggleableState.On, config[SemanticsProperties.ToggleableState])
        press(Key.DirectionCenter)
        assertEquals(listOf(CompanionRow.STARTUP), activated)
        content = content.copy(functions = content.functions.copy(startup = false))
        compose.waitForIdle()
        focused("Open when the TV turns on")
        assertEquals(ToggleableState.Off, compose.onNodeWithText("Open when the TV turns on")
            .fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
    }

    @Test fun aChangedFunctionPageOffersChangeRestoreAndRemoveAndBackReturnsToItsRow() {
        showContent(withButtons(liveTv = ButtonBinding.Custom(4001)))
        openFunction(CompanionFunction.LIVE_TV)
        compose.onNodeWithText("Functions · Open live TV").assertExists()
        compose.onNodeWithText("The TV button opens live TV in Tvheadend Player. The default is the TV button.").assertExists()
        compose.onNodeWithText("What changes").assertExists()
        focused("Change button")
        compose.onNodeWithText("Current: TV button").assertExists()
        compose.onNodeWithText("Restore default").assertExists()
        compose.onNodeWithText("Remove button").assertExists()
        assertTrue(activated.isEmpty())
        save("functions-05-function-page-changed")
        back()
        focused("Open live TV")
        assertEquals(CaptureState.Idle, capture)
        compose.onNodeWithText("Open when the TV turns on").assertExists()
    }

    @Test fun aFunctionWithoutAButtonOffersSetAndRestoreOnly() {
        showContent(withButtons(guide = ButtonBinding.NotSet))
        openFunction(CompanionFunction.GUIDE)
        compose.onNodeWithText("No button is set, so this function is off.").assertExists()
        compose.onNodeWithText("How it works").assertExists()
        focused("Set a button")
        compose.onNodeWithText("Restore default").assertExists()
        compose.onAllNodesWithText("Remove button").assertCountEquals(0)
        save("functions-06-function-page-not-set")
        back()
        focused("Open the Guide")
    }

    @Test fun aDefaultFunctionPageHidesRestore() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        focused("Change button")
        compose.onNodeWithText("Current: TV button").assertExists()
        compose.onAllNodesWithText("Restore default").assertCountEquals(0)
        compose.onNodeWithText("Remove button").assertExists()
    }

    @Test fun restoreActsAtOnceConfirmsOnTheLeftAndFocusesTheFirstRow() {
        showContent(withButtons(liveTv = ButtonBinding.Custom(4001)))
        openFunction(CompanionFunction.LIVE_TV)
        down(1)
        focused("Restore default")
        press(Key.DirectionCenter)
        assertEquals(ButtonBinding.Default, content.functions.liveTv)
        compose.onNodeWithText("Default restored").assertExists()
        focused("Change button")
        compose.onAllNodesWithText("Restore default").assertCountEquals(0)
    }

    @Test fun removeActsAtOnceConfirmsOnTheLeftAndFocusesTheFirstRow() {
        showContent(withButtons(liveTv = ButtonBinding.Custom(4001)))
        openFunction(CompanionFunction.LIVE_TV)
        down(2)
        focused("Remove button")
        press(Key.DirectionCenter)
        assertEquals(ButtonBinding.NotSet, content.functions.liveTv)
        compose.onNodeWithText("Button removed").assertExists()
        focused("Set a button")
        compose.onAllNodesWithText("Remove button").assertCountEquals(0)
        back()
        compose.onNodeWithText("Not set").assertExists()
        focused("Open live TV")
    }

    private fun assertSetIsOffWhenTheService(service: CompanionServiceStatus) {
        showContent(withButtons(guide = ButtonBinding.NotSet, service = service))
        // A service that is not on takes the initial focus: the Accessibility row, one below the first.
        openFunction(CompanionFunction.GUIDE, from = 1)
        focused("Set a button")
        compose.onNodeWithText("Needs the accessibility service").assertExists()
        assertTrue(SemanticsProperties.Disabled in compose.onNodeWithText("Set a button").fetchSemanticsNode().config)
        press(Key.DirectionCenter)
        assertTrue(capturesStarted.isEmpty())
        focused("Set a button")
    }

    @Test fun setIsOffWhileTheServiceIsOff() = assertSetIsOffWhenTheService(CompanionServiceStatus.OFF)

    @Test fun setIsOffWhileTheServiceIsBlocked() = assertSetIsOffWhenTheService(CompanionServiceStatus.BLOCKED)

    @Test fun waitingForAButtonLeavesNothingFocusableAndBackCancelsToThePage() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        focused("Change button")
        press(Key.DirectionCenter)
        assertEquals(listOf(CompanionFunction.LIVE_TV), capturesStarted)
        compose.onNodeWithText("Waiting for a button…").assertExists()
        compose.onNodeWithText("Press a button").assertExists()
        nothingIsFocusable()
        press(Key.DirectionCenter)
        assertEquals(1, capturesStarted.size)
        back()
        assertEquals(1, capturesCancelled)
        assertEquals(CaptureState.Idle, capture)
        focused("Change button")
        compose.onNodeWithText("Functions · Open live TV").assertExists()
        back()
        focused("Open live TV")
    }

    @Test fun theCaptureCardIsALiveRegion() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        press(Key.DirectionCenter)
        val config = compose.onNodeWithText("Waiting for a button…").fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
    }

    @Test fun captureWaiting() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        press(Key.DirectionCenter)
        // 7.3 s left, so the bar reads about 63% after the capture's one second of settling.
        // The frozen clock keeps the idle wait from running the bar's countdown to its end.
        setWhileFrozen(CaptureState.Waiting(CompanionFunction.LIVE_TV, SystemClock.elapsedRealtime() + 7_300))
        compose.onNodeWithText("Waiting for a button…").assertExists()
        nothingIsFocusable()
        save("functions-07-capture-waiting")
    }

    @Test fun captureRefused() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        press(Key.DirectionCenter)
        setWhileFrozen(CaptureState.Refused(CompanionFunction.LIVE_TV, "OK", SystemClock.elapsedRealtime() + 10_000))
        compose.onNodeWithText("OK can't be used").assertExists()
        compose.onNodeWithText("Press a different button.").assertExists()
        nothingIsFocusable()
        save("functions-08-capture-refused")
    }

    @Test fun aRefusalDoesNotChangeTheCaptureCardsHeight() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        press(Key.DirectionCenter)
        fun card() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .fetchSemanticsNode().boundsInRoot
        setWhileFrozen(CaptureState.Waiting(CompanionFunction.LIVE_TV, SystemClock.elapsedRealtime() + 10_000))
        val waiting = card()
        setWhileFrozen(CaptureState.Refused(CompanionFunction.LIVE_TV, "OK", SystemClock.elapsedRealtime() + 10_000))
        compose.onNodeWithText("OK can't be used").assertExists()
        assertEquals(waiting.height, card().height, 0f)
        assertEquals(waiting.bottom, card().bottom, 0f)
    }

    @Test fun captureTimedOutOffersTryAgainFirstAndCancel() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        press(Key.DirectionCenter)
        capture = CaptureState.TimedOut(CompanionFunction.LIVE_TV)
        compose.waitForIdle()
        compose.onNodeWithText("No button arrived").assertExists()
        focused("Try again")
        save("functions-09-capture-timed-out")
        press(Key.DirectionCenter)
        assertEquals(2, capturesStarted.size)
        compose.onNodeWithText("Waiting for a button…").assertExists()
        capture = CaptureState.TimedOut(CompanionFunction.LIVE_TV)
        compose.waitForIdle()
        down(1)
        focused("Cancel")
        press(Key.DirectionCenter)
        focused("Change button")
        assertEquals(CaptureState.Idle, capture)
    }

    @Test fun aCapturedButtonThatMovedSaysWhatLostItAndFocusesChange() {
        showContent(withButtons())
        openFunction(CompanionFunction.GUIDE)
        press(Key.DirectionCenter)
        // The service stores the capture before it reports it.
        val change = content.functions.assign(CompanionFunction.GUIDE, ButtonBinding.Custom(170))
        content = content.copy(functions = change.functions)
        capture = CaptureState.Captured(CompanionFunction.GUIDE, 170, change.moved.single())
        compose.waitForIdle()
        compose.onNodeWithText("Button moved").assertExists()
        compose.onNodeWithText("The TV button now opens the Guide.").assertExists()
        compose.onNodeWithText("Open live TV lost a button").assertExists()
        assertEquals(ButtonBinding.Custom(4001), content.functions.liveTv)
        focused("Change button")
        compose.onNodeWithText("Current: TV button").assertExists()
        assertEquals(CaptureState.Idle, capture)
        save("functions-10-capture-moved")
    }

    @Test fun aPlainCapturedButtonSaysSaved() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        press(Key.DirectionCenter)
        val change = content.functions.assign(CompanionFunction.LIVE_TV, ButtonBinding.Custom(4001))
        content = content.copy(functions = change.functions)
        capture = CaptureState.Captured(CompanionFunction.LIVE_TV, 4001, null)
        compose.waitForIdle()
        compose.onNodeWithText("Button saved").assertExists()
        compose.onNodeWithText("The TV button now opens live TV.").assertExists()
        focused("Change button")
        assertEquals(CaptureState.Idle, capture)
    }

    @Test fun aCaptureTheServiceCancelsReturnsToThePageAndBackThenLeavesIt() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        press(Key.DirectionCenter)
        capture = CaptureState.Cancelled
        compose.waitForIdle()
        focused("Change button")
        assertEquals(CaptureState.Idle, capture)
        back()
        assertEquals(0, capturesCancelled)
        focused("Open live TV")
    }

    @Test fun aCaptureAfterAnEarlierCancelIsNotMistakenForTheEarlierOne() {
        showContent(withButtons())
        openFunction(CompanionFunction.LIVE_TV)
        press(Key.DirectionCenter)
        back()
        assertEquals(CaptureState.Idle, capture)
        // Dismissed results cannot be mistaken for the second capture's waiting state.
        press(Key.DirectionCenter)
        compose.onNodeWithText("Waiting for a button…").assertExists()
        assertFalse(capture is CaptureState.Cancelled)
    }
}
