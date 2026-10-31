package at.bernhardberger.tvhplayer.baselineprofile

import android.os.Build
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.regex.Pattern

/** Exercises the configured real server without inspecting or supplying its credentials. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PlayerBaselineProfile {
    @get:Rule
    val baselineProfile = BaselineProfileRule()

    @Test fun startup01Home() = journey(startup = true) {
        coldStart()
        awaitHome()
        await(By.res("home-header"), "Fresh Home must draw its header")
        await(By.res(FirstHomeCard).focused(true), "Fresh Home must focus its topmost card")
        focusToward(By.res("home-onnow-0"), KeyEvent.KEYCODE_DPAD_DOWN)
    }

    @Test fun startup02Autostart() = journey(startup = true, autoplay = true) {
        coldStart()
        awaitVideo()
        playerToHome()
    }

    @Test fun evening01BrowseAndWatch() = journey(startup = false) {
        // Benchmark 1.5 kills the process before each profileBlock, even for baseline-only
        // collection. Launch here without an explicit cold-start/reset of our own.
        startActivityAndWait()
        awaitHome()
        homeToTop()
        // There are at most seven Home rows. At the ends, further presses stay in Home.
        repeat(7) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        homeToTop()
        focusToward(By.res("home-onnow-0"), KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_MENU)
        await(By.res("browse-context-menu"), "On now must open its context menu")
        focusToward(By.res("browse-context-action-details"), KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        await(By.res("programme-details-page"), "Programme details must open")
        key(KeyEvent.KEYCODE_BACK)
        awaitHome()

        key(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitVideo()
        listOf(KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN).forEach {
            key(it)
            awaitVideo()
        }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        await(By.res("player-pause").focused(true), "Down must reveal the player's action row")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        await(By.res("player-channel-browser"), "Down must open the player's channel row")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_GUIDE)
        await(By.res("channel-schedule-panel"), "Guide must open the channel schedule")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_BACK)
        playerToHome()

        railDestination("nav-home", "nav-epg", KeyEvent.KEYCODE_DPAD_DOWN, By.res("epg-screen"))
        await(By.res("epg-programme-viewport"), "Guide programmes must draw")
        repeat(3) { key(KeyEvent.KEYCODE_PAGE_DOWN) }
        repeat(12) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
        railDestination("nav-epg", "nav-home", KeyEvent.KEYCODE_DPAD_UP, homeContent())
        awaitHome()

        homeToChannels()
        val originalLayout = Layout.entries.single { has(By.res(it.readyTag)) }
        Layout.entries.forEach { layout ->
            selectLayout(layout)
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            if (layout != Layout.List) key(KeyEvent.KEYCODE_DPAD_RIGHT)
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            if (layout != Layout.List) key(KeyEvent.KEYCODE_DPAD_LEFT)
            key(KeyEvent.KEYCODE_DPAD_UP)
        }
        selectLayout(originalLayout)
        railDestination("nav-channels", "nav-recordings", KeyEvent.KEYCODE_DPAD_DOWN, By.res("recordings-header"))
        browseArchiveAndPlay()
    }

    private fun journey(startup: Boolean, autoplay: Boolean = false, body: MacrobenchmarkScope.() -> Unit) {
        // UI setup is outside collect: Settings and View options are not startup work.
        val setup = MacrobenchmarkScope(PackageName, launchWithClearTask = true)
        setup.startAtExistingChannels()
        val originalLayout = Layout.entries.single { setup.has(By.res(it.readyTag)) }
        setup.openAutoplay()
        val originalAutoplay = setup.device.findObject(By.res("settings-row-autoplay")).isChecked
        try {
            setup.setAutoplay(false)
            setup.settingsToChannels()
            if (autoplay) {
                // Establish a playable last channel through the real UI before enabling autostart.
                setup.tuneAndReturn(originalLayout)
                setup.openAutoplay()
                setup.setAutoplay(true)
                setup.settingsToChannels()
            }
            setup.railDestination("nav-channels", "nav-home", KeyEvent.KEYCODE_DPAD_UP, homeContent())
            setup.awaitHome()
            baselineProfile.collect(packageName = PackageName, includeInStartupProfile = startup, profileBlock = body)
        } finally {
            // Also restore after a failed collection. Never clear app data or change defaults.
            try {
                setup.startAtExistingChannels()
                setup.selectLayout(originalLayout)
                setup.openAutoplay()
                setup.setAutoplay(originalAutoplay)
                setup.settingsToChannels()
            } finally {
                setup.killProcess()
            }
        }
    }

    private fun MacrobenchmarkScope.coldStart() {
        killProcess()
        pressHome()
        startActivityAndWait()
    }

    private fun MacrobenchmarkScope.startAtExistingChannels() {
        coldStart()
        assertTrue("Precondition: a stored reachable server and playable channels",
            device.wait({ !has(By.res("main-startup-root")) && (has(homeContent()) || has(By.res("player-banner"))) }, ReadyTimeout))
        if (!has(homeContent())) {
            awaitVideo()
            playerToHome()
        }
        awaitHome()
        homeToChannels()
        awaitChannels()
        await(channel().focused(true), "Channels entry must finish restoring focus")
    }

    private fun MacrobenchmarkScope.awaitHome() {
        assertTrue("Cold start must draw Home and release startup", device.wait({
            // The topmost section's first card: Right now and Recent channels push On now below
            // the fold once something has played, and a lazy row off screen has no nodes.
            !has(By.res("main-startup-root")) &&
                has(homeContent())
        }, ReadyTimeout))
    }

    private fun MacrobenchmarkScope.homeToTop() {
        focusToward(By.res("home-header").hasDescendant(By.focused(true)), KeyEvent.KEYCODE_DPAD_UP, focused = false)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        await(By.res(FirstHomeCard).focused(true), "Home must return to its topmost card")
    }

    private fun MacrobenchmarkScope.playerToHome() {
        repeat(4) {
            key(KeyEvent.KEYCODE_BACK)
            if (device.wait({ !has(By.res("main-startup-root")) && has(homeContent()) }, FocusTimeout)) {
                awaitHome()
                return
            }
        }
        assertTrue("Back must return from playback to Home", false)
    }

    private fun MacrobenchmarkScope.railDestination(current: String, target: String, direction: Int, ready: BySelector) {
        openDrawer(current)
        focusToward(By.res(target), direction)
        await(ready, "The selected browsing destination must draw")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    private fun MacrobenchmarkScope.browseArchiveAndPlay() {
        focusToward(By.res("recordings-mode-tabs").hasDescendant(By.focused(true)), KeyEvent.KEYCODE_DPAD_UP, focused = false)
        focusToward(By.res("recordings-mode-archive"), KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        await(By.res("recordings-archive-list"), "Archive must draw")
        val active = By.res("depth-active")
        await(active, "Archive must finish preparing its active list")
        // Empty content has no focusable rows; do not confuse an in-flight focus request
        // on a populated Archive with an empty library.
        if (device.findObject(active).hasObject(By.focusable(true))) {
            await(By.res("depth-active").hasDescendant(By.focused(true)), "Archive rows must receive focus")
        } else return
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_UP)
        val entry = By.res(Pattern.compile("recording-list-entry-recording:.*"))
        // An empty Archive has no rows. Folder and recording rows keep focus in depth-active.
        repeat(8) {
            val pane = device.findObject(active)
            val recording = pane?.findObject(entry)
            if (recording != null) {
                focusToward(By.res(recording.resourceName), KeyEvent.KEYCODE_DPAD_DOWN)
                key(KeyEvent.KEYCODE_MENU)
                await(By.res("browse-context-menu"), "Recording actions must open")
                val play = By.res(Pattern.compile("browse-context-action-(resume|play|watch|start_over)"))
                focusToward(play, KeyEvent.KEYCODE_DPAD_DOWN)
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                awaitVideo()
                key(KeyEvent.KEYCODE_BACK)
                await(By.res("recordings-header"), "Back must return to Recordings")
                return
            }
            val focused = device.findObject(By.focused(true))
            assertTrue("A populated Archive must keep focus in its active list",
                focused != null && pane?.hasObject(By.focused(true)) == true)
            if (focused == null) return
            val previous = focused.resourceName
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue("Archive folder must finish opening", device.wait({
                has(By.res("depth-active").hasDescendant(By.focused(true))) &&
                    !has(By.res(previous).focused(true))
            }, ReadyTimeout))
        }
        assertTrue("Archive folders must lead to recording rows", false)
    }

    private fun MacrobenchmarkScope.homeToChannels() {
        openDrawer("nav-home")
        focusToward(By.res("nav-channels"), KeyEvent.KEYCODE_DPAD_DOWN)
        awaitChannels()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    private fun MacrobenchmarkScope.awaitChannels(layout: Layout? = null) {
        assertTrue("Channels must expose a ${layout?.name ?: "configured"} row or card after startup",
            device.wait({
                !has(By.res("main-startup-root")) && has(channel()) &&
                    (layout == null || has(By.res(layout.readyTag)))
            }, ReadyTimeout))
    }

    private fun MacrobenchmarkScope.tuneAndReturn(layout: Layout) {
        focusToward(channel(), KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        await(By.res("player-banner"), "Tuning must open the player's passive banner")
        videoAndReturn(layout)
    }

    private fun MacrobenchmarkScope.awaitVideo() {
        // PlayerView hides its AndroidView subtree from accessibility; use the Compose chrome.
        await(By.res("player-banner"), "Playback must enter the player's passive banner")
        assertTrue("Playback must present video", device.wait({ !has(By.res("video-cover")) }, ReadyTimeout))
        awaitStartupReveal()
        // The banner hides five seconds after the first frame, leaving several seconds of tune work.
        assertTrue("Playback must settle and hide its banner", device.wait({ !has(By.res("player-banner")) }, ReadyTimeout))
    }

    private fun MacrobenchmarkScope.videoAndReturn(layout: Layout) {
        awaitVideo()
        key(KeyEvent.KEYCODE_BACK)
        assertTrue("Back must reveal the browsing destination", device.wait({
            has(By.res("home-header")) || has(channel())
        }, ReadyTimeout))
        if (has(By.res("home-header"))) homeToChannels()
        awaitChannels(layout)
        await(channel().focused(true), "Back must restore channel focus")
    }

    private fun MacrobenchmarkScope.awaitStartupReveal() {
        // Content can be composed while startup still owns Back and initial focus.
        assertTrue("Startup must release input", device.wait({ !has(By.res("main-startup-root")) }, ReadyTimeout))
    }

    private fun MacrobenchmarkScope.selectLayout(layout: Layout) {
        // Re-enter the current channel from the drawer before moving to the header.
        openDrawer("nav-channels")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        await(channel().focused(true), "Channels content must receive focus")
        focusToward(By.res("channels-header").hasDescendant(By.focused(true)), KeyEvent.KEYCODE_DPAD_UP, focused = false)
        focusToward(By.res("channels-view-button"), KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        await(By.res("channels-view-panel"), "View options must open")
        focusToward(By.res("channels-view-layout-immersive"), KeyEvent.KEYCODE_DPAD_UP)
        focusToward(By.res("channels-view-layout-${layout.name.lowercase()}"), KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.KEYCODE_BACK)
        awaitChannels(layout)
    }

    private fun MacrobenchmarkScope.openAutoplay() {
        openDrawer("nav-channels")
        focusToward(By.res("nav-settings"), KeyEvent.KEYCODE_DPAD_DOWN)
        await(By.res("settings-row-PLAYER"), "Settings must open")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        await(By.res("depth-active").hasDescendant(By.focused(true)), "Settings content must receive focus")
        focusToward(By.res("settings-row-GENERAL"), KeyEvent.KEYCODE_DPAD_UP)
        focusToward(By.res("settings-row-PLAYER"), KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        await(By.res("settings-row-autoplay").focused(true).checkable(true), "Player settings must focus the autoplay switch")
    }

    private fun MacrobenchmarkScope.setAutoplay(enabled: Boolean) {
        val switch = By.res("settings-row-autoplay")
        if (device.findObject(switch).isChecked != enabled) key(KeyEvent.KEYCODE_DPAD_CENTER)
        await(By.copy(switch).checked(enabled), "Autoplay must be $enabled")
    }

    private fun MacrobenchmarkScope.settingsToChannels() {
        key(KeyEvent.KEYCODE_BACK)
        await(By.res("settings-row-PLAYER").focused(true), "Back must return to the Playback row in Settings root")
        openDrawer("nav-settings")
        focusToward(By.res("nav-channels"), KeyEvent.KEYCODE_DPAD_UP)
        awaitChannels()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        await(channel().focused(true), "Channels content must receive focus")
    }

    private fun MacrobenchmarkScope.openDrawer(current: String) {
        repeat(3) {
            if (has(By.res(current).focused(true))) return
            key(KeyEvent.KEYCODE_BACK)
            // Do not send another Back while the drawer is still acquiring focus: it can exit.
            if (device.wait({ has(By.res(current).focused(true)) }, FocusTimeout)) return
        }
        await(By.res(current).focused(true), "Back must focus the current drawer destination")
    }

    private fun MacrobenchmarkScope.focusToward(selector: BySelector, direction: Int, focused: Boolean = true) {
        val target = if (focused) By.copy(selector).focused(true) else selector
        repeat(10) {
            if (has(target)) return
            val previous = device.findObject(By.focused(true))?.resourceName
            key(direction)
            device.wait({ has(target) || (previous != null && !has(By.res(previous).focused(true))) }, FocusTimeout)
        }
        await(target, "D-pad must reach the requested item")
    }

    private fun MacrobenchmarkScope.key(code: Int) {
        assertTrue("Key $code must be delivered", device.pressKeyCode(code))
    }

    private fun MacrobenchmarkScope.await(selector: BySelector, message: String) {
        assertTrue(message, device.wait({ has(selector) }, ReadyTimeout))
    }

    private fun MacrobenchmarkScope.has(selector: BySelector): Boolean {
        // Refresh observations rather than navigating against a stale accessibility snapshot.
        if (Build.VERSION.SDK_INT >= 33) uiAutomation.clearCache()
        return device.hasObject(selector)
    }

    private fun channel() = By.res(Pattern.compile("channel-(row|card)-.*"))

    private fun homeContent() = By.res(Pattern.compile("home-((activity|recent|onnow)-0|section-.*)"))

    private enum class Layout(val readyTag: String) {
        Immersive("channels-immersive-cards"),
        Grid("channels-grid"),
        List("channels-list"),
    }

    private companion object {
        const val PackageName = "at.bernhardberger.tvhplayer"
        const val ReadyTimeout = 60_000L
        const val FocusTimeout = 2_000L
    }
}

private val FirstHomeCard = java.util.regex.Pattern.compile("home-(activity|recent|onnow)-0")

private var failureShots = 0

/** Like JUnit's assertTrue, but keeps a screenshot of the failing frame in the test output. */
private fun assertTrue(message: String, condition: Boolean) {
    if (condition) return
    val args = InstrumentationRegistry.getArguments()
    args.getString("additionalTestOutputDir")?.let { dir ->
        val name = "failure-${++failureShots}-" + message.replace(Regex("[^A-Za-z0-9]+"), "-").take(60) + ".png"
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(dir, name))
    }
    org.junit.Assert.fail(message)
}
