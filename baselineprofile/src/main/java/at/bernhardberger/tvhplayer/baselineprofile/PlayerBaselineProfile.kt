package at.bernhardberger.tvhplayer.baselineprofile

import android.os.Build
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import org.junit.Assert.assertTrue
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

    @Test fun startup01Immersive() = startup(Layout.Immersive)

    @Test fun startup02Grid() = startup(Layout.Grid)

    @Test fun startup03List() = startup(Layout.List)

    @Test fun startup04AutostartPlayback() = startup(Layout.Immersive, autoplay = true)

    private fun startup(layout: Layout, autoplay: Boolean = false) {
        // UI setup is outside collect: Settings and View options are not startup work.
        val setup = MacrobenchmarkScope(PackageName, launchWithClearTask = true)
        setup.startAtExistingChannels()
        val originalLayout = Layout.entries.single { setup.has(By.res(it.readyTag)) }
        setup.openAutoplay()
        val originalAutoplay = setup.device.findObject(By.res("settings-row-autoplay")).isChecked
        try {
            setup.setAutoplay(false)
            setup.settingsToChannels()
            setup.selectLayout(layout)
            if (autoplay) {
                // Establish a playable last channel through the real UI before enabling autostart.
                setup.tuneAndReturn(layout)
                setup.openAutoplay()
                setup.setAutoplay(true)
                setup.settingsToChannels()
            }
            baselineProfile.collect(packageName = PackageName, includeInStartupProfile = true) {
                coldStart()
                if (autoplay) {
                    await(By.res("video-surface"), "Autostart must enter the player")
                    assertTrue("Autostart must bypass Channels", !has(channel()))
                    videoAndReturn(layout)
                } else {
                    awaitChannels(layout)
                    await(channel().focused(true), "Cold start must assign channel focus")
                    tuneAndReturn(layout)
                }
            }
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
            device.wait({ !has(By.res("main-startup-root")) && (has(channel()) || has(By.res("video-surface"))) }, ReadyTimeout))
        if (!has(channel())) {
            awaitVideo()
            key(KeyEvent.KEYCODE_BACK)
        }
        awaitChannels()
        await(channel().focused(true), "Channels entry must finish restoring focus")
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
        await(By.res("video-surface"), "Playback must have a video surface")
        assertTrue("Playback must present video", device.wait({ !has(By.res("video-cover")) }, ReadyTimeout))
        awaitStartupReveal()
        // The banner hides five seconds after the first frame, leaving several seconds of tune work.
        assertTrue("Playback must settle and hide its banner", device.wait({ !has(By.res("player-banner")) }, ReadyTimeout))
    }

    private fun MacrobenchmarkScope.videoAndReturn(layout: Layout) {
        awaitVideo()
        key(KeyEvent.KEYCODE_BACK)
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
