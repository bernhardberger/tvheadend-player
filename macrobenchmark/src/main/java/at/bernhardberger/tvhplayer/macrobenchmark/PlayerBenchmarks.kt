package at.bernhardberger.tvhplayer.macrobenchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Real-server measurements. Only screen/focus readiness is asserted, never speed. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class PlayerBenchmarks {
    @get:Rule val benchmark = MacrobenchmarkRule()

    private val iterations: Int
        get() = (InstrumentationRegistry.getArguments().getString("iterations") ?: "3")
            .toInt().also { require(it > 0) { "iterations must be positive" } }

    @Test fun coldStart() = startup(StartupMode.COLD)
    @Test fun warmStart() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) = benchmark.measureRepeated(
        packageName = PackageName,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric(), section("P44:channelScope")),
        compilationMode = CompilationMode.Partial(),
        startupMode = mode,
        iterations = iterations,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        await(channel(), "Configured Channels must be ready; disable autoplay")
    }

    @Test fun drawerFirstVisit() = interaction("P44:measure:sidebar", "P44:guideIndex") {
        drawerTour()
    }

    @Test fun drawerWarmVisit() = interaction("P44:measure:sidebar", setup = {
        drawerTour()
    }) { drawerTour() }

    // Select View options before running one of these methods; don't silently time another layout.
    @Test fun channelsImmersive() = channelScroll("channels-immersive-cards", horizontal = true)
    @Test fun channelsGrid() = channelScroll("channels-grid")
    @Test fun channelsList() = channelScroll("channels-list")

    private fun channelScroll(tag: String, horizontal: Boolean = false) = interaction(
        "P44:focus:channel", "P44:measure:picon", setup = {
            await(By.res(tag), "Precondition: choose the matching Channels layout in View options")
            focusChannel()
        },
    ) {
        keys(if (horizontal) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_DOWN, 12)
        keys(if (horizontal) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_UP, 12)
        await(By.res(tag), "Channels layout must remain visible")
    }

    @Test fun guideFirstOpen() = interaction("P44:guideIndex", "P44:measure:guide") {
        navigate("nav-epg", "epg-screen")
        focusGuide()
    }

    @Test fun guideNavigation() = interaction("P44:focus:guide", "P44:measure:guide", setup = {
        navigate("nav-epg", "epg-screen")
        focusGuide()
    }) {
        keys(KeyEvent.KEYCODE_DPAD_DOWN, 5)
        keys(KeyEvent.KEYCODE_DPAD_UP, 5)
        keys(KeyEvent.KEYCODE_DPAD_RIGHT, 5)
        keys(KeyEvent.KEYCODE_DPAD_LEFT, 5)
        key(KeyEvent.KEYCODE_CHANNEL_DOWN)
        key(KeyEvent.KEYCODE_CHANNEL_UP)
        await(By.res("epg-programme-viewport"), "Guide must remain visible")
    }

    @Test fun sideRail() = interaction("P44:focus:rail", "P44:measure:picon", setup = { tune() }) {
        openPlayerRail()
        key(KeyEvent.KEYCODE_BACK)
        key(KeyEvent.KEYCODE_BACK)
        await(By.res("video-surface"), "Closing the rail must leave playback")
    }

    @Test fun zapRailScroll() = interaction("P44:focus:rail", "P44:measure:picon", setup = { tune() }) {
        openPlayerRail()
        keys(KeyEvent.KEYCODE_DPAD_RIGHT, 12)
        keys(KeyEvent.KEYCODE_DPAD_LEFT, 12)
        key(KeyEvent.KEYCODE_BACK)
        key(KeyEvent.KEYCODE_BACK)
        await(By.res("video-surface"), "Rail scrolling must leave playback available")
    }

    @Test fun recordingsTabs() = interaction("P44:measure:recordings", setup = {
        navigate("nav-recordings", "recordings-header")
        await(By.res("recordings-mode-tabs").hasDescendant(By.focused(true)), "Start on a recordings tab")
    }) {
        keys(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        keys(KeyEvent.KEYCODE_DPAD_LEFT, 2)
        await(By.res("recordings-mode-tabs"), "Recordings tabs must remain visible")
    }

    @Test fun recordingsScroll() = interaction("P44:measure:recordings", setup = {
        navigate("nav-recordings", "recordings-header")
        // Archive is first; Schedule is second. Avoid opening an archive folder or recording.
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        await(By.res("recordings-schedule-list").hasDescendant(By.focused(true)),
            "Precondition: nonempty Schedule with enough entries to scroll")
    }) {
        keys(KeyEvent.KEYCODE_DPAD_DOWN, 12)
        keys(KeyEvent.KEYCODE_DPAD_UP, 12)
        await(By.res("recordings-schedule-list"), "Schedule must remain visible")
    }

    @Test fun settingsGeneral() = settingsPage("GENERAL")
    @Test fun settingsConnection() = settingsPage("CONNECTION")
    @Test fun settingsAbout() = settingsPage("settings-about")

    private fun settingsPage(id: String) = interaction(setup = {
        navigate("nav-settings", "settings-level-settings-root")
        focusWithDown(By.res("settings-row-$id"))
    }) {
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        await(activeLevel(id), "Settings child must open")
        key(KeyEvent.KEYCODE_BACK)
        await(activeLevel("settings-root"), "Back must return to Settings root")
    }

    @Test fun playerTune() = interaction("P44:measure:picon", setup = { focusChannel() }) { tune() }

    @Test fun playerControls() = interaction("P44:focus:control", setup = { tune() }) {
        repeat(5) {
            key(KeyEvent.KEYCODE_DPAD_UP, 2_500)
            await(By.res("player-actions"), "Up must reveal controls")
            key(KeyEvent.KEYCODE_BACK, 2_000)
        }
    }

    @Test fun playerZap() = interaction("P44:measure:picon", setup = { tune() }) {
        // D-pad Up/Down navigate chrome; Channel Up/Down actually tune adjacent channels.
        repeat(5) {
            key(KeyEvent.KEYCODE_CHANNEL_UP, 2_500)
            key(KeyEvent.KEYCODE_CHANNEL_DOWN, 2_500)
        }
        await(By.res("video-surface"), "Zap journey must stay in the player")
    }

    private fun interaction(
        vararg traces: String,
        setup: MacrobenchmarkScope.() -> Unit = {},
        measure: MacrobenchmarkScope.() -> Unit,
    ) = benchmark.measureRepeated(
        packageName = PackageName,
        metrics = listOf(FrameTimingMetric(), section("P44:input:down")) + traces.map(::section),
        compilationMode = CompilationMode.Partial(),
        iterations = iterations,
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
            await(channel(), "Precondition: stored server profile, autoplay off, populated Channels")
            setup()
        },
        measureBlock = measure,
    )

    private fun MacrobenchmarkScope.drawerTour() {
        openDrawer("nav-channels")
        listOf("nav-epg" to "epg-screen", "nav-recordings" to "recordings-header",
            "nav-settings" to "settings-level-settings-root").forEach { (nav, page) ->
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            await(By.res(nav).focused(true), "Drawer must focus $nav")
            await(By.res(page), "Drawer focus must load its page")
        }
        listOf("nav-recordings", "nav-epg", "nav-channels").forEach { nav ->
            key(KeyEvent.KEYCODE_DPAD_UP)
            await(By.res(nav).focused(true), "Drawer must focus $nav")
        }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        await(channel(), "Return to Channels")
    }

    private fun MacrobenchmarkScope.openDrawer(current: String) {
        repeat(3) {
            if (device.hasObject(By.res(current).focused(true))) return
            key(KeyEvent.KEYCODE_BACK)
        }
        await(By.res(current).focused(true), "Back must focus the current drawer destination")
    }

    private fun MacrobenchmarkScope.navigate(nav: String, page: String) {
        openDrawer("nav-channels")
        focusWithDown(By.res(nav))
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        await(By.res(page), "Destination must become ready")
    }

    private fun MacrobenchmarkScope.focusWithDown(selector: BySelector) {
        repeat(10) {
            if (device.hasObject(By.copy(selector).focused(true))) return
            key(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        await(By.copy(selector).focused(true), "D-pad must reach the requested item")
    }

    private fun MacrobenchmarkScope.focusChannel() = focusWithDown(channel())

    private fun MacrobenchmarkScope.focusGuide() {
        val viewport = By.res("epg-programme-viewport")
        val focusedGrid = By.res("epg-programme-viewport").hasDescendant(By.focused(true))
        await(viewport, "Precondition: populated Guide programme viewport")
        repeat(10) {
            if (device.hasObject(By.copy(viewport).focused(true)) || device.hasObject(focusedGrid)) return
            key(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        assertTrue("Guide programme viewport or a cell must own focus",
            device.hasObject(By.copy(viewport).focused(true)) || device.hasObject(focusedGrid))
    }

    private fun MacrobenchmarkScope.tune() {
        focusChannel()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        await(By.res("player-banner"), "Tune must open the player banner")
        assertTrue("Playback must present video", device.wait(Until.gone(By.res("video-cover")), ReadyTimeout))
        assertTrue("Playback banner must settle", device.wait(Until.gone(By.res("player-banner")), ReadyTimeout))
    }

    private fun MacrobenchmarkScope.openPlayerRail() {
        key(KeyEvent.KEYCODE_DPAD_UP, 2_000)
        key(KeyEvent.KEYCODE_DPAD_DOWN, 2_000)
        await(By.res(Pattern.compile("player-channel-card-.*")).focused(true), "Player rail must own focus")
    }

    private fun MacrobenchmarkScope.keys(code: Int, count: Int) = repeat(count) { key(code) }
    private fun MacrobenchmarkScope.key(code: Int, delay: Long = 400) {
        device.pressKeyCode(code)
        SystemClock.sleep(delay)
    }

    private fun MacrobenchmarkScope.await(selector: BySelector, message: String) {
        assertTrue(message, device.wait(Until.hasObject(selector), ReadyTimeout))
    }

    private fun activeLevel(id: String) = By.res("settings-level-$id").hasAncestor(By.res("depth-active"))
    private fun channel() = By.res(Pattern.compile("channel-(row|card)-.*"))
    private fun section(name: String) = TraceSectionMetric(name, TraceSectionMetric.Mode.Sum)

    private companion object {
        const val PackageName = "at.bernhardberger.tvhplayer"
        const val ReadyTimeout = 60_000L
    }
}
