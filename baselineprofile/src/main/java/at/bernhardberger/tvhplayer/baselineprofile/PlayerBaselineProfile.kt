package at.bernhardberger.tvhplayer.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Exercises the configured real server without inspecting or supplying its credentials. */
@RunWith(AndroidJUnit4::class)
class PlayerBaselineProfile {
    @get:Rule
    val baselineProfile = BaselineProfileRule()

    @Test
    fun startupToChannelsAndTune() = baselineProfile.collect(
        packageName = PackageName,
        includeInStartupProfile = true,
    ) {
        startAtChannels()
        // First entry focuses the group tabs; Down enters the first channel row.
        if (!device.hasObject(channelRow().focused(true))) device.pressDPadDown()
        await(channelRow().focused(true), "A channel row must have D-pad focus before tuning")
        device.pressDPadCenter()
        await(By.res("player-banner"), "Tuning must open the player's passive banner")
        assertTrue("Playback must present video", device.wait(Until.gone(By.res("video-cover")), ReadyTimeout))
        // The banner hides five seconds after the first frame, leaving several seconds of tune work.
        assertTrue("Playback must settle and hide its banner", device.wait(Until.gone(By.res("player-banner")), ReadyTimeout))
        device.pressBack()
        await(channelRow(), "Back from the player must return to Channels")
    }

    private fun MacrobenchmarkScope.startAtChannels() {
        pressHome()
        startActivityAndWait()
        await(channelRow(), "Precondition: store a reachable server profile in a debug-key build of the same app ID, disable autoplay, and make channels available; Channels did not become ready")
    }

    private fun MacrobenchmarkScope.await(selector: BySelector, message: String) {
        assertTrue(message, device.wait(Until.hasObject(selector), ReadyTimeout))
    }

    private fun channelRow() = By.res(Pattern.compile("channel-row-.*"))

    private companion object {
        const val PackageName = "at.bernhardberger.tvhplayer"
        const val ReadyTimeout = 60_000L
    }
}
